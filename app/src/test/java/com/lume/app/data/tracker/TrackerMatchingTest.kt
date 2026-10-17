package com.lume.app.data.tracker

import com.lume.app.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerMatchingTest {
    private fun ch(id: String, title: String = "Chapter $id", number: String? = null) =
        Chapter(id = id, title = title, url = "/$id", number = number)

    // Newest first, like most sources.
    private val chapters = listOf(
        ch("c12", number = "12"),
        ch("c11.5", number = "11.5"),
        ch("c11", number = "11"),
        ch("c10", title = "Глава 10"),
        ch("c1", number = "1"),
    )

    @Test
    fun progressNumberUsesChapterNumber() {
        assertEquals(12, chapterProgressNumber(chapters, "c12"))
        // Trackers count whole chapters: 11.5 counts as 11.
        assertEquals(11, chapterProgressNumber(chapters, "c11.5"))
    }

    @Test
    fun progressNumberFallsBackToTitleThenPosition() {
        assertEquals(10, chapterProgressNumber(chapters, "c10"))
        val unnumbered = listOf(ch("a", title = "Prologue"), ch("b", title = "Finale"))
        assertEquals(2, chapterProgressNumber(unnumbered, "b"))
        assertEquals(0, chapterProgressNumber(unnumbered, "missing"))
    }

    @Test
    fun chapterZeroIsNotProgress() {
        val list = listOf(ch("p", number = "0"), ch("q", number = "1"))
        // "0" is not a usable number, so the list position (1-based) is used.
        assertEquals(1, chapterProgressNumber(list, "p"))
    }

    @Test
    fun findChapterForProgressMatchesExactly() {
        assertEquals("c11.5", findChapterForProgress(chapters, 11)?.id)
        assertEquals("c12", findChapterForProgress(chapters, 12)?.id)
    }

    @Test
    fun findChapterForProgressTakesClosestBelow() {
        // No chapter 5: the furthest chapter not past it.
        assertEquals("c1", findChapterForProgress(chapters, 5)?.id)
        assertEquals("c12", findChapterForProgress(chapters, 40)?.id)
        assertNull(findChapterForProgress(chapters, 0))
        assertNull(findChapterForProgress(emptyList(), 3))
    }

    @Test
    fun localProgressIsTheFurthestOfCurrentAndRead() {
        val (best, chapter) = resolveLocalChapterProgress(
            chapters = chapters,
            progressChapterId = "c10",
            readChapterIds = setOf("c1", "c11", "unknown"),
        )
        assertEquals(11, best)
        assertEquals("c11", chapter?.id)
    }

    @Test
    fun localProgressWithNothingReadIsZero() {
        val (best, chapter) = resolveLocalChapterProgress(chapters, null, emptySet())
        assertEquals(0, best)
        assertNull(chapter)
    }
}
