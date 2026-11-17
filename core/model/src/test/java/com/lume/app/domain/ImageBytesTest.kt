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

    /** An AVIF header like MangaLib's: ftyp, then an `av1C` box with the given settings bytes. */
    private fun avif(profileAndLevel: Int, depthAndChroma: Int): ByteArray =
        "0000001c66747970617669660000000061766966".hexBytes() +
            "6d6966316d696166".hexBytes() +
            List(40) { 0.toByte() }.toByteArray() +
            "61763143".hexBytes() + byteArrayOf(0x81.toByte(), profileAndLevel.toByte(), depthAndChroma.toByte(), 0) +
            List(16) { 0.toByte() }.toByteArray()

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun flagsAvifThatAndroidCannotDraw() {
        // Professional profile, 12-bit 4:4:4 (One Punch Man on MangaLib): av1C 81 4c 60.
        org.junit.Assert.assertTrue(ImageBytes.isUnsupportedAvif(avif(0x4C, 0x60)))
        // High profile (4:4:4, 8-bit).
        org.junit.Assert.assertTrue(ImageBytes.isUnsupportedAvif(avif(0x28, 0x00)))
        // Main profile, 10-bit 4:2:0: fine.
        org.junit.Assert.assertFalse(ImageBytes.isUnsupportedAvif(avif(0x08, 0x4C)))
        // Not AVIF, or no av1C in the bytes read.
        org.junit.Assert.assertFalse(ImageBytes.isUnsupportedAvif(bytes(0xFF, 0xD8, 0xFF)))
        org.junit.Assert.assertFalse(ImageBytes.isUnsupportedAvif(avif(0x4C, 0x60).copyOf(60)))
    }
}
