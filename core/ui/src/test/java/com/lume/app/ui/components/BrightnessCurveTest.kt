package com.lume.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessCurveTest {
    @Test
    fun endsMapToEnds() {
        assertEquals(1f, BrightnessCurve.levelToWindow(1f), 0.001f)
        assertTrue(BrightnessCurve.levelToWindow(0.01f) < 0.001f)
        assertTrue(BrightnessCurve.levelToWindow(0f) > 0f)
    }

    @Test
    fun halfLevelIsDimInLinearTerms() {
        // The system slider's middle is about 1/12 of full light output.
        assertEquals(1f / 12f, BrightnessCurve.levelToWindow(0.5f), 0.002f)
    }

    @Test
    fun increasesSteadily() {
        var prev = 0f
        for (i in 1..100) {
            val w = BrightnessCurve.levelToWindow(i / 100f)
            assertTrue("level $i", w > prev)
            prev = w
        }
    }

    @Test
    fun roundTrips() {
        for (i in 5..100 step 5) {
            val level = i / 100f
            assertEquals(level, BrightnessCurve.windowToLevel(BrightnessCurve.levelToWindow(level)), 0.002f)
        }
    }
}
