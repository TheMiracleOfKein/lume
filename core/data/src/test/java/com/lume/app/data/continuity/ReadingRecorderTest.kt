package com.lume.app.data.continuity

import com.lume.app.domain.ReadingSessionTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingRecorderTest {
    private val session = ReadingSessionTracker.Session("c1", startedAt = 1000L, durationMs = 5000L, pagesRead = 7)

    @Test
    fun comicSessionKeepsPages() {
        val e = ReadingRecorder.sessionEntity("src", "t", "Title", session, countsPages = true)
        assertEquals(7, e.pagesRead)
        assertEquals("c1", e.chapterId)
        assertEquals(1000L, e.startedAt)
        assertEquals(5000L, e.durationMs)
        assertEquals("Title", e.titleName)
    }

    @Test
    fun textSessionCountsTimeOnly() {
        val e = ReadingRecorder.sessionEntity("src", "t", "Title", session, countsPages = false)
        assertEquals(0, e.pagesRead)
        assertEquals(5000L, e.durationMs)
    }
}
