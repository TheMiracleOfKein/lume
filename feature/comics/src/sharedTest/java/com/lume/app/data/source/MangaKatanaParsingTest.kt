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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved MangaKatana pages (fixtures/mangakatana). */
class MangaKatanaParsingTest {
    private val engine = MangaKatanaEngine(
        SourceManifest(id = "mangakatana", name = "MangaKatana", engine = "mangakatana", baseUrl = "https://mangakatana.com"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("mangakatana/popular.html"), 1)
        assertEquals(20, page.items.size)
        assertTrue(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertTrue(page.items.all { it.id.startsWith("/manga/") && it.title.isNotBlank() })
        assertEquals("/manga/aishiteru-uso-dakedo.10797", page.items.first().id)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun exactSearchMatchRedirectsToTheTitlePage() {
        val result = engine.parseSearch(
            Fixtures.text("mangakatana/search.html"),
            "https://mangakatana.com/manga/aishiteru-uso-dakedo.10797",
            1,
        )
        val item = result.items.single()
        assertEquals("/manga/aishiteru-uso-dakedo.10797", item.id)
        assertEquals("\"Aishiteru\", Uso Dakedo.", item.title)
        assertFalse(result.hasNext)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("mangakatana/details.html"), "/manga/aishiteru-uso-dakedo.10797")
        assertEquals("\"Aishiteru\", Uso Dakedo.", details.title)
        assertTrue(details.genres.isNotEmpty())
        assertTrue(!details.description.isNullOrBlank())
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersAreNewestFirst() {
        val chapters = engine.parseChapters(Fixtures.text("mangakatana/details.html"))
        assertEquals(listOf("2", "1"), chapters.map { it.number })
        assertUniqueIds(chapters.map { it.id })
        assertEquals("/manga/aishiteru-uso-dakedo.10797/c2", chapters.first().id)
        assertHttpUrls(chapters.map { it.url })
    }

    @Test
    fun pagesComeFromTheInlineImageArray() {
        val pages = engine.parsePages(Fixtures.text("mangakatana/pages.html"))
        assertEquals(51, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.first().imageUrl.endsWith("/0.jpg"))
        assertTrue(pages.last().imageUrl.endsWith("/50.jpg"))
    }
}
