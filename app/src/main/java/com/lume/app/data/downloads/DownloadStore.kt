package com.lume.app.data.downloads

import android.content.Context
import com.lume.app.R
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.network.AppHttp
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.source.SourceRegistry
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.ComicInfo
import com.lume.app.domain.ImageBytes
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.toXml
import com.lume.app.data.prefs.AppPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TitleDiskMeta(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val description: String? = null,
    val status: String? = null,
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
)

@Serializable
data class ChapterDiskMeta(
    val chapterId: String,
    val chapterName: String,
    val url: String = "",
    val number: String? = null,
)

/** A place downloads can live (Settings → Downloads → Storage). */
data class StorageChoice(
    /** Folder that holds `downloads/`; empty for the internal default. */
    val path: String,
    val removable: Boolean,
    val freeBytes: Long,
)

class DownloadStore(
    private val context: Context,
    private val dao: LibraryDao,
    private val http: AppHttp,
    private val prefs: AppPreferences? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val progressMap = ConcurrentHashMap<String, Float>()
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun keyOf(sourceId: String, titleId: String, chapterId: String) =
        "$sourceId|$titleId|$chapterId"

    fun safe(value: String) =
        value.replace(Regex("""[<>:"/\\|?*]"""), "_").replace(' ', '_')

    /** `downloads/` in the chosen storage (falls back to internal storage when it is gone). */
    fun downloadsDir(): File {
        val chosen = prefs?.downloadsRoot?.value.orEmpty()
        val base = chosen.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory || it.mkdirs() } ?: context.filesDir
        return File(base, "downloads")
    }

    /** Every `downloads/` folder Lume may have written to, the current one first. */
    private fun allDownloadDirs(): List<File> =
        (listOf(downloadsDir(), File(context.filesDir, "downloads")) +
            context.getExternalFilesDirs(null).filterNotNull().map { File(it, "downloads") })
            .distinctBy { it.absolutePath }

    /**
     * Internal storage plus every mounted external volume of the app (SD cards included).
     * The free space is only shown next to each choice, so `usableSpace` is enough.
     */
    @android.annotation.SuppressLint("UsableSpace")
    fun storageChoices(): List<StorageChoice> {
        val internal = StorageChoice(path = "", removable = false, freeBytes = context.filesDir.usableSpace)
        val external = context.getExternalFilesDirs(null).filterNotNull().filter { it.isDirectory || it.mkdirs() }.map { dir ->
            StorageChoice(
                path = dir.absolutePath,
                removable = runCatching { android.os.Environment.isExternalStorageRemovable(dir) }.getOrDefault(false),
                freeBytes = dir.usableSpace,
            )
        }
        return listOf(internal) + external
    }

    /** Bytes on disk per title ("sourceId|titleId") and in total. */
    suspend fun usage(): Pair<Map<String, Long>, Long> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val perTitle = HashMap<String, Long>()
        for (download in dao.allDownloads()) {
            val paths = runCatching { json.decodeFromString<List<String>>(download.pagePathsJson) }.getOrDefault(emptyList())
            val bytes = paths.sumOf { File(it).takeIf(File::isFile)?.length() ?: 0L }
            val key = "${download.sourceId}|${download.titleId}"
            perTitle[key] = (perTitle[key] ?: 0L) + bytes
        }
        perTitle to perTitle.values.sum()
    }

    private fun titleDir(sourceId: String, titleId: String): File {
        val dir = File(downloadsDir(), "${safe(sourceId)}/${safe(titleId)}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun chapterDir(sourceId: String, titleId: String, chapterId: String): File {
        val dir = File(titleDir(sourceId, titleId), safe(chapterId))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun downloadedChapterIds(sourceId: String, titleId: String): Set<String> =
        dao.downloadedChapterIds(sourceId, titleId).toSet()

    suspend fun localPages(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            val paths = runCatching { json.decodeFromString<List<String>>(meta.pagePathsJson) }.getOrNull()
            if (paths != null) {
                val pages = mutableListOf<ComicPage>()
                var ok = true
                // A CBZ download stores the archive as its only path.
                paths.singleOrNull()?.takeIf { it.endsWith(".cbz") }?.let { cbz ->
                    File(cbz).takeIf(File::isFile)?.let { return pagesFromCbz(it) }
                }
                for ((i, path) in paths.withIndex()) {
                    val file = File(path)
                    if (!file.exists()) {
                        ok = false
                        break
                    }
                    pages += ComicPage(index = i, imageUrl = file.absolutePath)
                }
                if (ok && pages.isNotEmpty()) return pages
            }
        }
        return scanChapterDir(sourceId, titleId, chapterId)
    }

    private fun scanChapterDir(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        for (root in allDownloadDirs()) {
            val titleFolder = File(root, "${safe(sourceId)}/${safe(titleId)}")
            // Recovered rows may already store safe folder names as ids.
            for (dir in listOf(File(titleFolder, safe(chapterId)), File(titleFolder, chapterId))) {
                if (dir.isDirectory) listPagesIn(dir)?.let { return it }
            }
        }
        return null
    }

    private fun listPagesIn(dir: File): List<ComicPage>? {
        File(dir, CBZ_NAME).takeIf { it.isFile }?.let { return pagesFromCbz(it) }
        val files = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name != "meta.json" }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) return null
        return files.mapIndexed { i, f -> ComicPage(index = i, imageUrl = f.absolutePath) }
    }

    /** Pages of a CBZ download, unpacked once into the cache (the reader needs plain files). */
    private fun pagesFromCbz(archive: File): List<ComicPage>? = runCatching {
        val outDir = File(context.cacheDir, "cbz/${archive.absolutePath.hashCode().toUInt()}_${archive.lastModified()}")
        java.util.zip.ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && ImageBytes.isImageName(it.name) }
                .sortedBy { it.name }
                .toList()
            if (!outDir.isDirectory) outDir.mkdirs()
            entries.mapIndexed { i, entry ->
                val dest = File(outDir, "%04d.%s".format(i, entry.name.substringAfterLast('.')))
                if (!dest.isFile || dest.length() == 0L) {
                    zip.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
                }
                ComicPage(index = i, imageUrl = dest.absolutePath)
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeTitleMeta(sourceId: String, titleId: String, title: TitleDetails?) {
        if (title == null) return
        val file = File(titleDir(sourceId, titleId), "meta.json")
        val meta = TitleDiskMeta(
            sourceId = sourceId,
            titleId = titleId,
            titleName = title.title,
            coverUrl = title.coverUrl,
            typeLabel = title.typeLabel,
            description = title.description,
            status = title.status,
            authors = title.authors,
            genres = title.genres,
        )
        runCatching { file.writeText(json.encodeToString(meta)) }
    }

    private fun writeChapterMeta(sourceId: String, titleId: String, chapter: Chapter) {
        val file = File(chapterDir(sourceId, titleId, chapter.id), "meta.json")
        val meta = ChapterDiskMeta(
            chapterId = chapter.id,
            chapterName = chapter.title,
            url = chapter.url,
            number = chapter.number,
        )
        runCatching { file.writeText(json.encodeToString(meta)) }
    }

    private fun readTitleMeta(titleFolder: File): TitleDiskMeta? {
        val file = File(titleFolder, "meta.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<TitleDiskMeta>(file.readText()) }.getOrNull()
    }

    private fun readChapterMeta(chapterFolder: File): ChapterDiskMeta? {
        val file = File(chapterFolder, "meta.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<ChapterDiskMeta>(file.readText()) }.getOrNull()
    }

    /**
     * Rebuilds Room download rows from files left on disk after a DB wipe.
     */
    suspend fun recoverFromDisk(): Int {
        var recovered = 0
        for (root in allDownloadDirs()) {
            if (root.isDirectory) recovered += recoverFrom(root)
        }
        return recovered
    }

    private suspend fun recoverFrom(root: File): Int {
        var recovered = 0
        root.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
            val folderSourceId = sourceDir.name
            sourceDir.listFiles()?.filter { it.isDirectory }?.forEach { titleFolder ->
                val titleMeta = readTitleMeta(titleFolder)
                val sourceId = titleMeta?.sourceId ?: folderSourceId
                val titleId = titleMeta?.titleId ?: titleFolder.name
                val titleName = titleMeta?.titleName ?: titleFolder.name
                val coverUrl = titleMeta?.coverUrl

                titleFolder.listFiles()?.filter { it.isDirectory }?.forEach { chapterFolder ->
                    val chapterMeta = readChapterMeta(chapterFolder)
                    val chapterId = chapterMeta?.chapterId ?: chapterFolder.name
                    val chapterName = chapterMeta?.chapterName ?: chapterFolder.name
                    val existing = dao.getDownload(sourceId, titleId, chapterId)
                        ?: dao.getDownload(folderSourceId, titleFolder.name, chapterFolder.name)
                    if (existing != null &&
                        existing.titleName != existing.titleId &&
                        existing.coverUrl != null
                    ) {
                        return@forEach
                    }
                    val cbz = File(chapterFolder, CBZ_NAME).takeIf { it.isFile }
                    val pages = listPagesIn(chapterFolder) ?: return@forEach
                    val paths = if (cbz != null) listOf(cbz.absolutePath) else pages.map { it.imageUrl }
                    dao.upsertDownload(
                        DownloadEntity(
                            sourceId = sourceId,
                            titleId = titleId,
                            chapterId = chapterId,
                            titleName = titleName,
                            coverUrl = coverUrl,
                            chapterName = chapterName,
                            pageCount = pages.size,
                            pagePathsJson = json.encodeToString(paths),
                        ),
                    )
                    // Drop weak duplicate keyed by folder names if ids differ
                    if (folderSourceId != sourceId || titleFolder.name != titleId || chapterFolder.name != chapterId) {
                        runCatching {
                            dao.deleteDownload(folderSourceId, titleFolder.name, chapterFolder.name)
                        }
                    }
                    recovered++
                }

                // Title meta is written above; chapters filled in enrich / chaptersFromDownloads.
            }
        }
        return recovered
    }

    /**
     * Pulls live title/chapter names + cover from sources and rebuilds offline cache.
     */
    suspend fun enrichMetadata(sources: SourceRegistry, offlineCache: OfflineTitleCache): Int {
        val all = dao.allDownloads()
        if (all.isEmpty()) return 0
        var updated = 0
        val groups = all.groupBy { it.sourceId to it.titleId }
        for ((key, items) in groups) {
            val (sourceId, titleId) = key
            val source = sources.sourceById(sourceId) ?: continue
            val details = runCatching { source.getTitle(titleId) }.getOrNull()
            val chapters = runCatching { source.getChapters(titleId) }.getOrNull().orEmpty()
            if (details != null) {
                writeTitleMeta(sourceId, titleId, details)
                if (chapters.isNotEmpty()) {
                    offlineCache.save(sourceId, titleId, details, chapters)
                } else {
                    offlineCache.save(
                        sourceId,
                        titleId,
                        details,
                        items.map {
                            Chapter(id = it.chapterId, title = it.chapterName, url = it.chapterId)
                        },
                    )
                }
            }
            for (item in items) {
                val matched = chapters.firstOrNull {
                    it.id == item.chapterId || safe(it.id) == item.chapterId || safe(it.id) == safe(item.chapterId)
                }
                val chapterName = matched?.title ?: item.chapterName
                val chapterId = matched?.id ?: item.chapterId
                if (matched != null) {
                    writeChapterMeta(sourceId, titleId, matched)
                }
                val titleName = details?.title ?: item.titleName
                val cover = details?.coverUrl ?: item.coverUrl
                val needsUpdate = titleName != item.titleName ||
                    cover != item.coverUrl ||
                    chapterName != item.chapterName ||
                    chapterId != item.chapterId
                if (!needsUpdate && details == null) continue
                if (chapterId != item.chapterId) {
                    dao.deleteDownload(item.sourceId, item.titleId, item.chapterId)
                }
                dao.upsertDownload(
                    item.copy(
                        chapterId = chapterId,
                        titleName = titleName,
                        coverUrl = cover,
                        chapterName = chapterName,
                    ),
                )
                updated++
            }
            // If network failed, still build offline cache from whatever we have on disk meta / rows
            if (details == null) {
                val titleFolder = allDownloadDirs().map { File(it, "${safe(sourceId)}/${safe(titleId)}") }
                    .firstOrNull { it.isDirectory } ?: File(downloadsDir(), "${safe(sourceId)}/${safe(titleId)}")
                val disk = readTitleMeta(titleFolder)
                val d = TitleDetails(
                    id = titleId,
                    title = disk?.titleName ?: items.first().titleName,
                    coverUrl = disk?.coverUrl ?: items.first().coverUrl,
                    typeLabel = disk?.typeLabel,
                    description = disk?.description,
                    status = disk?.status,
                    authors = disk?.authors.orEmpty(),
                    genres = disk?.genres.orEmpty(),
                )
                val ch = items.map { row ->
                    val folder = File(titleFolder, safe(row.chapterId)).takeIf { it.isDirectory }
                        ?: File(titleFolder, row.chapterId)
                    val cm = readChapterMeta(folder)
                    Chapter(
                        id = cm?.chapterId ?: row.chapterId,
                        title = cm?.chapterName ?: row.chapterName,
                        url = cm?.url?.takeIf { it.isNotBlank() } ?: (cm?.chapterId ?: row.chapterId),
                        number = cm?.number,
                    )
                }
                offlineCache.save(sourceId, titleId, d, ch)
                if (disk != null && (disk.titleName != items.first().titleName || disk.coverUrl != items.first().coverUrl)) {
                    for (item in items) {
                        dao.upsertDownload(
                            item.copy(titleName = disk.titleName, coverUrl = disk.coverUrl),
                        )
                        updated++
                    }
                }
            }
        }
        return updated
    }

    fun abortPartial(sourceId: String, titleId: String, chapterId: String) {
        val key = keyOf(sourceId, titleId, chapterId)
        progressMap.remove(key)
        _progress.value = progressMap.toMap()
        chapterDir(sourceId, titleId, chapterId).deleteRecursively()
    }

    /**
     * Downloads [chapter] of [source]'s title [titleId]. [fetchPages] says where the pages come
     * from when that is not [source] itself (a linked source's chapter, or another copy after the
     * chapter failed); page requests then carry that source's headers.
     */
    suspend fun downloadChapter(
        source: CatalogSource,
        titleId: String,
        title: TitleDetails?,
        chapter: Chapter,
        fetchPages: (suspend () -> Pair<CatalogSource, List<ComicPage>>)? = null,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val sourceId = source.manifest.id
        val key = keyOf(sourceId, titleId, chapter.id)
        setProgress(key, 0f)
        onProgress?.invoke(0f)

        try {
            val (pageSource, pages) = fetchPages?.invoke() ?: (source to source.getPages(chapter))
            if (pages.isEmpty()) error(context.getString(R.string.error_no_pages_download))

            writeTitleMeta(sourceId, titleId, title)
            val dir = chapterDir(sourceId, titleId, chapter.id)
            val referer = pageSource.manifest.headers["Referer"] ?: "${pageSource.manifest.baseUrl.trimEnd('/')}/"
            val parallel = prefs?.downloadParallelPages?.value ?: 1
            val permits = Semaphore(parallel)
            val done = java.util.concurrent.atomic.AtomicInteger(0)

            // Several pages at once; each is checked to really be an image before it is kept.
            val files = coroutineScope {
                pages.mapIndexed { i, page ->
                    async {
                        permits.withPermit {
                            currentCoroutineContext().ensureActive()
                            val url = page.imageUrl
                            val bytes = if (url.startsWith("/") || (!url.startsWith("http") && File(url).exists())) {
                                File(url).readBytes()
                            } else {
                                http.getBytes(
                                    URI(url),
                                    source = pageSource.manifest,
                                    headers = mapOf(
                                        "Referer" to referer,
                                        "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                                    ),
                                )
                            }
                            val ext = ImageBytes.extension(bytes)
                                ?: throw SourceFetchException(context.getString(R.string.error_page_not_image, i + 1))
                            val file = File(dir, "%04d.%s".format(i, ext))
                            file.writeBytes(bytes)
                            val p = done.incrementAndGet().toFloat() / pages.size
                            setProgress(key, p)
                            onProgress?.invoke(p)
                            file
                        }
                    }
                }.awaitAll()
            }

            writeChapterMeta(sourceId, titleId, chapter)
            val paths = if (prefs?.downloadsAsCbz?.value == true) {
                listOf(packCbz(dir, files, title, chapter, source).absolutePath)
            } else {
                files.map { it.absolutePath }
            }
            dao.upsertDownload(
                DownloadEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    chapterId = chapter.id,
                    titleName = title?.title ?: titleId,
                    coverUrl = title?.coverUrl,
                    chapterName = chapter.title,
                    pageCount = files.size,
                    pagePathsJson = json.encodeToString(paths),
                ),
            )
        } catch (e: CancellationException) {
            abortPartial(sourceId, titleId, chapter.id)
            throw e
        } finally {
            progressMap.remove(key)
            _progress.value = progressMap.toMap()
        }
    }

    /**
     * Zips [files] (plus ComicInfo.xml) into the chapter folder as [CBZ_NAME] and removes the
     * loose pages. Pages are stored uncompressed: images do not shrink and reading stays fast.
     */
    private fun packCbz(dir: File, files: List<File>, title: TitleDetails?, chapter: Chapter, source: CatalogSource): File {
        val target = File(dir, CBZ_NAME)
        val partial = File(dir, "$CBZ_NAME.part")
        java.util.zip.ZipOutputStream(partial.outputStream().buffered()).use { zip ->
            for (file in files) {
                val bytes = file.readBytes()
                val entry = java.util.zip.ZipEntry(file.name).apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            val info = ComicInfo(
                series = title?.title ?: chapter.title,
                title = chapter.title,
                number = chapter.number,
                volume = chapter.volume,
                summary = title?.description,
                writer = title?.authors.orEmpty(),
                penciller = title?.artists.orEmpty(),
                genre = title?.genres.orEmpty(),
                pageCount = files.size,
                web = title?.id?.let { runCatching { source.webUrl(it) }.getOrNull() },
                status = title?.status,
            )
            zip.putNextEntry(java.util.zip.ZipEntry("ComicInfo.xml"))
            zip.write(info.toXml().toByteArray())
            zip.closeEntry()
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        files.forEach { it.delete() }
        return target
    }

    suspend fun delete(sourceId: String, titleId: String, chapterId: String) {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            val paths = runCatching { json.decodeFromString<List<String>>(meta.pagePathsJson) }.getOrDefault(emptyList())
            paths.forEach { File(it).delete() }
            // The chapter folder sits next to its pages, whichever storage it was saved to.
            paths.firstOrNull()?.let { File(it).parentFile }?.takeIf { it.name == safe(chapterId) }?.deleteRecursively()
            for (root in allDownloadDirs()) {
                File(root, "${safe(sourceId)}/${safe(titleId)}/${safe(chapterId)}").takeIf { it.isDirectory }?.deleteRecursively()
            }
        }
        dao.deleteDownload(sourceId, titleId, chapterId)
    }

    suspend fun deleteTitle(sourceId: String, titleId: String) {
        val items = dao.downloadsForTitle(sourceId, titleId)
        items.forEach { delete(it.sourceId, it.titleId, it.chapterId) }
        for (root in allDownloadDirs()) {
            File(root, "${safe(sourceId)}/${safe(titleId)}").takeIf { it.isDirectory }?.deleteRecursively()
        }
    }

    /** Removes the unpacked CBZ pages (they come back on the next read). */
    fun clearUnpackedCache() {
        File(context.cacheDir, "cbz").deleteRecursively()
    }

    private fun setProgress(key: String, value: Float) {
        progressMap[key] = value
        _progress.value = progressMap.toMap()
    }

    private companion object {
        const val CBZ_NAME = "chapter.cbz"
    }
}
