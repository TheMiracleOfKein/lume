package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalogue parsing for ReManga (API v2) and MangaBuff (HTML cards). */
class RuSourceParsingTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)

    private fun manifest(engine: String, baseUrl: String) =
        SourceManifest(id = engine, name = engine, engine = engine, baseUrl = baseUrl)

    private val remanga = RemangaEngine(manifest("remanga", "https://remanga.org"), http)
    private val mangaBuff = MangaBuffEngine(manifest("mangabuff", "https://mangabuff.ru"), http)

    @Test
    fun remangaV2CatalogPage() {
        val body = """
            {"next":"/api/v2/search/catalog/?page=2&count=30","previous":null,"results":[
              {"id":2060,"main_name":"Поднятие уровня в одиночку","secondary_name":"Solo Leveling",
               "type":{"id":2,"name":"Манхва"},"dir":"solo-leveling_",
               "cover":{"low":"/media/titles/solo-leveling_/low.webp","mid":"/media/titles/solo-leveling_/mid.webp"}}
            ]}
        """.trimIndent()
        val page = remanga.parseTitleList(body, 1)
        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("solo-leveling_", item.id)
        assertEquals("Поднятие уровня в одиночку", item.title)
        assertEquals("Манхва", item.typeLabel)
        assertEquals("https://remanga.org/media/titles/solo-leveling_/mid.webp", item.coverUrl)
        assertTrue(page.hasNext)
    }

    @Test
    fun remangaV2LastPageAndSearchMeta() {
        val last = """{"next":null,"previous":"/x","results":[{"dir":"a","main_name":"A"}]}"""
        assertFalse(remanga.parseTitleList(last, 3).hasNext)
        val search = """{"meta":{"total_items":63,"total_pages":4,"page":1},"results":[{"dir":"a","main_name":"A"}]}"""
        assertTrue(remanga.parseTitleList(search, 1).hasNext)
        assertFalse(remanga.parseTitleList(search, 4).hasNext)
    }

    @Test
    fun mangaBuffReadsCardsWithBackgroundCovers() {
        val html = """
            <html><body>
              <header><a href="https://mangabuff.ru/manga?year=2026">За 2026 год</a></header>
              <div class="tabs"><a href="/manga/top?period=year">За 2026 год</a></div>
              <a href="/manga?year=2026" class="collection">За 2026 год</a>
              <div class="cards">
                <a class="cards__item" href="https://mangabuff.ru/manga/berserk">
                  <div class="cards__img" style="background-image: url('/img/manga/posters/berserk.jpg')"></div>
                  <div class="cards__info"><div class="cards__name">Берсерк</div></div>
                </a>
                <div class="cards__item">
                  <a href="/manga/vagabond"><img src="/img/plug.png" data-src="/img/manga/posters/vagabond.jpg"></a>
                  <div class="cards__name">Бродяга</div>
                </div>
              </div>
            </body></html>
        """.trimIndent()
        val items = mangaBuff.parseCatalog(html, 1).items
        assertEquals(listOf("berserk", "vagabond"), items.map { it.id })
        assertEquals(listOf("Берсерк", "Бродяга"), items.map { it.title })
        assertEquals("https://mangabuff.ru/img/manga/posters/berserk.jpg", items[0].coverUrl)
        assertEquals("https://mangabuff.ru/img/manga/posters/vagabond.jpg", items[1].coverUrl)
    }

    @Test
    fun mangaBuffWithoutCardsKeepsOnlyPosterLinks() {
        val html = """
            <div><a href="/manga?year=2026">За 2026 год</a>
            <a href="/manga/top">Топ</a>
            <a href="/manga/monster" title="Монстр"><img src="https://mangabuff.ru/img/monster.jpg"></a></div>
        """.trimIndent()
        val items = mangaBuff.parseCatalog(html, 1).items
        assertEquals(listOf("monster"), items.map { it.id })
        assertEquals("Монстр", items.single().title)
    }
}
