package com.lume.app.domain.page

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Page analysis without any model: white borders and comic panels, found from the gutters
// (rows and columns of background colour) of a small grey copy of the page.

/** A page reduced to grey values 0..255, row by row. Small (a few hundred px), built once per page. */
class LumaGrid(val width: Int, val height: Int, private val luma: ByteArray) {
    init {
        require(width > 0 && height > 0 && luma.size == width * height) { "bad grid" }
    }

    operator fun get(x: Int, y: Int): Int = luma[y * width + x].toInt() and 0xFF

    companion object {
        /** From ARGB pixels (as `Bitmap.getPixels` gives them); alpha over white. */
        fun fromArgb(pixels: IntArray, width: Int, height: Int): LumaGrid {
            val out = ByteArray(width * height)
            for (i in out.indices) {
                val p = pixels[i]
                val a = (p ushr 24) and 0xFF
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF
                if (a < 255) {
                    r = (r * a + 255 * (255 - a)) / 255
                    g = (g * a + 255 * (255 - a)) / 255
                    b = (b * a + 255 * (255 - a)) / 255
                }
                out[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
            return LumaGrid(width, height, out)
        }
    }
}

/** A rectangle as fractions (0..1) of the page's width and height. */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    companion object {
        val Full = PageRect(0f, 0f, 1f, 1f)
    }
}

/** Order of panels: rows top to bottom, then within a row left to right, right to left, or not at all. */
enum class PanelOrder { LeftToRight, RightToLeft, Vertical }

/** Tunables shared by border and panel detection. */
data class AnalysisTuning(
    /** A pixel differs from the background when their grey values are further apart than this. */
    val tolerance: Int = 40,
    /** A row or column still counts as blank with this fraction of differing pixels (scan noise). */
    val noise: Float = 0.01f,
    /** Gutters thinner than this fraction of the page side are ignored. */
    val minGutter: Float = 0.006f,
    /** Panels smaller than this fraction of the page area are dropped (text, specks). */
    val minPanelArea: Float = 0.012f,
)

object PageAnalysis {

    /** The page's background grey: the median of its outer rows and columns. */
    fun background(grid: LumaGrid): Int {
        val samples = ArrayList<Int>(2 * (grid.width + grid.height))
        for (x in 0 until grid.width) {
            samples += grid[x, 0]
            samples += grid[x, grid.height - 1]
        }
        for (y in 0 until grid.height) {
            samples += grid[0, y]
            samples += grid[grid.width - 1, y]
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    /**
     * The part of the page inside its plain border, or null when there is nothing worth cropping
     * (no border, or a blank page).
     */
    fun contentBounds(grid: LumaGrid, tuning: AnalysisTuning = AnalysisTuning()): PageRect? {
        val ink = InkMap(grid, background(grid), tuning)
        val box = ink.trim(0, 0, grid.width, grid.height) ?: return null
        if (box.x0 == 0 && box.y0 == 0 && box.x1 == grid.width && box.y1 == grid.height) return null
        val rect = box.toRect(grid)
        // A sliver of content (a page number on a blank page) is not a page to zoom into.
        if (rect.width < 0.2f || rect.height < 0.2f) return null
        // Less than 1 % off every side isn't worth a visible jump.
        if (rect.left < 0.01f && rect.top < 0.01f && rect.right > 0.99f && rect.bottom > 0.99f) return null
        return rect
    }

    /**
     * The page's panels in reading [order]. A page without gutters is one panel (its content, or
     * the whole page). Never empty.
     */
    fun panels(
        grid: LumaGrid,
        order: PanelOrder,
        tuning: AnalysisTuning = AnalysisTuning(),
    ): List<PageRect> {
        val ink = InkMap(grid, background(grid), tuning)
        val content = ink.trim(0, 0, grid.width, grid.height) ?: return listOf(PageRect.Full)
        val minGutterX = max(2, (grid.width * tuning.minGutter).toInt())
        val minGutterY = max(2, (grid.height * tuning.minGutter).toInt())
        val leaves = ArrayList<Box>()
        cut(ink, content, order, minGutterX, minGutterY, depth = 0, out = leaves)
        val pageArea = grid.width.toFloat() * grid.height
        val kept = leaves.filter { it.area / pageArea >= tuning.minPanelArea }
        return if (kept.isEmpty()) listOf(content.toRect(grid)) else kept.map { it.toRect(grid) }
    }

    /** The panel holding the spot at [y] (fraction of the page height, x in the middle), if any. */
    fun panelAt(panels: List<PageRect>, y: Float): PageRect? {
        val spot = y.coerceIn(0f, 1f)
        return panels.firstOrNull { spot in it.top..it.bottom && 0.5f in it.left..it.right }
            ?: panels.firstOrNull { spot in it.top..it.bottom }
            ?: panels.minByOrNull { min(abs(it.top - spot), abs(it.bottom - spot)) }
    }

    /**
     * The panel to show for a saved spot at [y] (fraction of the page height) on a page of
     * [aspect] (width / height): a band of a tall webtoon strip, else a comic panel. Null when
     * no panel says more than the whole page would (no panels found, or a panel much taller than
     * wide, where a slice around the spot shows more).
     */
    fun spotPanel(panels: List<PageRect>, bands: List<PageRect>, aspect: Float, y: Float): PageRect? {
        if (aspect <= 0f) return null
        val strip = aspect < TALL_STRIP_ASPECT
        val panel = panelAt(if (strip) bands else panels, y) ?: return null
        if (panel.area > 0.8f) return null
        // Height over width in real proportions.
        val tallness = (panel.height / aspect) / panel.width
        return panel.takeIf { tallness <= MAX_SPOT_TALLNESS }
    }

    /** Pages narrower than this (width / height) are webtoon strips: panels are horizontal bands. */
    private const val TALL_STRIP_ASPECT = 0.45f

    /** A spot panel more than this many times taller than wide shows less than a slice would. */
    private const val MAX_SPOT_TALLNESS = 2.5f

    private const val MAX_DEPTH = 8

    /** Recursive XY cut: split at full-width gutters into rows, rows at full-height gutters into columns. */
    private fun cut(
        ink: InkMap,
        box: Box,
        order: PanelOrder,
        minGutterX: Int,
        minGutterY: Int,
        depth: Int,
        out: MutableList<Box>,
    ) {
        if (depth >= MAX_DEPTH) {
            out += box
            return
        }
        val rows = ink.splitRows(box, minGutterY)
        if (rows.size > 1) {
            rows.forEach { row -> ink.trim(row)?.let { cut(ink, it, order, minGutterX, minGutterY, depth + 1, out) } }
            return
        }
        if (order == PanelOrder.Vertical) {
            out += box
            return
        }
        val columns = ink.splitColumns(box, minGutterX)
        if (columns.size > 1) {
            val ordered = if (order == PanelOrder.RightToLeft) columns.reversed() else columns
            ordered.forEach { col -> ink.trim(col)?.let { cut(ink, it, order, minGutterX, minGutterY, depth + 1, out) } }
            return
        }
        out += box
    }
}

/** Grid rows [y0, y1) and columns [x0, x1). */
internal data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val area: Float get() = (x1 - x0).toFloat() * (y1 - y0)

    fun toRect(grid: LumaGrid) = PageRect(
        x0.toFloat() / grid.width,
        y0.toFloat() / grid.height,
        x1.toFloat() / grid.width,
        y1.toFloat() / grid.height,
    )
}

/** Which pixels differ from the background, with blank-row and blank-column tests over a box. */
internal class InkMap(grid: LumaGrid, background: Int, private val tuning: AnalysisTuning) {
    private val w = grid.width
    private val ink = BooleanArray(grid.width * grid.height) { i ->
        abs(grid[i % grid.width, i / grid.width] - background) > tuning.tolerance
    }

