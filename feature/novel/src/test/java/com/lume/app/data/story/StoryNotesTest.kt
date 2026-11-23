package com.lume.app.data.story

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.data.db.CODEX_AI
import com.lume.app.data.db.CODEX_IGNORED
import com.lume.app.data.db.CODEX_USER
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexRelationEntity
import com.lume.app.data.db.LumeDatabase
import com.lume.app.domain.Chapter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** AI story notes into the codex, facts and relations, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class StoryNotesTest {
    private lateinit var db: LumeDatabase
    private val dao get() = db.libraryDao()
    private val notes get() = StoryNotes(dao, "s", "t")
    private val order = mapOf("c1" to 0, "c2" to 1, "c3" to 2)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LumeDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun namesBecomeAiEntriesWithFactsAndADigest() = runBlocking {
        notes.save(
            "c2", "ru", "m",
            ChapterNotes(
                summary = "Глава вторая.",
                names = listOf(
                    NoteName("Yu San A-san", listOf("Yu San A's"), "Ю Сан А", "character", "Приходит в секту."),
                    NoteName("Yu San A", emptyList(), null, "character", "Получает меч."),
                    NoteName("the sword", fact = "Not a name."),
                ),
            ),
            order,
        )
        val entry = dao.codexOnce("s", "t").single()
        assertEquals("Yu San A", entry.entry.name)
        assertEquals(CODEX_AI, entry.entry.origin)
        assertEquals("c2", entry.entry.firstChapterId)
        assertEquals(setOf("Yu San A-san", "Yu San A's", "Ю Сан А"), entry.aliases.map { it.alias }.toSet())
        val fact = dao.factsFor("s", "t", listOf("yu san a")).single()
        assertEquals("Приходит в секту. Получает меч.", fact.text)
        assertEquals("Глава вторая.", dao.digestsAnyLang("s", "t", listOf("c2")).single().text)

        // Seen in an earlier chapter: the entry is visible from there on.
        notes.save("c1", "ru", "m", ChapterNotes(names = listOf(NoteName("Yu San A"))), order, digest = false)
        assertEquals("c1", dao.codexOnce("s", "t").single().entry.firstChapterId)
        assertTrue(dao.digestsAnyLang("s", "t", listOf("c1")).isEmpty())
    }

    @Test
    fun theReadersEntriesGetNewSpellingsAndRemovedOnesStayAway() = runBlocking {
        dao.saveCodexEntry(CodexEntryEntity(sourceId = "s", titleId = "t", name = "Lin Feng", kind = "character", origin = CODEX_USER), listOf("Лин Фэн"))
        dao.saveCodexEntry(CodexEntryEntity(sourceId = "s", titleId = "t", name = "Klein", kind = "character", origin = CODEX_IGNORED), emptyList())
        notes.save(
            "c1", "ru", "m",
            ChapterNotes(names = listOf(NoteName("Lin Feng", listOf("Lin-sama")), NoteName("Klein", fact = "Шутит."))),
            order,
        )
        val all = dao.codexWithIgnored("s", "t").associateBy { it.entry.name }
        assertEquals(CODEX_USER, all.getValue("Lin Feng").entry.origin)
        assertEquals(setOf("Лин Фэн", "Lin-sama"), all.getValue("Lin Feng").aliases.map { it.alias }.toSet())
        assertEquals(CODEX_IGNORED, all.getValue("Klein").entry.origin)
        assertTrue(dao.factsFor("s", "t", listOf("klein")).isEmpty())
        // The codex list leaves the removed entry out.
        assertEquals(listOf("Lin Feng"), dao.codexOnce("s", "t").map { it.entry.name })
    }

    @Test
    fun namesNotInTheTextAreDropped() = runBlocking {
        notes.save("c1", "ru", "m", ChapterNotes(names = listOf(NoteName("Asuna"), NoteName("Ghost"))), order, original = "Asuna smiled.")
        assertEquals(listOf("Asuna"), dao.codexOnce("s", "t").map { it.entry.name })
    }

    @Test
    fun relationsAreAddedOncePerChapterTheyFirstShowIn() = runBlocking {
        val r = listOf(NoteRelation("Lin Feng", "Iron Sect", "член"), NoteRelation("Lin Feng", "Lin Feng's", "self"))
        notes.save("c1", "ru", "m", ChapterNotes(names = listOf(NoteName("Lin Feng"), NoteName("Iron Sect")), relations = r), order)
        notes.save("c2", "ru", "m", ChapterNotes(relations = listOf(NoteRelation("Lin Feng", "Iron Sect", "Член"))), order)
        val saved = dao.titleRelations("s", "t").single()
        assertEquals("c1", saved.upToChapterId)
        assertTrue(saved.origin.startsWith(StoryNotes.NOTES_ORIGIN))
        // A summary's own relations are replaced; those from notes stay.
        dao.replaceRelationsFrom("s", "t", "Lin Feng", emptyList())
        assertEquals(1, dao.titleRelations("s", "t").size)
    }

    @Test
    fun removingAnAiEntryKeepsItIgnored() = runBlocking {
        notes.save("c1", "ru", "m", ChapterNotes(names = listOf(NoteName("Asuna"))), order)
        removeCodexEntry(dao, dao.codexOnce("s", "t").single().entry)
        assertTrue(dao.codexOnce("s", "t").isEmpty())
        notes.save("c2", "ru", "m", ChapterNotes(names = listOf(NoteName("Asuna"))), order)
        assertTrue(dao.codexOnce("s", "t").isEmpty())
        // Saved by the reader, it is theirs again.
        val ignored = dao.codexWithIgnored("s", "t").single().entry
        saveCodexEntry(dao, "s", "t", ignored, "Asuna", "character", "", emptyList())
        assertEquals(CODEX_USER, dao.codexOnce("s", "t").single().entry.origin)
    }

    @Test
    fun spoilerRules() {
        val chapters = listOf(chapter("c1", 1.0), chapter("c2", 2.0), chapter("c3", 3.0))
        val ai = CodexEntryEntity(sourceId = "s", titleId = "t", name = "A", origin = CODEX_AI, firstChapterId = "c2")
        assertFalse(entryVisible(ai, chapters, "c1"))
        assertTrue(entryVisible(ai, chapters, "c2"))
        assertTrue(entryVisible(ai.copy(origin = CODEX_USER), chapters, "c1"))
        val fromFacts = ai.copy(summary = "x", summaryOrigin = "facts:m", summaryUpToChapterId = "c2")
        assertFalse(summaryVisible(fromFacts, chapters, "c2"))
        assertTrue(summaryVisible(fromFacts, chapters, "c3"))
        assertTrue(summaryVisible(fromFacts.copy(summaryOrigin = "llm:m"), chapters, "c2"))
        val noted = CodexRelationEntity(sourceId = "s", titleId = "t", fromName = "A", toName = "B", label = "x", upToChapterId = "c2", origin = "notes:m", createdAt = 0)
        assertFalse(relationVisible(noted, chapters, "c2"))
        assertTrue(relationVisible(noted, chapters, "c3"))
    }

    @Test
    fun factsBeforeTheSpotOnlyInReadingOrder() {
        val f = listOf("c3" to "three", "c1" to "one", "c2" to "two").map { (c, t) ->
            com.lume.app.data.db.CodexFactEntity("s", "t", "k", c, "ru", t, "m", 0)
        }
        assertEquals(listOf("one", "two"), factsBefore(f, order, "c3").map { it.text })
        assertEquals(emptyList<String>(), factsBefore(f, order, "c1").map { it.text })
        assertNull(factsBefore(f, order, "unknown").firstOrNull())
    }

    private fun chapter(id: String, number: Double) = Chapter(id = id, title = id, url = "/$id", number = number.toString())
}
