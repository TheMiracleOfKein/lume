package com.lume.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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

@Entity(tableName = "download_queue")
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

@Dao
interface LibraryDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId)")
    fun observeIsBookmarked(sourceId: String, titleId: String): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getBookmark(sourceId: String, titleId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteBookmark(sourceId: String, titleId: String)

    /** Library titles with the bell on; the ones update checks look at. */
    @Query(
        """
        SELECT b.* FROM bookmarks b JOIN title_prefs p ON p.sourceId = b.sourceId AND p.titleId = b.titleId
        WHERE p.autoDownload = 1 ORDER BY b.createdAt DESC
        """,
    )
    suspend fun watchedBookmarks(): List<BookmarkEntity>

    @Query(
        """
        SELECT COUNT(*) FROM bookmarks b JOIN title_prefs p ON p.sourceId = b.sourceId AND p.titleId = b.titleId
        WHERE p.autoDownload = 1
        """,
    )
    fun observeWatchedCount(): Flow<Int>

    /** The [limit] library titles read (or, unread, added) most recently. */
    @Query(
        """
        SELECT b.* FROM bookmarks b LEFT JOIN reading_progress r ON r.sourceId = b.sourceId AND r.titleId = b.titleId
        ORDER BY COALESCE(r.updatedAt, b.createdAt) DESC LIMIT :limit
        """,
    )
    suspend fun recentBookmarks(limit: Int): List<BookmarkEntity>

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun allProgress(): List<ProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getProgress(sourceId: String, titleId: String): ProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    fun observeProgress(sourceId: String, titleId: String): Flow<ProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(item: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChapterRead(item: ChapterReadEntity)

    @Query("SELECT chapterId FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun readChapterIds(sourceId: String, titleId: String): List<String>

    @Query("SELECT chapterId FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeReadChapterIds(sourceId: String, titleId: String): Flow<List<String>>

    @Query("SELECT * FROM chapter_read")
    suspend fun allChapterRead(): List<ChapterReadEntity>

    // Per-chapter progress
    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun getChapterProgress(sourceId: String, titleId: String, chapterId: String): ChapterProgressEntity?

    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeChapterProgress(sourceId: String, titleId: String): Flow<List<ChapterProgressEntity>>

    @Query("SELECT * FROM chapter_progress")
    suspend fun allChapterProgress(): List<ChapterProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapterProgress(item: ChapterProgressEntity)

    // Page bookmarks
    @Query("SELECT * FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY createdAt DESC")
    fun observePageBookmarks(sourceId: String, titleId: String): Flow<List<PageBookmarkEntity>>

    @Query("SELECT * FROM page_bookmarks ORDER BY createdAt ASC")
    suspend fun allPageBookmarks(): List<PageBookmarkEntity>

    @Insert
    suspend fun insertPageBookmark(item: PageBookmarkEntity): Long

    @Query("UPDATE page_bookmarks SET note = :note WHERE id = :id")
    suspend fun updatePageBookmarkNote(id: Long, note: String?)

    @Query("DELETE FROM page_bookmarks WHERE id = :id")
    suspend fun deletePageBookmark(id: Long)

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY downloadedAt DESC")
    suspend fun downloadsForTitle(sourceId: String, titleId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun allDownloads(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteDownloadsForTitle(sourceId: String, titleId: String)

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun getDownload(sourceId: String, titleId: String, chapterId: String): DownloadEntity?

    @Query("SELECT chapterId FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun downloadedChapterIds(sourceId: String, titleId: String): List<String>

    @Query("DELETE FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId")
    suspend fun deleteDownload(sourceId: String, titleId: String, chapterId: String)

    @Query("SELECT * FROM offline_title_cache WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getOfflineTitleCache(sourceId: String, titleId: String): OfflineTitleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineTitleCache(item: OfflineTitleCacheEntity)

    @Query("DELETE FROM reading_progress")
    suspend fun clearAllProgress()

    @Query("DELETE FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun clearProgress(sourceId: String, titleId: String)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistoryLimit(limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM source_packs ORDER BY installedAt ASC")
    suspend fun allSourcePacks(): List<SourcePackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourcePack(item: SourcePackEntity)

    @Query("DELETE FROM source_packs WHERE id = :id")
    suspend fun deleteSourcePack(id: String)

    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun allCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(item: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    // Title meta
    @Query("SELECT * FROM title_meta")
    fun observeTitleMeta(): Flow<List<TitleMetaEntity>>

    @Query("SELECT * FROM title_meta WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getTitleMeta(sourceId: String, titleId: String): TitleMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleMeta(item: TitleMetaEntity)

    // Title prefs
    @Query("SELECT * FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getTitlePrefs(sourceId: String, titleId: String): TitlePrefsEntity?

    @Query("SELECT * FROM title_prefs")
    suspend fun allTitlePrefs(): List<TitlePrefsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitlePrefs(item: TitlePrefsEntity)

    // Tracker links
    @Query("SELECT * FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeTrackerLinks(sourceId: String, titleId: String): Flow<List<TrackerLinkEntity>>

    @Query("SELECT * FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun getTrackerLinks(sourceId: String, titleId: String): List<TrackerLinkEntity>

    @Query("SELECT * FROM tracker_links")
    suspend fun allTrackerLinks(): List<TrackerLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackerLink(item: TrackerLinkEntity)

    @Query("DELETE FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId AND tracker = :tracker")
    suspend fun deleteTrackerLink(sourceId: String, titleId: String, tracker: String)

    // Download queue
    @Query("SELECT * FROM download_queue ORDER BY createdAt ASC")
    fun observeDownloadQueue(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE status IN ('pending','running') ORDER BY createdAt ASC")
    suspend fun pendingDownloadQueue(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE id = :id LIMIT 1")
    suspend fun getDownloadQueueItem(id: Long): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadQueue(item: DownloadQueueEntity): Long

    @Query("UPDATE download_queue SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateDownloadQueueStatus(id: Long, status: String, progress: Float)

    /** Progress of a running row only, so a late write cannot revive a paused or failed one. */
    @Query("UPDATE download_queue SET progress = :progress WHERE id = :id AND status = 'running'")
    suspend fun updateRunningDownloadProgress(id: Long, progress: Float)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteDownloadQueueItem(id: Long)

    @Query("SELECT * FROM download_queue WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun findDownloadQueueItem(sourceId: String, titleId: String, chapterId: String): DownloadQueueEntity?

    @Query("DELETE FROM download_queue WHERE status = 'done'")
    suspend fun clearDoneDownloadQueue()

    /** Rows left "running" when the process died; they go back to the queue at startup. */
    @Query("UPDATE download_queue SET status = 'pending', progress = 0 WHERE status = 'running'")
    suspend fun resetRunningDownloads(): Int

    // Library chapters
    @Query("SELECT * FROM library_chapters WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun libraryChapters(sourceId: String, titleId: String): List<LibraryChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryChapters(items: List<LibraryChapterEntity>)

    @Query("DELETE FROM library_chapters WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun deleteLibraryChapters(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("DELETE FROM library_chapters WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteLibraryChaptersForTitle(sourceId: String, titleId: String)

    @Query(
        """
        SELECT c.sourceId, c.titleId, c.chapterId, c.name AS chapterName, c.discoveredAt,
               b.titleName, b.coverUrl,
               EXISTS(SELECT 1 FROM library_chapters o
                      JOIN chapter_read r ON r.sourceId = o.sourceId AND r.titleId = o.titleId
                           AND r.chapterId = o.chapterId
                      WHERE o.sourceId = c.sourceId AND o.titleId = c.titleId
                           AND o.dedupeKey = c.dedupeKey) AS read
        FROM library_chapters c
        JOIN bookmarks b ON b.sourceId = c.sourceId AND b.titleId = c.titleId
        WHERE c.discoveredAt IS NOT NULL
        ORDER BY c.discoveredAt DESC, c.sourceOrder ASC
        LIMIT :limit
        """,
    )
    fun observeUpdates(limit: Int): Flow<List<UpdateRow>>

    /**
     * Unread = chapters (one per [LibraryChapterEntity.dedupeKey], so a chapter several teams
     * translated counts once) with no read copy.
     *
     * One pass over the chapters, grouped, instead of correlated subqueries per title: the old
     * `NOT IN` form re-ran a subquery for every chapter and took many seconds on a big library.
     */
    @Query(
        """
        SELECT b.sourceId, b.titleId,
            COALESCE(t.total, 0) AS total,
            COALESCE(t.unread, 0) AS unread,
            COALESCE(d.downloaded, 0) AS downloaded,
            p.updatedAt AS lastReadAt,
            t.latestFoundAt AS latestFoundAt
        FROM bookmarks b
        LEFT JOIN (
            SELECT k.sourceId, k.titleId, COUNT(*) AS total, SUM(k.isRead = 0) AS unread,
                MAX(k.foundAt) AS latestFoundAt
            FROM (
                SELECT c.sourceId, c.titleId, MAX(r.chapterId IS NOT NULL) AS isRead,
                    MAX(c.discoveredAt) AS foundAt
                FROM library_chapters c
                LEFT JOIN chapter_read r ON r.sourceId = c.sourceId AND r.titleId = c.titleId
                    AND r.chapterId = c.chapterId
                GROUP BY c.sourceId, c.titleId, c.dedupeKey
            ) k
            GROUP BY k.sourceId, k.titleId
        ) t ON t.sourceId = b.sourceId AND t.titleId = b.titleId
        LEFT JOIN (
            SELECT sourceId, titleId, COUNT(*) AS downloaded FROM downloads GROUP BY sourceId, titleId
        ) d ON d.sourceId = b.sourceId AND d.titleId = b.titleId
        LEFT JOIN reading_progress p ON p.sourceId = b.sourceId AND p.titleId = b.titleId
        """,
    )
    fun observeLibraryStats(): Flow<List<LibraryTitleStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChaptersRead(items: List<ChapterReadEntity>)

    @Query("DELETE FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun markChaptersUnread(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("DELETE FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun deleteChapterProgress(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("SELECT DISTINCT sourceId, titleId FROM tracker_links")
    fun observeTrackedTitles(): Flow<List<TitleKey>>

    @Query("SELECT * FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    fun observeTitlePrefs(sourceId: String, titleId: String): Flow<TitlePrefsEntity?>

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setCategoryOrder(id: Long, sortOrder: Int)

    @Query("SELECT * FROM offline_title_cache")
    suspend fun allOfflineTitleCache(): List<OfflineTitleCacheEntity>

    /** Cached details of library titles (status, alternative title) without the chapter lists. */
    @Query(
        """
        SELECT o.sourceId, o.titleId, o.titleJson FROM offline_title_cache o
        JOIN bookmarks b ON b.sourceId = o.sourceId AND b.titleId = o.titleId
        """,
    )
    fun observeLibraryTitleJson(): Flow<List<TitleJsonRow>>

    @Query("SELECT DISTINCT chapterId FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observePageBookmarkChapterIds(sourceId: String, titleId: String): Flow<List<String>>

    // Title sources
    @Query("SELECT * FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY priority ASC, addedAt ASC")
    fun observeTitleSources(sourceId: String, titleId: String): Flow<List<TitleSourceEntity>>

    @Query("SELECT * FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY priority ASC, addedAt ASC")
    suspend fun titleSources(sourceId: String, titleId: String): List<TitleSourceEntity>

    @Query("SELECT * FROM title_sources")
    suspend fun allTitleSources(): List<TitleSourceEntity>

    /** Linked sources per title that has any (for the library's sources badge). */
    @Query("SELECT sourceId, titleId, COUNT(*) AS linked FROM title_sources WHERE state = 'linked' GROUP BY sourceId, titleId")
    fun observeLinkedSourceCounts(): Flow<List<LinkedSourceCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleSource(item: TitleSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleSources(items: List<TitleSourceEntity>)

    @Query("DELETE FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId AND linkSourceId = :linkSourceId AND linkTitleId = :linkTitleId")
    suspend fun deleteTitleSource(sourceId: String, titleId: String, linkSourceId: String, linkTitleId: String)

    @Query("DELETE FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleSources(sourceId: String, titleId: String)

    @Query("SELECT * FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun chapterReadFor(sourceId: String, titleId: String): List<ChapterReadEntity>

    @Query("DELETE FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteChapterReadFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun chapterProgressFor(sourceId: String, titleId: String): List<ChapterProgressEntity>

    @Query("DELETE FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteChapterProgressFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun pageBookmarksFor(sourceId: String, titleId: String): List<PageBookmarkEntity>

    @Query("UPDATE page_bookmarks SET sourceId = :newSourceId, titleId = :newTitleId, chapterId = :chapterId WHERE id = :id")
    suspend fun movePageBookmark(id: Long, newSourceId: String, newTitleId: String, chapterId: String)

    @Query("DELETE FROM title_meta WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleMeta(sourceId: String, titleId: String)

    @Query("DELETE FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitlePrefs(sourceId: String, titleId: String)

    @Query("DELETE FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTrackerLinksFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM download_queue WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun downloadQueueFor(sourceId: String, titleId: String): List<DownloadQueueEntity>
}

data class TitleKey(val sourceId: String, val titleId: String)

data class LinkedSourceCount(val sourceId: String, val titleId: String, val linked: Int)

data class TitleJsonRow(val sourceId: String, val titleId: String, val titleJson: String)

@Database(
    entities = [
        BookmarkEntity::class,
        ProgressEntity::class,
        ChapterReadEntity::class,
        DownloadEntity::class,
        SourcePackEntity::class,
        CategoryEntity::class,
        TitleMetaEntity::class,
        TitlePrefsEntity::class,
        TrackerLinkEntity::class,
        DownloadQueueEntity::class,
        OfflineTitleCacheEntity::class,
        ChapterProgressEntity::class,
        PageBookmarkEntity::class,
        LibraryChapterEntity::class,
        TitleSourceEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class LumeDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_title_cache (
                        sourceId TEXT NOT NULL,
                        titleId TEXT NOT NULL,
                        titleJson TEXT NOT NULL,
                        chaptersJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceId, titleId)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Exact in-chapter resume and page bookmarks. Additive; existing progress is kept. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN pageOffset REAL NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chapter_progress (
                        sourceId TEXT NOT NULL,
                        titleId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        pageOffset REAL NOT NULL,
                        pageCount INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        pageAspectsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceId, titleId, chapterId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS page_bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceId TEXT NOT NULL,
                        titleId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        chapterName TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        pageOffset REAL NOT NULL,
                        pageImageUrl TEXT,
                        note TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_page_bookmarks_sourceId_titleId ON page_bookmarks (sourceId, titleId)",
                )
                // The chapter each title was last read in keeps its page.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO chapter_progress
                        (sourceId, titleId, chapterId, pageIndex, pageOffset, pageCount, completed, pageAspectsJson, updatedAt)
                    SELECT sourceId, titleId, chapterId, pageIndex, 0, pageCount, 0, '[]', updatedAt FROM reading_progress
                    """.trimIndent(),
                )
            }
        }
        /** Library chapters (updates, unread counts) and per-title chapter list settings. Additive. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_chapters (
                        sourceId TEXT NOT NULL,
                        titleId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        dedupeKey TEXT NOT NULL,
                        name TEXT NOT NULL,
                        branchId TEXT,
                        branchName TEXT,
                        sourceOrder INTEGER NOT NULL,
                        discoveredAt INTEGER,
                        PRIMARY KEY(sourceId, titleId, chapterId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_library_chapters_discoveredAt ON library_chapters (discoveredAt)",
                )
                db.execSQL("ALTER TABLE title_prefs ADD COLUMN chapterFilter INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE title_prefs ADD COLUMN chapterSortAsc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE title_prefs ADD COLUMN branch TEXT")
                db.execSQL("ALTER TABLE title_prefs ADD COLUMN showDuplicates INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Other sources of a title (linked, suggested, dismissed). Additive. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS title_sources (
                        sourceId TEXT NOT NULL,
                        titleId TEXT NOT NULL,
                        linkSourceId TEXT NOT NULL,
                        linkTitleId TEXT NOT NULL,
                        linkTitleName TEXT NOT NULL,
                        coverUrl TEXT,
                        state TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceId, titleId, linkSourceId, linkTitleId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_title_sources_linkSourceId_linkTitleId ON title_sources (linkSourceId, linkTitleId)",
                )
            }
        }

        /** Per-title subscription to new-chapter downloads. Additive. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE title_prefs ADD COLUMN autoDownload INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
    }
}

/** How many library titles can have the bell; update checks look only at these. */
const val WATCHED_TITLES_LIMIT = 20
