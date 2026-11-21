package com.lume.app.ui.screens

/**
 * How one row of the reader lays out on a page, in pixels. [Lines] can be split between pages at
 * any line; [Whole] (a picture) gets a page of its own.
 */
sealed interface PageMeasure {
    /** Starts a new page (a chapter's first row). */
    val breakBefore: Boolean

    /**
     * Text: space above and below it, and where each line starts and ends inside the text.
     * [lineTops] and [lineBottoms] have one entry per line and are never empty.
     */
    data class Lines(
        val top: Int,
        val bottom: Int,
        val lineTops: List<Int>,
        val lineBottoms: List<Int>,
        override val breakBefore: Boolean = false,
    ) : PageMeasure {
        val lineCount: Int get() = lineTops.size
        val height: Int get() = lineBottoms.last()
    }

    data class Whole(override val breakBefore: Boolean = false) : PageMeasure
}

/** Lines [from] until [to] of row [item], with [gap] pixels of space above them on the page. */
data class PageSlice(val item: Int, val from: Int, val to: Int, val gap: Int)

data class NovelPage(val slices: List<PageSlice>)

/**
 * Fills pages of [pageHeight] pixels with [measures] in order, line by line. A row's space above
 * is left out at the top of a page; a line taller than a whole page still gets one to itself.
 */
fun paginate(measures: List<PageMeasure>, pageHeight: Int): List<NovelPage> {
    val pages = ArrayList<NovelPage>()
    var slices = ArrayList<PageSlice>()
    var used = 0
    var pendingBottom = 0

    fun close() {
        if (slices.isNotEmpty()) pages += NovelPage(slices)
        slices = ArrayList()
        used = 0
        pendingBottom = 0
    }

    measures.forEachIndexed { index, m ->
        if (m.breakBefore) close()
        when (m) {
            is PageMeasure.Whole -> {
                close()
                pages += NovelPage(listOf(PageSlice(index, 0, 1, 0)))
            }
            is PageMeasure.Lines -> {
                var line = 0
                while (line < m.lineCount) {
                    val gap = if (slices.isEmpty()) 0 else pendingBottom + if (line == 0) m.top else 0
                    val start = m.lineTops[line]
                    // As many lines as fit below the gap.
                    var end = line
                    while (end < m.lineCount && used + gap + (m.lineBottoms[end] - start) <= pageHeight) end++
                    if (end == line) {
                        if (slices.isEmpty()) {
                            end = line + 1 // too tall for any page: alone on this one
                        } else {
                            close()
                            continue
                        }
                    }
                    slices += PageSlice(index, line, end, gap)
                    used += gap + (m.lineBottoms[end - 1] - start)
                    pendingBottom = 0
                    line = end
                    if (line < m.lineCount) close()
                }
                pendingBottom = m.bottom
            }
        }
    }
    close()
    return pages
}

/** The page that shows line [line] of row [item]; the nearest page before it when none does, else 0. */
fun pageOf(pages: List<NovelPage>, item: Int, line: Int): Int {
    var best = 0
    for ((i, page) in pages.withIndex()) {
        val first = page.slices.first()
        if (first.item > item || (first.item == item && first.from > line)) break
        best = i
    }
    return best
}

/**
 * The row drawn [y] pixels below the top of [page] (slices stack with their gaps, as the page
 * draws them), or null in a gap or below the last slice. A picture fills the page.
 */
fun sliceAt(page: NovelPage, measures: List<PageMeasure>, y: Float): Int? {
    var top = 0f
    for (slice in page.slices) {
        top += slice.gap
        val height = when (val m = measures.getOrNull(slice.item)) {
            is PageMeasure.Lines -> (m.lineBottoms[slice.to - 1] - m.lineTops[slice.from]).toFloat()
            is PageMeasure.Whole -> return slice.item
            null -> return null
        }
        if (y >= top && y < top + height) return slice.item
        top += height
    }
    return null
}

/** After a restore the restored page is not reported; every page is once the reader has moved off it. */
fun shouldReportPage(moved: Boolean, restoredPage: Int, page: Int): Boolean = moved || page != restoredPage

/** The line of [m] that sits [fraction] (0..1) of the way down its text. */
fun lineAtFraction(m: PageMeasure.Lines, fraction: Float): Int {
    val y = fraction.coerceIn(0f, 1f) * m.height
    val i = m.lineBottoms.indexOfFirst { it > y }
    return if (i < 0) m.lineCount - 1 else i
}

/** How far down its text line [line] of [m] starts, 0..1. */
fun fractionOfLine(m: PageMeasure.Lines, line: Int): Float =
    if (m.height <= 0) 0f else (m.lineTops[line.coerceIn(0, m.lineCount - 1)].toFloat() / m.height).coerceIn(0f, 1f)
