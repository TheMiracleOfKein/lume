package com.lume.app.translate

import com.lume.app.llm.AiFeature
import com.lume.app.llm.ChatOptions
import com.lume.app.llm.LlmClient
import com.lume.app.llm.LlmConfig
import com.lume.app.llm.LlmPreset
import com.lume.app.llm.LlmUsage
import com.lume.app.llm.chatBody
import com.lume.app.llm.extractJsonObject
import com.lume.app.llm.llmError
import com.lume.app.llm.parseChat
import com.lume.app.llm.parseModels
import com.lume.app.llm.refusedParams
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        assertEquals("deepseek/deepseek-v4-flash", c.model)
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

    private fun body(preset: LlmPreset, model: String, json: Boolean = true, maxTokens: Int? = 900, dropped: Set<String> = emptySet()) =
        Json.parseToJsonElement(chatBody(preset, model, "sys", "usr", ChatOptions(json, 0.3, maxTokens), dropped)).jsonObject

    @Test
    fun chatBodyAsksForJson() {
        val body = body(LlmPreset.DeepSeek, "m")
        assertEquals("m", body["model"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("usr", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("900", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("0.3", body["temperature"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("reasoning_effort"))
        val plain = body(LlmPreset.DeepSeek, "m", json = false, maxTokens = null)
        assertFalse(plain.containsKey("response_format"))
        assertFalse(plain.containsKey("max_tokens"))
    }

    @Test
    fun chatBodyFitsEachService() {
        // OpenAI's reasoning models: no temperature, the cap under its new name, the least thinking.
        val gpt5 = body(LlmPreset.OpenAI, "gpt-5-mini")
        assertFalse(gpt5.containsKey("temperature"))
        assertFalse(gpt5.containsKey("max_tokens"))
        assertEquals("900", gpt5["max_completion_tokens"]!!.jsonPrimitive.content)
        assertEquals("minimal", gpt5["reasoning_effort"]!!.jsonPrimitive.content)
        val mini = body(LlmPreset.OpenAI, "gpt-4.1-mini")
        assertTrue(mini.containsKey("temperature"))
        assertFalse(mini.containsKey("reasoning_effort"))
        assertEquals("none", body(LlmPreset.Gemini, "gemini-2.5-flash")["reasoning_effort"]!!.jsonPrimitive.content)
        assertFalse(body(LlmPreset.Gemini, "gemini-1.5-flash").containsKey("reasoning_effort"))
        val or = body(LlmPreset.OpenRouter, "deepseek/deepseek-v4-flash")
        assertEquals("low", or["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("true", or["usage"]!!.jsonObject["include"]!!.jsonPrimitive.content)
        // What a service refused once is left out.
        val without = body(LlmPreset.OpenRouter, "x", dropped = setOf("reasoning", "response_format"))
        assertFalse(without.containsKey("reasoning"))
        assertFalse(without.containsKey("response_format"))
        assertTrue(without.containsKey("usage"))
    }

    @Test
    fun refusedParametersAreFoundInTheError() {
        val sent = chatBody(LlmPreset.OpenAI, "gpt-5-mini", "s", "u", ChatOptions(true, 0.3, 100))
        assertEquals(
            setOf("reasoning_effort"),
            refusedParams(sent, """{"error":{"message":"Unsupported value: 'reasoning_effort' does not support 'minimal' with this model."}}"""),
        )
        // A parameter that wasn't sent isn't counted, even when the error names it.
        val plain = chatBody(LlmPreset.DeepSeek, "m", "s", "u", ChatOptions(false, 0.3, 100))
        assertEquals(
            setOf("max_tokens"),
            refusedParams(plain, "Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead."),
        )
        assertEquals(emptySet<String>(), refusedParams(plain, "messages: too long"))
    }

    @Test
    fun clientDropsRefusedParametersAndRetriesABusyService() = runBlocking {
        val bodies = mutableListOf<String>()
        val answers = ArrayDeque(
            listOf(
                400 to """{"error":{"message":"Unsupported parameter: 'max_tokens'"}}""",
                429 to """{"error":{"message":"slow down"}}""",
                200 to """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3}}""",
            ),
        )
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            bodies += buffer.readUtf8()
            val (code, text) = answers.removeFirst()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .header("Retry-After", "0")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val used = mutableListOf<Pair<AiFeature, LlmUsage>>()
        val client = LlmClient(http, LlmConfig(LlmPreset.Custom, "http://local/v1", "refuse-test-model", null)) { f, u -> used += f to u }
        val answer = client.chat("s", "u", json = false, maxTokens = 50, feature = AiFeature.Recap)
        assertEquals("ok", answer.content)
        assertEquals(3, bodies.size)
        assertTrue(bodies[0].contains("max_tokens"))
        assertFalse(bodies[1].contains("max_tokens"))
        assertEquals(listOf(AiFeature.Recap to LlmUsage(12, 3, null)), used)
    }

    @Test
    fun readsChatAnswers() {
        val a = parseChat("""{"choices":[{"message":{"role":"assistant","content":"{\"paragraphs\":[]}"},"finish_reason":"stop"}]}""")
        assertEquals("{\"paragraphs\":[]}", a.content)
        assertFalse(a.truncated)
        assertNull(a.usage)
        assertTrue(parseChat("""{"choices":[{"message":{"content":"{\"para"},"finish_reason":"length"}]}""").truncated)
        val withUsage = parseChat("""{"choices":[{"message":{"content":"x"}}],"usage":{"prompt_tokens":100,"completion_tokens":20,"cost":0.00042}}""")
        assertEquals(LlmUsage(100, 20, 0.00042), withUsage.usage)
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
            userPrompt(listOf(3 to "Hi <span data-g=\"7\">Kirito</span>"), mapOf(7 to SuggestedTerm("Kirito", "Кирито", "character")), listOf("Раньше.")),
        ).jsonObject
        val g = p["glossary"]!!.jsonObject["7"]!!.jsonArray
        assertEquals(listOf("Kirito", "Кирито"), g.map { it.jsonPrimitive.content })
        assertEquals("Раньше.", p["previous_translated"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("3", p["paragraphs"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        assertFalse(Json.parseToJsonElement(userPrompt(listOf(0 to "x"), emptyMap(), emptyList())).jsonObject.containsKey("glossary"))
    }

    @Test
    fun eachRequestSendsOnlyTheGlossaryItsTextNeeds() = runBlocking {
        val sent = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            val user = Json.parseToJsonElement(buffer.readUtf8()).jsonObject["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
            sent += user
            val ids = Json.parseToJsonElement(user).jsonObject["paragraphs"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
            // The first answer also names a new character, used from then on.
            val terms = if (sent.size == 1) ""","terms":[{"source":"Klein-san","target":"Кляйн-сан","kind":"character"}]""" else ""
            val content = """{"paragraphs":[${ids.joinToString(",") { """{"id":$it,"html":"т"}""" }}]$terms}"""
            val reply = buildJsonObject {
                putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
            }.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val glossary = listOf("Asuna" to "Асуна", "Kirito" to "Кирито", "Agil" to "Эгиль").map { (s, t) ->
            com.lume.app.data.db.GlossaryTermEntity("src", "t", s, t)
        }
        val saved = mutableListOf<SuggestedTerm>()
        val provider = LlmProvider(LlmClient(http, LlmConfig(LlmPreset.Custom, "http://local/v1", "m", null)), glossary, emptyList(), null) { saved += it }
        provider.translate("en", "ru", listOf("<span data-g=\"1\">Kirito</span> met Klein-san."))
        provider.translate("en", "ru", listOf("Klein waved at <span data-g=\"2\">Agil</span>."))
        val first = Json.parseToJsonElement(sent[0]).jsonObject["glossary"]!!.jsonObject
        assertEquals(setOf("1"), first.keys)
        // The second request has the marked term and the name found in the first, not the rest.
        val second = Json.parseToJsonElement(sent[1]).jsonObject["glossary"]!!.jsonObject
        assertEquals(setOf("2", "3"), second.keys)
        assertEquals(listOf("Klein", "Кляйн"), second["3"]!!.jsonArray.map { it.jsonPrimitive.content })
        // "Klein-san" is kept as the base name.
        assertEquals(listOf(SuggestedTerm("Klein", "Кляйн", "character")), saved)
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
    fun notesComeWithTheTranslationInChapterOrder() = runBlocking {
        var call = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            val system = Json.parseToJsonElement(buffer.readUtf8()).jsonObject["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(system.contains("\"notes\""))
            call++
            val content = if (call == 1) {
                """{"paragraphs":[{"id":0,"html":"Позже."}],"notes":{"summary":"Second part.","names":[{"name":"Kirito","fact":"Wins."}]}}"""
            } else {
                """{"paragraphs":[{"id":0,"html":"Сначала."}],"notes":{"summary":"First part.","names":[{"name":"Nobody","fact":"Not in the text."}]}}"""
            }
            val reply = buildJsonObject {
                putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
            }.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val glossary = listOf(com.lume.app.data.db.GlossaryTermEntity("src", "t", "Kirito", "Кирито"))
        val provider = LlmProvider(LlmClient(http, LlmConfig(LlmPreset.Custom, "http://local/v1", "m", null)), glossary, emptyList(), null, notes = true)
        // The part further on is translated first (the reader was there), then the start.
        provider.position = 5
        provider.translate("en", "ru", listOf("Kirito wins."))
        provider.position = 0
        provider.translate("en", "ru", listOf("It begins."))
        val (notes, covered) = provider.takeNotes()!!
        assertEquals("First part. Second part.", notes.summary)
        assertEquals(listOf(com.lume.app.data.story.NoteName("Kirito", target = "Кирито", fact = "Wins.")), notes.names)
        assertEquals(2, covered)
        assertNull(provider.takeNotes())
        assertFalse(systemPrompt("en", "ru", null).contains("\"notes\""))
    }

    @Test
    fun formsOfOneNameBecomeOneTerm() {
        val original = "Yu San A-san bowed. \"Yu San A,\" said Lin. Yu San A's sword fell. Elder Mo-sama nodded."
        val found = listOf(
            SuggestedTerm("Yu San A", "Ю Сан А", "character"),
            SuggestedTerm("Yu San A-san", "Ю Сан А-сан", "character"),
            SuggestedTerm("Yu San A's", "Ю Сан А", "character"),
            SuggestedTerm("Mo-sama", "Мо-сама", "character"), // only the honorific form: kept as its base
            SuggestedTerm("Lin-san", "Лин-сан", "character"), // the base is known
        )
        val kept = filterSuggestions(found, original, setOf("lin"))
        assertEquals(listOf(SuggestedTerm("Yu San A", "Ю Сан А", "character"), SuggestedTerm("Mo", "Мо", "character")), kept)
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
