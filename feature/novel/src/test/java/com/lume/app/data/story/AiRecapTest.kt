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
    fun digestBatchesStayUnderTheLimit() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), digestBatches(listOf(15_000, 20_000, 30_000, 10_000, 9_000), 40_000))
        // A chapter longer than the limit goes alone.
        assertEquals(listOf(listOf(0), listOf(1)), digestBatches(listOf(50_000, 100), 40_000))
        assertEquals(emptyList<List<Int>>(), digestBatches(emptyList(), 40_000))
    }

    @Test
    fun digestsAreReadByChapter() {
        val answer = "```json\n{\"chapters\":[{\"id\":\"1\",\"digest\":\"Первая.\"},{\"id\":2,\"digest\":\"Вторая.\"},{\"id\":\"3\"}]}\n```"
        assertEquals(mapOf("1" to "Первая.", "2" to "Вторая."), parseDigests(answer))
        val p = Json.parseToJsonElement(digestUserPrompt(listOf("1" to "Text one", "2" to "Text two"))).jsonObject
        assertEquals(listOf("1", "2"), p["chapters"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun aSummaryGetsTheDigestsThatNameTheEntry() {
        val all = (1..20).map { "Ch $it" to if (it % 5 == 0) "Lin Feng fights." else "Others talk." }
        val picked = relevantDigests(all, listOf("Lin Feng", "Лин Фэн"))
        // Chapters 5, 10 and 15 name him; the last four are kept for where the story is now.
        assertEquals(listOf("Ch 5", "Ch 10", "Ch 15", "Ch 17", "Ch 18", "Ch 19", "Ch 20"), picked.map { it.first })
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
