package com.lume.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class NovelReaderSettingsTest {
    @Test
    fun defaultsAreInsideTheirRanges() {
        val d = NovelReaderSettings()
        assertEquals(d, d.clamped())
        assertEquals(18, d.fontSize)
        assertEquals(1.5f, d.lineHeight)
        assertEquals(NovelFont.Serif, d.font)
        assertEquals(NovelTheme.App, d.theme)
    }

    @Test
    fun valuesOutsideTheRangesAreClamped() {
        val low = NovelReaderSettings(fontSize = 2, lineHeight = 0.5f, sideMargin = -4, paragraphSpacing = -1).clamped()
        assertEquals(14, low.fontSize)
        assertEquals(1.2f, low.lineHeight)
        assertEquals(8, low.sideMargin)
        assertEquals(0, low.paragraphSpacing)
        val high = NovelReaderSettings(fontSize = 99, lineHeight = 5f, sideMargin = 400, paragraphSpacing = 80).clamped()
        assertEquals(28, high.fontSize)
        assertEquals(2.0f, high.lineHeight)
        assertEquals(48, high.sideMargin)
        assertEquals(24, high.paragraphSpacing)
    }

    @Test
    fun readingComfortDefaults() {
        val d = NovelReaderSettings()
        assertEquals(true, d.showChapterTitle)
        assertEquals(true, d.brightnessSwipe)
        assertEquals(NovelReaderSettings.BRIGHTNESS_SYSTEM, d.brightness)
        assertEquals(false, d.volumeKeys)
        assertEquals(true, d.statusLine)
        assertEquals(0, d.paragraphIndent)
        assertEquals(3, d.autoScrollSpeed)
    }

    @Test
    fun brightnessIndentAndSpeedAreClamped() {
        assertEquals(NovelReaderSettings.BRIGHTNESS_SYSTEM, NovelReaderSettings(brightness = -0.3f).clamped().brightness)
        assertEquals(NovelReaderSettings.BRIGHTNESS_SYSTEM, NovelReaderSettings(brightness = Float.NaN).clamped().brightness)
        assertEquals(NovelReaderSettings.BRIGHTNESS_MIN, NovelReaderSettings(brightness = 0f).clamped().brightness)
        assertEquals(1f, NovelReaderSettings(brightness = 3f).clamped().brightness)
        assertEquals(0.4f, NovelReaderSettings(brightness = 0.4f).clamped().brightness)
        assertEquals(2, NovelReaderSettings(paragraphIndent = 9).clamped().paragraphIndent)
        assertEquals(0, NovelReaderSettings(paragraphIndent = -1).clamped().paragraphIndent)
        assertEquals(1, NovelReaderSettings(autoScrollSpeed = 0).clamped().autoScrollSpeed)
        assertEquals(10, NovelReaderSettings(autoScrollSpeed = 50).clamped().autoScrollSpeed)
    }

    @Test
    fun lineHeightSnapsToTwentiethsSoStoredDoublesCompareEqual() {
        assertEquals(1.45f, NovelReaderSettings(lineHeight = 1.4500001f).clamped().lineHeight)
    }
}
