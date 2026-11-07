package com.lume.app.domain.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PageAnalysisTest {

    /** A white page of [w] x [h] with grey-level [ink] rectangles (grid coordinates, end exclusive). */
    private fun page(w: Int, h: Int, background: Int = 255, vararg boxes: IntArray, ink: Int = 30): LumaGrid {
        val px = IntArray(w * h) { argb(background) }
        boxes.forEach { (x0, y0, x1, y1) ->
            for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = argb(ink)
        }
        return LumaGrid.fromArgb(px, w, h)
    }

    private fun argb(grey: Int) = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey

    private fun box(x0: Int, y0: Int, x1: Int, y1: Int) = intArrayOf(x0, y0, x1, y1)

    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, 0.011f)

    @Test
    fun `content bounds inside a white border`() {
        val grid = page(200, 300, boxes = arrayOf(box(20, 30, 180, 270)))
        val rect = PageAnalysis.contentBounds(grid)!!
        close(0.1f, rect.left)
        close(0.1f, rect.top)
        close(0.9f, rect.right)
        close(0.9f, rect.bottom)
    }

    @Test
    fun `no crop for a page without a border or a blank page`() {
        assertNull(PageAnalysis.contentBounds(page(100, 100, boxes = arrayOf(box(0, 0, 100, 100)))))
        assertNull(PageAnalysis.contentBounds(page(100, 100)))
    }

    @Test
    fun `black border is cropped too`() {
        val grid = page(200, 200, background = 0, boxes = arrayOf(box(40, 40, 160, 160)), ink = 220)
        val rect = PageAnalysis.contentBounds(grid)!!
        close(0.2f, rect.left)
        close(0.8f, rect.bottom)
    }

    @Test
    fun `scan noise in the border is ignored`() {
        val w = 300
        val h = 300
        val px = IntArray(w * h) { argb(255) }
        for (y in 30 until 270) for (x in 30 until 270) px[y * w + x] = argb(20)
        val random = Random(7)
        repeat(200) { px[random.nextInt(w * h)] = argb(0) }
        val rect = PageAnalysis.contentBounds(LumaGrid.fromArgb(px, w, h))!!
        close(0.1f, rect.left)
        close(0.9f, rect.right)
    }

    /** Two rows: one wide panel on top, two panels below. */
    private val twoRowPage = page(
        200, 300,
        boxes = arrayOf(box(10, 10, 190, 140), box(10, 150, 95, 290), box(105, 150, 190, 290)),
    )

    @Test
    fun `panels left to right`() {
        val panels = PageAnalysis.panels(twoRowPage, PanelOrder.LeftToRight)
        assertEquals(3, panels.size)
        close(0.05f, panels[0].left); close(0.95f, panels[0].right)
        close(0.05f, panels[1].left)
        close(0.525f, panels[2].left)
    }

    @Test
    fun `panels right to left`() {
        val panels = PageAnalysis.panels(twoRowPage, PanelOrder.RightToLeft)
        assertEquals(3, panels.size)
        close(0.525f, panels[1].left)
        close(0.05f, panels[2].left)
    }

    @Test
    fun `two by three grid in reading order`() {
        val boxes = buildList {
            for (row in 0 until 3) for (col in 0 until 2) {
                add(box(10 + col * 95, 10 + row * 95, 95 + col * 95, 95 + row * 95))
            }
        }
        val panels = PageAnalysis.panels(page(200, 300, boxes = boxes.toTypedArray()), PanelOrder.LeftToRight)
        assertEquals(6, panels.size)
        // Rows go down, and within a row left comes before right.
        for (i in 1 until panels.size) {
            val prev = panels[i - 1]
            val cur = panels[i]
            assertTrue(cur.top > prev.top || (cur.top == prev.top && cur.left > prev.left))
        }
    }

    @Test
    fun `vertical order only cuts into bands`() {
        val panels = PageAnalysis.panels(twoRowPage, PanelOrder.Vertical)
        assertEquals(2, panels.size)
        close(0.5f, panels[1].top)
    }

    @Test
    fun `page without gutters is one panel`() {
        val panels = PageAnalysis.panels(page(100, 100, boxes = arrayOf(box(5, 5, 95, 95))), PanelOrder.LeftToRight)
        assertEquals(1, panels.size)
        close(0.05f, panels[0].left)
        assertEquals(listOf(PageRect.Full), PageAnalysis.panels(page(50, 50), PanelOrder.LeftToRight))
    }

    @Test
    fun `L-shaped panels stay one region instead of breaking`() {
        // An L across the left and bottom, a square in the top right: no straight gutter crosses the page.
        val grid = page(
            200, 200,
            boxes = arrayOf(box(10, 10, 90, 190), box(90, 110, 190, 190), box(100, 10, 190, 100)),
        )
        val panels = PageAnalysis.panels(grid, PanelOrder.LeftToRight)
        assertTrue(panels.isNotEmpty())
        assertTrue(panels.all { it.area > 0f })
    }

    @Test
    fun `specks are not panels`() {
        val grid = page(200, 200, boxes = arrayOf(box(10, 10, 190, 150), box(95, 180, 99, 184)))
        val panels = PageAnalysis.panels(grid, PanelOrder.LeftToRight)
        assertEquals(1, panels.size)
    }

    @Test
    fun `spot panel on a manga page and a webtoon strip`() {
        val panels = PageAnalysis.panels(twoRowPage, PanelOrder.LeftToRight)
        val bands = PageAnalysis.panels(twoRowPage, PanelOrder.Vertical)
        // A portrait page: the comic panel under the spot.
        assertEquals(panels[0], PageAnalysis.spotPanel(panels, bands, aspect = 0.66f, y = 0.2f))
        // A tall strip: the horizontal band under the spot.
        val stripBands = listOf(PageRect(0f, 0f, 1f, 0.05f), PageRect(0f, 0.06f, 1f, 0.1f))
        assertEquals(stripBands[1], PageAnalysis.spotPanel(emptyList(), stripBands, aspect = 0.06f, y = 0.08f))
    }

    @Test
    fun `no spot panel when it says no more than the page`() {
        // The only panel is nearly the whole page.
        assertNull(PageAnalysis.spotPanel(listOf(PageRect(0.02f, 0.02f, 0.98f, 0.98f)), emptyList(), 0.7f, 0.5f))
        // A band of a strip that is far taller than wide: a slice shows more.
        assertNull(PageAnalysis.spotPanel(emptyList(), listOf(PageRect(0f, 0f, 1f, 0.6f)), aspect = 0.06f, y = 0.3f))
        assertNull(PageAnalysis.spotPanel(emptyList(), emptyList(), 0.7f, 0.5f))
    }

    @Test
    fun `panel at a spot`() {
        val panels = PageAnalysis.panels(twoRowPage, PanelOrder.Vertical)
        assertEquals(panels[0], PageAnalysis.panelAt(panels, 0.2f))
        assertEquals(panels[1], PageAnalysis.panelAt(panels, 0.8f))
        // In the gutter: the nearest band.
        assertNotNull(PageAnalysis.panelAt(panels, 0.48f))
    }
}
