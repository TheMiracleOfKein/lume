package com.lume.app.ui.screens

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.lume.app.data.novel.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelTextStyleTest {
    @Test
    fun spansBecomeStyledRunsOfOneString() {
        val text = listOf(Span("Plain "), Span("bold", bold = true), Span(" and "), Span("both", bold = true, italic = true), Span(" or "), Span("slanted", italic = true))
            .toAnnotatedString()
        assertEquals("Plain bold and both or slanted", text.text)
        assertEquals(3, text.spanStyles.size)
        val (bold, both, italic) = text.spanStyles
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(null, bold.item.fontStyle)
        assertEquals("bold", text.text.substring(bold.start, bold.end))
        assertEquals(FontWeight.Bold, both.item.fontWeight)
        assertEquals(FontStyle.Italic, both.item.fontStyle)
        assertEquals(FontStyle.Italic, italic.item.fontStyle)
        assertEquals("slanted", text.text.substring(italic.start, italic.end))
    }

    @Test
    fun plainTextHasNoStyles() {
        assertTrue(listOf(Span("just text")).toAnnotatedString().spanStyles.isEmpty())
    }

    @Test
    fun headingsShrinkWithTheirLevel() {
        val scales = (1..6).map(::headingScale)
        assertEquals(scales.sortedDescending(), scales)
        assertTrue(scales.all { it > 1f })
    }
}
