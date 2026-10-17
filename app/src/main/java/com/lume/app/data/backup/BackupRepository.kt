package com.lume.app.data.backup

import android.content.Context
import android.net.Uri
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.SourcePackEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.library.LibraryChapters
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.title.TitleRepository
import com.lume.app.domain.chapterSortKey
import android.provider.DocumentsContract
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version 2 adds per-chapter progress, page bookmarks and read marks; version 3 adds settings.
 * Older files still load: the new lists default to empty.
 */
@Serializable
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val bookmarks: List<BookmarkEntityDto> = emptyList(),
    val progress: List<ProgressEntityDto> = emptyList(),
    val categories: List<CategoryEntityDto> = emptyList(),
    val sourcePacks: List<SourcePackDto> = emptyList(),
    val titlePrefs: List<TitlePrefsDto> = emptyList(),
    val trackerLinks: List<TrackerLinkDto> = emptyList(),
    val chapterProgress: List<ChapterProgressDto> = emptyList(),
    val pageBookmarks: List<PageBookmarkDto> = emptyList(),
    val chapterRead: List<ChapterReadDto> = emptyList(),
    val settings: List<SettingDto> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 3

        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun parse(text: String): BackupPayload = json.decodeFromString(serializer(), text)
    }
}

/** One stored preference; [type] is bool / int / long / double / float / string / set. */
@Serializable
data class SettingDto(
    val key: String,
    val type: String,
    val value: String,
)

/** What a Mihon / Tachiyomi import did. */
data class MihonImportResult(
    val added: Int,
    val alreadyInLibrary: Int,
    /** Titles whose source Lume doesn't have (or whose link it can't read). */
    val unmatched: List<String>,
    val chaptersMarkedRead: Int,
    /** Titles whose chapter list couldn't be loaded, so their read marks are missing. */
    val readMarksFailed: Int,
)

@Serializable
data class ChapterProgressDto(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val pageIndex: Int = 0,
    val pageOffset: Float = 0f,
    val pageCount: Int = 0,
    val completed: Boolean = false,
    val pageAspectsJson: String = "[]",
    val updatedAt: Long = 0L,
)

