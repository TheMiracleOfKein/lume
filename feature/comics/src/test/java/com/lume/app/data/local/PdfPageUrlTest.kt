package com.lume.app.data.local

import com.lume.app.data.local.pdf.PdfPageRef
import com.lume.app.data.local.pdf.PdfPageUrl
import com.lume.app.domain.pageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfPageUrlTest {
    @Test
    fun `a page url round-trips paths with spaces, hashes and unicode`() {
        for (path in listOf("/data/user/0/com.lume.app/files/local/Series/Vol 1.pdf", "/a/b#c/d?e&f.pdf", "/data/Манга/Глава 1.pdf")) {
            val url = PdfPageUrl.build(path, 7)
            assertTrue(url.startsWith("lume-pdf://"))
            assertEquals(PdfPageRef(path, 7), PdfPageUrl.parse(url))
        }
    }

    @Test
    fun `other urls are not pdf pages`() {
        assertNull(PdfPageUrl.parse("https://example.com/a.png#1"))
        assertNull(PdfPageUrl.parse("lume-pdf://nohash"))
        assertNull(PdfPageUrl.parse("lume-pdf://%2Fa.pdf#x"))
        assertNull(PdfPageUrl.parse("lume-pdf://%2Fa.pdf#-1"))
        assertFalse(PdfPageUrl.isPdfPage("/data/a.pdf"))
    }

    @Test
    fun `page data keeps web and pdf urls as text and turns paths into files`() {
        assertEquals("https://x/y.jpg", pageData("https://x/y.jpg"))
        val pdf = PdfPageUrl.build("/a.pdf", 0)
        assertEquals(pdf, pageData(pdf))
        assertEquals(File("/data/p.png"), pageData("/data/p.png"))
    }
}
