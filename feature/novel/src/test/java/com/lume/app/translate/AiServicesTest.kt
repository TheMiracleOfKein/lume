package com.lume.app.translate

import com.lume.app.llm.CheckStatus
import com.lume.app.llm.LlmPreset
import com.lume.app.llm.ServiceCheck
import com.lume.app.llm.ServiceHttpException
import com.lume.app.llm.failedCheck
import com.lume.app.llm.llmError
import com.lume.app.llm.parseOpenRouterKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AiServicesTest {
    @Test
    fun autoModelPrefersTheListedFavourite() {
        assertEquals("openrouter/auto", LlmPreset.OpenRouter.autoModel(null))
        assertEquals("gpt-5-mini", LlmPreset.OpenAI.autoModel(null))
        assertEquals("gpt-4.1-mini", LlmPreset.OpenAI.autoModel(listOf("gpt-4o", "gpt-4.1-mini", "gpt-4o-mini")))
        // Gemini lists ids with a "models/" prefix.
        assertEquals("gemini-2.0-flash", LlmPreset.Gemini.autoModel(listOf("models/gemini-2.0-flash", "models/gemini-pro")))
    }

    @Test
    fun autoModelFallsBackToACloseMatch() {
        assertEquals(
            "gpt-6-mini",
            LlmPreset.OpenAI.autoModel(listOf("gpt-6", "gpt-6-mini", "gpt-6-mini-audio", "gpt-realtime-mini")),
        )
        assertEquals("gemini-3.0-flash", LlmPreset.Gemini.autoModel(listOf("gemini-3.0-pro", "gemini-3.0-flash", "gemini-3.0-flash-image")))
        assertEquals("llama3", LlmPreset.Custom.autoModel(listOf("nomic-embed-text", "llama3")))
        assertNull(LlmPreset.Custom.autoModel(null))
    }

    @Test
    fun checkSurvivesStorage() {
        val c = ServiceCheck(CheckStatus.Ok, 1234L, modelCount = 300, autoModel = "openrouter/auto")
        assertEquals(c, ServiceCheck.decode(c.encode()))
        val q = ServiceCheck(CheckStatus.NoCredits, 5L, used = 500_000, limit = 500_000)
        assertEquals(q, ServiceCheck.decode(q.encode()))
        assertNull(ServiceCheck.decode("not json"))
        assertNull(ServiceCheck.decode("""{"s":"unknown","at":1}"""))
        assertNull(ServiceCheck.decode(null))
    }

    @Test
    fun failuresAreSortedByCause() {
        assertEquals(CheckStatus.Refused, failedCheck(llmError(401, "{}"), 0).status)
        assertEquals(CheckStatus.NoCredits, failedCheck(llmError(402, "{}"), 0).status)
        assertEquals(CheckStatus.NoCredits, failedCheck(deeplError(456), 0).status)
        assertEquals(CheckStatus.Failed, failedCheck(llmError(500, "{}"), 0).status)
        assertEquals(CheckStatus.Unreachable, failedCheck(IOException("Unable to resolve host"), 0).status)
        assertTrue(failedCheck(llmError(401, "{}"), 0).let { !it.works })
        assertTrue(ServiceCheck(CheckStatus.ModelMissing, 0).works)
    }

    @Test
    fun settingsCanFixKeyAndModelErrors() {
        assertTrue(llmError(401, "{}").needsSettings)
        assertTrue(llmError(404, "{}").needsSettings)
        assertFalse(llmError(429, "{}").needsSettings)
        assertFalse(ServiceHttpException(500, "x").needsSettings)
        assertTrue(deeplError(403).message!!.contains("Settings"))
    }

    @Test
    fun parsesOpenRouterKeyAndDeepLUsage() {
        assertEquals(1.5, parseOpenRouterKey("""{"data":{"label":"x","limit":10,"limit_remaining":1.5}}""")!!, 0.0)
        assertNull(parseOpenRouterKey("""{"data":{"label":"x","limit":null,"limit_remaining":null}}"""))
        assertEquals(DeepLUsage(1200, 500000), parseDeepLUsage("""{"character_count":1200,"character_limit":500000}"""))
    }

    @Test
    fun translationResumesOnlyOnAShortReturnToTheSameTitle() {
        TranslationSession.leave("s", "t", "ru", now = 1_000)
        assertNull(TranslationSession.resume("s", "other", now = 2_000))
        // Another title doesn't use up the session.
        assertEquals("ru", TranslationSession.resume("s", "t", now = 2_000))
        // Used once.
        assertNull(TranslationSession.resume("s", "t", now = 3_000))

        TranslationSession.leave("s", "t", "ru", now = 0)
        assertNull(TranslationSession.resume("s", "t", now = TranslationSession.RESUME_MS + 1))

        TranslationSession.leave("s", "t", null, now = 0)
        assertNull(TranslationSession.resume("s", "t", now = 1))
    }
}
