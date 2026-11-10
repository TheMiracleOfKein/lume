package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Genre chips, library filter chips, the bulk bell, duplicates on add and update rules. */
class LibraryExtrasTest {

    private val filters = FilterList(
        listOf(
            SourceFilter.Sort("sort", "Sort", listOf("popular", "new")),
            SourceFilter.Group(
                "genres",
                "Genres",
                listOf(
                    SourceFilter.TriState("g-1", "Sci-Fi"),
                    SourceFilter.TriState("g-2", "Slice of Life"),
                ),
            ),
            SourceFilter.Group("tags", "Tags", listOf(SourceFilter.Check("t-9", "Time Travel"))),
        ),
    )

    @Test
    fun genreIncludesTheMatchingTriState() {
        val result = filters.withGenre("sci fi")
        assertEquals(listOf("g-1"), result?.included("genres"))
    }

    @Test
    fun genreMatchesIgnoringCaseAndSeparators() {
        assertEquals(listOf("g-2"), filters.withGenre("  slice_of-life ")?.included("genres"))
    }

    @Test
    fun genreTicksAMatchingCheck() {
        assertEquals(listOf("t-9"), filters.withGenre("TIME TRAVEL")?.checked("tags"))
    }

    @Test
    fun unknownOrBlankGenreGivesNull() {
        assertNull(filters.withGenre("Romance"))
        assertNull(filters.withGenre("   "))
        // Sorts and other filters are never a genre.
        assertNull(filters.withGenre("Sort"))
    }

    @Test
    fun filterChipCyclesOffIncludeExcludeOff() {
        var s = LibrarySettings()
        s = s.cycled(LibraryFilter.Unread)
        assertEquals(TriState.Include, s.filter(LibraryFilter.Unread))
        s = s.cycled(LibraryFilter.Unread)
        assertEquals(TriState.Exclude, s.filter(LibraryFilter.Unread))
        s = s.cycled(LibraryFilter.Unread)
        assertEquals(TriState.Off, s.filter(LibraryFilter.Unread))
        assertEquals(TriState.Off, s.filter(LibraryFilter.Downloaded))
    }

    @Test
    fun bellTurnsOffWhenAllSelectedHaveIt() {
        assertEquals(BulkBell.Off(3), planBulkBell(ringing = 3, selected = 3, watched = 10, limit = 20))
    }

    @Test
    fun bellAddsTheMissingOnes() {
        assertEquals(BulkBell.On(added = 2, skipped = 0), planBulkBell(ringing = 1, selected = 3, watched = 5, limit = 20))
    }

    @Test
    fun bellStopsAtThePoolLimit() {
        assertEquals(BulkBell.On(added = 2, skipped = 3), planBulkBell(ringing = 0, selected = 5, watched = 18, limit = 20))
        assertEquals(BulkBell.On(added = 0, skipped = 4), planBulkBell(ringing = 0, selected = 4, watched = 20, limit = 20))
    }

    @Test
    fun sameNamedFindsLibraryTitleByAnyName() {
        val library = listOf("Solo Leveling", "Omniscient Reader", "Na Honjaman Level Up")
        assertEquals(
            listOf("Solo Leveling", "Na Honjaman Level Up"),
            sameNamed(listOf("SOLO LEVELING!", "na honjaman level-up"), library) { it },
        )
        assertTrue(sameNamed(listOf("Tower of God"), library) { it }.isEmpty())
        assertTrue(sameNamed(listOf("!!!"), library) { it }.isEmpty())
    }

    @Test
    fun updateRulesOffSkipNothing() {
        val rules = UpdateRules()
        assertFalse(rules.skipsAny)
        assertFalse(rules.skips(completed = true, total = 10, unread = 10, lastReadAt = null))
    }

    @Test
    fun updateRulesSkipCompletedUnstartedAndUnread() {
        assertTrue(UpdateRules(skipCompleted = true).skips(completed = true, total = 5, unread = 0, lastReadAt = 1L))
        assertFalse(UpdateRules(skipCompleted = true).skips(completed = false, total = 5, unread = 0, lastReadAt = 1L))

        val unstarted = UpdateRules(skipNotStarted = true)
        assertTrue(unstarted.skips(completed = false, total = 5, unread = 5, lastReadAt = null))
        // Read marks (an import) count as started even without a reading time.
        assertFalse(unstarted.skips(completed = false, total = 5, unread = 3, lastReadAt = null))

        val unread = UpdateRules(skipUnread = true)
        assertTrue(unread.skips(completed = false, total = 5, unread = 1, lastReadAt = 1L))
        assertFalse(unread.skips(completed = false, total = 5, unread = 0, lastReadAt = 1L))
    }

    @Test
    fun noChosenCategoriesMeansEverything() {
        val rules = UpdateRules()
        assertTrue(rules.inCategories(emptySet()))
        assertTrue(rules.inCategories(setOf(3L)))
    }

    @Test
    fun chosenCategoriesMatchOnAnyIntersection() {
        val rules = UpdateRules(categories = setOf(1L, 2L))
        assertTrue(rules.inCategories(setOf(1L)))
        assertTrue(rules.inCategories(setOf(5L, 2L)))
        assertFalse(rules.inCategories(setOf(5L)))
        assertFalse(rules.inCategories(emptySet()))
    }

    @Test
    fun uncategorizedTitlesMatchZero() {
        val rules = UpdateRules(categories = setOf(0L))
        assertTrue(rules.inCategories(emptySet()))
        assertFalse(rules.inCategories(setOf(1L)))
    }

    @Test
    fun categoriesCountAsSkipping() {
        assertTrue(UpdateRules(categories = setOf(1L)).skipsAny)
    }
}
