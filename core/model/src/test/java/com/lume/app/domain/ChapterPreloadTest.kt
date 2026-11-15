package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChapterPreloadTest {
    private fun ch(id: String, number: String) = Chapter(id = id, title = "Chapter $number", url = "/$id", number = number)

    // Newest first, as sources list them.
    private val chapters = listOf(ch("d", "4"), ch("c", "3"), ch("b", "2"), ch("a", "1"))

    @Test
    fun takesTheNextChaptersInReadingOrder() {
        assertEquals(listOf("b", "c"), chaptersToPreload(chapters, "a", 2).map { it.id })
    }

    @Test
    fun stopsAtTheLastChapter() {
        assertEquals(listOf("d"), chaptersToPreload(chapters, "c", 2).map { it.id })
        assertTrue(chaptersToPreload(chapters, "d", 2).isEmpty())
    }

    @Test
    fun skipsDownloadedChapters() {
        assertEquals(listOf("c", "d"), chaptersToPreload(chapters, "a", 2) { it.id == "b" }.map { it.id })
    }

    @Test
    fun zeroOrUnknownChapterLoadsNothing() {
        assertTrue(chaptersToPreload(chapters, "a", 0).isEmpty())
        assertTrue(chaptersToPreload(chapters, "x", 2).isEmpty())
    }

    @Test
    fun pacerWaitsBetweenImagesAndBacksOff() {
        val pacer = PreloadPacer(Random(1))
        val calm = pacer.nextImageDelayMs()
        assertTrue(calm in PreloadPacer.BASE_DELAY_MS..PreloadPacer.BASE_DELAY_MS + PreloadPacer.JITTER_MS)
        pacer.onFailure()
        pacer.onFailure()
        assertTrue(pacer.nextImageDelayMs() >= PreloadPacer.BASE_DELAY_MS + 2 * PreloadPacer.BACKOFF_STEP_MS)
        pacer.onSuccess()
        assertTrue(pacer.nextImageDelayMs() <= PreloadPacer.BASE_DELAY_MS + PreloadPacer.JITTER_MS)
    }

    @Test
    fun pacerGivesUpAfterFailuresInARow() {
        val pacer = PreloadPacer(Random(1))
        repeat(PreloadPacer.MAX_FAILURES_IN_ROW - 1) { pacer.onFailure() }
        assertFalse(pacer.gaveUp)
        pacer.onFailure()
        assertTrue(pacer.gaveUp)
    }
}
