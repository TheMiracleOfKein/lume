package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingSessionTrackerTest {
    private val tracker = ReadingSessionTracker()

    @Test
    fun timeBetweenPagesAdds() {
        tracker.onPage("c1", 0, 0L)
        tracker.onPage("c1", 1, 20_000L)
        val s = tracker.finish(30_000L)!!
        assertEquals(30_000L, s.durationMs)
        assertEquals(2, s.pagesRead)
        assertEquals(0L, s.startedAt)
    }

    @Test
    fun longGapIsCappedAtTheIdleLimit() {
        tracker.onPage("c1", 0, 0L)
        tracker.onPage("c1", 1, 60 * 60_000L)
        val s = tracker.finish(60 * 60_000L)!!
        assertEquals(ReadingSessionTracker.IDLE_CAP_MS, s.durationMs)
    }

    @Test
    fun pausedTimeDoesNotCount() {
        tracker.onPage("c1", 0, 0L)
        tracker.pause(10_000L)
        tracker.resume(500_000L)
        tracker.onPage("c1", 1, 510_000L)
        val s = tracker.finish(510_000L)!!
        assertEquals(20_000L, s.durationMs)
    }

    @Test
    fun shortSessionWithOnePageIsDropped() {
        tracker.onPage("c1", 0, 0L)
        assertNull(tracker.finish(3_000L))
    }

    @Test
    fun shortSessionWithSeveralPagesIsKept() {
        tracker.onPage("c1", 0, 0L)
        tracker.onPage("c1", 1, 1_000L)
        tracker.onPage("c1", 2, 2_000L)
        assertEquals(3, tracker.finish(3_000L)!!.pagesRead)
    }

    @Test
    fun pagesAreCountedOnce() {
        tracker.onPage("c1", 0, 0L)
        tracker.onPage("c1", 1, 10_000L)
        tracker.onPage("c1", 0, 20_000L)
        tracker.onPage("c1", 1, 30_000L)
        assertEquals(2, tracker.finish(30_000L)!!.pagesRead)
    }

    @Test
    fun anotherChapterFinishesTheFirstAndStartsANewOne() {
        tracker.onPage("c1", 0, 0L)
        tracker.onPage("c1", 1, 30_000L)
        val first = tracker.onPage("c2", 0, 40_000L)!!
        assertEquals("c1", first.chapterId)
        assertEquals(40_000L, first.durationMs)
        tracker.onPage("c2", 1, 100_000L)
        val second = tracker.finish(100_000L)!!
        assertEquals("c2", second.chapterId)
        assertEquals(40_000L, second.startedAt)
        assertEquals(60_000L, second.durationMs)
    }

    @Test
    fun finishWithoutASessionIsNull() {
        assertNull(tracker.finish(1_000L))
    }
}
