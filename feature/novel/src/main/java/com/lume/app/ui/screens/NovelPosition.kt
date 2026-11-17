package com.lume.app.ui.screens

import com.lume.app.data.novel.NovelBlock
import com.lume.app.domain.Chapter
import kotlin.math.roundToInt

/** A spot in a chapter: the [block] at the top of the screen and how far [fraction] (0..1) it is scrolled. */
data class NovelAnchor(val chapterId: String, val block: Int, val fraction: Float)

/** A chapter whose text is loaded into the reader. */
class LoadedNovelChapter(val chapter: Chapter, val blocks: List<NovelBlock>)

/** One row of the reader's list: a chapter's title, or one of its blocks. */
sealed interface NovelItem {
    /** Stable across appending a chapter, so the list keeps its place. */
    val key: String

    data class Header(val chapter: Chapter) : NovelItem {
        override val key: String get() = "h:${chapter.id}"
    }

    data class Block(val chapterId: String, val index: Int, val block: NovelBlock) : NovelItem {
        override val key: String get() = "b:$chapterId:$index"
    }
}

/** The list rows for the loaded chapters in order: each chapter's title, then its blocks. */
fun buildNovelItems(loaded: List<LoadedNovelChapter>): List<NovelItem> {
    val out = ArrayList<NovelItem>(loaded.sumOf { it.blocks.size + 1 })
    for (lc in loaded) {
        out += NovelItem.Header(lc.chapter)
        lc.blocks.forEachIndexed { i, block -> out += NovelItem.Block(lc.chapter.id, i, block) }
    }
    return out
}

/**
 * The row an [anchor] points to, or -1 when its chapter isn't in [items]. The top of a chapter
 * (block 0, not scrolled) is its title row, so opening a chapter shows the title.
 */
fun novelItemIndex(items: List<NovelItem>, anchor: NovelAnchor): Int {
    val header = items.indexOfFirst { it is NovelItem.Header && it.chapter.id == anchor.chapterId }
    if (header < 0) return -1
    if (anchor.block <= 0 && anchor.fraction <= 0f) return header
    val blockRows = items.indexOfFirst { it is NovelItem.Block && it.chapterId == anchor.chapterId && it.index == anchor.block }
    return if (blockRows >= 0) blockRows else header
}

/**
 * The anchor for the row at [index] when it is scrolled [offsetPx] past the top of the screen and
 * is [sizePx] tall; null when the index is outside the list.
 */
fun novelAnchorAt(items: List<NovelItem>, index: Int, offsetPx: Int, sizePx: Int): NovelAnchor? =
    when (val item = items.getOrNull(index)) {
        is NovelItem.Header -> NovelAnchor(item.chapter.id, 0, 0f)
        is NovelItem.Block -> NovelAnchor(item.chapterId, item.index, scrollFraction(offsetPx, sizePx))
        null -> null
    }

/** How much of a row of [sizePx] is scrolled past the top, 0..1. */
fun scrollFraction(offsetPx: Int, sizePx: Int): Float =
    if (sizePx <= 0) 0f else (offsetPx.toFloat() / sizePx).coerceIn(0f, 1f)

/** The pixel offset that puts [fraction] of a row of [sizePx] above the top of the screen. */
fun restoreOffsetPx(fraction: Float, sizePx: Int): Int = (fraction.coerceIn(0f, 1f) * sizePx).roundToInt()

/** The last row of [chapterId] in [items], or -1. */
fun chapterEndIndex(items: List<NovelItem>, chapterId: String): Int {
    val first = items.indexOfFirst { it is NovelItem.Header && it.chapter.id == chapterId }
    if (first < 0) return -1
    var end = first
    while (end + 1 < items.size && items[end + 1] is NovelItem.Block) end++
    return end
}

/** Progress through a chapter of [count] blocks, 0..100. */
fun chapterPercent(block: Int, fraction: Float, count: Int): Int {
    if (count <= 0) return 0
    return (((block + fraction) / count) * 100f).toInt().coerceIn(0, 100)
}
