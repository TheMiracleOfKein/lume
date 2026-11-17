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
    fun lineHeightSnapsToTwentiethsSoStoredDoublesCompareEqual() {
        assertEquals(1.45f, NovelReaderSettings(lineHeight = 1.4500001f).clamped().lineHeight)
    }
}
