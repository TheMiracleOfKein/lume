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
