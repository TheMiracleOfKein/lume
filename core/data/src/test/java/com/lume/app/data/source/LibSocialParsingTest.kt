package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import com.lume.app.testing.assertHttpUrls
import com.lume.app.testing.assertSequentialIndexes
import com.lume.app.testing.assertUniqueIds
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved LibSocial (cdnlibs API) responses from MangaLib (fixtures/mangalib). */
class LibSocialParsingTest {
    private val engine = LibSocialEngine(
        SourceManifest(id = "mangalib", name = "MangaLib", engine = "libsocial", baseUrl = "https://mangalib.me"),
        AppHttp(CookieJar.NO_COOKIES),
    )
    private val titleId = "/29916--ag-yeog-uiending-eunjug-eumppun"

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("mangalib/popular.json"), 1)
        assertEquals(60, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertEquals(titleId, page.items.first().id)
        assertEquals("Единственный исход злодейки — смерть", page.items.first().title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val item = engine.parseTitleList(Fixtures.text("mangalib/search.json"), 1).items.single()
        assertEquals(titleId, item.id)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("mangalib/details.json"), titleId)
        assertEquals(titleId, details.id)
        assertEquals("Единственный исход злодейки — смерть", details.title)
        assertEquals("Villains Are Destined to Die", details.altTitle)
        assertEquals(2020, details.year)
        assertEquals(listOf("Gwon Gyeoeul"), details.authors)
        assertTrue(details.genres.size >= 9)
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersAreNewestFirst() {
        val chapters = engine.parseChapters(Fixtures.text("mangalib/chapters.json"), titleId)
        assertEquals(30, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        // The API lists oldest first; the app shows newest first.
        assertEquals("$titleId/v1/c29", chapters.first().id)
        assertEquals("$titleId/v1/c0", chapters.last().id)
        assertEquals(listOf("29", "28"), chapters.take(2).map { it.number })
        assertEquals("1", chapters.first().volume)
    }

    @Test
    fun pagesGetTheChosenImageServer() {
        val raw = engine.parseRawPages(Fixtures.text("mangalib/pages.json"))
        assertEquals(7, raw.urls.size)
        val pages = engine.buildPages(raw, "https://img2.imglib.info")
        assertEquals(7, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertEquals(
            "https://img2.imglib.info/manga/ag-yeog-uiending-eunjug-eumppun/chapters/635735/01_6JUc.png",
            pages.first().imageUrl,
        )
    }
}