@Serializable
data class PageBookmarkDto(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val chapterName: String,
    val pageIndex: Int,
    val pageOffset: Float = 0f,
    val pageImageUrl: String? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class ChapterReadDto(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val readAt: Long = 0L,
)

@Serializable
data class BookmarkEntityDto(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val createdAt: Long = 0L,
    val categoryIdsJson: String = "[]",
)

@Serializable
data class ProgressEntityDto(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val chapterId: String,
    val chapterName: String,
    val chapterUrl: String,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val updatedAt: Long = 0L,
    val pageOffset: Float = 0f,
)

@Serializable
data class CategoryEntityDto(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Serializable
data class SourcePackDto(
    val id: String,
    val json: String,
    val installedAt: Long = 0L,
)

@Serializable
data class TitlePrefsDto(
    val sourceId: String,
    val titleId: String,
    val readerMode: Int? = null,
    val readerTheme: Int? = null,
    val gap: Float? = null,
    val chapterFilter: Int = 0,
    val chapterSortAsc: Boolean = false,
    val branch: String? = null,
    val showDuplicates: Boolean = false,
)

@Serializable
data class TrackerLinkDto(
    val sourceId: String,
    val titleId: String,
    val tracker: String,
    val remoteId: String,
    val remoteUrl: String? = null,
    val status: String? = null,
    val score: Float? = null,
    val lastSync: Long = 0L,
)

class BackupRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
    private val prefs: AppPreferences? = null,
    private val titles: TitleRepository? = null,
    private val libraryChapters: LibraryChapters? = null,
    private val json: Json = BackupPayload.json,
) {
    fun backupsDir(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** A backup saved in the app's own folder (Settings → Create backup). */
    suspend fun createBackup(prefix: String = "LUME_backup_"): File = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(backupsDir(), "$prefix$stamp.json")
        file.writeText(backupJson())
        file
    }

    /** Writes a backup to a file the user picked (any folder, cloud drives included). */
    suspend fun writeBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val text = backupJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error(com.lume.app.data.locale.appString(com.lume.app.R.string.error_backup_write))
    }

    fun suggestedFileName(): String =
        "LUME_backup_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"

    /**
     * Automatic backup: saved in the app folder and, when one is set, copied to the picked
     * folder. Only the newest [keep] automatic backups stay in each place.
     */
    suspend fun autoBackup(keep: Int, folder: Uri?) = withContext(Dispatchers.IO) {
        val file = createBackup(prefix = AUTO_PREFIX)
        backupsDir().listFiles()
            ?.filter { it.isFile && it.name.startsWith(AUTO_PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { it.delete() }
        if (folder != null) runCatching { copyToFolder(folder, file, keep) }
            .onFailure { android.util.Log.w("Backup", "Copy to folder failed: ${it.message}") }
        file
    }

    private fun copyToFolder(tree: Uri, file: File, keep: Int) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val created = DocumentsContract.createDocument(resolver, parent, "application/json", file.name)
            ?: error("createDocument")
        resolver.openOutputStream(created, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
        // Rotation in the folder too: the oldest automatic backups go.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(AUTO_PREFIX)) found += c.getString(0) to name
            }
        }
        found.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    private suspend fun backupJson(): String {
        val payload = BackupPayload(
            bookmarks = dao.allBookmarks().map {
                BookmarkEntityDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    titleName = it.titleName,
                    coverUrl = it.coverUrl,
                    typeLabel = it.typeLabel,
                    createdAt = it.createdAt,
                    categoryIdsJson = it.categoryIdsJson,
                )
            },
            progress = dao.allProgress().map {
                ProgressEntityDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    titleName = it.titleName,
                    coverUrl = it.coverUrl,
                    typeLabel = it.typeLabel,
                    chapterId = it.chapterId,
                    chapterName = it.chapterName,
                    chapterUrl = it.chapterUrl,
                    pageIndex = it.pageIndex,
                    pageCount = it.pageCount,
                    updatedAt = it.updatedAt,
                    pageOffset = it.pageOffset,
                )
            },
            categories = dao.allCategories().map {
                CategoryEntityDto(id = it.id, name = it.name, sortOrder = it.sortOrder)
            },
            sourcePacks = dao.allSourcePacks().map {
                SourcePackDto(id = it.id, json = it.json, installedAt = it.installedAt)
            },
            titlePrefs = dao.allTitlePrefs().map {
                TitlePrefsDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    readerMode = it.readerMode,
                    readerTheme = it.readerTheme,
                    gap = it.gap,
                    chapterFilter = it.chapterFilter,
                    chapterSortAsc = it.chapterSortAsc,
                    branch = it.branch,
                    showDuplicates = it.showDuplicates,
                )
            },
            trackerLinks = dao.allTrackerLinks().map {
                TrackerLinkDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    tracker = it.tracker,
                    remoteId = it.remoteId,
                    remoteUrl = it.remoteUrl,
                    status = it.status,
                    score = it.score,
                    lastSync = it.lastSync,
                )
            },
            chapterProgress = dao.allChapterProgress().map {
                ChapterProgressDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    chapterId = it.chapterId,
                    pageIndex = it.pageIndex,
                    pageOffset = it.pageOffset,
                    pageCount = it.pageCount,
                    completed = it.completed,
                    pageAspectsJson = it.pageAspectsJson,
                    updatedAt = it.updatedAt,
                )
            },
            pageBookmarks = dao.allPageBookmarks().map {
                PageBookmarkDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    chapterId = it.chapterId,
                    chapterName = it.chapterName,
                    pageIndex = it.pageIndex,
                    pageOffset = it.pageOffset,
                    pageImageUrl = it.pageImageUrl,
                    note = it.note,
                    createdAt = it.createdAt,
                )
            },
            chapterRead = dao.allChapterRead().map {
                ChapterReadDto(it.sourceId, it.titleId, it.chapterId, it.readAt)
            },
            settings = prefs?.exportSettings().orEmpty().map { (key, type, value) -> SettingDto(key, type, value) },
        )
        return json.encodeToString(payload)
    }

    suspend fun restoreFromFile(file: File) = withContext(Dispatchers.IO) {
        restorePayload(BackupPayload.parse(file.readText()))
    }

    /** What [restoreFromUri] found in the file. */
    sealed interface RestoreResult {
        data object Lume : RestoreResult
        data class Mihon(val result: MihonImportResult) : RestoreResult
    }

    /**
     * Restores a file the user picked: a Lume backup (JSON) or a Mihon / Tachiyomi backup
     * (`.tachibk`, gzipped protobuf). [onProgress] reports titles done / total for Mihon imports.
     */
    suspend fun restoreFromUri(uri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): RestoreResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error(com.lume.app.data.locale.appString(com.lume.app.R.string.error_backup_read))
        val text = if (MihonBackupFormat.isGzip(bytes)) {
            java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        val firstChar = text.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar()
        if (firstChar == '{') {
            restorePayload(BackupPayload.parse(text.decodeToString()))
            RestoreResult.Lume
        } else {
            RestoreResult.Mihon(importMihon(MihonBackupFormat.parse(text), onProgress))
        }
    }

    /**
     * Adds a Mihon / Tachiyomi library: titles of sources Lume has, their categories, tracker
     * links and read chapters. Read marks need each title's chapter list, so they are loaded from
     * the sources (a few at a time); titles that fail keep their place in the library.
     */
    suspend fun importMihon(backup: MihonBackup, onProgress: (Int, Int) -> Unit = { _, _ -> }): MihonImportResult {
        sources.awaitLoaded()
        val sourceNames = backup.backupSources.associate { it.sourceId to it.name }
        val lumeSources = sources.sources.value.filter { it.manifest.id != "local" }.map { it.manifest.id to it.manifest.name }

        // Categories by name; Mihon titles point at categories by their order value.
        val existing = dao.allCategories().toMutableList()
        val categoryByOrder = HashMap<Long, Long>()
        for (cat in backup.backupCategories.sortedBy { it.order }) {
            val found = existing.firstOrNull { it.name.equals(cat.name, ignoreCase = true) }
            val id = found?.id ?: dao.upsertCategory(
                CategoryEntity(name = cat.name, sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1),
            ).also { newId -> existing += CategoryEntity(id = newId, name = cat.name, sortOrder = existing.size) }
            categoryByOrder[cat.order] = id
        }

        data class Pending(val sourceId: String, val titleId: String, val manga: MihonManga)

        val pending = mutableListOf<Pending>()
        val unmatched = mutableListOf<String>()
        var added = 0
        var already = 0
        for (manga in backup.backupManga.filter { it.favorite }) {
            val sourceId = sourceNames[manga.source]?.let { MihonBackupFormat.matchSource(it, lumeSources) }
            val source = sourceId?.let(sources::sourceById)
            val link = source?.let { if (manga.url.startsWith("http")) manga.url else it.manifest.baseUrl.trimEnd('/') + manga.url }
            val titleId = link?.let { runCatching { source.titleIdFromUrl(it) }.getOrNull() }
            if (source == null || titleId == null) {
                unmatched += manga.title.ifBlank { manga.url }
                continue
            }
            val categories = manga.categories.mapNotNull { categoryByOrder[it] }.toSet()
            val current = dao.getBookmark(source.manifest.id, titleId)
            if (current != null) {
                already++
                val merged = com.lume.app.data.library.parseCategoryIds(current.categoryIdsJson) + categories
                dao.upsertBookmark(current.copy(categoryIdsJson = com.lume.app.data.library.encodeCategoryIds(merged)))
            } else {
                added++
                dao.upsertBookmark(
                    BookmarkEntity(
                        sourceId = source.manifest.id,
                        titleId = titleId,
                        titleName = manga.title.ifBlank { titleId },
                        coverUrl = manga.thumbnailUrl,
                        createdAt = manga.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        categoryIdsJson = com.lume.app.data.library.encodeCategoryIds(categories),
                    ),
                )
            }
            for (track in manga.tracking) {
                val tracker = MihonBackupFormat.trackerId(track.syncId) ?: continue
                val remoteId = (track.mediaId.takeIf { it > 0 } ?: track.mediaIdInt.toLong().takeIf { it > 0 })?.toString() ?: continue
                if (dao.getTrackerLinks(source.manifest.id, titleId).any { it.tracker == tracker }) continue
                dao.upsertTrackerLink(
                    TrackerLinkEntity(
                        sourceId = source.manifest.id,
                        titleId = titleId,
                        tracker = tracker,
                        remoteId = remoteId,
                        remoteUrl = track.trackingUrl.ifBlank { null },
                        score = track.score.takeIf { it > 0f },
                    ),
                )
            }
            pending += Pending(source.manifest.id, titleId, manga)
        }

        // Read marks and "Continue" need the source's chapter ids.
        var marked = 0
        var failed = 0
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        onProgress(0, pending.size)
        val permits = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            pending.map { item ->
                async {
                    permits.withPermit {
                        val ok = runCatching { applyMihonChapters(item.sourceId, item.titleId, item.manga) }
                        ok.onSuccess { synchronized(this@BackupRepository) { marked += it } }
                            .onFailure { synchronized(this@BackupRepository) { failed++ } }
                        onProgress(done.incrementAndGet(), pending.size)
                    }
                }
            }.awaitAll()
        }
        return MihonImportResult(added, already, unmatched, marked, failed)
    }

    /** Marks chapters read by number and points "Continue" at the last chapter read in Mihon. */
    private suspend fun applyMihonChapters(sourceId: String, titleId: String, manga: MihonManga): Int {
        val source = sources.sourceById(sourceId) ?: return 0
        val chapters = kotlinx.coroutines.withTimeout(45_000) { source.getChapters(titleId) }
        titles?.updateChapters(sourceId, titleId, chapters)
        libraryChapters?.sync(sourceId, titleId, chapters)
        val readNumbers = MihonBackupFormat.readNumbers(manga)
        val read = chapters.filter { ch ->
            val n = chapterSortKey(ch)?.toFloat() ?: return@filter false
            readNumbers.any { kotlin.math.abs(it - n) < 0.001f }
        }
        val now = System.currentTimeMillis()
        if (read.isNotEmpty()) dao.markChaptersRead(read.map { ChapterReadEntity(sourceId, titleId, it.id, now) })

        val last = manga.history.maxByOrNull { it.lastRead }
        val lastNumber = last?.let { h -> manga.chapters.firstOrNull { it.url == h.url }?.chapterNumber }
        val target = lastNumber?.let { n -> chapters.firstOrNull { ch -> chapterSortKey(ch)?.let { kotlin.math.abs(it.toFloat() - n) < 0.001f } == true } }
        if (target != null && last != null && dao.getProgress(sourceId, titleId) == null) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = manga.title,
                    coverUrl = manga.thumbnailUrl,
                    chapterId = target.id,
                    chapterName = target.title,
                    chapterUrl = target.url,
                    updatedAt = last.lastRead,
                ),
            )
        }
        return read.size
    }

    suspend fun restoreLatest(): File? = withContext(Dispatchers.IO) {
        val latest = backupsDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
            ?: return@withContext null
        restoreFromFile(latest)
        latest
    }

    private suspend fun restorePayload(payload: BackupPayload) {
        if (payload.settings.isNotEmpty()) {
            prefs?.importSettings(payload.settings.map { Triple(it.key, it.type, it.value) })
        }
        for (c in payload.categories) {
            dao.upsertCategory(CategoryEntity(id = c.id, name = c.name, sortOrder = c.sortOrder))
        }
        for (b in payload.bookmarks) {
            dao.upsertBookmark(
                BookmarkEntity(
                    sourceId = b.sourceId,
                    titleId = b.titleId,
                    titleName = b.titleName,
                    coverUrl = b.coverUrl,
                    typeLabel = b.typeLabel,
                    createdAt = b.createdAt,
                    categoryIdsJson = b.categoryIdsJson,
                ),
            )
        }
        for (p in payload.progress) {
            // Keep whichever position is newer: a restore must not move you back.
            val current = dao.getProgress(p.sourceId, p.titleId)
            if (current != null && current.updatedAt >= p.updatedAt) continue
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = p.sourceId,
                    titleId = p.titleId,
                    titleName = p.titleName,
                    coverUrl = p.coverUrl,
                    typeLabel = p.typeLabel,
                    chapterId = p.chapterId,
                    chapterName = p.chapterName,
                    chapterUrl = p.chapterUrl,
                    pageIndex = p.pageIndex,
                    pageCount = p.pageCount,
                    updatedAt = p.updatedAt,
                    pageOffset = p.pageOffset,
                ),
            )
        }
        for (c in payload.chapterProgress) {
            val current = dao.getChapterProgress(c.sourceId, c.titleId, c.chapterId)
            if (current != null && current.updatedAt >= c.updatedAt) continue
            dao.upsertChapterProgress(
                ChapterProgressEntity(
                    sourceId = c.sourceId,
                    titleId = c.titleId,
                    chapterId = c.chapterId,
                    pageIndex = c.pageIndex,
                    pageOffset = c.pageOffset,
                    pageCount = c.pageCount,
                    completed = c.completed,
                    pageAspectsJson = c.pageAspectsJson,
                    updatedAt = c.updatedAt,
                ),
            )
        }
        for (r in payload.chapterRead) {
            dao.markChapterRead(ChapterReadEntity(r.sourceId, r.titleId, r.chapterId, r.readAt))
        }
        if (payload.pageBookmarks.isNotEmpty()) {
            // Restoring the same file twice must not duplicate bookmarks.
            val existing = dao.allPageBookmarks()
                .map { listOf(it.sourceId, it.titleId, it.chapterId, it.pageIndex, it.createdAt) }
                .toHashSet()
            for (b in payload.pageBookmarks) {
                if (listOf(b.sourceId, b.titleId, b.chapterId, b.pageIndex, b.createdAt) in existing) continue
                dao.insertPageBookmark(
                    PageBookmarkEntity(
                        sourceId = b.sourceId,
                        titleId = b.titleId,
                        chapterId = b.chapterId,
                        chapterName = b.chapterName,
                        pageIndex = b.pageIndex,
                        pageOffset = b.pageOffset,
                        pageImageUrl = b.pageImageUrl,
                        note = b.note,
                        createdAt = b.createdAt,
                    ),
                )
            }
        }
        for (s in payload.sourcePacks) {
            dao.upsertSourcePack(
                SourcePackEntity(id = s.id, json = s.json, installedAt = s.installedAt),
            )
        }
        for (t in payload.titlePrefs) {
            dao.upsertTitlePrefs(
                TitlePrefsEntity(
                    sourceId = t.sourceId,
                    titleId = t.titleId,
                    readerMode = t.readerMode,
                    readerTheme = t.readerTheme,
                    gap = t.gap,
                    chapterFilter = t.chapterFilter,
                    chapterSortAsc = t.chapterSortAsc,
                    branch = t.branch,
                    showDuplicates = t.showDuplicates,
                ),
            )
        }
        for (l in payload.trackerLinks) {
            dao.upsertTrackerLink(
                TrackerLinkEntity(
                    sourceId = l.sourceId,
                    titleId = l.titleId,
                    tracker = l.tracker,
                    remoteId = l.remoteId,
                    remoteUrl = l.remoteUrl,
                    status = l.status,
                    score = l.score,
                    lastSync = l.lastSync,
                ),
            )
        }
        sources.load()
    }

    private companion object {
        const val AUTO_PREFIX = "LUME_auto_"
    }
}
