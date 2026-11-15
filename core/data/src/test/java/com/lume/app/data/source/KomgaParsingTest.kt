package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import com.lume.app.testing.assertHttpUrls
import com.lume.app.testing.assertSequentialIndexes
import com.lume.app.testing.assertUniqueIds
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Komga JSON shapes (fixtures/komga), written from the server's DTOs. */
class KomgaParsingTest {
    private val engine = KomgaEngine(
        SourceManifest(id = "server-test", name = "Komga", engine = "komga", baseUrl = "http://10.0.2.2:25600/"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun seriesPage() {
        val page = engine.parseSeriesPage(Fixtures.text("komga/series_page.json"), 1)
        assertEquals(2, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        // The metadata title wins; a blank one falls back to the folder name.
        assertEquals("Frieren: Beyond Journey's End", page.items[0].title)
        assertEquals("no-title-series", page.items[1].title)
        assertEquals("http://10.0.2.2:25600/api/v1/series/0A1B2C3D4E5F6/thumbnail", page.items[0].coverUrl)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun details() {
        val d = engine.parseDetails(Fixtures.text("komga/series.json"))
        assertEquals("Frieren: Beyond Journey's End", d.title)
        assertEquals("Sousou no Frieren", d.altTitle)
        assertEquals("Ongoing", d.status)
        assertEquals(listOf("Kanehito Yamada"), d.authors)
        assertEquals(listOf("Tsukasa Abe"), d.artists)
        assertEquals(listOf("Adventure", "Fantasy"), d.genres)
        assertEquals(2020, d.year)
        assertEquals("An elf mage looks back on a quest that ended.", d.description)
    }

    @Test
    fun chaptersAreNewestFirstAndSkipUnreadableBooks() {
        val chapters = engine.parseChapters(Fixtures.text("komga/books.json"))
        assertEquals(listOf("BOOK0002", "BOOK0001"), chapters.map { it.id })
        assertEquals(listOf("2", "1"), chapters.map { it.number })
        assertEquals("The End of the Journey", chapters.last().title)
        assertEquals("2020-04-28", chapters.last().dateLabel)
        assertEquals("http://10.0.2.2:25600/book/BOOK0001", chapters.last().url)
    }

    @Test
    fun pagesAreOneBasedAndConvertedWhenNeeded() {
        val pages = engine.parsePages("BOOK0001", Fixtures.text("komga/pages.json"))
        assertSequentialIndexes(pages.map { it.index })
        assertEquals("http://10.0.2.2:25600/api/v1/books/BOOK0001/pages/1", pages[0].imageUrl)
        assertEquals("http://10.0.2.2:25600/api/v1/books/BOOK0001/pages/2", pages[1].imageUrl)
        assertEquals("http://10.0.2.2:25600/api/v1/books/BOOK0001/pages/3?convert=jpeg", pages[2].imageUrl)
        assertFalse(pages.any { it.imageUrl.contains("zero_based") })
    }

    @Test
    fun linksToSeriesPages() {
        assertEquals("0A1B2C3D4E5F6", engine.titleIdFromUrl("http://10.0.2.2:25600/series/0A1B2C3D4E5F6"))
        assertNull(engine.titleIdFromUrl("http://other.example/series/0A1B2C3D4E5F6"))
        assertEquals("http://10.0.2.2:25600/series/ABC", engine.webUrl("ABC"))
    }
}
