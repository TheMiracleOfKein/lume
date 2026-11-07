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

/**
 * MangaBuff pages (fixtures/mangabuff). Hand-written from the markup the parser reads: the
 * site is geo-blocked from the build machine, so nothing could be recorded.
 */
class MangaBuffParsingTest {
    private val engine = MangaBuffEngine(
        SourceManifest(id = "mangabuff", name = "MangaBuff", engine = "mangabuff", baseUrl = "https://mangabuff.ru"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun catalogCards() {
        val page = engine.parseCatalog(Fixtures.text("mangabuff/popular.html"), 1)
        assertEquals(listOf("sample-hero", "sample-village"), page.items.map { it.id })
        assertEquals(listOf("Пример героя", "Пример деревни"), page.items.map { it.title })
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
        assertFalse(page.hasNext) // fewer than a full page
    }

    @Test
    fun searchSuggestions() {
        val page = engine.parseSearch(Fixtures.text("mangabuff/search.json"))
        assertEquals(listOf("sample-hero", "sample-village"), page.items.map { it.id })
        assertEquals("Манга", page.items.first().typeLabel)
        assertEquals("https://mangabuff.ru/img/manga/posters/sample-hero.jpg", page.items.first().coverUrl)
        assertFalse(page.hasNext)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("mangabuff/details.html"), "sample-hero")
        assertEquals("Пример героя", details.title)
        assertEquals("Описание для офлайн-теста.", details.description)
        assertEquals(listOf("Экшен", "Фэнтези"), details.genres)
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersJoinThePageAndTheAjaxList() {
        val page = Fixtures.text("mangabuff/details.html")
        val (mangaId, csrf) = engine.chapterAjaxParams(page)
        assertEquals("777", mangaId)
        assertEquals("sample-token", csrf)
        val chapters = engine.parseChapters("sample-hero", page, Fixtures.text("mangabuff/chapters-2.json"))
        assertEquals(listOf("sample-hero/2/3", "sample-hero/2/2", "sample-hero/1/1"), chapters.map { it.id })
        assertUniqueIds(chapters.map { it.id })
        assertEquals("Том 2 Глава 3 — Третья глава", chapters.first().title)
        assertEquals("/manga/sample-hero/2/3", chapters.first().url)
        // Without the ajax part only the chapters on the page are known.
        assertEquals(2, engine.parseChapters("sample-hero", page, null).size)
    }

    @Test
    fun pages() {
        val pages = engine.parsePages(Fixtures.text("mangabuff/pages.html"), "https://mangabuff.ru/manga/sample-hero/1/1")
        assertEquals(3, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.all { it.imageUrl.contains("/chapters/") })
    }
}
