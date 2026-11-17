package com.lume.app.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChapterFilesTest {
    private val html = "<p>Первый</p><img loading=\"lazy\" src=\"https://site.test/a.jpg\" />" +
        "<p><img src=\"https://site.test/b.png\"></p><img src=\"https://site.test/a.jpg\"><img src=\"/relative.png\">"

    @Test
    fun remoteImagesInOrderOnce() {
        assertEquals(listOf("https://site.test/a.jpg", "https://site.test/b.png"), TextChapterFiles.remoteImages(html))
    }

    @Test
    fun rewriteReplacesOnlyWhatIsAnswered() {
        val out = TextChapterFiles.rewriteImages(html) { if (it.endsWith("a.jpg")) "img-0000.jpg" else null }
        assertEquals(
            "<p>Первый</p><img loading=\"lazy\" src=\"img-0000.jpg\">" +
                "<p><img src=\"https://site.test/b.png\"></p><img src=\"img-0000.jpg\"><img src=\"/relative.png\">",
            out,
        )
    }

    @Test
    fun imageNames() {
        assertEquals("img-0003.png", TextChapterFiles.imageName(3, "png"))
        assertEquals(3, TextChapterFiles.imageIndex("img-0003.png"))
        assertNull(TextChapterFiles.imageIndex("https://site.test/img-0003.png"))
        assertNull(TextChapterFiles.imageIndex("0003.png"))
        assertTrue(TextChapterFiles.isImageName("img-0000.jpg"))
        assertFalse(TextChapterFiles.isImageName(TextChapterFiles.HTML_NAME))
        assertFalse(TextChapterFiles.isImageName("img-0000.jpg.part"))
    }
}
