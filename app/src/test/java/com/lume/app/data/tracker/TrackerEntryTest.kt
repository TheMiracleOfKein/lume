package com.lume.app.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerEntryTest {
    @Test
    fun exactMatchUsesAllNamesAndIgnoresPunctuation() {
        val results = listOf(
            TrackerSearchResult("1", "Solo Leveling: Ragnarok"),
            TrackerSearchResult("2", "Ore dake Level Up na Ken", altTitles = listOf("Solo Leveling")),
        )
        assertEquals("2", exactMatch(listOf("Solo leveling!"), results)?.remoteId)
        assertNull(exactMatch(listOf("Solo"), results))
        assertNull(exactMatch(listOf(""), results))
    }

    @Test
    fun statusMapRoundTrip() {
        val map = StatusMap(listOf(TrackStatus.Reading to "CURRENT", TrackStatus.Planned to "PLANNING"))
        assertEquals("CURRENT", map.toRemote(TrackStatus.Reading))
        assertEquals(TrackStatus.Planned, map.fromRemote("planning"))
        assertNull(map.toRemote(TrackStatus.Dropped))
        assertEquals(listOf(TrackStatus.Reading, TrackStatus.Planned), map.statuses)
    }

    @Test
    fun dates() {
        assertEquals("2024-03-05", fuzzyDate(2024, 3, 5))
        assertEquals("2024-01-01", fuzzyDate(2024, null, null))
        assertNull(fuzzyDate(null, 3, 5))
        assertEquals("2024-03-05", cleanDate("2024-03-05T10:00:00.000Z"))
        assertNull(cleanDate("05.03.2024"))
    }
}
