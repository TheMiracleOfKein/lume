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

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteDownloadQueueItem(id: Long)

    @Query("SELECT * FROM download_queue WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun findDownloadQueueItem(sourceId: String, titleId: String, chapterId: String): DownloadQueueEntity?

    @Query("DELETE FROM download_queue WHERE status = 'done'")
    suspend fun clearDoneDownloadQueue()

    /** Rows left "running" when the process died; they go back to the queue at startup. */
    @Query("UPDATE download_queue SET status = 'pending', progress = 0 WHERE status = 'running'")
    suspend fun resetRunningDownloads(): Int
}

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
    ],
    version = 7,
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
    }
}
