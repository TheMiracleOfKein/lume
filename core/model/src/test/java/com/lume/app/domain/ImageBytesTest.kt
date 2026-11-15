package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageBytesTest {
    private fun bytes(vararg head: Int, text: String = "") =
        (head.map { it.toByte() } + text.toByteArray().toList() + List(16) { 0.toByte() }).toByteArray()

    @Test
    fun recognisesCommonFormats() {
        assertEquals("jpg", ImageBytes.extension(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("png", ImageBytes.extension(bytes(0x89, text = "PNG\r\n")))
        assertEquals("gif", ImageBytes.extension(bytes(text = "GIF89a")))
        assertEquals("webp", ImageBytes.extension(bytes(text = "RIFF\u0000\u0000\u0000\u0000WEBP")))
        assertEquals("avif", ImageBytes.extension(bytes(0, 0, 0, 0x1C, text = "ftypavif")))
    }

    @Test
    fun rejectsHtmlAndShortFiles() {
        assertNull(ImageBytes.extension("<!DOCTYPE html><html>Just a moment...</html>".toByteArray()))
        assertNull(ImageBytes.extension(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
    }

    @Test
    fun comicInfoRoundTrip() {
        val info = ComicInfo(
            series = "Tom & Jerry <1>",
            title = "Chapter 2",
            number = "2",
            writer = listOf("A", "B"),
            genre = listOf("Comedy"),
            pageCount = 20,
            status = "Ongoing",
        )
        val parsed = parseComicInfo(info.toXml())!!
        assertEquals("Tom & Jerry <1>", parsed.series)
        assertEquals(listOf("A", "B"), parsed.writer)
        assertEquals(20, parsed.pageCount)
        assertEquals("Ongoing", parsed.status)
        assertNull(parseComicInfo("<ComicInfo></ComicInfo>"))
    }
}
