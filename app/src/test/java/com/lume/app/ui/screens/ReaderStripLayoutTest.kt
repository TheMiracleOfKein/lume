package com.lume.app.ui.screens

import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.page.PageFit
import com.lume.app.domain.page.PageRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStripLayoutTest {
    private fun chapter(id: String) = Chapter(id = id, title = "Chapter $id", url = id)

    private fun loaded(id: String, pages: Int) =
        LoadedChapter(chapter(id), List(pages) { ComicPage(it, "https://img/$id/$it.jpg") })

    /** Pages listed in [wide] are double pages; the rest are portrait. */
    private fun aspects(vararg wide: Int): (String, Int) -> Float? = { _, i -> if (i in wide) 1.5f else 0.7f }

    private fun ReaderStripItem.label(): String = when (this) {
        is ReaderStripItem.Page -> "${indexInChapter}" + when (part) {
            PagePart.Whole -> ""
            PagePart.LeftHalf -> "L"
            PagePart.RightHalf -> "R"
        }
        is ReaderStripItem.Spread -> "${first.indexInChapter}+${second?.indexInChapter ?: "-"}"
        is ReaderStripItem.Divider -> "|"
    }

    @Test
    fun `wide pages split in reading order`() {
        val ltr = buildReaderStrip(listOf(loaded("c", 4)), StripLayout(splitWide = true, aspect = aspects(1)))
        assertEquals(listOf("0", "1L", "1R", "2", "3"), ltr.map { it.label() })
        val rtl = buildReaderStrip(listOf(loaded("c", 4)), StripLayout(splitWide = true, rightToLeft = true, aspect = aspects(1)))
        assertEquals(listOf("0", "1R", "1L", "2", "3"), rtl.map { it.label() })
    }

    @Test
    fun `unknown aspects are not split`() {
        val strip = buildReaderStrip(listOf(loaded("c", 3)), StripLayout(splitWide = true))
        assertEquals(listOf("0", "1", "2"), strip.map { it.label() })
    }

    @Test
    fun `spreads pair pages after the cover`() {
        val strip = buildReaderStrip(listOf(loaded("c", 6)), StripLayout(spreads = true, aspect = aspects()))
        assertEquals(listOf("0+-", "1+2", "3+4", "5+-"), strip.map { it.label() })
    }

    @Test
    fun `spreads without a lone cover`() {
        val strip = buildReaderStrip(listOf(loaded("c", 5)), StripLayout(spreads = true, coverAlone = false, aspect = aspects()))
        assertEquals(listOf("0+1", "2+3", "4+-"), strip.map { it.label() })
    }

    @Test
    fun `a wide page stands alone and the pairing goes on after it`() {
        val strip = buildReaderStrip(listOf(loaded("c", 6)), StripLayout(spreads = true, aspect = aspects(2)))
        assertEquals(listOf("0+-", "1+-", "2+-", "3+4", "5+-"), strip.map { it.label() })
    }

    @Test
    fun `spreads keep chapter dividers`() {
        val strip = buildReaderStrip(listOf(loaded("a", 3), loaded("b", 3)), StripLayout(spreads = true, aspect = aspects()))
        assertEquals(listOf("0+-", "1+2", "|", "0+-", "1+2"), strip.map { it.label() })
    }

    @Test
    fun `anchors survive a rebuild into halves and spreads`() {
        val plain = buildReaderStrip(listOf(loaded("c", 4)))
        val split = buildReaderStrip(listOf(loaded("c", 4)), StripLayout(splitWide = true, aspect = aspects(1)))
        val spread = buildReaderStrip(listOf(loaded("c", 4)), StripLayout(spreads = true, aspect = aspects()))

        // Page 2 in the plain strip is index 2; after the split it moves to 3; in spreads it is "1+2".
        val anchor = anchorAt(plain, 2)!!
        assertEquals(3, stripIndexOf(split, anchor))
        assertEquals(1, stripIndexOf(spread, anchor))

        // The second half of a split page is offset 0.5, and maps back to that half.
        val secondHalf = anchorAt(split, 2)!!
        assertEquals(1, secondHalf.pageIndex)
        assertEquals(0.5f, secondHalf.pageOffset)
        assertEquals(2, stripIndexOf(split, secondHalf))
        assertEquals(1, stripIndexOf(split, anchorAt(split, 1)!!))

        // A spread is its first page.
        assertEquals(ReaderAnchor("c", 1, 0f), anchorAt(spread, 1))
    }

    @Test
    fun `region placement fills the viewport with the region`() {
        // A 1000 x 2000 viewport, a portrait page (0.5) cropped to its middle 80 %.
        val region = PageRect(0.1f, 0.1f, 0.9f, 0.9f)
        val p = PageFit.placeRegion(1000f, 2000f, 0.5f, region)!!
        // The region (0.8 of the page) fills the viewport exactly, the page is 1/0.8 larger.
        assertEquals(1250f, p.width, 0.5f)
        assertEquals(2500f, p.height, 0.5f)
        assertEquals(-125f, p.left, 0.5f)
        assertEquals(-250f, p.top, 0.5f)
    }

    @Test
    fun `left half of a wide page in portrait`() {
        val p = PageFit.placeRegion(1000f, 2000f, 1.4f, PageRect(0f, 0f, 0.5f, 1f))!!
        // The half is 0.7 wide: fits by width, 1000 x ~1428, centred vertically.
        assertEquals(2000f, p.width, 0.5f)
        assertEquals(0f, p.left, 0.5f)
        assertTrue(p.top > 0f)
    }

    @Test
    fun `intersect regions`() {
        val half = PageRect(0.5f, 0f, 1f, 1f)
        val content = PageRect(0.05f, 0.1f, 0.95f, 0.9f)
        assertEquals(PageRect(0.5f, 0.1f, 0.95f, 0.9f), PageFit.intersect(half, content))
        assertNull(PageFit.intersect(PageRect(0f, 0f, 0.2f, 0.2f), PageRect(0.5f, 0.5f, 1f, 1f)))
    }
}
