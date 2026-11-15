package com.lume.app.data.local

import com.lume.app.data.local.pdf.PdfRendererPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFitSizeTest {
    @Test
    fun `normal page is doubled up to the width cap`() {
        assertEquals(1200 to 1600, PdfRendererPool.fitSize(600, 800, 2000))
        assertEquals(2000 to 2666, PdfRendererPool.fitSize(1200, 1600, 2000))
    }

    @Test
    fun `very tall page stays under the pixel cap and keeps its ratio`() {
        val (w, h) = PdfRendererPool.fitSize(600, 20000, 2000)
        assertTrue(w.toLong() * h <= PdfRendererPool.MAX_PIXELS)
        assertTrue(w >= 1 && h >= 1)
        assertEquals(20000.0 / 600, h.toDouble() / w, 0.05)
    }
}
