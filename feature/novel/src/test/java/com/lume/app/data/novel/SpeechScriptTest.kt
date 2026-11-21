package com.lume.app.data.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeechScriptTest {
    private fun para(text: String) = NovelBlock.Paragraph(listOf(Span(text)))

    private fun texts(text: String, locale: Locale = Locale.ENGLISH, max: Int = SpeechScript.MAX_LENGTH) =
        SpeechScript.sentences(text, locale, max).map { text.substring(it.first, it.last + 1) }

    @Test
    fun splitsEnglishSentences() {
        assertEquals(
            listOf("The night was cold and very dark.", "Nobody came to the gate that evening."),
            texts("The night was cold and very dark. Nobody came to the gate that evening."),
        )
    }

    @Test
    fun splitsRussianSentences() {
        val ru = Locale.forLanguageTag("ru")
        assertEquals(
            listOf("Ночь была холодной и очень тёмной.", "Никто не пришёл к воротам в тот вечер."),
            texts("Ночь была холодной и очень тёмной. Никто не пришёл к воротам в тот вечер.", ru),
        )
    }

    @Test
    fun shortRepliesAreJoinedToTheNextSentence() {
        assertEquals(
            listOf("Yes. No. Maybe tomorrow, if the rain stops."),
            texts("Yes. No. Maybe tomorrow, if the rain stops."),
        )
    }

    @Test
    fun aShortLastSentenceStaysOnItsOwn() {
        assertEquals(listOf("He opened the door very slowly.", "Empty."), texts("He opened the door very slowly. Empty."))
    }

    @Test
    fun aLoneMarkGoesOntoTheSentenceBefore() {
        val text = "He waited at the window for a long time. …"
        assertEquals(listOf(text), texts(text))
    }

    @Test
    fun longSentencesAreCutAfterACommaOrAtASpace() {
        val text = "one two three four, five six seven eight nine ten"
        val parts = texts(text, max = 25)
        assertTrue(parts.all { it.length <= 25 })
        assertEquals("one two three four,", parts.first())
        assertEquals(text.replace(",", "").split(' ').size, parts.joinToString(" ").replace(",", "").split(' ').size)
    }

    @Test
    fun offsetsPointIntoTheBlocksPlainText() {
        val blocks = listOf(
            NovelBlock.Paragraph(listOf(Span("Bold start of a line. "), Span("Then italic words follow here.", italic = true))),
        )
        val script = SpeechScript.build(null, blocks, Locale.ENGLISH)
        val plain = (blocks[0] as NovelBlock.Paragraph).spans.plainText
        script.forEach { u -> assertEquals(u.text, plain.substring(u.start, u.end)) }
        assertEquals(2, script.size)
    }

    @Test
    fun titleFirstThenBlocksWithBreaksAsPausesAndPicturesSkipped() {
        val blocks = listOf(
            NovelBlock.Heading(1, listOf(Span("Part one of the story"))),
            para("The first paragraph is right here."),
            NovelBlock.Image("x.png"),
            NovelBlock.Divider,
            para("The second paragraph comes after."),
        )
        val script = SpeechScript.build("Chapter 1: The beginning", blocks, Locale.ENGLISH)
        assertEquals(listOf(SpeechScript.TITLE, 0, 1, 3, 4), script.map { it.block })
        assertTrue(script[3].pause)
    }

    @Test
    fun lineBreaksInsideAParagraphAreSpokenAsSpaces() {
        val script = SpeechScript.build(null, listOf(para("First line of the poem\nsecond line of the poem")), Locale.ENGLISH)
        assertEquals("First line of the poem second line of the poem", script.single().text)
    }
}
