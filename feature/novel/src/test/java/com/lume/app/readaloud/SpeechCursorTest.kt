package com.lume.app.readaloud

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import com.lume.app.data.novel.SpeechScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeechCursorTest {
    private val p0 = "The first sentence of the chapter. The second sentence of the chapter."
    private val blocks = listOf(
        NovelBlock.Paragraph(listOf(Span(p0))),
        NovelBlock.Image("x.png"),
        NovelBlock.Divider,
        NovelBlock.Paragraph(listOf(Span("A whole new paragraph begins here. And it goes on a little."))),
    )
    private val script = SpeechScript.build("Chapter title here", blocks, Locale.ENGLISH)

    @Test
    fun theTopOfTheChapterStartsAtTheTitle() {
        assertEquals(0, utteranceAt(script, blocks, 0, 0f))
    }

    @Test
    fun aFractionPicksTheSentenceAtThatPoint() {
        val i = utteranceAt(script, blocks, 0, 0.7f)
        assertEquals(0, script[i].block)
        assertTrue(script[i].text.startsWith("The second"))
    }

    @Test
    fun aPictureStartsAtTheNextText() {
        val i = utteranceAt(script, blocks, 1, 0.5f)
        assertEquals(3, script[i].block)
        assertFalse(script[i].pause)
    }

    @Test
    fun anchorAndUtteranceRoundTrip() {
        script.forEachIndexed { i, u ->
            // The first sentence's anchor is the top of the chapter, which starts at the title.
            if (u.pause || (u.block == 0 && u.start == 0)) return@forEachIndexed
            val a = anchorOf("c", u, blocks)
            assertEquals(i, utteranceAt(script, blocks, a.block, a.fraction))
        }
    }

    @Test
    fun nextParagraphSkipsPausesAndEndsAtMinusOne() {
        val second = script.indexOfFirst { it.block == 0 && it.start > 0 }
        val next = nextParagraph(script, second)
        assertEquals(3, script[next].block)
        assertEquals(0, script[next].start)
        assertEquals(-1, nextParagraph(script, script.lastIndex))
    }

    @Test
    fun previousParagraphGoesToItsStartThenToTheOneBefore() {
        val last = script.lastIndex
        val start = previousParagraph(script, last)
        assertEquals(3, script[start].block)
        assertEquals(0, script[start].start)
        val before = previousParagraph(script, start)
        assertEquals(0, script[before].block)
        assertEquals(0, script[before].start)
        assertEquals(0, previousParagraph(script, 0))
    }

    @Test
    fun sleepTimerRules() {
        assertFalse(SleepTimer.Off.dueAtChapterEnd(0))
        assertTrue(SleepTimer.EndOfChapter.dueAtChapterEnd(0))
        assertFalse(SleepTimer.EndOfChapter.dueAfterSentence(0))
        val t = SleepTimer.minutes(15, now = 1_000)
        assertFalse(t.dueAfterSentence(1_000))
        assertTrue(t.dueAfterSentence(1_000 + 15 * 60_000))
        assertEquals(60_000L, t.remainingMs(1_000 + 14 * 60_000))
    }
}
