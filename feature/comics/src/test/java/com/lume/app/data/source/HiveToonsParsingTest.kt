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

/** Saved HiveToons / Iken responses: HiveToons and Vortex Scans (fixtures/hivetoons, fixtures/vortexscans). */
class HiveToonsParsingTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)
    private val hive = HiveToonsEngine(
        SourceManifest(id = "hivetoons", name = "HiveToons", engine = "hivetoons", baseUrl = "https://hivetoons.org"),
        http,
    )
    private val vortex = HiveToonsEngine(
        SourceManifest(id = "vortexscans", name = "Vortex Scans", engine = "hivetoons", baseUrl = "https://vortexscans.org"),
        http,
    )

    @Test
    fun popularList() {
        val page = hive.parseTitleList(Fixtures.text("hivetoons/popular.json"), 1)
        assertEquals(24, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertTrue(page.items.all { it.id.startsWith("/series/") })
        assertEquals("/series/lookism", page.items.first().id)
        assertEquals("Lookism", page.items.first().title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val item = hive.parseTitleList(Fixtures.text("hivetoons/search.json"), 1).items.single()
        assertEquals("/series/lookism", item.id)
    }

    @Test
    fun details() {
        val details = hive.parseDetails(Fixtures.text("hivetoons/details.json"), "/series/lookism")
        assertEquals("Lookism", details.title)
        assertEquals("ONGOING", details.status)
        assertEquals(listOf("Park Tae Jun"), details.authors)
        assertTrue(details.genres.isNotEmpty())
        assertTrue(!details.description.isNullOrBlank())
    }

    @Test
    fun chaptersAreNewestFirst() {
        val chapters = hive.parseChapters(Fixtures.text("hivetoons/chapters.json"), "lookism")
        assertEquals(30, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertEquals("/series/lookism/chapter-632", chapters.first().id)
        val numbers = chapters.map { it.number!!.toInt() }
        assertEquals(numbers.sortedDescending(), numbers)
    }

    @Test
    fun pagesFollowTheReaderMarkers() {
        val pages = hive.parsePages(Fixtures.text("hivetoons/pages.html"))
        assertEquals(92, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.first().imageUrl.contains("image_1_"))
    }

    @Test
    fun vortexScansUsesTheSameEngine() {
        val list = vortex.parseTitleList(Fixtures.text("vortexscans/popular.json"), 1)
        assertEquals(24, list.items.size)
        assertEquals("/series/reality-quest-2", list.items.first().id)
        val chapters = vortex.parseChapters(Fixtures.text("vortexscans/chapters.json"), "reality-quest-2")
        assertEquals(30, chapters.size)
        assertEquals("/series/reality-quest-2/chapter-229", chapters.first().id)
        val pages = vortex.parsePages(Fixtures.text("vortexscans/pages.html"))
        assertEquals(233, pages.size)
        assertHttpUrls(pages.map { it.imageUrl })
    }
}
