package com.lume.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudSettingsTest {
    @Test
    fun speedIsKeptInRangeAndRoundedToATenth() {
        assertEquals(0.5f, ReadAloudSettings(speed = 0.1f).clamped().speed)
        assertEquals(3.0f, ReadAloudSettings(speed = 9f).clamped().speed)
        assertEquals(1.3f, ReadAloudSettings(speed = 1.27f).clamped().speed)
    }

    @Test
    fun blankVoiceAndEngineMeanTheDefaults() {
        val s = ReadAloudSettings(voiceName = " ", engine = "").clamped()
        assertNull(s.voiceName)
        assertNull(s.engine)
    }
}
