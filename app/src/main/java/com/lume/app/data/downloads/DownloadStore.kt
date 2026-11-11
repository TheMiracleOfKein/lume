package com.lume.app.data.downloads

import android.content.Context
import android.net.Uri
import com.lume.app.R
import com.lume.app.domain.isContentUri
import kotlinx.coroutines.Dispatchers
import com.lume.app.data.local.archive.ArchiveReaders
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.network.AppHttp
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.ArchiveCache
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.ComicInfo
import com.lume.app.domain.ImageBytes
import com.lume.app.domain.NaturalOrder
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.toXml
import com.lume.app.data.prefs.AppPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    val saf: SafStorage = SafStorage(context),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val progressMap = ConcurrentHashMap<String, Float>()
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun keyOf(sourceId: String, titleId: String, chapterId: String) =
        "$sourceId|$titleId|$chapterId"

    fun safe(value: String) = safeFolderName(value)

    /**
     * `downloads/` in the chosen storage (falls back to internal storage when it is gone, and when
     * the choice is a picked folder: those downloads are written through [saf], see [activeTree]).
     */
    fun downloadsDir(): File {
        val chosen = prefs?.downloadsRoot?.value.orEmpty()
        val base = chosen.takeIf { it.isNotBlank() && !isContentUri(it) }?.let(::File)?.takeIf { it.isDirectory || it.mkdirs() } ?: context.filesDir
        return File(base, "downloads")
    }

    /** The picked folder new downloads go to; null for a file storage, or when its permission is gone. */
    fun activeTree(): Uri? {
        val chosen = prefs?.downloadsRoot?.value.orEmpty()
        if (!isContentUri(chosen)) return null
        return Uri.parse(chosen).takeIf { saf.hasAccess(it) }
    }

    /** Chapters of a picked folder are collected here, in app storage, before they are copied over. */
    private fun stagingDir() = File(context.filesDir, STAGING_DIR)

    private fun stagedPartDir(sourceId: String, titleId: String, chapterId: String) =
        File(stagingDir(), "${safe(sourceId)}/${safe(titleId)}/${DownloadLocations.partName(chapterId)}")

    /** The picked folder a stored page lives in, or null for a plain file path. */
    private fun treeOfPath(path: String): Uri? = if (isContentUri(path)) saf.treeOf(Uri.parse(path)) else null

    private fun decodePaths(row: DownloadEntity): List<String> =
        runCatching { json.decodeFromString<List<String>>(row.pagePathsJson) }.getOrDefault(emptyList())

    /** True when a stored download still sits in [tree], so its permission must be kept. */
    suspend fun referencesTree(tree: String): Boolean = withContext(Dispatchers.IO) {
        dao.allDownloads().any { row -> decodePaths(row).any { DownloadLocations.isInTree(it, tree) } }
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
            val bytes = decodePaths(download).sumOf { path ->
                if (isContentUri(path)) saf.size(Uri.parse(path)) else File(path).takeIf(File::isFile)?.length() ?: 0L
            }
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

    /** Where a finished chapter lives (not created). */
    private fun chapterDirPath(sourceId: String, titleId: String, chapterId: String): File =
        File(titleDir(sourceId, titleId), safe(chapterId))

    /** Where a chapter is downloaded to before it is complete; renamed to [chapterDirPath] after. */
    private fun partDirPath(sourceId: String, titleId: String, chapterId: String): File =
        File(titleDir(sourceId, titleId), safe(chapterId) + PART_SUFFIX)

    suspend fun downloadedChapterIds(sourceId: String, titleId: String): Set<String> =
        dao.downloadedChapterIds(sourceId, titleId).toSet()

    suspend fun localPages(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? = withContext(Dispatchers.IO) {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            val paths = runCatching { json.decodeFromString<List<String>>(meta.pagePathsJson) }.getOrNull()
            if (paths != null) {
                val pages = mutableListOf<ComicPage>()
                var ok = true
                // A CBZ download stores the archive as its only path.
                val single = paths.singleOrNull()
                if (single != null && isContentUri(single)) {
                    // Document ids need not end in the file name: ask the provider for the name.
                    if (saf.stat(Uri.parse(single))?.name?.endsWith(".cbz", ignoreCase = true) == true) {
                        pagesFromSafCbz(Uri.parse(single))?.let { return@withContext it }
                        ok = false
                    }
                } else {
                    single?.takeIf { it.endsWith(".cbz") }?.let { cbz ->
                        File(cbz).takeIf(File::isFile)?.let { return@withContext pagesFromCbz(it) }
                    }
                }
                for ((i, path) in paths.withIndex()) {
                    if (!ok) break
                    if (isContentUri(path)) {
                        // One lookup for the first page; checking every page would be a query each.
                        if (i == 0 && !saf.exists(Uri.parse(path))) {
                            ok = false
                            break
                        }
                        pages += ComicPage(index = i, imageUrl = path)
                        continue
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        ok = false
                        break
                    }
                    pages += ComicPage(index = i, imageUrl = file.absolutePath)
                }
                if (ok && pages.isNotEmpty()) return@withContext pages
            }
        }
        scanChapterDir(sourceId, titleId, chapterId)
    }

    /** Pages of a chapter folder in the picked folder, found by name (a row that lost its paths). */
    private fun scanSafChapter(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        val tree = activeTree() ?: return null
        val id = saf.folder(tree, DownloadLocations.chapterSegments(sourceId, titleId, chapterId), create = false) ?: return null
        return listPagesInSaf(tree, saf.children(tree, id))
    }

    private fun listPagesInSaf(tree: Uri, children: List<SafStorage.Child>): List<ComicPage>? {
        children.firstOrNull { !it.isDir && it.name == CBZ_NAME && it.size > 0L }?.let {
            return pagesFromSafCbz(saf.documentUri(tree, it.id))
        }
        val files = children
            .filter { !it.isDir && it.size > 0L && DownloadLocations.isPageName(it.name) }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        if (files.isEmpty()) return null
        return files.mapIndexed { i, f -> ComicPage(index = i, imageUrl = saf.documentUri(tree, f.id).toString()) }
    }

    /**
     * Pages of a CBZ stored in a picked folder. The archive is copied into the cache for unpacking,
     * then removed again; the unpacked pages stay (marked complete), so the next read needs no copy.
     */
    private fun pagesFromSafCbz(archive: Uri): List<ComicPage>? = runCatching {
        val stat = saf.stat(archive) ?: return@runCatching null
        val outDir = File(context.cacheDir, "cbz/${ArchiveCache.dirKey(archive.toString(), stat.modified, stat.size)}")
        val marker = File(outDir, UNPACKED_MARKER)
        val files = if (marker.isFile) {
            outDir.listFiles()?.filter { it.isFile && it.name != UNPACKED_MARKER && !it.name.endsWith(".part") }
                ?.sortedWith(compareBy(NaturalOrder) { it.name }).orEmpty()
        } else {
            val copy = File(context.cacheDir, "cbz_src/${ArchiveCache.dirKey(archive.toString(), stat.modified, stat.size)}.cbz")
            copy.parentFile?.mkdirs()
            try {
                (saf.open(archive) ?: return@runCatching null).use { input -> copy.outputStream().use { input.copyTo(it) } }
                val unpacked = ArchiveReaders.open(copy).use { reader ->
                    ArchiveReaders.extractImages(reader, outDir) { i, name -> "%04d.%s".format(i, name.substringAfterLast('.')) }
                }
                marker.writeText("")
                unpacked
            } finally {
                copy.delete()
            }
        }
        if (files.isEmpty()) return@runCatching null
        ArchiveCache.touch(outDir)
        ArchiveCache.trim(outDir.parentFile ?: outDir, keep = outDir)
        files.mapIndexed { i, file -> ComicPage(index = i, imageUrl = file.absolutePath) }
    }.getOrNull()

    private fun scanChapterDir(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        scanSafChapter(sourceId, titleId, chapterId)?.let { return it }
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
            ?.sortedWith(compareBy(NaturalOrder) { it.name })
            .orEmpty()
        if (files.isEmpty()) return null
        return files.mapIndexed { i, f -> ComicPage(index = i, imageUrl = f.absolutePath) }
    }

    /** Pages of a CBZ download, unpacked once into the cache (the reader needs plain files). */
    private fun pagesFromCbz(archive: File): List<ComicPage>? = runCatching {
        val outDir = File(context.cacheDir, "cbz/${ArchiveCache.dirKey(archive)}")
        val files = ArchiveReaders.open(archive).use { reader ->
            ArchiveReaders.extractImages(reader, outDir) { i, name -> "%04d.%s".format(i, name.substringAfterLast('.')) }
        }
        ArchiveCache.touch(outDir)
        ArchiveCache.trim(outDir.parentFile ?: outDir, keep = outDir)
        files.mapIndexed { i, file -> ComicPage(index = i, imageUrl = file.absolutePath) }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Writes the title's `meta.json` next to its chapters: in [tree] when the downloads are in a picked folder. */
    private fun writeTitleMeta(sourceId: String, titleId: String, title: TitleDetails?, tree: Uri? = null) {
        if (title == null) return
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
        val text = json.encodeToString(meta)
        if (tree != null) {
            runCatching {
                val folder = saf.folder(tree, DownloadLocations.titleSegments(sourceId, titleId), create = true)
                if (folder != null) saf.writeText(tree, folder, DownloadLocations.META_NAME, text)
            }
        } else {
            runCatching { File(titleDir(sourceId, titleId), "meta.json").writeText(text) }
        }
    }

    private fun chapterMetaJson(chapter: Chapter): String = json.encodeToString(
        ChapterDiskMeta(
            chapterId = chapter.id,
            chapterName = chapter.title,
            url = chapter.url,
            number = chapter.number,
        ),
    )

    private fun writeChapterMeta(dir: File, chapter: Chapter) {
        runCatching { File(dir, "meta.json").writeText(chapterMetaJson(chapter)) }
    }

    /** Rewrites `meta.json` of a finished chapter that sits in [tree] (not created when the folder is gone). */
    private fun writeSafChapterMeta(tree: Uri, sourceId: String, titleId: String, chapter: Chapter) {
        runCatching {
            val folder = saf.folder(tree, DownloadLocations.chapterSegments(sourceId, titleId, chapter.id), create = false) ?: return
            saf.writeText(tree, folder, DownloadLocations.META_NAME, chapterMetaJson(chapter))
        }
    }

    private inline fun <reified T> readSafMeta(tree: Uri, children: List<SafStorage.Child>): T? =
        children.firstOrNull { !it.isDir && it.name == DownloadLocations.META_NAME }
            ?.let { saf.readText(saf.documentUri(tree, it.id)) }
            ?.let { text -> runCatching { json.decodeFromString<T>(text) }.getOrNull() }

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
    suspend fun recoverFromDisk(): Int = withContext(Dispatchers.IO) {
        var recovered = 0
        for (root in allDownloadDirs()) {
            if (root.isDirectory) recovered += recoverFrom(root)
        }
        // Chapters collected for a picked folder when the app was stopped are not complete.
        stagingDir().deleteRecursively()
        activeTree()?.let { recovered += recoverFromSaf(it) }
        recovered
    }

    /** Same as [recoverFrom], for the `Lume` folder inside the picked folder [tree] (slow: one query per folder). */
    private suspend fun recoverFromSaf(tree: Uri): Int {
        var recovered = 0
        val root = saf.folder(tree, listOf(DownloadLocations.SAF_ROOT), create = false) ?: return 0
        for (sourceDir in saf.children(tree, root).filter { it.isDir }) {
            for (titleFolder in saf.children(tree, sourceDir.id).filter { it.isDir }) {
                val titleChildren = saf.children(tree, titleFolder.id)
                val titleMeta = readSafMeta<TitleDiskMeta>(tree, titleChildren)
                val sourceId = titleMeta?.sourceId ?: sourceDir.name
                val titleId = titleMeta?.titleId ?: titleFolder.name
                val titleName = titleMeta?.titleName ?: titleFolder.name
                // Unfinished copies (the app was killed) are dropped, never registered.
                titleChildren.filter { it.isDir && DownloadLocations.isPartName(it.name) }
                    .forEach { saf.delete(saf.documentUri(tree, it.id)) }
                for (chapterFolder in titleChildren.filter { it.isDir && !DownloadLocations.isPartName(it.name) }) {
                    val children = saf.children(tree, chapterFolder.id)
                    val chapterMeta = readSafMeta<ChapterDiskMeta>(tree, children)
                    val chapterId = chapterMeta?.chapterId ?: chapterFolder.name
                    val chapterName = chapterMeta?.chapterName ?: chapterFolder.name
                    val existing = dao.getDownload(sourceId, titleId, chapterId)
                        ?: dao.getDownload(sourceDir.name, titleFolder.name, chapterFolder.name)
                    if (existing != null && existing.titleName != existing.titleId && existing.coverUrl != null) continue
                    val cbz = children.firstOrNull { !it.isDir && it.name == CBZ_NAME && it.size > 0L }
                    val pages = listPagesInSaf(tree, children) ?: continue
                    val paths = if (cbz != null) listOf(saf.documentUri(tree, cbz.id).toString()) else pages.map { it.imageUrl }
                    dao.upsertDownload(
                        DownloadEntity(
                            sourceId = sourceId,
                            titleId = titleId,
                            chapterId = chapterId,
                            titleName = titleName,
                            coverUrl = titleMeta?.coverUrl,
                            chapterName = chapterName,
                            pageCount = pages.size,
                            pagePathsJson = json.encodeToString(paths),
                        ),
                    )
                    if (sourceDir.name != sourceId || titleFolder.name != titleId || chapterFolder.name != chapterId) {
                        runCatching { dao.deleteDownload(sourceDir.name, titleFolder.name, chapterFolder.name) }
                    }
                    recovered++
                }
            }
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

                // Unfinished downloads (the app was killed) are dropped, never registered.
                titleFolder.listFiles()?.filter { it.isDirectory && it.name.endsWith(PART_SUFFIX) }
                    ?.forEach { it.deleteRecursively() }
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
            // Downloads stored in a picked folder keep their meta.json files there.
            val safTree = items.firstNotNullOfOrNull { row -> decodePaths(row).firstNotNullOfOrNull(::treeOfPath) }
                ?.takeIf { saf.hasAccess(it) }
            if (details != null) {
                writeTitleMeta(sourceId, titleId, details, safTree)
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
                    if (safTree != null && decodePaths(item).firstOrNull()?.let(::isContentUri) == true) {
                        writeSafChapterMeta(safTree, sourceId, titleId, matched)
                    } else {
                        File(downloadsDir(), "${safe(sourceId)}/${safe(titleId)}/${safe(matched.id)}").takeIf { it.isDirectory }
                            ?.let { writeChapterMeta(it, matched) }
                    }
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
                val safTitleChildren = safTree?.let { tree ->
                    saf.folder(tree, DownloadLocations.titleSegments(sourceId, titleId), create = false)
                        ?.let { saf.children(tree, it) }
                }
                val disk = if (safTitleChildren != null && safTree != null) {
                    readSafMeta<TitleDiskMeta>(safTree, safTitleChildren)
                } else {
                    readTitleMeta(titleFolder)
                }
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
                    val safChapter = if (safTree != null && safTitleChildren != null) {
                        safTitleChildren.firstOrNull { it.isDir && it.name == safe(row.chapterId) }
                            ?.let { readSafMeta<ChapterDiskMeta>(safTree, saf.children(safTree, it.id)) }
                    } else {
                        null
                    }
                    val folder = File(titleFolder, safe(row.chapterId)).takeIf { it.isDirectory }
                        ?: File(titleFolder, row.chapterId)
                    val cm = safChapter ?: readChapterMeta(folder)
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

    /**
     * Drops an unfinished download of a chapter. A finished copy (it has a download row) stays;
     * a folder without a row is a partial one left by an older version and is removed too.
     */
    suspend fun abortPartial(sourceId: String, titleId: String, chapterId: String) {
        val key = keyOf(sourceId, titleId, chapterId)
        progressMap.remove(key)
        _progress.value = progressMap.toMap()
        stagedPartDir(sourceId, titleId, chapterId).deleteRecursively()
        val hasRow = dao.getDownload(sourceId, titleId, chapterId) != null
        val fileTitle = File(downloadsDir(), "${safe(sourceId)}/${safe(titleId)}")
        File(fileTitle, safe(chapterId) + PART_SUFFIX).deleteRecursively()
        if (!hasRow) File(fileTitle, safe(chapterId)).takeIf { it.isDirectory }?.deleteRecursively()
        activeTree()?.let { tree ->
            withContext(Dispatchers.IO) {
                val title = saf.folder(tree, DownloadLocations.titleSegments(sourceId, titleId), create = false) ?: return@withContext
                // A copy that was cut short; a finished chapter (it has a row) stays.
                saf.deleteChild(tree, title, DownloadLocations.partName(chapterId))
                if (!hasRow) saf.deleteChild(tree, title, safe(chapterId))
            }
        }
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

            // A picked folder: the chapter is built in app storage, then copied over (see publishToSaf).
            val tree = activeTree()
            withContext(Dispatchers.IO) { writeTitleMeta(sourceId, titleId, title, tree) }
            // Pages go to a temporary folder; only a complete chapter is moved into place.
            val dir = (if (tree != null) stagedPartDir(sourceId, titleId, chapter.id) else partDirPath(sourceId, titleId, chapter.id)).apply {
                deleteRecursively()
                mkdirs()
            }
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
                            val bytes = if (isContentUri(url)) {
                                saf.open(Uri.parse(url))?.use { it.readBytes() } ?: throw SourceFetchException("Can't read $url")
                            } else if (url.startsWith("/") || (!url.startsWith("http") && File(url).exists())) {
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

            writeChapterMeta(dir, chapter)
            val names = if (prefs?.downloadsAsCbz?.value == true) {
                listOf(packCbz(dir, files, title, chapter, source).name)
            } else {
                files.map { it.name }
            }
            val paths = if (tree != null) {
                withContext(Dispatchers.IO) {
                    try {
                        publishToSaf(tree, sourceId, titleId, chapter.id, dir, names)
                    } catch (e: java.io.IOException) {
                        saf.forgetAll(tree) // a remembered folder may be gone; look again next time
                        throw e
                    }
                }.also { dir.deleteRecursively() }
            } else {
                val finalDir = chapterDirPath(sourceId, titleId, chapter.id)
                finalDir.deleteRecursively()
                if (!dir.renameTo(finalDir)) {
                    dir.copyRecursively(finalDir, overwrite = true)
                    dir.deleteRecursively()
                }
                names.map { File(finalDir, it).absolutePath }
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
        } catch (e: Throwable) {
            // Any failure (network, not an image, cancel): nothing half-done stays on disk.
            withContext(NonCancellable) { abortPartial(sourceId, titleId, chapter.id) }
            throw e
        } finally {
            progressMap.remove(key)
            _progress.value = progressMap.toMap()
        }
    }

    /**
     * Copies a finished chapter from [dir] (app storage) into `Lume/<source>/<title>/<chapter>` of
     * [tree]. It goes into a `.lume-part` folder first, renamed when every file is there, so a cut-off
     * copy is never taken for a chapter. Returns the document URIs of [names], in order.
     */
    private suspend fun publishToSaf(tree: Uri, sourceId: String, titleId: String, chapterId: String, dir: File, names: List<String>): List<String> {
        val titleSegments = DownloadLocations.titleSegments(sourceId, titleId)
        // Folder ids we remembered may be stale (the folder was deleted or moved outside the app).
        val titleFolder = saf.folder(tree, titleSegments, create = true)
            ?: run {
                saf.forgetAll(tree)
                saf.folder(tree, titleSegments, create = true)
            }
            ?: throw java.io.IOException("Can't create the download folder")
        val finalName = safe(chapterId)
        val partName = DownloadLocations.partName(chapterId)
        saf.deleteChild(tree, titleFolder, partName)
        saf.deleteChild(tree, titleFolder, finalName)

        val toCopy = (names + DownloadLocations.META_NAME).filter { File(dir, it).isFile }

        suspend fun copyInto(folderId: String) {
            for (name in toCopy) {
                currentCoroutineContext().ensureActive()
                saf.copyIn(tree, folderId, File(dir, name), name, DownloadLocations.mimeFor(name))
            }
        }

        val partId = saf.createFolder(tree, titleFolder, partName) ?: throw java.io.IOException("Can't create $partName")
        copyInto(partId)
        // Providers that can't rename a folder get the files copied a second time, straight into place.
        val finalId = saf.rename(tree, partId, finalName) ?: run {
            saf.delete(saf.documentUri(tree, partId))
            val direct = saf.createFolder(tree, titleFolder, finalName) ?: throw java.io.IOException("Can't create $finalName")
            copyInto(direct)
            direct
        }
        val children = saf.children(tree, finalId)
        return names.map { name ->
            val child = children.firstOrNull { !it.isDir && it.name == name }
                ?: throw java.io.IOException("$name is missing after the copy")
            saf.documentUri(tree, child.id).toString()
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

    suspend fun delete(sourceId: String, titleId: String, chapterId: String) = deleteChapter(sourceId, titleId, chapterId, removeSafFiles = true)

    private suspend fun deleteChapter(sourceId: String, titleId: String, chapterId: String, removeSafFiles: Boolean) {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            val paths = decodePaths(meta)
            val (contentPaths, filePaths) = paths.partition(::isContentUri)
            filePaths.forEach { File(it).delete() }
            // The chapter folder sits next to its pages, whichever storage it was saved to.
            filePaths.firstOrNull()?.let { File(it).parentFile }?.takeIf { it.name == safe(chapterId) }?.deleteRecursively()
            for (root in allDownloadDirs()) {
                File(root, "${safe(sourceId)}/${safe(titleId)}/${safe(chapterId)}").takeIf { it.isDirectory }?.deleteRecursively()
            }
            if (removeSafFiles && contentPaths.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    // The chapter folder goes with its pages and meta.json; single files cover other layouts.
                    contentPaths.mapNotNull(::treeOfPath).distinct().filter(saf::hasAccess).forEach { tree ->
                        saf.deleteFolder(tree, DownloadLocations.chapterSegments(sourceId, titleId, chapterId))
                    }
                    contentPaths.forEach { saf.delete(Uri.parse(it)) }
                }
            }
        }
        dao.deleteDownload(sourceId, titleId, chapterId)
    }

    suspend fun deleteTitle(sourceId: String, titleId: String) {
        val items = dao.downloadsForTitle(sourceId, titleId)
        // Whole title folders in picked folders go at once, instead of one lookup per chapter.
        val trees = items.flatMap(::decodePaths).mapNotNull(::treeOfPath).toSet() + listOfNotNull(activeTree())
        withContext(Dispatchers.IO) {
            trees.filter(saf::hasAccess).forEach { saf.deleteFolder(it, DownloadLocations.titleSegments(sourceId, titleId)) }
        }
        items.forEach { deleteChapter(it.sourceId, it.titleId, it.chapterId, removeSafFiles = false) }
        for (root in allDownloadDirs()) {
            File(root, "${safe(sourceId)}/${safe(titleId)}").takeIf { it.isDirectory }?.deleteRecursively()
        }
    }

    /** Removes the unpacked CBZ pages (they come back on the next read). */
    fun clearUnpackedCache() {
        File(context.cacheDir, "cbz").deleteRecursively()
        File(context.cacheDir, "cbz_src").deleteRecursively()
    }

    private fun setProgress(key: String, value: Float) {
        progressMap[key] = value
        _progress.value = progressMap.toMap()
    }

    private companion object {
        const val CBZ_NAME = "chapter.cbz"
        const val PART_SUFFIX = ".lume-part"
        const val STAGING_DIR = "downloads_staging"
        const val UNPACKED_MARKER = ".complete"
    }
}

/**
 * A folder name for a source, title or chapter id. Characters that file systems refuse become
 * `_`; names made only of dots (`.`, `..`) get a `_` in front, so an id can never point at a
 * parent folder; very long names are shortened and keep a hash, to stay under the 255-byte limit.
 */
internal fun safeFolderName(value: String): String {
    val cleaned = value.replace(Regex("""[<>:"/\\|?*\u0000-\u001f]"""), "_").replace(' ', '_')
    if (cleaned.isEmpty() || cleaned.all { it == '.' }) return "_$cleaned"
    if (cleaned.toByteArray(Charsets.UTF_8).size <= MAX_FOLDER_BYTES) return cleaned
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val hash = digest.take(8).joinToString("") { "%02x".format(it) }
    return cleaned.take(60) + "_" + hash
}

private const val MAX_FOLDER_BYTES = 200