    private fun rowBlank(y: Int, x0: Int, x1: Int): Boolean {
        val allowed = ((x1 - x0) * tuning.noise).toInt()
        var count = 0
        for (x in x0 until x1) if (ink[y * w + x] && ++count > allowed) return false
        return true
    }

    private fun columnBlank(x: Int, y0: Int, y1: Int): Boolean {
        val allowed = ((y1 - y0) * tuning.noise).toInt()
        var count = 0
        for (y in y0 until y1) if (ink[y * w + x] && ++count > allowed) return false
        return true
    }

    fun trim(box: Box): Box? = trim(box.x0, box.y0, box.x1, box.y1)

    /** The box shrunk to its content; null when it is all background. */
    fun trim(x0: Int, y0: Int, x1: Int, y1: Int): Box? {
        var top = y0
        while (top < y1 && rowBlank(top, x0, x1)) top++
        if (top == y1) return null
        var bottom = y1
        while (bottom > top && rowBlank(bottom - 1, x0, x1)) bottom--
        var left = x0
        while (left < x1 && columnBlank(left, top, bottom)) left++
        var right = x1
        while (right > left && columnBlank(right - 1, top, bottom)) right--
        if (left >= right || top >= bottom) return null
        return Box(left, top, right, bottom)
    }

    /** The box split at blank rows at least [minGutter] tall; one entry when there is none. */
    fun splitRows(box: Box, minGutter: Int): List<Box> {
        val bands = runs(box.y0, box.y1, minGutter) { y -> rowBlank(y, box.x0, box.x1) }
        return bands.map { (a, b) -> Box(box.x0, a, box.x1, b) }
    }

    /** The box split at blank columns at least [minGutter] wide; one entry when there is none. */
    fun splitColumns(box: Box, minGutter: Int): List<Box> {
        val bands = runs(box.x0, box.x1, minGutter) { x -> columnBlank(x, box.y0, box.y1) }
        return bands.map { (a, b) -> Box(a, box.y0, b, box.y1) }
    }

    /** Content stretches between blank runs of at least [minGutter] lines (shorter runs don't split). */
    private inline fun runs(from: Int, to: Int, minGutter: Int, blank: (Int) -> Boolean): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var start = from
        var i = from
        while (i < to) {
            if (!blank(i)) {
                i++
                continue
            }
            var end = i
            while (end < to && blank(end)) end++
            if (end - i >= minGutter && i > start && end < to) {
                out += start to i
                start = end
            }
            i = end
        }
        out += start to to
        return out
    }
}
