package com.lume.app.ui.screens

import com.lume.app.data.prefs.NovelReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelBrightnessTest {
    @Test
    fun swipingUpBrightensAndDownDims() {
        // 0.6 of a 1000 px screen is the full range.
        assertEquals(0.8f, brightnessAfterDrag(0.5f, -180f, 1000f), 1e-4f)
        assertEquals(0.2f, brightnessAfterDrag(0.5f, 180f, 1000f), 1e-4f)
        assertEquals(0.5f, brightnessAfterDrag(0.5f, 0f, 1000f), 0f)
    }

    @Test
    fun brightnessStaysInRange() {
        assertEquals(1f, brightnessAfterDrag(0.9f, -5000f, 1000f), 0f)
        assertEquals(NovelReaderSettings.BRIGHTNESS_MIN, brightnessAfterDrag(0.1f, 5000f, 1000f), 0f)
        assertEquals(0.3f, brightnessAfterDrag(0.3f, 100f, 0f), 0f)
    }

    @Test
    fun layoutOnlyIgnoresSettingsThatDontShapeTheText() {
        val base = NovelReaderSettings(fontSize = 20, paragraphIndent = 1, showChapterTitle = false)
        val other = base.copy(brightness = 0.3f, brightnessSwipe = false, volumeKeys = true, statusLine = false, autoScrollSpeed = 9)
        assertEquals(base.layoutOnly(), other.layoutOnly())
        assertEquals(false, base.layoutOnly() == base.copy(paragraphIndent = 2).layoutOnly())
    }
}
