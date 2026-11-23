package com.lume.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUsageTest {
    @Test
    fun totalsAddUpPerMonthAndFeature() {
        var text: String? = null
        text = AiUsage.add(text, "2026-11", "translation", LlmUsage(1_000, 400, 0.002))
        text = AiUsage.add(text, "2026-11", "translation", LlmUsage(500, 100, null))
        text = AiUsage.add(text, "2026-11", "recap", LlmUsage(200, 50, 0.0001))
        val month = AiUsage.month(text, "2026-11")
        val t = month.getValue("translation")
        assertEquals(UsageTotals(2, 1_500, 500, 0.002, 1), t)
        assertFalse(t.costComplete)
        assertTrue(month.getValue("recap").costComplete)
        assertEquals(emptyMap<String, UsageTotals>(), AiUsage.month(text, "2026-10"))
    }

    @Test
    fun onlyTheLastMonthsAreKept() {
        var text: String? = null
        for (m in listOf("2026-07", "2026-08", "2026-09", "2026-10", "2026-11")) text = AiUsage.add(text, m, "recap", LlmUsage(1, 1, null))
        assertEquals(setOf("2026-09", "2026-10", "2026-11"), AiUsage.decode(text).keys)
    }

    @Test
    fun brokenTextReadsAsEmpty() {
        assertEquals(emptyMap<String, Map<String, UsageTotals>>(), AiUsage.decode("not json"))
        assertEquals(emptyMap<String, Map<String, UsageTotals>>(), AiUsage.decode(null))
    }
}
