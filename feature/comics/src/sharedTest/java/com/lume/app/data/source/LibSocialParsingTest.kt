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
        // The summary comes as a rich-text document, not a string.
        assertTrue(details.description.orEmpty().startsWith("Я перевоплотилась"))
    }

    @Test
    fun descriptionFromHtmlStringOrDocument() {
        fun description(summary: String) = engine.parseDetails("""{"data":{"name":"X","summary":$summary}}""", titleId).description
        assertEquals("One\n\nTwo", description("\"<p>One</p><p>Two</p>\""))
        assertEquals(
            "One\n\nTwo",
            description("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"One"}]},{"type":"paragraph","content":[{"type":"text","text":"Two"}]}]}"""),
        )
        assertEquals(null, description("null"))
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
    fun paidChaptersAreLeftOut() {
        // Shapes from the live API: a free chapter, a paid one (no branch id, as on MangaLib),
        // and one with a paid and a free translation.
        val body = """
            {"data": [
              {"id": 1, "volume": "3", "number": "259", "name": "",
               "branches": [{"id": 1, "branch_id": null, "restricted_view": {"is_open": true, "expired_at": null, "price": null}}]},
              {"id": 2, "volume": "3", "number": "260", "name": "",
               "branches": [{"id": 2, "branch_id": null, "expired_type": 1,
                 "restricted_view": {"is_open": false, "expired_at": "2026-12-26T17:33:00.000000Z", "price": null}}]},
              {"id": 3, "volume": "3", "number": "261", "name": "",
               "branches": [
                 {"id": 3, "branch_id": 11, "teams": [{"name": "Paid team"}], "restricted_view": {"is_open": false}},
                 {"id": 4, "branch_id": 12, "teams": [{"name": "Free team"}]}
               ]}
            ]}
        """.trimIndent()
        val chapters = engine.parseChapters(body, titleId)
        assertEquals(listOf("261", "259"), chapters.map { it.number })
        assertEquals("12", chapters.first().branchId)
        assertEquals("Free team", chapters.first().branchName)
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
