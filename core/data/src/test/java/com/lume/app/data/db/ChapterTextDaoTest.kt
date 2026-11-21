package com.lume.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The chapter text index (FTS4, unicode61) and the codex, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class ChapterTextDaoTest {
    private lateinit var db: LumeDatabase
    private val dao get() = db.libraryDao()

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LumeDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private fun rows(chapter: String, vararg texts: String) =
        texts.mapIndexed { i, t -> ChapterTextEntity(sourceId = "s", titleId = "t", chapterId = chapter, block = i, text = t) }

    private fun index(chapter: String, vararg texts: String) = runBlocking {
        dao.replaceChapterText("s", "t", chapter, rows(chapter, *texts))
    }

    @Test
    fun matchesEnglishCaseInsensitively() = runBlocking {
        index("c1", "Lin Feng smiled.", "Nothing here.")
        index("c2", "Then LIN FENG left.", "lin feng again", "other")

        val hits = dao.mentionsByChapter("s", "t", "\"lin feng\"").associateBy { it.chapterId }

        assertEquals(setOf("c1", "c2"), hits.keys)
        assertEquals(ChapterMention("c1", 0, 0, 1), hits.getValue("c1"))
        assertEquals(ChapterMention("c2", 0, 1, 2), hits.getValue("c2"))
    }

    @Test
    fun matchesCyrillicCaseInsensitively() = runBlocking {
        index("c1", "Ли Фэн вошёл в зал.", "Тишина.")
        index("c2", "ЛИ ФЭН ушёл.")

        val hits = dao.mentionsByChapter("s", "t", "\"ли фэн\" OR \"фэном\"")

        assertEquals(setOf("c1", "c2"), hits.map { it.chapterId }.toSet())
    }

    @Test
    fun otherTitlesAreNotSearched() = runBlocking {
        index("c1", "Kirito")
        dao.replaceChapterText("s", "other", "c9", listOf(ChapterTextEntity(sourceId = "s", titleId = "other", chapterId = "c9", block = 0, text = "Kirito")))

        assertEquals(listOf("c1"), dao.mentionsByChapter("s", "t", "kirito").map { it.chapterId })
    }

    @Test
    fun replaceIsIdempotent() = runBlocking {
        index("c1", "Kirito one", "two")
        index("c1", "Kirito one", "two")

        assertEquals(2, dao.textIndexSize().blocks)
        assertEquals(listOf(ChapterMention("c1", 0, 0, 1)), dao.mentionsByChapter("s", "t", "kirito"))
        assertTrue(dao.isChapterIndexed("s", "t", "c1"))
        assertEquals(listOf("c1"), dao.indexedChapterIds("s", "t"))
    }

    @Test
    fun replaceDropsOldTextFromTheIndex() = runBlocking {
        index("c1", "Kirito one")
        index("c1", "Asuna two")

        assertTrue(dao.mentionsByChapter("s", "t", "kirito").isEmpty())
        assertEquals(1, dao.mentionsByChapter("s", "t", "asuna").size)
    }

    @Test
    fun matchingBlocksBeforeIsNewestFirstAndBounded() = runBlocking {
        index("c1", "Kirito a", "x", "Kirito b", "Kirito c", "y")

        val hits = dao.matchingBlocksBefore("s", "t", "c1", "kirito", beforeBlock = 3, limit = 5)

        assertEquals(listOf(2, 0), hits.map { it.block })
    }

    @Test
    fun blockAccessors() = runBlocking {
        index("c1", "a", "b", "c", "d")

        assertEquals("c", dao.blockText("s", "t", "c1", 2))
        assertEquals(listOf(1, 2), dao.blocksInRange("s", "t", "c1", 1, 2).map { it.block })
        assertEquals(listOf("c", "d"), dao.chapterTail("s", "t", "c1", 2).map { it.text })
    }

    @Test
    fun deleteTitleAndAll() = runBlocking {
        index("c1", "Kirito")
        dao.replaceChapterText("s", "other", "c9", listOf(ChapterTextEntity(sourceId = "s", titleId = "other", chapterId = "c9", block = 0, text = "Kirito")))

        dao.deleteTitleText("s", "t")
        assertFalse(dao.isChapterIndexed("s", "t", "c1"))
        assertTrue(dao.isChapterIndexed("s", "other", "c9"))

        dao.deleteAllText()
        assertEquals(0, dao.textIndexSize().blocks)
    }

    @Test
    fun codexAliasesFollowTheirEntry() = runBlocking {
        val id = dao.saveCodexEntry(CodexEntryEntity(sourceId = "s", titleId = "t", name = "Lin Feng", kind = "character"), listOf("Feng", " Feng ", ""))
        assertEquals(listOf("Feng"), dao.codexOnce("s", "t").single().aliases.map { it.alias })

        dao.saveCodexEntry(dao.codexOnce("s", "t").single().entry.copy(note = "hero"), listOf("A", "B"))
        assertEquals(listOf("A", "B"), dao.codexOnce("s", "t").single().aliases.map { it.alias }.sorted())

        dao.deleteCodexEntry(id)
        assertTrue(dao.codexOnce("s", "t").isEmpty())
        assertTrue(dao.allCodexAliases().isEmpty())
    }
}
