package com.lume.app.translate

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import com.lume.app.data.novel.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationMarkupTest {
    private val glossary = listOf(
        GlossaryEntry("Shirou", "Широ"),
        GlossaryEntry("mana core", "ядро маны"),
        GlossaryEntry("mana", "мана"),
    )

    private fun para(vararg spans: Span) = NovelBlock.Paragraph(spans.toList())

    @Test
    fun marksWholeWordsLongestFirst() {
        val html = TranslationMarkup.toHtml(para(Span("Shirou felt his Mana core. Shiroua and manas aren't terms.")), glossary)
        assertEquals(
            "<span data-g=\"0\">Shirou</span> felt his <span data-g=\"1\">Mana core</span>. Shiroua and manas aren't terms.",
            html,
        )
    }

    @Test
    fun escapesTextAndKeepsStyles() {
        val html = TranslationMarkup.toHtml(para(Span("a < b & c"), Span("bold", bold = true), Span("it", italic = true)), emptyList())
        assertEquals("a &lt; b &amp; c<b>bold</b><i>it</i>", html)
    }

    @Test
    fun skipsBlocksWithoutText() {
        assertNull(TranslationMarkup.toHtml(NovelBlock.Divider, glossary))
        assertNull(TranslationMarkup.toHtml(NovelBlock.Image("x.png"), glossary))
        assertNull(TranslationMarkup.toHtml(para(Span("  ")), glossary))
    }

    @Test
    fun replacesMarkedWordsWithTheGlossaryTarget() {
        val translated = "<span data-g=\"0\">Ширу</span> вошла, чувствуя <span data-g=\"1\">манное ядро</span>."
        val block = TranslationMarkup.fromHtml(para(Span("x")), translated, glossary)
        assertEquals("Широ вошла, чувствуя ядро маны.", (block as NovelBlock.Paragraph).spans.plainText)
    }

    @Test
    fun keepsBoldAndItalicFromTheTranslation() {
        val spans = TranslationMarkup.spans("что <b>было почти</b> <i>правдой</i>.", emptyList())
        assertEquals(
            listOf(Span("что "), Span("было почти", bold = true), Span(" "), Span("правдой", italic = true), Span(".")),
            spans,
        )
    }

    @Test
    fun aLostOrUnknownMarkLeavesTheEngineText() {
        val spans = TranslationMarkup.spans("<span data-g=\"9\">Ширу</span> и <span>он</span>", glossary)
        assertEquals("Ширу и он", spans.plainText)
    }

    @Test
    fun headingStaysAHeading() {
        val block = TranslationMarkup.fromHtml(NovelBlock.Heading(2, listOf(Span("Chapter 1"))), "Глава 1", glossary)
        assertEquals(NovelBlock.Heading(2, listOf(Span("Глава 1"))), block)
    }

    @Test
    fun unescapesEntities() {
        assertEquals("a < b & c", TranslationMarkup.spans("a &lt; b &amp; c", emptyList()).plainText)
    }

    @Test
    fun translatesFromTheReadingSpotFirst() {
        assertEquals(listOf(3, 4, 5, 2, 1, 0), readingOrderFrom(3, 6))
        assertEquals(listOf(0, 1, 2), readingOrderFrom(0, 3))
        assertEquals(emptyList<Int>(), readingOrderFrom(0, 0))
    }

    @Test
    fun batchesStayUnderTheLimit() {
        val items = listOf("a".repeat(10), "b".repeat(10), "c".repeat(25), "d".repeat(3))
        assertEquals(listOf(listOf(items[0], items[1]), listOf(items[2]), listOf(items[3])), batches(items, 20))
        assertEquals(emptyList<List<String>>(), batches(emptyList(), 20))
    }
}
