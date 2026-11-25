package com.lume.app.ui.screens

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import com.lume.app.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovelPositionTest {
    private fun chapter(id: String) = Chapter(id = id, title = "Title $id", url = id)
    private fun paragraphs(n: Int) = List(n) { NovelBlock.Paragraph(listOf(Span("p$it"))) }

    private val items = buildNovelItems(
        listOf(LoadedNovelChapter(chapter("a"), paragraphs(3)), LoadedNovelChapter(chapter("b"), paragraphs(2))),
    )

    @Test
    fun itemsAreATitleThenTheBlocksOfEachChapter() {
        assertEquals(7, items.size)
        assertEquals(listOf("h:a", "b:a:0", "b:a:1", "b:a:2", "h:b", "b:b:0", "b:b:1"), items.map { it.key })
    }

    @Test
    fun aChapterThatOpensWithItsOwnHeadingHasNoTitleRow() {
        val heading = listOf(NovelBlock.Heading(1, listOf(Span("TITLE A: the start"))), NovelBlock.Paragraph(listOf(Span("p"))))
        val withHeading = buildNovelItems(
            listOf(
                LoadedNovelChapter(Chapter(id = "a", title = "title a", url = "a"), heading),
                LoadedNovelChapter(chapter("b"), paragraphs(1)),
            ),
        )
        assertEquals(listOf("b:a:0", "b:a:1", "h:b", "b:b:0"), withHeading.map { it.key })
        assertEquals(0, novelItemIndex(withHeading, NovelAnchor("a", 0, 0f)))
        assertEquals(1, novelItemIndex(withHeading, NovelAnchor("a", 1, 0f)))
        assertEquals(2, novelItemIndex(withHeading, NovelAnchor("b", 0, 0f)))
        assertEquals(1, chapterEndIndex(withHeading, "a"))
        assertEquals(3, chapterEndIndex(withHeading, "b"))
        assertEquals(NovelAnchor("a", 0, 0f), novelAnchorAt(withHeading, 0, 0, 100))
    }

    @Test
    fun aDifferentFirstHeadingKeepsTheTitleRow() {
        val other = listOf(NovelBlock.Heading(2, listOf(Span("Something else"))))
        assertEquals(listOf("h:a", "b:a:0"), buildNovelItems(listOf(LoadedNovelChapter(chapter("a"), other))).map { it.key })
        val notFirst = listOf(NovelBlock.Paragraph(listOf(Span("x"))), NovelBlock.Heading(2, listOf(Span("Title a"))))
        assertEquals(3, buildNovelItems(listOf(LoadedNovelChapter(chapter("a"), notFirst))).size)
    }

    @Test
    fun hiddenTitlesLeaveOutTheTitleRowAndTheTextsOwnTitle() {
        val heading = listOf(NovelBlock.Heading(1, listOf(Span("Chapter 1: Title a"))), NovelBlock.Paragraph(listOf(Span("p"))))
        val hidden = buildNovelItems(
            listOf(
                LoadedNovelChapter(chapter("a"), heading),
                LoadedNovelChapter(chapter("b"), paragraphs(2)),
            ),
            showTitles = false,
        )
        // Block indices stay the book's own: the hidden heading is block 0 of "a".
        assertEquals(listOf("b:a:1", "b:b:0", "b:b:1"), hidden.map { it.key })
        assertEquals(0, chapterStartIndex(hidden, "a"))
        assertEquals(0, novelItemIndex(hidden, NovelAnchor("a", 0, 0f)))
        assertEquals(1, novelItemIndex(hidden, NovelAnchor("b", 0, 0f)))
        assertEquals(2, novelItemIndex(hidden, NovelAnchor("b", 1, 0.5f)))
        assertEquals(0, chapterEndIndex(hidden, "a"))
    }

    @Test
    fun hiddenTitlesKeepAChapterThatIsOnlyItsHeading() {
        val only = listOf(NovelBlock.Heading(1, listOf(Span("Title a"))))
        assertEquals(listOf("b:a:0"), buildNovelItems(listOf(LoadedNovelChapter(chapter("a"), only)), showTitles = false).map { it.key })
        // An empty chapter keeps its title row, so it still has a place in the list.
        assertEquals(listOf("h:a"), buildNovelItems(listOf(LoadedNovelChapter(chapter("a"), emptyList())), showTitles = false).map { it.key })
    }

    @Test
    fun hiddenTitlesKeepOtherHeadings() {
        val other = listOf(NovelBlock.Heading(2, listOf(Span("Something else"))), NovelBlock.Paragraph(listOf(Span("p"))))
        assertEquals(listOf("b:a:0", "b:a:1"), buildNovelItems(listOf(LoadedNovelChapter(chapter("a"), other)), showTitles = false).map { it.key })
    }

    @Test
    fun topOfAChapterIsItsTitleRow() {
        assertEquals(0, novelItemIndex(items, NovelAnchor("a", 0, 0f)))
        assertEquals(4, novelItemIndex(items, NovelAnchor("b", 0, 0f)))
    }

    @Test
    fun blockAndFractionPointAtTheBlockRow() {
        assertEquals(3, novelItemIndex(items, NovelAnchor("a", 2, 0f)))
        assertEquals(1, novelItemIndex(items, NovelAnchor("a", 0, 0.5f)))
        assertEquals(6, novelItemIndex(items, NovelAnchor("b", 1, 0.9f)))
    }

    @Test
    fun missingChapterOrBlockFallBack() {
        assertEquals(-1, novelItemIndex(items, NovelAnchor("zzz", 0, 0f)))
        assertEquals(4, novelItemIndex(items, NovelAnchor("b", 50, 0f)))
    }

    @Test
    fun rowAndOffsetBecomeAnAnchor() {
        assertEquals(NovelAnchor("a", 0, 0f), novelAnchorAt(items, 0, 120, 200))
        assertEquals(NovelAnchor("a", 1, 0.25f), novelAnchorAt(items, 2, 50, 200))
        assertEquals(NovelAnchor("b", 1, 0f), novelAnchorAt(items, 6, 0, 80))
        assertNull(novelAnchorAt(items, 99, 0, 80))
    }

    @Test
    fun anchorRoundTripsThroughTheListWithinAPixel() {
        val sizePx = 333
        val anchor = NovelAnchor("a", 1, 0.37f)
        val index = novelItemIndex(items, anchor)
        val offset = restoreOffsetPx(anchor.fraction, sizePx)
        val back = novelAnchorAt(items, index, offset, sizePx)!!
        assertEquals(anchor.chapterId, back.chapterId)
        assertEquals(anchor.block, back.block)
        assertEquals(anchor.fraction, back.fraction, 1f / sizePx)
    }

    @Test
    fun fractionIsClampedAndSafeForZeroSize() {
        assertEquals(0f, scrollFraction(10, 0), 0f)
        assertEquals(1f, scrollFraction(500, 100), 0f)
        assertEquals(0f, scrollFraction(-5, 100), 0f)
        assertEquals(100, restoreOffsetPx(2f, 100))
    }

    @Test
    fun chapterEndIsItsLastRow() {
        assertEquals(3, chapterEndIndex(items, "a"))
        assertEquals(6, chapterEndIndex(items, "b"))
        assertEquals(-1, chapterEndIndex(items, "zzz"))
    }

    @Test
    fun percentRunsFromZeroToOneHundred() {
        assertEquals(0, chapterPercent(0, 0f, 10))
        assertEquals(50, chapterPercent(5, 0f, 10))
        assertEquals(55, chapterPercent(5, 0.5f, 10))
        assertEquals(100, chapterPercent(9, 1f, 10))
        assertEquals(0, chapterPercent(3, 0.5f, 0))
        assertEquals(100, chapterPercent(50, 0f, 10))
    }
}
