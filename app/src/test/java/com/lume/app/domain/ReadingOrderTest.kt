package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingOrderTest {
    private fun ch(id: String, title: String = "Chapter $id", number: String? = null) =
        Chapter(id = id, title = title, url = "/$id", number = number)

    private fun ids(list: List<Chapter>) = list.map { it.id }

    @Test
    fun newestFirstIsReversed() {
        val list = listOf(ch("3", number = "3"), ch("2", number = "2"), ch("1", number = "1"))
        assertEquals(listOf("1", "2", "3"), ids(list.inReadingOrder()))
    }

    @Test
    fun oldestFirstStaysAsIs() {
        val list = listOf(ch("1", number = "1"), ch("2", number = "2"), ch("3", number = "3"))
        assertEquals(listOf("1", "2", "3"), ids(list.inReadingOrder()))
    }

    @Test
    fun decimalsAndCommaDecimalsSortNumerically() {
        val list = listOf(ch("b", number = "10,5"), ch("c", number = "11"), ch("a", number = "10"), ch("d", number = "9.5"))
        assertEquals(listOf("d", "a", "b", "c"), ids(list.inReadingOrder()))
    }

    @Test
    fun numberComesFromTitleWhenMissing() {
        val list = listOf(ch("x", title = "Глава 12"), ch("y", title = "Глава 2"), ch("z", title = "Chapter 7: The end"))
        assertEquals(listOf("y", "z", "x"), ids(list.inReadingOrder()))
    }

    @Test
    fun equalNumbersKeepSourceOrder() {
        // Two teams' copies of chapter 5: the source's order decides which comes first.
        val list = listOf(ch("5b", number = "5"), ch("6", number = "6"), ch("5a", number = "5"))
        assertEquals(listOf("5b", "5a", "6"), ids(list.inReadingOrder()))
    }

    @Test
    fun unnumberedChaptersGoLastWhenMostAreNumbered() {
        val list = listOf(ch("extra", title = "Extra"), ch("2", number = "2"), ch("1", number = "1"))
        assertEquals(listOf("1", "2", "extra"), ids(list.inReadingOrder()))
    }

    @Test
    fun mostlyUnnumberedListIsReversedOnlyWhenEndsSayNewestFirst() {
        // Fewer than half numbered: no sorting, only the two ends decide the direction.
        val newestFirst = listOf(
            ch("c", title = "Part 3"), ch("b", title = "Prologue"), ch("x", title = "Intro"),
            ch("y", title = "Bonus"), ch("a", title = "Part 1"),
        )
        assertEquals(listOf("a", "y", "x", "b", "c"), ids(newestFirst.inReadingOrder()))
        val unknown = listOf(ch("p", title = "Prologue"), ch("q", title = "Interlude"), ch("r", title = "Finale"))
        assertEquals(listOf("p", "q", "r"), ids(unknown.inReadingOrder()))
    }

    @Test
    fun shortListsAreUnchanged() {
        assertEquals(emptyList<String>(), ids(emptyList<Chapter>().inReadingOrder()))
        assertEquals(listOf("only"), ids(listOf(ch("only")).inReadingOrder()))
    }

    @Test
    fun sortKeyPrefersNumberField() {
        assertEquals(3.0, chapterSortKey(ch("a", title = "Chapter 99", number = "3"))!!, 0.0)
        assertEquals(99.0, chapterSortKey(ch("a", title = "Chapter 99", number = "n/a"))!!, 0.0)
        assertNull(chapterSortKey(ch("a", title = "Prologue")))
    }
}
