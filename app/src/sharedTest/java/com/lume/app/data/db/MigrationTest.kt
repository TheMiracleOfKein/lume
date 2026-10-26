package com.lume.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks every migration from v7 (the first exported schema, `app/schemas`) against the schema
 * Room expects. 6→7 can't be checked here: no v6 schema was ever exported.
 *
 * Shared between JVM tests (Robolectric, run by CI on Linux) and instrumented tests
 * (`connectedDebugAndroidTest`). Room's test driver compares database paths with "/", so the
 * JVM run is skipped on Windows; there, run it on a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @Before
    fun notOnWindowsJvm() {
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LumeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate7To8KeepsDataAndAddsLibraryChapters() {
        helper.createDatabase(dbName, 7).apply {
            execSQL("INSERT INTO bookmarks (sourceId, titleId, titleName, createdAt, categoryIdsJson) VALUES ('s', 't', 'Title', 1, '[1]')")
            execSQL("INSERT INTO chapter_read (sourceId, titleId, chapterId, readAt) VALUES ('s', 't', 'c1', 2)")
            execSQL("INSERT INTO title_prefs (sourceId, titleId, readerMode) VALUES ('s', 't', 1)")
            execSQL(
                "INSERT INTO chapter_progress (sourceId, titleId, chapterId, pageIndex, pageOffset, pageCount, completed, pageAspectsJson, updatedAt) " +
                    "VALUES ('s', 't', 'c2', 4, 0.5, 10, 0, '[]', 3)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, LumeDatabase.MIGRATION_7_8)

        db.query("SELECT titleName, categoryIdsJson FROM bookmarks").use {
            it.moveToFirst()
            assertEquals("Title", it.getString(0))
            assertEquals("[1]", it.getString(1))
        }
        db.query("SELECT COUNT(*) FROM chapter_read").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT pageIndex, pageOffset FROM chapter_progress").use {
            it.moveToFirst()
            assertEquals(4, it.getInt(0))
            assertEquals(0.5f, it.getFloat(1), 0f)
        }
        // Old per-title prefs keep their values; the new chapter-list settings start at defaults.
        db.query("SELECT readerMode, chapterFilter, chapterSortAsc, branch, showDuplicates FROM title_prefs").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(0, it.getInt(1))
            assertEquals(0, it.getInt(2))
            assertNull(it.getString(3))
            assertEquals(0, it.getInt(4))
        }
        db.execSQL(
            "INSERT INTO library_chapters (sourceId, titleId, chapterId, dedupeKey, name, sourceOrder, discoveredAt) " +
                "VALUES ('s', 't', 'c3', 'v1#c3', 'Ch 3', 0, 9)",
        )
        db.query("SELECT discoveredAt FROM library_chapters WHERE chapterId = 'c3'").use {
            it.moveToFirst()
            assertEquals(9L, it.getLong(0))
        }
    }

    @Test
    fun migrate8To9KeepsDataAndAddsTitleSources() {
        helper.createDatabase(dbName, 8).apply {
            execSQL("INSERT INTO bookmarks (sourceId, titleId, titleName, createdAt, categoryIdsJson) VALUES ('s', 't', 'Title', 1, '[]')")
            execSQL(
                "INSERT INTO library_chapters (sourceId, titleId, chapterId, dedupeKey, name, sourceOrder, discoveredAt) " +
                    "VALUES ('s', 't', 'c1', 'v#c1', 'Ch 1', 0, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true, LumeDatabase.MIGRATION_8_9)

        db.query("SELECT COUNT(*) FROM library_chapters").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.execSQL(
            "INSERT INTO title_sources (sourceId, titleId, linkSourceId, linkTitleId, linkTitleName, state, priority, addedAt) " +
                "VALUES ('s', 't', 'dex', 'x', 'Title', 'linked', 0, 5)",
        )
        db.query("SELECT linkSourceId, state FROM title_sources").use {
            it.moveToFirst()
            assertEquals("dex", it.getString(0))
            assertEquals("linked", it.getString(1))
        }
    }

    @Test
    fun migrate9To10KeepsTitlePrefsAndAddsSubscription() {
        helper.createDatabase(dbName, 9).apply {
            execSQL("INSERT INTO title_prefs (sourceId, titleId, readerMode, branch) VALUES ('s', 't', 1, 'team')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 10, true, LumeDatabase.MIGRATION_9_10)

        db.query("SELECT readerMode, branch, autoDownload FROM title_prefs").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals("team", it.getString(1))
            assertEquals(0, it.getInt(2))
        }
    }

    @Test
    fun freshDatabaseOpensWithAllMigrations() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        helper.createDatabase(dbName, 7).close()
        val db = androidx.room.Room.databaseBuilder(context, LumeDatabase::class.java, dbName)
            .addMigrations(*LumeDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        // Opening runs the migrations and Room's own schema check.
        db.openHelper.writableDatabase
        db.close()
    }
}
