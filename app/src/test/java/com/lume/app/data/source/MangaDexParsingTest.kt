package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import com.lume.app.testing.assertHttpUrls
import com.lume.app.testing.assertSequentialIndexes
import com.lume.app.testing.assertUniqueIds
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MangaDex API responses (fixtures/mangadex). These files are hand-written to the API's
 * documented shape: the recorder could not reach the API from the build machine.
 */
class MangaDexParsingTest {
    private val engine = MangaDexEngine(
        SourceManifest(id = "mangadex", name = "MangaDex", engine = "mangadex", baseUrl = "https://mangadex.org"),
        AppHttp(CookieJar.NO_COOKIES),
    )
    private val titleId = "11111111-1111-4111-8111-111111111111"

    @Test
    fun popularList() {
        val page = engine.parseMangaList(Fixtures.text("mangadex/popular.json"), 1)
        assertEquals(3, page.items.size)
        assertTrue(page.hasNext) // total 2340, 20 per page
        assertUniqueIds(page.items.map { it.id })
        assertEquals(titleId, page.items.first().id)
        assertEquals("Sample Dungeon Chronicle", page.items.first().title)
        assertEquals("120", page.items.first().latestChapter)
        assertEquals("https://uploads.mangadex.org/covers/$titleId/cover-one.jpg.256.jpg", page.items.first().coverUrl)
        // Falls back to the romanised title; a title without cover art has no cover.
        assertEquals("Sanpuru Koi", page.items[1].title)
        assertNull(page.items[2].coverUrl)
    }

    @Test
    fun lastPageHasNoNext() {
        val page = engine.parseMangaList(Fixtures.text("mangadex/search.json"), 117)
        assertTrue(!page.hasNext)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(
            Fixtures.text("mangadex/details.json"),
            titleId,
            engine.parseScore(Fixtures.text("mangadex/details-2.json"), titleId),
        )
        assertEquals(titleId, details.id)
        assertEquals("Sample Dungeon Chronicle", details.title)
        assertEquals("A sample description for the offline parser tests.", details.description)
        assertEquals(2021, details.year)
        assertEquals(8.43, details.score!!, 0.001)
        assertEquals(listOf("Sample Author"), details.authors)
        assertEquals(listOf("Sample Artist"), details.artists)
        assertEquals(listOf("Action", "Fantasy"), details.genres)
        // English and Russian names come first.
        assertEquals(listOf("Dungeon Chronicle Sample", "Хроника подземелья", "Sanpuru Danjon"), details.otherNames)
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersKeepTheFeedOrder() {
        val chapters = engine.parseChapters(Fixtures.text("mangadex/chapters.json"))
        assertEquals(3, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertEquals(listOf("12", "11.5", "1"), chapters.map { it.number })
        assertEquals(listOf("2", null, "1"), chapters.map { it.volume })
        assertEquals("2024-05-03", chapters.first().dateLabel)
        assertTrue(chapters.first().title.contains("The Third Gate"))
        assertTrue(chapters[1].title.contains("[ru]"))
    }

    @Test
    fun pages() {
        val pages = engine.parsePages(Fixtures.text("mangadex/pages.json"))
        assertEquals(3, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertEquals(
            "https://uploads.example-at-home.network/data/0123456789abcdef0123456789abcdef/1-aaaa.png",
            pages.first().imageUrl,
        )
    }
}
