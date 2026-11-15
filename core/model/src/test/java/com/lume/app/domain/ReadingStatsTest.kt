package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class ReadingStatsTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        java.time.LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun session(title: String, startedAt: Long, ms: Long, pages: Int) =
        StatSession("s", title, "Name $title", startedAt, ms, pages)

    @Test
    fun emptyInputGivesTwelveEmptyMonths() {
        val stats = computeReadingStats(emptyList(), emptyList(), at(2025, 3, 10), utc)
        assertTrue(stats.isEmpty)
        assertEquals(12, stats.months.size)
        assertEquals(YearMonth.of(2025, 3), stats.months.first().month)
        assertEquals(YearMonth.of(2024, 4), stats.months.last().month)
    }

    @Test
    fun monthsAreBucketedAcrossAYearBoundary() {
        val sessions = listOf(
            session("a", at(2024, 12, 31, 23), 60_000, 10),
            session("a", at(2025, 1, 1, 0), 30_000, 5),
        )
        val reads = listOf(StatChapterRead("s", "a", at(2024, 12, 31, 23)), StatChapterRead("s", "a", at(2025, 1, 2)))
        val stats = computeReadingStats(sessions, reads, at(2025, 1, 15), utc)
        val jan = stats.months.first { it.month == YearMonth.of(2025, 1) }
        val dec = stats.months.first { it.month == YearMonth.of(2024, 12) }
        assertEquals(30_000L, jan.durationMs)
        assertEquals(1, jan.chapters)
        assertEquals(60_000L, dec.durationMs)
        assertEquals(10, dec.pages)
    }

    @Test
    fun theLocalZoneDecidesTheMonth() {
        val sessions = listOf(session("a", at(2025, 1, 31, 23), 10_000, 1))
        val tokyo = ZoneId.of("Asia/Tokyo")
        val stats = computeReadingStats(sessions, emptyList(), at(2025, 2, 15), tokyo)
        assertEquals(10_000L, stats.months.first { it.month == YearMonth.of(2025, 2) }.durationMs)
    }

    @Test
    fun titlesAreSortedByTimeAndCapped() {
        val sessions = (1..60).map { session("t$it", at(2025, 1, 1), it * 1_000L, 1) }
        val stats = computeReadingStats(sessions, emptyList(), at(2025, 1, 15), utc)
        assertEquals(50, stats.titles.size)
        assertEquals("t60", stats.titles.first().titleId)
        assertEquals("t11", stats.titles.last().titleId)
    }

    @Test
    fun totalsAndPerTitleCounts() {
        val sessions = listOf(
            session("a", at(2025, 1, 1), 60_000, 10),
            session("a", at(2025, 1, 2), 40_000, 6),
            session("b", at(2025, 1, 3), 10_000, 2),
        )
        val reads = listOf(
            StatChapterRead("s", "a", at(2025, 1, 1)),
            StatChapterRead("s", "a", at(2025, 1, 2)),
            StatChapterRead("s", "b", at(2025, 1, 3)),
        )
        val stats = computeReadingStats(sessions, reads, at(2025, 1, 15), utc)
        assertEquals(3, stats.chapters)
        assertEquals(18, stats.pages)
        assertEquals(110_000L, stats.durationMs)
        val a = stats.titles.first()
        assertEquals("a", a.titleId)
        assertEquals(2, a.chapters)
        assertEquals(16, a.pages)
        assertEquals(100_000L, a.durationMs)
    }
}
