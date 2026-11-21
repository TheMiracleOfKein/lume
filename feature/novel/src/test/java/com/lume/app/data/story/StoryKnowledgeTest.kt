package com.lume.app.data.story

import com.lume.app.data.db.CodexAliasEntity
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.domain.Chapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StoryKnowledgeTest {
    // Newest first, as sources list them.
    private val chapters = (5 downTo 1).map { Chapter("c$it", "Chapter $it", "/c$it", number = "$it") }

    private val kirito = CodexEntryWithAliases(
        CodexEntryEntity(id = 1, sourceId = "s", titleId = "t", name = "Kirito", kind = "character", note = "The Black Swordsman"),
        listOf(CodexAliasEntity(1, "Kirigaya")),
    )

    @Test
    fun summaryShowsOnlyAtOrAfterItsChapter() {
        assertTrue(visibleAt("c3", chapters, "c3"))
        assertTrue(visibleAt("c3", chapters, "c5"))
        assertFalse(visibleAt("c3", chapters, "c2"))
        // Unknown chapters count as further on.
        assertFalse(visibleAt("gone", chapters, "c5"))
        assertFalse(visibleAt(null, chapters, "c5"))
    }

    @Test
    fun parsesSummaryAndRelations() {
        val k = parseKnowledge(
            """{"summary":" A swordsman trapped in the game. ","relations":[
                {"name":"Asuna","relation":"partner"},
                {"name":"Kirigaya","relation":"himself"},
                {"name":"asuna","relation":"duplicate"},
                {"name":"Klein","label":"friend"},
                {"name":"","relation":"empty"}
            ]}""",
            kirito,
        )
        assertEquals("A swordsman trapped in the game.", k.summary)
        assertEquals(listOf("Asuna" to "partner", "Klein" to "friend"), k.relations)
    }

    @Test(expected = IOException::class)
    fun anAnswerWithoutSummaryIsAnError() {
        parseKnowledge("""{"relations":[]}""", kirito)
    }

    @Test
    fun relationsAreCapped() {
        val many = (1..20).joinToString(",") { """{"name":"N$it","relation":"r"}""" }
        assertEquals(8, parseKnowledge("""{"summary":"x","relations":[$many]}""", kirito).relations.size)
    }

    @Test
    fun promptCarriesTheEntryAndWhatWasRead() {
        val p = Json.parseToJsonElement(
            knowledgeUserPrompt(kirito, listOf("Asuna"), listOf("Chapter 1" to "Kirito logs in."), listOf("Chapter 2" to "Kirito met Asuna.")),
        ).jsonObject
        assertEquals("Kirito", p["name"]!!.jsonPrimitive.content)
        assertEquals("Kirigaya", p["other_spellings"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("The Black Swordsman", p["reader_note"]!!.jsonPrimitive.content)
        assertEquals("Asuna", p["known_names"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("Kirito logs in.", p["chapter_digests"]!!.jsonArray.single().jsonObject["digest"]!!.jsonPrimitive.content)
        assertEquals("Kirito met Asuna.", p["passages"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(knowledgeSystemPrompt("ru").contains("Russian"))
    }
}
