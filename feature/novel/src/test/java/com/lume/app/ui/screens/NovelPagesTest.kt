package com.lume.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NovelPagesTest {
    @Test
    fun restoredPageIsNotReportedUntilMoved() {
        assertEquals(false, shouldReportPage(moved = false, restoredPage = 5, page = 5))
        assertEquals(true, shouldReportPage(moved = false, restoredPage = 5, page = 6))
        assertEquals(true, shouldReportPage(moved = true, restoredPage = 5, page = 5))
    }

    /** [lines] lines of 10 px each, [bottom] px below. */
    private fun text(lines: Int, top: Int = 0, bottom: Int = 0, breakBefore: Boolean = false) = PageMeasure.Lines(
        top = top,
        bottom = bottom,
        lineTops = List(lines) { it * 10 },
        lineBottoms = List(lines) { (it + 1) * 10 },
        breakBefore = breakBefore,
    )

    @Test
    fun longPressFindsTheRowUnderTheFinger() {
        val measures = listOf(text(2, bottom = 5), text(3, top = 4))
        val page = paginate(measures, pageHeight = 100).single()
        // Row 0: 0..20, gap 9, row 1: 29..59.
        assertEquals(0, sliceAt(page, measures, 5f))
        assertEquals(null, sliceAt(page, measures, 25f))
        assertEquals(1, sliceAt(page, measures, 30f))
        assertEquals(null, sliceAt(page, measures, 80f))
        assertEquals(0, sliceAt(NovelPage(listOf(PageSlice(0, 0, 1, 0))), listOf(PageMeasure.Whole(false)), 500f))
    }

    @Test
    fun rowsShareAPageWithTheSpaceBetweenThem() {
        val pages = paginate(listOf(text(2, bottom = 5), text(3, top = 4)), pageHeight = 100)
        assertEquals(listOf(NovelPage(listOf(PageSlice(0, 0, 2, 0), PageSlice(1, 0, 3, 9)))), pages)
    }

    @Test
    fun aParagraphGoesOnOverTheNextPage() {
        val pages = paginate(listOf(text(3, bottom = 5), text(6)), pageHeight = 50)
        assertEquals(
            listOf(
                NovelPage(listOf(PageSlice(0, 0, 3, 0), PageSlice(1, 0, 1, 5))),
                NovelPage(listOf(PageSlice(1, 1, 6, 0))),
            ),
            pages,
        )
    }

    @Test
    fun spaceAboveARowIsDroppedAtTheTopOfAPage() {
        val pages = paginate(listOf(text(5, bottom = 8), text(2, top = 20)), pageHeight = 50)
        assertEquals(PageSlice(1, 0, 2, 0), pages[1].slices.first())
    }

    @Test
    fun picturesAndChaptersStartTheirOwnPage() {
        val pages = paginate(
            listOf(text(1), PageMeasure.Whole(), text(1), text(1, breakBefore = true)),
            pageHeight = 100,
        )
        assertEquals(
            listOf(
                NovelPage(listOf(PageSlice(0, 0, 1, 0))),
                NovelPage(listOf(PageSlice(1, 0, 1, 0))),
                NovelPage(listOf(PageSlice(2, 0, 1, 0))),
                NovelPage(listOf(PageSlice(3, 0, 1, 0))),
            ),
            pages,
        )
    }

    @Test
    fun aLineTallerThanThePageStillGetsOne() {
        val tall = PageMeasure.Lines(0, 0, listOf(0), listOf(500))
        val pages = paginate(listOf(text(1), tall, text(1)), pageHeight = 100)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), pages.map { p -> p.slices.map { it.item } })
    }

    @Test
    fun findsThePageOfALine() {
        val pages = paginate(listOf(text(3), text(10), text(2)), pageHeight = 50)
        assertEquals(0, pageOf(pages, 0, 2))
        assertEquals(0, pageOf(pages, 1, 1))
        assertEquals(1, pageOf(pages, 1, 2))
        assertEquals(2, pageOf(pages, 1, 9))
        assertEquals(2, pageOf(pages, 2, 0))
        assertEquals(0, pageOf(emptyList(), 3, 0))
    }

    @Test
    fun fractionsAndLinesRoundTrip() {
        val m = text(10)
        assertEquals(0, lineAtFraction(m, 0f))
        assertEquals(4, lineAtFraction(m, 0.45f))
        assertEquals(9, lineAtFraction(m, 1f))
        assertEquals(0.4f, fractionOfLine(m, 4), 0.0001f)
        assertEquals(4, lineAtFraction(m, fractionOfLine(m, 4)))
    }
}
