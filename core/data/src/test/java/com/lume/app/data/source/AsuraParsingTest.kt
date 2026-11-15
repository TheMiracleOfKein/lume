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

/** Saved Asura Scans API responses (fixtures/asura). */
class AsuraParsingTest {
    private val engine = AsuraEngine(
        SourceManifest(id = "asura", name = "Asura Scans", engine = "asura", baseUrl = "https://asurascans.com"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("asura/popular.json"), 1)
        assertEquals(20, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertEquals("overgeared-ab39de10", page.items.first().id)
        assertEquals("Overgeared", page.items.first().title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val item = engine.parseTitleList(Fixtures.text("asura/search.json"), 1).items.single()
        assertEquals("overgeared-ab39de10", item.id)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("asura/details.json"), "overgeared")
        assertEquals("overgeared", details.id)
        assertEquals("Overgeared", details.title)
        assertTrue(details.genres.isNotEmpty())
        assertTrue(!details.description.isNullOrBlank())
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersAreNewestFirstWithStableIds() {
        val chapters = engine.parseChapters(Fixtures.text("asura/chapters.json"), "overgeared")
        assertEquals(30, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertEquals("/comics/overgeared/chapter/347", chapters.first().id)
        assertEquals("/comics/overgeared/chapter/318", chapters.last().id)
        val numbers = chapters.map { it.number!!.toInt() }
        assertEquals(numbers.sortedDescending(), numbers)
    }

    @Test
    fun pages() {
        val pages = engine.parsePages(Fixtures.text("asura/pages.json"))
        assertEquals(19, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
    }
}
