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

/** Saved Weeb Central pages (fixtures/weebcentral). */
class WeebCentralParsingTest {
    private val engine = WeebCentralEngine(
        SourceManifest(id = "weebcentral", name = "Weeb Central", engine = "weebcentral", baseUrl = "https://weebcentral.com"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("weebcentral/popular.html"), 1)
        assertEquals(32, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertTrue(page.items.all { it.id.startsWith("/series/") })
        assertEquals("One Piece", page.items.first().title)
        assertEquals("/series/01J76XY7E9FNDZ1DBBM6PBJPFK/One-Piece", page.items.first().id)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val page = engine.parseTitleList(Fixtures.text("weebcentral/search.html"), 1)
        assertEquals(5, page.items.size)
        assertTrue(page.items.all { it.title.contains("One Piece") })
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("weebcentral/details.html"), "/series/01J76XY7E9FNDZ1DBBM6PBJPFK/One-Piece")
        assertEquals("One Piece", details.title)
        assertEquals(listOf("ODA Eiichiro"), details.authors)
        assertEquals("Ongoing", details.status)
        assertTrue("Action" in details.genres)
        assertTrue(!details.description.isNullOrBlank())
    }

    @Test
    fun chaptersAreNewestFirst() {
        val chapters = engine.parseChapters(Fixtures.text("weebcentral/chapters.html"))
        assertEquals(29, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertTrue(chapters.all { it.id.startsWith("/chapters/") })
        assertEquals("Chapter 1198", chapters.first().title)
        assertEquals("2026-11-06", chapters.first().dateLabel)
        val numbers = chapters.map { it.title.removePrefix("Chapter ").toInt() }
        assertEquals(numbers.sortedDescending(), numbers)
    }

    @Test
    fun pages() {
        val pages = engine.parsePages(Fixtures.text("weebcentral/pages.html"))
        assertEquals(57, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.first().imageUrl.endsWith("0001-001.png"))
    }
}
