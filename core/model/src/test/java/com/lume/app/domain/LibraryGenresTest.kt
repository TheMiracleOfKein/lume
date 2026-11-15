package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGenresTest {
    private fun item(id: String, vararg genres: String) =
        LibraryItem(sourceId = "s", titleId = id, title = "Title $id", genres = genres.toList())

    @Test
    fun normalizeIgnoresCaseAndSpacing() {
        assertEquals("slice of life", normalizeGenre("  Slice   of Life "))
        assertEquals(normalizeGenre("ЭКШЕН"), normalizeGenre("экшен"))
    }

    @Test
    fun cleanGenresDropsBlanksAndCaseRepeats() {
        assertEquals(listOf("Action", "Drama"), cleanGenres(listOf("Action", " action", "", "Drama", "DRAMA")))
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val json = encodeGenres(listOf("Action", "action", "Slice of Life"))
        assertEquals(listOf("Action", "Slice of Life"), decodeGenres(json))
        assertEquals("[]", encodeGenres(emptyList()))
    }

    @Test
    fun decodeToleratesGarbage() {
        assertTrue(decodeGenres(null).isEmpty())
        assertTrue(decodeGenres("").isEmpty())
        assertTrue(decodeGenres("[]").isEmpty())
        assertTrue(decodeGenres("not json").isEmpty())
    }

    @Test
    fun collectMergesCaseInsensitivelyAndSorts() {
        val items = listOf(
            item("1", "Romance", "action"),
            item("2", "Action", "Drama"),
            item("3", "Action"),
        )
        val options = collectLibraryGenres(items)
        assertEquals(listOf("Action", "Drama", "Romance"), options.map { it.label })
        assertEquals(listOf("action", "drama", "romance"), options.map { it.key })
    }

    @Test
    fun collectOfEmptyLibraryIsEmpty() {
        assertTrue(collectLibraryGenres(emptyList()).isEmpty())
        assertTrue(collectLibraryGenres(listOf(item("1"))).isEmpty())
    }

    @Test
    fun filterRequiresEverySelectedGenre() {
        val items = listOf(item("1", "Action", "Drama"), item("2", "action"), item("3", "Comedy"), item("4"))
        fun ids(selected: Set<String>) =
            queryLibrary(items, LibrarySettings(genres = selected)).map { it.titleId }.sorted()
        assertEquals(listOf("1", "2", "3", "4"), ids(emptySet()))
        assertEquals(listOf("1", "2"), ids(setOf("action")))
        assertEquals(listOf("1"), ids(setOf("action", "drama")))
        assertTrue(ids(setOf("horror")).isEmpty())
    }

    @Test
    fun genreFilterCombinesWithOtherFiltersAndCategory() {
        val items = listOf(
            item("1", "Action").copy(unread = 3, categoryIds = setOf(1L)),
            item("2", "Action").copy(unread = 0, categoryIds = setOf(1L)),
            item("3", "Action").copy(unread = 5, categoryIds = setOf(2L)),
        )
        val settings = LibrarySettings(genres = setOf("action")).cycled(LibraryFilter.Unread)
        assertEquals(listOf("1"), queryLibrary(items, settings, categoryId = 1L).map { it.titleId })
    }

    @Test
    fun toggleAndCount() {
        val on = LibrarySettings().withGenreToggled("action")
        assertEquals(setOf("action"), on.genres)
        assertEquals(1, on.activeFilterCount)
        assertTrue(on.withGenreToggled("action").genres.isEmpty())
    }
}
