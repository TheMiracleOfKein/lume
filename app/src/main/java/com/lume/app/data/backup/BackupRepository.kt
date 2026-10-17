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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version 2 adds per-chapter progress, page bookmarks and read marks. Version 1 files still load:
 * the new lists default to empty.
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
) {
    companion object {
        const val CURRENT_VERSION = 2

        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun parse(text: String): BackupPayload = json.decodeFromString(serializer(), text)
    }
}

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
    private val json: Json = BackupPayload.json,
) {
    fun backupsDir(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
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
        )
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(backupsDir(), "LUME_backup_$stamp.json")
        file.writeText(json.encodeToString(payload))
        file
    }

    suspend fun restoreFromFile(file: File) = withContext(Dispatchers.IO) {
        restorePayload(BackupPayload.parse(file.readText()))
    }

    @Suppress("unused")
    suspend fun restoreFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error(com.lume.app.data.locale.appString(com.lume.app.R.string.error_backup_read))
        restorePayload(BackupPayload.parse(text))
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
}
