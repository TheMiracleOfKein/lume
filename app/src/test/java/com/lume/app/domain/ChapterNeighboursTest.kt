package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNeighboursTest {
    private fun ch(id: String, number: String) = Chapter(id = id, title = "Chapter $number", url = "/$id", number = number)

    private val oldestFirst = listOf(ch("a", "1"), ch("b", "2"), ch("c", "3"))

    @Test
    fun middleChapterHasBothNeighbours() {
        val n = chapterNeighbours(oldestFirst, "b")
        assertEquals("a", n.previous?.id)
        assertEquals("c", n.next?.id)
    }

    @Test
    fun firstChapterHasNoPrevious() {
        val n = chapterNeighbours(oldestFirst, "a")
        assertNull(n.previous)
        assertEquals("b", n.next?.id)
    }

    @Test
    fun lastChapterHasNoNext() {
        val n = chapterNeighbours(oldestFirst, "c")
        assertEquals("b", n.previous?.id)
        assertNull(n.next)
    }

    @Test
    fun singleChapterHasNoNeighbours() {
        val n = chapterNeighbours(listOf(ch("a", "1")), "a")
        assertNull(n.previous)
        assertNull(n.next)
    }

    @Test
    fun newestFirstInputStillFollowsReadingOrder() {
        val n = chapterNeighbours(oldestFirst.reversed(), "b")
        assertEquals("a", n.previous?.id)
        assertEquals("c", n.next?.id)
    }

    @Test
    fun shuffledInputStillFollowsReadingOrder() {
        val n = chapterNeighbours(listOf(ch("c", "3"), ch("a", "1"), ch("b", "2")), "a")
        assertNull(n.previous)
        assertEquals("b", n.next?.id)
    }

    @Test
    fun duplicateNumbersKeepListedOrder() {
        val list = listOf(ch("x1", "5"), ch("x2", "5"), ch("y", "6"))
        val n = chapterNeighbours(list, "x1")
        assertNull(n.previous)
        assertEquals("x2", n.next?.id)
        assertEquals("x1", chapterNeighbours(list, "x2").previous?.id)
        assertEquals("y", chapterNeighbours(list, "x2").next?.id)
    }

    @Test
    fun unknownIdHasNoNeighbours() {
        val n = chapterNeighbours(oldestFirst, "zzz")
        assertNull(n.previous)
        assertNull(n.next)
    }

    @Test
    fun trailingSlashInIdStillMatches() {
        val n = chapterNeighbours(oldestFirst, "b/")
        assertEquals("a", n.previous?.id)
        assertEquals("c", n.next?.id)
    }
}
