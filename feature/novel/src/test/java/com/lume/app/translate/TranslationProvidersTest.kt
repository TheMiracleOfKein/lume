package com.lume.app.translate

import com.lume.app.data.db.GlossaryTermEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class TranslationProvidersTest {
    @Test
    fun readsGoogleAnswersKeepingMarks() {
        // As translate_a/t answered on 2026-11-21 for two paragraphs.
        val body = """["<span data-g=\"0\">Широ<\/span> почувствовал <b>ядро маны<\/b>.","Мастер рассмеялся."]"""
        assertEquals(listOf("<span data-g=\"0\">Широ</span> почувствовал <b>ядро маны</b>.", "Мастер рассмеялся."), parseGoogle(body))
    }

    @Test
    fun readsGoogleTextLanguagePairs() {
        assertEquals(listOf("Привет", "Мир"), parseGoogle("""[["Привет","en"],["Мир","en"]]"""))
    }

    @Test(expected = IOException::class)
    fun aGoogleErrorPageIsAnError() {
        parseGoogle("<html>Too many requests</html>")
    }

    @Test
    fun deeplPicksTheEndpointByKey() {
        assertEquals("https://api-free.deepl.com/v2/translate", deeplUrl("abc-123:fx"))
        assertEquals("https://api.deepl.com/v2/translate", deeplUrl("abc-123"))
    }

    @Test
    fun deeplRequestKeepsHtml() {
        val body = Json.parseToJsonElement(deeplBody("en", "ru", listOf("<b>Hi</b>", "There"))).jsonObject
        assertEquals("EN", body["source_lang"]!!.jsonPrimitive.content)
        assertEquals("RU", body["target_lang"]!!.jsonPrimitive.content)
        assertEquals("html", body["tag_handling"]!!.jsonPrimitive.content)
        assertEquals(2, body["text"]!!.jsonArray.size)
        val toEnglish = Json.parseToJsonElement(deeplBody("ru", "en", listOf("x"))).jsonObject
        assertEquals("EN-US", toEnglish["target_lang"]!!.jsonPrimitive.content)
    }

    @Test
    fun readsDeepLAnswers() {
        val body = """{"translations":[{"detected_source_language":"EN","text":"<b>Привет</b>"},{"detected_source_language":"EN","text":"Там"}]}"""
        assertEquals(listOf("<b>Привет</b>", "Там"), parseDeepL(body))
    }

    @Test
    fun parsesPastedGlossaryLines() {
        val text = """
            Shirou = Широ
            mana core → ядро маны
            Rin	Рин
            Saber -> Сэйбер
            no separator here
             = empty source
            Archer =
        """.trimIndent()
        assertEquals(
            listOf("Shirou" to "Широ", "mana core" to "ядро маны", "Rin" to "Рин", "Saber" to "Сэйбер"),
            parseGlossary(text),
        )
    }

    @Test
    fun copiedGlossaryReadsBack() {
        val terms = listOf(term("mana core", "ядро маны"), term("Archer", "Арчер"))
        val text = formatGlossary(terms)
        assertEquals("Archer = Арчер\nmana core = ядро маны", text)
        assertEquals(listOf("Archer" to "Арчер", "mana core" to "ядро маны"), parseGlossary(text))
    }

    @Test
    fun searchLooksAtBothSides() {
        val terms = listOf(term("Shirou", "Широ"), term("mana core", "ядро маны"))
        assertEquals(listOf("Shirou"), filterGlossary(terms, "шир").map { it.source })
        assertEquals(listOf("mana core"), filterGlossary(terms, "MANA").map { it.source })
        assertEquals(2, filterGlossary(terms, "  ").size)
    }

    private fun term(s: String, t: String) = GlossaryTermEntity("src", "title", s, t, 0L)
}
