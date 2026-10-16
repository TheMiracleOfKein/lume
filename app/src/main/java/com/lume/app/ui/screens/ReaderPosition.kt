package com.lume.app.ui.screens

import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage

/**
 * Pure position math for the reader: no Compose, no Android, so it is unit-tested on the JVM.
 *
 * The reader shows a *strip*: the pages of one or more loaded chapters, with a divider between
 * chapters. Positions are stored chapter-relative ([ReaderAnchor]) and mapped to strip indexes
 * only when a list or pager is built, so a strip with several chapters never confuses the two.
 */

/** A spot inside a chapter: the page and how far (0..1 of its height) it is scrolled past. */
data class ReaderAnchor(
    val chapterId: String,
    val pageIndex: Int,
    val pageOffset: Float = 0f,
)

data class LoadedChapter(
    val chapter: Chapter,
    val pages: List<ComicPage>,
)

sealed class ReaderStripItem {
    /** Shown before [chapter]'s first page; [previous] is the chapter that just ended. */
    data class Divider(val chapter: Chapter, val previous: Chapter, val previousPageCount: Int) : ReaderStripItem()

    data class Page(
        val chapter: Chapter,
        val page: ComicPage,
        val indexInChapter: Int,
        val chapterPageCount: Int,
    ) : ReaderStripItem()
}

/** One laid-out item of the list, in pixels relative to the viewport start. */
data class VisibleItem(val index: Int, val offset: Int, val size: Int)

fun chapterIdsMatch(a: String, b: String): Boolean =
    a == b || a.trimEnd('/') == b.trimEnd('/')

fun buildReaderStrip(loaded: List<LoadedChapter>): List<ReaderStripItem> {
    val out = ArrayList<ReaderStripItem>(loaded.sumOf { it.pages.size + 1 })
    loaded.forEachIndexed { index, lc ->
        if (index > 0) {
            val prev = loaded[index - 1]
            out += ReaderStripItem.Divider(lc.chapter, prev.chapter, prev.pages.size)
        }
        lc.pages.forEachIndexed { pi, page ->
            out += ReaderStripItem.Page(lc.chapter, page, pi, lc.pages.size)
        }
    }
    return out
}

/**
 * Strip index of page [pageIndex] of [chapterId] (clamped to that chapter's pages), or -1 when
 * the chapter is not in the strip.
 */
fun stripIndexOf(strip: List<ReaderStripItem>, chapterId: String, pageIndex: Int): Int {
    var first = -1
    var last = -1
    strip.forEachIndexed { i, item ->
        if (item is ReaderStripItem.Page && chapterIdsMatch(item.chapter.id, chapterId)) {
            if (first < 0) first = i
            last = i
            if (item.indexInChapter == pageIndex) return i
        }
    }
    if (first < 0) return -1
    return if (pageIndex <= 0) first else last
}

fun stripIndexOf(strip: List<ReaderStripItem>, anchor: ReaderAnchor): Int =
    stripIndexOf(strip, anchor.chapterId, anchor.pageIndex)

/**
 * Inverse of [stripIndexOf]. A divider means "the previous chapter is finished": it resolves to
 * that chapter's last page, fully scrolled past — never to "next chapter, page 0".
 */
fun anchorAt(strip: List<ReaderStripItem>, index: Int, offset: Float = 0f): ReaderAnchor? =
    when (val item = strip.getOrNull(index)) {
        is ReaderStripItem.Page -> ReaderAnchor(item.chapter.id, item.indexInChapter, offset.coerceIn(0f, 1f))
        is ReaderStripItem.Divider -> ReaderAnchor(item.previous.id, (item.previousPageCount - 1).coerceAtLeast(0), 1f)
        null -> null
    }

/**
 * The anchor for a scrolled list: the first visible item, with `offset = -item.offset / size`
 * (how much of it has scrolled above the viewport).
 */
fun anchorFrom(visible: List<VisibleItem>, strip: List<ReaderStripItem>): ReaderAnchor? {
    val first = visible.firstOrNull { it.index in strip.indices } ?: return null
    val fraction = if (first.size > 0) -first.offset.toFloat() / first.size else 0f
    return anchorAt(strip, first.index, fraction)
}

/**
 * Chapters whose ending has been seen: at least [threshold] of the last page has entered the
 * viewport, or a later chapter's divider is visible. Works for short and very tall last pages.
 */
fun finishedChapters(
    visible: List<VisibleItem>,
    viewportEnd: Int,
    strip: List<ReaderStripItem>,
    threshold: Float = 0.9f,
): Set<String> {
    val out = HashSet<String>()
    for (v in visible) {
        when (val item = strip.getOrNull(v.index)) {
            is ReaderStripItem.Divider -> out += item.previous.id
            is ReaderStripItem.Page -> if (item.indexInChapter == item.chapterPageCount - 1 && v.size > 0) {
                val seen = (viewportEnd - v.offset).toFloat() / v.size
                if (seen >= threshold) out += item.chapter.id
            }
            null -> Unit
        }
    }
    return out
}

/** True when [anchor] sits at the end of a chapter with [pageCount] pages. */
fun isAtChapterEnd(anchor: ReaderAnchor, pageCount: Int, threshold: Float = 0.9f): Boolean =
    pageCount > 0 && anchor.pageIndex >= pageCount - 1 && anchor.pageOffset >= threshold

/** Encodes page aspects (width / height, 0 = unknown) for `chapter_progress.pageAspectsJson`. */
fun encodeAspects(aspects: Map<Int, Float>, pageCount: Int): String {
    val size = maxOf(pageCount, (aspects.keys.maxOrNull() ?: -1) + 1)
    if (size <= 0) return "[]"
    return (0 until size).joinToString(",", "[", "]") { i ->
        val a = aspects[i] ?: 0f
        if (a > 0f) "%.4f".format(java.util.Locale.US, a) else "0"
    }
}

fun decodeAspects(json: String?): Map<Int, Float> {
    val body = json?.trim()?.removePrefix("[")?.removeSuffix("]")?.trim().orEmpty()
    if (body.isEmpty()) return emptyMap()
    val out = HashMap<Int, Float>()
    body.split(',').forEachIndexed { i, raw ->
        val v = raw.trim().toFloatOrNull() ?: return@forEachIndexed
        if (v > 0f) out[i] = v
    }
    return out
}
