package com.lume.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        WatchedFolderEntity::class,
        ImportedFileEntity::class,
    ],
    version = 13,
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

        /** One queue row per chapter: duplicates are dropped (lowest id kept), then the index keeps them out. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM download_queue WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM download_queue GROUP BY sourceId, titleId, chapterId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_download_queue_sourceId_titleId_chapterId " +
                        "ON download_queue (sourceId, titleId, chapterId)",
                )
            }
        }

        /** The page image of the "Continue" spot, for showing its panel. Additive. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN pageImageUrl TEXT")
            }
        }

        /** Watched folders and the ledger of files copied from them. Additive. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watched_folders (
                        treeUri TEXT NOT NULL,
                        label TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        lastScanAt INTEGER NOT NULL,
                        available INTEGER NOT NULL,
                        PRIMARY KEY(treeUri)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS imported_files (
                        treeUri TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        destPath TEXT NOT NULL,
                        PRIMARY KEY(treeUri, documentId)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATIONS: Array<Migration> get() = arrayOf(
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13,
        )
    }
}

/** How many library titles can have the bell; update checks look only at these. */
const val WATCHED_TITLES_LIMIT = 20
