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

/**
 * Saved ReManga API v2 responses (fixtures/remanga). `chapters-page1.json` is the first page of
 * the chapter list (50 of 63, `next` set) and `chapters-last.json` the last one.
 */
class RemangaParsingTest {
    private val engine = RemangaEngine(
        SourceManifest(id = "remanga", name = "ReManga", engine = "remanga", baseUrl = "https://remanga.org"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("remanga/popular.json"), 1)
        assertEquals(30, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertEquals("martial_peak", page.items.first().id)
        assertEquals("Пик боевых искусств", page.items.first().title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val item = engine.parseTitleList(Fixtures.text("remanga/search.json"), 1).items.single()
        assertEquals("martial_peak", item.id)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("remanga/details.json"), "omniscient-reader_")
        assertEquals("omniscient-reader_", details.id)
        assertEquals("Всеведущий читатель", details.title)
        assertEquals("Omniscient Reader", details.altTitle)
        assertTrue(details.genres.isNotEmpty())
        assertTrue(!details.description.isNullOrBlank())
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersRunNewestFirstAcrossPages() {
        assertEquals("11527", engine.parseBranchId(Fixtures.text("remanga/details.json")))
        val first = engine.parseChapters(Fixtures.text("remanga/chapters-page1.json"), "11527")
        val last = engine.parseChapters(Fixtures.text("remanga/chapters-last.json"), "11527")
        assertEquals(50, first.size)
        assertEquals(13, last.size)
        assertUniqueIds((first + last).map { it.id })
        assertTrue((first + last).all { it.branchId == "11527" })
        val numbers = (first + last).map { it.number!!.toDouble() }
        assertEquals(numbers.sortedDescending(), numbers)
    }

    @Test
    fun pages() {
        val pages = engine.parsePages(Fixtures.text("remanga/pages.json"))
        assertEquals(14, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
    }
}
