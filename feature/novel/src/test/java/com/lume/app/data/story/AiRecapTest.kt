package com.lume.app.data.story

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecapTest {
    @Test
    fun shortChapterIsSentWhole() {
        assertEquals("One.\nTwo.", digestInput(listOf(" One. ", "", "Two.")))
    }

    @Test
    fun longChapterKeepsStartAndEnd() {
        val paragraphs = (1..100).map { "Paragraph $it with some words in it." }
        val input = digestInput(paragraphs, maxChars = 600)
        assertTrue(input.startsWith("Paragraph 1 "))
        assertTrue(input.endsWith("Paragraph 100 with some words in it."))
        assertTrue(input.contains("\n…\n"))
        assertTrue(input.length <= 600 + 3)
    }

    @Test
    fun currentTailStartsAtAParagraph() {
        val paragraphs = (1..50).map { "Line number $it of the chapter." }
        val tail = currentTail(paragraphs, maxChars = 200)
        assertTrue(tail, tail.startsWith("Line number "))
        assertTrue(tail.endsWith("Line number 50 of the chapter."))
        assertTrue(tail.length <= 200)
        assertEquals("A\nB", currentTail(listOf("A", "B")))
    }

    @Test
    fun promptsNameTheLanguage() {
        assertTrue(digestSystemPrompt("ru").contains("Russian"))
        assertTrue(recapSystemPrompt("en").contains("English"))
        assertTrue(recapSystemPrompt("en").contains("never guess"))
    }

    @Test
    fun recapPromptListsDigestsInOrder() {
        val p = Json.parseToJsonElement(recapUserPrompt(listOf("Ch 1" to "A", "Ch 2" to "B"), "Ch 3", "So far.")).jsonObject
        val earlier = p["earlier_chapters"]!!.jsonArray
        assertEquals(listOf("Ch 1", "Ch 2"), earlier.map { it.jsonObject["chapter"]!!.jsonPrimitive.content })
        assertEquals("Ch 3", p["current_chapter"]!!.jsonPrimitive.content)
        assertEquals("So far.", p["read_so_far_in_current_chapter"]!!.jsonPrimitive.content)
    }
}
