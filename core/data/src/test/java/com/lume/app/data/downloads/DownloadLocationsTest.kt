package com.lume.app.data.downloads

import com.lume.app.domain.isContentUri
import com.lume.app.domain.pageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadLocationsTest {
    @Test
    fun tellsContentUrisFromFilePaths() {
        assertTrue(isContentUri("content://com.android.externalstorage.documents/tree/1234-5678%3AComics/document/1234-5678%3AComics%2F0000.jpg"))
        assertFalse(isContentUri("/data/user/0/com.lume.app/files/downloads/s/t/c/0000.jpg"))
        assertFalse(isContentUri("https://example.org/a.jpg"))
        assertFalse(isContentUri("file:///sdcard/a.jpg"))
        assertFalse(isContentUri(""))
    }

    @Test
    fun pageDataKeepsContentUrisAsStringsAndFilePathsAsFiles() {
        val uri = "content://provider/tree/x/document/y"
        assertEquals(uri, pageData(uri))
        assertEquals(File("/a/b/0000.jpg"), pageData("/a/b/0000.jpg"))
        assertEquals("https://example.org/a.jpg", pageData("https://example.org/a.jpg"))
    }

    @Test
    fun layoutUsesSafeNamesUnderTheLumeFolder() {
        assertEquals(listOf("Lume", "mangadex", "title_1"), DownloadLocations.titleSegments("mangadex", "title 1"))
        assertEquals(
            listOf("Lume", "src", "a_b", "ch_1"),
            DownloadLocations.chapterSegments("src", "a/b", "ch:1"),
        )
    }

    @Test
    fun layoutNeverEscapesTheFolder() {
        val segments = DownloadLocations.chapterSegments("..", ".", "../..")
        assertTrue(segments.none { it == "." || it == ".." || it.contains('/') })
    }

    @Test
    fun partFolderIsMarkedAndRecognised() {
        val part = DownloadLocations.partName("ch 1")
        assertEquals("ch_1.lume-part", part)
        assertTrue(DownloadLocations.isPartName(part))
        assertFalse(DownloadLocations.isPartName("ch_1"))
    }

    @Test
    fun pageNamesSortInReadingOrder() {
        assertEquals("0000.jpg", DownloadLocations.pageName(0, "jpg"))
        assertEquals("0012.webp", DownloadLocations.pageName(12, "webp"))
        assertEquals("12345.png", DownloadLocations.pageName(12345, "png"))
    }

    @Test
    fun metadataAndLeftoversAreNotPages() {
        assertTrue(DownloadLocations.isPageName("0000.jpg"))
        assertTrue(DownloadLocations.isPageName("chapter.cbz"))
        assertFalse(DownloadLocations.isPageName("meta.json"))
        assertFalse(DownloadLocations.isPageName("chapter.cbz.part"))
    }

    @Test
    fun documentBelongsToItsTreeOnly() {
        val tree = "content://auth/tree/primary%3AComics"
        assertTrue(DownloadLocations.isInTree("$tree/document/primary%3AComics%2FLume%2Fa.jpg", tree))
        assertFalse(DownloadLocations.isInTree("content://auth/tree/primary%3AComics2/document/x", tree))
        assertFalse(DownloadLocations.isInTree("/data/files/a.jpg", tree))
    }

    @Test
    fun mimeTypesFollowTheExtension() {
        assertEquals("image/jpeg", DownloadLocations.mimeFor("0000.jpg"))
        assertEquals("image/png", DownloadLocations.mimeFor("0001.PNG"))
        assertEquals("application/json", DownloadLocations.mimeFor("meta.json"))
        assertEquals("application/octet-stream", DownloadLocations.mimeFor("chapter.cbz"))
    }
}
