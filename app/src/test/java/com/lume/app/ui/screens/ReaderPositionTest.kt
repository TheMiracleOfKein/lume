package com.lume.app.ui.screens

import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionTest {
    private fun chapter(id: String) = Chapter(id = id, title = "Chapter $id", url = id)

    private fun loaded(id: String, pages: Int) =
        LoadedChapter(chapter(id), List(pages) { ComicPage(it, "https://img/$id/$it.jpg") })

    // c1: strip 0..2, divider 3, c2: 4..7, divider 8, c3: 9..10
    private val strip = buildReaderStrip(listOf(loaded("c1", 3), loaded("c2", 4), loaded("c3", 2)))

    @Test
    fun stripHasDividersOnlyBetweenChapters() {
        assertEquals(3 + 1 + 4 + 1 + 2, strip.size)
        assertTrue(strip[3] is ReaderStripItem.Divider)
        assertTrue(strip[8] is ReaderStripItem.Divider)
        assertTrue(strip.first() is ReaderStripItem.Page)
    }

    @Test
    fun stripIndexAcrossSeveralChapters() {
        assertEquals(0, stripIndexOf(strip, "c1", 0))
        assertEquals(2, stripIndexOf(strip, "c1", 2))
        assertEquals(4, stripIndexOf(strip, "c2", 0))
        assertEquals(6, stripIndexOf(strip, "c2", 2))
        assertEquals(10, stripIndexOf(strip, "c3", 1))
        assertEquals(-1, stripIndexOf(strip, "missing", 0))
    }

    @Test
    fun stripIndexClampsOutOfRangePages() {
        assertEquals(7, stripIndexOf(strip, "c2", 99))
        assertEquals(4, stripIndexOf(strip, "c2", -3))
    }

    @Test
    fun trailingSlashChapterIdsMatch() {
        assertEquals(5, stripIndexOf(strip, "c2/", 1))
        assertTrue(chapterIdsMatch("/ch/1/", "/ch/1"))
        assertFalse(chapterIdsMatch("/ch/1", "/ch/2"))
    }

    @Test
    fun anchorAtIsTheInverseOfStripIndexOf() {
        for (i in strip.indices) {
            val item = strip[i]
            if (item !is ReaderStripItem.Page) continue
            val anchor = anchorAt(strip, i, 0.25f)!!
            assertEquals(i, stripIndexOf(strip, anchor))
            assertEquals(0.25f, anchor.pageOffset, 0f)
        }
        assertNull(anchorAt(strip, 99))
    }

    @Test
    fun dividerResolvesToEndOfPreviousChapterNotNextChapterStart() {
        val anchor = anchorAt(strip, 3)!!
        assertEquals(ReaderAnchor("c1", 2, 1f), anchor)
        val second = anchorAt(strip, 8)!!
        assertEquals(ReaderAnchor("c2", 3, 1f), second)
    }

    @Test
    fun offsetComesFromFirstVisibleItem() {
        // Page 5 is 2000 px tall and 500 px of it has scrolled above the viewport.
        val visible = listOf(VisibleItem(5, -500, 2000), VisibleItem(6, 1500, 2000))
        assertEquals(ReaderAnchor("c2", 1, 0.25f), anchorFrom(visible, strip))
    }

    @Test
    fun dividerAtTopIsSavedAsFinishedPreviousChapter() {
        val visible = listOf(VisibleItem(3, -10, 80), VisibleItem(4, 70, 2000))
        assertEquals(ReaderAnchor("c1", 2, 1f), anchorFrom(visible, strip))
    }

    @Test
    fun offsetIsClampedAndEmptyLayoutGivesNoAnchor() {
        assertEquals(1f, anchorFrom(listOf(VisibleItem(1, -3000, 2000)), strip)!!.pageOffset, 0f)
        assertEquals(0f, anchorFrom(listOf(VisibleItem(1, 40, 2000)), strip)!!.pageOffset, 0f)
        assertNull(anchorFrom(emptyList(), strip))
    }

    @Test
    fun chapterFinishesWhenMostOfLastPageWasSeen() {
        val viewportEnd = 2000
        // Last page of c1 (index 2): 1000 px, top at 1200 → only 80 % visible.
        assertTrue(finishedChapters(listOf(VisibleItem(2, 1200, 1000)), viewportEnd, strip).isEmpty())
        // Top at 1050 → 95 % visible.
        assertEquals(setOf("c1"), finishedChapters(listOf(VisibleItem(2, 1050, 1000)), viewportEnd, strip))
        // A visible divider also finishes the chapter before it.
        assertEquals(setOf("c2"), finishedChapters(listOf(VisibleItem(8, 300, 80)), viewportEnd, strip))
    }

    @Test
    fun anchorAtChapterEnd() {
        assertTrue(isAtChapterEnd(ReaderAnchor("c1", 2, 0.95f), 3))
        assertFalse(isAtChapterEnd(ReaderAnchor("c1", 2, 0.5f), 3))
        assertFalse(isAtChapterEnd(ReaderAnchor("c1", 1, 1f), 3))
        assertFalse(isAtChapterEnd(ReaderAnchor("c1", 0, 1f), 0))
    }

    @Test
    fun aspectsRoundTrip() {
        val aspects = mapOf(0 to 0.5f, 2 to 0.125f)
        val json = encodeAspects(aspects, pageCount = 4)
        assertEquals("[0.5000,0,0.1250,0]", json)
        assertEquals(aspects, decodeAspects(json))
        assertEquals(emptyMap<Int, Float>(), decodeAspects("[]"))
        assertEquals(emptyMap<Int, Float>(), decodeAspects(null))
        assertEquals("[]", encodeAspects(emptyMap(), 0))
    }
}
