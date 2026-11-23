package com.lume.app.translate

import com.lume.app.llm.LlmConfig
import com.lume.app.llm.LlmPreset
import com.lume.app.llm.chatBody
import com.lume.app.llm.extractJsonObject
import com.lume.app.llm.llmError
import com.lume.app.llm.parseChat
import com.lume.app.llm.parseModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LlmTranslationTest {
    @Test
    fun configNeedsKeyAndModel() {
        assertNull(LlmConfig.from("openrouter", null, null, emptyMap()))
        val c = LlmConfig.from("openrouter", null, null, mapOf("openrouter" to "k"))!!
        assertEquals("https://openrouter.ai/api/v1", c.baseUrl)
        assertEquals("openrouter/auto", c.model)
        // Without a picked model, a service uses its first automatic preference.
        assertEquals("gpt-5-mini", LlmConfig.from("openai", null, null, mapOf("openai" to "k"))!!.model)
        // A custom server has no automatic model before a check.
        assertNull(LlmConfig.from("custom", "http://10.0.0.1/v1", null, emptyMap()))
        assertEquals("gpt-x", LlmConfig.from("openai", null, " gpt-x ", mapOf("openai" to "k"))!!.model)
    }

    @Test
    fun customServerNeedsAnAddressButNoKey() {
        assertNull(LlmConfig.from("custom", null, "llama3", emptyMap()))
        assertNull(LlmConfig.from("custom", "localhost:11434", "llama3", emptyMap()))
        val c = LlmConfig.from("custom", "http://192.168.1.5:11434/v1/", "llama3", emptyMap())!!
        assertEquals("http://192.168.1.5:11434/v1", c.baseUrl)
        assertNull(c.key)
        assertEquals(LlmPreset.Custom, c.preset)
    }

    @Test
    fun unknownPresetFallsBackToOpenRouter() {
        assertEquals(LlmPreset.OpenRouter, LlmPreset.fromId("gone"))
        assertEquals(LlmPreset.OpenRouter, LlmPreset.fromId(null))
    }

    @Test
    fun chatBodyAsksForJson() {
        val body = Json.parseToJsonElement(chatBody("m", "sys", "usr", json = true, temperature = 0.3)).jsonObject
        assertEquals("m", body["model"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("usr", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val plain = Json.parseToJsonElement(chatBody("m", "s", "u", json = false, temperature = 0.3)).jsonObject
        assertFalse(plain.containsKey("response_format"))
    }

    @Test
    fun readsChatAnswers() {
        val a = parseChat("""{"choices":[{"message":{"role":"assistant","content":"{\"paragraphs\":[]}"},"finish_reason":"stop"}]}""")
        assertEquals("{\"paragraphs\":[]}", a.content)
        assertFalse(a.truncated)
        assertTrue(parseChat("""{"choices":[{"message":{"content":"{\"para"},"finish_reason":"length"}]}""").truncated)
    }

    @Test(expected = IOException::class)
    fun anErrorInsideA200IsAnError() {
        parseChat("""{"error":{"message":"Provider returned error","code":502}}""")
    }

    @Test
    fun errorsSayWhatToDo() {
        assertTrue(llmError(401, "{}").message!!.contains("key"))
        assertTrue(llmError(402, """{"error":{"message":"Insufficient credits"}}""").message!!.contains("Insufficient credits"))
        assertTrue(llmError(429, "").message!!.contains("limiting"))
        assertEquals("AI service: HTTP 500", llmError(500, "<html>").message)
    }

    @Test
    fun readsModelsWithOpenRouterPrices() {
        val models = parseModels(
            """{"data":[
                {"id":"b/model","name":"B","pricing":{"prompt":"0.0000003","completion":"0.0000025"}},
                {"id":"a/free","name":"A","pricing":{"prompt":"0","completion":"0"}},
                {"id":"c/plain"}
            ]}""",
        )
        assertEquals(listOf("a/free", "b/model", "c/plain"), models.map { it.id })
        assertEquals(0.3, models[1].promptPrice!!, 1e-9)
        assertEquals(2.5, models[1].completionPrice!!, 1e-9)
        assertEquals(0.0, models[0].promptPrice!!, 0.0)
        assertNull(models[2].promptPrice)
    }

    @Test
    fun findsJsonInFencesAndChatter() {
        assertEquals("1", extractJsonObject("{\"a\":1}")["a"]!!.jsonPrimitive.content)
        assertEquals("2", extractJsonObject("```json\n{\"a\":2}\n```")["a"]!!.jsonPrimitive.content)
        assertEquals("3", extractJsonObject("Here you go: {\"a\":3} Hope it helps")["a"]!!.jsonPrimitive.content)
    }

    @Test(expected = IOException::class)
    fun noJsonIsAnError() {
        extractJsonObject("Sorry, I can't help with that.")
    }

    @Test
    fun parsesParagraphsAndTerms() {
        val t = parseLlmTranslation(
            """{"paragraphs":[{"id":1,"html":"<b>Б</b>"},{"id":"0","html":"<span data-g=\"0\">Кирито</span> пришёл."}],
               "terms":[{"source":"Asuna","target":"Асуна","kind":"character"},{"source":"x"}]}""",
        )
        assertEquals(mapOf(0 to "<span data-g=\"0\">Кирито</span> пришёл.", 1 to "<b>Б</b>"), t.paragraphs)
        assertEquals(listOf(SuggestedTerm("Asuna", "Асуна", "character")), t.terms)
    }

    @Test
    fun userPromptCarriesGlossaryAndPrevious() {
        val p = Json.parseToJsonElement(
            userPrompt(listOf(3 to "Hi <span data-g=\"0\">Kirito</span>"), listOf(SuggestedTerm("Kirito", "Кирито", "character")), listOf("Раньше.")),
        ).jsonObject
        val g = p["glossary"]!!.jsonArray.single().jsonObject
        assertEquals("0", g["id"]!!.jsonPrimitive.content)
        assertEquals("Кирито", g["target"]!!.jsonPrimitive.content)
        assertEquals("Раньше.", p["previous_translated"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("3", p["paragraphs"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun systemPromptNamesLanguagesAndStyle() {
        val s = systemPrompt("en", "ru", "keep -san")
        assertTrue(s.contains("from English into Russian"))
        assertTrue(s.contains("keep -san"))
        assertFalse(systemPrompt("en", "ru", null).contains("Style notes"))
    }

    @Test
    fun keepsOnlyNewNamesFromTheText() {
        val original = "Asuna met Klein at the Town of Beginnings. The sword shone. Kirito laughed."
        val found = listOf(
            SuggestedTerm(" Asuna ", "Асуна", "Character"), // kept, trimmed, kind normalised
            SuggestedTerm("asuna", "Асуна", "character"), // duplicate
            SuggestedTerm("Klein", "Кляйн", "wizard"), // kept, unknown kind dropped
            SuggestedTerm("sword", "меч", "item"), // not a name
            SuggestedTerm("Kirito", "Кирито", "character"), // already in the glossary
            SuggestedTerm("Aincrad", "Айнкрад", "place"), // not in these paragraphs
            SuggestedTerm("Town", "Город", "place"), // a word inside the text is fine
            SuggestedTerm("Beginnings", "", "place"), // empty translation
            SuggestedTerm("Asu", "Асу", "character"), // only part of a word
        )
        val kept = filterSuggestions(found, original, setOf("kirito"))
        assertEquals(listOf(SuggestedTerm("Asuna", "Асуна", "character"), SuggestedTerm("Klein", "Кляйн", null), SuggestedTerm("Town", "Город", "place")), kept)
    }

    @Test
    fun cjkNamesNeedNoWordBreaks() {
        val kept = filterSuggestions(listOf(SuggestedTerm("林风", "Лин Фэн", "character")), "林风走进了大厅。", emptySet())
        assertEquals(1, kept.size)
    }

    @Test
    fun cachedMarksSurviveGlossaryReordering() {
        val before = listOf(GlossaryEntry("Kirito", "Кирито"), GlossaryEntry("Asuna", "Асуна"))
        val html = "<span data-g=\"1\">Асуна</span> и <b><span data-g=\"0\">Кирито</span></b>"
        val stored = TranslationMarkup.marksToSources(html, before)
        assertFalse(stored.contains("data-g=\""))
        // A new term sorted first shifts every number.
        val after = listOf(GlossaryEntry("Agil", "Эгиль"), GlossaryEntry("Asuna", "Асуна Юки"), GlossaryEntry("Kirito", "Кирито"))
        val restored = TranslationMarkup.sourcesToMarks(stored, after)
        assertEquals("Асуна Юки и Кирито", TranslationMarkup.spans(restored, after).joinToString("") { it.text })
        // A removed term keeps the words the engine wrote.
        val without = listOf(GlossaryEntry("Kirito", "Кирито"))
        assertEquals("Асуна и Кирито", TranslationMarkup.spans(TranslationMarkup.sourcesToMarks(stored, without), without).joinToString("") { it.text })
    }
}
