package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    private val defaults = FilterList(
        listOf(
            SourceFilter.Select("sort", "Sort", options = listOf("a", "b"), labels = listOf("A", "B")),
            SourceFilter.Group(
                "status",
                "Status",
                listOf(SourceFilter.Check("ongoing", "Ongoing"), SourceFilter.Check("done", "Done")),
            ),
            SourceFilter.TriState("genre", "Genre"),
        ),
    )

    @Test
    fun updatedReturnsACopyAndLeavesTheOriginal() {
        val next = defaults
            .updated("ongoing") { (it as SourceFilter.Check).copy(state = true) }
            .updated("sort") { (it as SourceFilter.Select).copy(selected = 1) }
        assertEquals(listOf("ongoing"), next.checked("status"))
        assertEquals("b", next.selected("sort"))
        assertEquals(emptyList<String>(), defaults.checked("status"))
        assertEquals("a", defaults.selected("sort"))
    }

    @Test
    fun sameAsComparesOnlyState() {
        assertTrue(defaults.sameAs(defaults.copy()))
        val changed = defaults.updated("genre") { (it as SourceFilter.TriState).copy(state = 2) }
        assertFalse(changed.sameAs(defaults))
        assertEquals(listOf("genre"), changed.excluded())
        assertEquals(emptyList<String>(), changed.included())
    }

    @Test
    fun labelsFallBackToValues() {
        val select = SourceFilter.Select("x", "X", options = listOf("one", "two"))
        assertEquals("two", select.labelAt(1))
        assertEquals("B", (defaults.filters[0] as SourceFilter.Select).labelAt(1))
    }

    @Test
    fun carryOverKeepsStatesWhenTheSiteListsArrive() {
        val set = defaults
            .choose("sort", "b")
            .include("done")
            .updated("genre") { (it as SourceFilter.TriState).copy(state = 2) }
        val loaded = FilterList(
            defaults.filters + SourceFilter.Group(
                "genres",
                "Genres",
                listOf(SourceFilter.TriState("genres:1", "Action"), SourceFilter.TriState("genres:2", "Drama")),
            ),
        )
        val merged = loaded.carryOver(set)
        assertEquals("b", merged.selected("sort"))
        assertEquals(listOf("done"), merged.checked("status"))
        assertEquals(listOf("genre"), merged.excluded())
        assertEquals(emptyList<String>(), merged.included("genres"))
        assertEquals(2, merged.flat().count { it.id.startsWith("genres:") })
    }

    @Test
    fun carryOverDropsAChoiceWhenTheOptionsChanged() {
        val set = defaults.choose("sort", "b")
        val other = FilterList(listOf(SourceFilter.Select("sort", "Sort", options = listOf("x", "y", "z"))))
        assertEquals("x", other.carryOver(set).selected("sort"))
    }

    @Test
    fun chooseAndIncludeSetFiltersByTheirSiteValue() {
        val sort = FilterList(listOf(SourceFilter.Sort("order", "Order", options = listOf("views", "new"))))
        val chosen = sort.choose("order", "new", ascending = true).sort("order")!!
        assertEquals("new", chosen.selectedValue)
        assertTrue(chosen.ascending)
        // Unknown values leave the filter as it was.
        assertEquals("a", defaults.choose("sort", "nope").selected("sort"))
        assertEquals(listOf("genre"), defaults.include("genre").included())
    }
}
