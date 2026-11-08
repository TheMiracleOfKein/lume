package com.lume.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Room entities and query rows of the library database.

@Entity(tableName = "bookmarks", primaryKeys = ["sourceId", "titleId"])
data class BookmarkEntity(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** JSON array of category ids, e.g. `[1,2]` */
    val categoryIdsJson: String = "[]",
)

@Entity(tableName = "reading_progress", primaryKeys = ["sourceId", "titleId"])
data class ProgressEntity(
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
    val updatedAt: Long = System.currentTimeMillis(),
    /** Fraction (0..1) of page [pageIndex]'s height scrolled past; see `ReaderAnchor`. */
    @ColumnInfo(defaultValue = "0")
    val pageOffset: Float = 0f,
    /**
     * The image of page [pageIndex] when it was last read here, so "Continue" can show the panel
     * you stopped at. Not backed up or synced: page links change, and the card falls back to the cover.
     */
    val pageImageUrl: String? = null,
)

/**
 * Where you are inside one chapter. Each chapter keeps its own spot, so peeking at another
 * chapter no longer erases the one you were reading. [ProgressEntity] stays the title-level
 * "Continue" pointer.
 */
@Entity(tableName = "chapter_progress", primaryKeys = ["sourceId", "titleId", "chapterId"])
data class ChapterProgressEntity(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val pageIndex: Int = 0,
    /** Fraction (0..1) of the page's height scrolled past. */
    val pageOffset: Float = 0f,
    val pageCount: Int = 0,
    /** The position is at the end of the chapter (opening it again starts from the top). */
    val completed: Boolean = false,
    /** JSON array of width/height per page (0 = not measured yet), so placeholders get real sizes. */
    val pageAspectsJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A spot the reader pinned by hand, with an optional note. */
@Entity(tableName = "page_bookmarks", indices = [Index("sourceId", "titleId")])
data class PageBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val chapterName: String,
    val pageIndex: Int,
    val pageOffset: Float = 0f,
    val pageImageUrl: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chapter_read", primaryKeys = ["sourceId", "titleId", "chapterId"])
data class ChapterReadEntity(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val readAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloads", primaryKeys = ["sourceId", "titleId", "chapterId"])
data class DownloadEntity(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val chapterName: String,
    val pageCount: Int,
    val pagePathsJson: String,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "source_packs")
data class SourcePackEntity(
    @PrimaryKey val id: String,
    val json: String,
    val installedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(tableName = "title_meta", primaryKeys = ["sourceId", "titleId"])
data class TitleMetaEntity(
    val sourceId: String,
    val titleId: String,
    val lastChapterId: String? = null,
    val lastChapterName: String? = null,
    val lastCheckedAt: Long = 0L,
    val knownChapterCount: Int = 0,
    /** Chapters found since last open; drives library «новое» badge. */
    val unreadNewCount: Int = 0,
)

@Entity(tableName = "title_prefs", primaryKeys = ["sourceId", "titleId"])
data class TitlePrefsEntity(
    val sourceId: String,
    val titleId: String,
    val readerMode: Int? = null,
    val readerTheme: Int? = null,
    val gap: Float? = null,
    /** Chapter list filters, a [com.lume.app.domain.ChapterFilter] bitmask. */
    @ColumnInfo(defaultValue = "0")
    val chapterFilter: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val chapterSortAsc: Boolean = false,
    /** Preferred translation team (branch id); null = the team with the most chapters. */
    val branch: String? = null,
    /** Show every team's copy of a chapter instead of one row per chapter. */
    @ColumnInfo(defaultValue = "0")
    val showDuplicates: Boolean = false,
    /**
     * The bell: library update checks look at this title (at most [WATCHED_TITLES_LIMIT] titles
     * have it), and its new chapters download when downloads are set to subscribed titles.
     */
    @ColumnInfo(defaultValue = "0")
    val autoDownload: Boolean = false,
)

/**
 * Every known chapter of a library title. New chapters are found by comparing ids with these
 * rows; rows found that way get [discoveredAt] and make up the Updates screen.
 */
@Entity(
    tableName = "library_chapters",
    primaryKeys = ["sourceId", "titleId", "chapterId"],
    indices = [Index("discoveredAt")],
)
data class LibraryChapterEntity(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    /** Same value for every team's copy of one chapter; see `domain/ChapterDedupe.kt`. */
    val dedupeKey: String,
    val name: String,
    val branchId: String? = null,
    val branchName: String? = null,
    /** Position in the source's list (0 = first, usually the newest). */
    val sourceOrder: Int = 0,
    /** When a library check found it; null for chapters known from the start. */
    val discoveredAt: Long? = null,
)

/** One row of the Updates screen. */
data class UpdateRow(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val chapterName: String,
    val discoveredAt: Long,
    val titleName: String,
    val coverUrl: String?,
    val read: Boolean,
)

/** Per-title numbers for the library badges, filters and sorting. */
data class LibraryTitleStats(
    val sourceId: String,
    val titleId: String,
    val total: Int,
    val unread: Int,
    val downloaded: Int,
    val lastReadAt: Long?,
    val latestFoundAt: Long?,
)

@Entity(tableName = "tracker_links", primaryKeys = ["sourceId", "titleId", "tracker"])
data class TrackerLinkEntity(
    val sourceId: String,
    val titleId: String,
    val tracker: String,
    val remoteId: String,
    val remoteUrl: String? = null,
    val status: String? = null,
    val score: Float? = null,
    val lastSync: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "download_queue",
    indices = [Index(value = ["sourceId", "titleId", "chapterId"], unique = true)],
)
data class DownloadQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val titleName: String,
    val chapterName: String,
    val coverUrl: String? = null,
    /** pending / running / done / error */
    val status: String = STATUS_PENDING,
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"
    }
}

@Entity(tableName = "offline_title_cache", primaryKeys = ["sourceId", "titleId"])
data class OfflineTitleCacheEntity(
    val sourceId: String,
    val titleId: String,
    val titleJson: String,
    val chaptersJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Another source's copy of a title ([sourceId], [titleId] is the main source). [state] is
 * [LINKED] (its chapters are part of the title), [SUGGESTED] (found by Lume, waiting for the
 * reader) or [DISMISSED] (not suggested again). Linked sources fill in chapters in [priority]
 * order, lowest first.
 */
@Entity(
    tableName = "title_sources",
    primaryKeys = ["sourceId", "titleId", "linkSourceId", "linkTitleId"],
    indices = [Index("linkSourceId", "linkTitleId")],
)
data class TitleSourceEntity(
    val sourceId: String,
    val titleId: String,
    val linkSourceId: String,
    val linkTitleId: String,
    val linkTitleName: String,
    val coverUrl: String? = null,
    val state: String = LINKED,
    val priority: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val LINKED = "linked"
        const val SUGGESTED = "suggested"
        const val DISMISSED = "dismissed"
    }
}

/** A device folder Lume re-scans for new chapters (Settings → Library → Watched folders). */
@Entity(tableName = "watched_folders")
data class WatchedFolderEntity(
    @PrimaryKey val treeUri: String,
    val label: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** 0 = never scanned. */
    val lastScanAt: Long = 0,
    /** False when the read permission was lost; "Reconnect" picks the folder again. */
    val available: Boolean = true,
)

/** One file copied from a picked folder: what it looked like then and where the copy is. */
@Entity(tableName = "imported_files", primaryKeys = ["treeUri", "documentId"])
data class ImportedFileEntity(
    val treeUri: String,
    val documentId: String,
    val size: Long,
    val lastModified: Long,
    val destPath: String,
)
