package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.FilterList
import com.lume.app.domain.MediaKind
import com.lume.app.domain.NovelSource
import com.lume.app.domain.SourceFilter
import com.lume.app.testing.Fixtures
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pages saved from novelfull.com (fixtures/novelfull) and novgo.net (fixtures/novgo), trimmed of
 * scripts and styles. Each chapter's text was replaced by three neutral paragraphs in the same
 * markup; NovelFull's ad frames and "report chapter" line were kept. `chapters.html` is the
 * `/ajax-chapter-option` answer.
 */
class NovelFullParsingTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)
    private val novelFull = NovelFullEngine(Fixtures.manifest("novelfull"), http)
    private val novgo = NovelFullEngine(Fixtures.manifest("novgo"), http)

    @Test
    fun bundledManifestsAreEnglishNovelSources() {
        for (id in listOf("novelfull", "novgo")) {
            val manifest = Fixtures.manifest(id)
            assertEquals(MediaKind.Novel, manifest.kind)
            assertEquals("en", manifest.language)
            assertEquals(SourceEngines.NOVELFULL, manifest.engine)
            assertTrue(SourceEngines.create(manifest, http, modules = listOf(NovelModule)) is NovelSource)
        }
    }

    @Test
    fun novelFullLists() {
        val popular = novelFull.parseTitleList(Fixtures.text("novelfull/popular.html"), 1)
        assertEquals(20, popular.items.size)
        assertTrue(popular.hasNext)
        val first = popular.items.first()
        assertEquals("/reincarnation-of-the-strongest-sword-god.html", first.id)
        assertEquals("Reincarnation Of The Strongest Sword God", first.title)
        assertTrue(first.coverUrl!!.startsWith("https://novelfull.com/uploads/"))
        assertTrue(first.subtitle!!.isNotBlank())
        assertEquals(20, novelFull.parseTitleList(Fixtures.text("novelfull/latest.html"), 1).items.size)
    }

    @Test
    fun novelFullSearchKeepsOnlyResults() {
        val page = novelFull.parseTitleList(Fixtures.text("novelfull/search.html"), 1)
        assertTrue(page.items.any { it.id == "/overlord-ln.html" && it.title == "Overlord (LN)" })
        // The side lists (hot and completed novels) are not results.
        assertTrue(page.items.all { "overlord" in it.id })
    }

    @Test
    fun novgoLists() {
        val popular = novgo.parseTitleList(Fixtures.text("novgo/popular.html"), 1)
        assertEquals(20, popular.items.size)
        assertTrue(popular.hasNext)
        val first = popular.items.first()
        assertEquals("/hidden-marriage.html", first.id)
        assertEquals("Hidden Marriage", first.title)
        assertTrue(first.coverUrl!!.startsWith("https://novgo.net/uploads/"))
        val search = novgo.parseTitleList(Fixtures.text("novgo/search.html"), 1)
        assertEquals("/classroom-of-the-elite.html", search.items.first().id)
        assertFalse(search.hasNext)
    }

    @Test
    fun emptyPageHasNoNext() {
        val page = novgo.parseTitleList("<html><body><p>No results</p></body></html>", 1)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasNext)
    }

    @Test
    fun novelFullDetails() {
        val body = Fixtures.text("novelfull/details.html")
        val details = novelFull.parseDetails(body, "/overlord-ln.html")
        assertEquals("Overlord (LN)", details.title)
        assertEquals(listOf("MARUYAMA Kugane"), details.authors)
        assertEquals("Completed", details.status)
        assertTrue(details.genres.containsAll(listOf("Action", "Fantasy", "Seinen")))
        assertTrue(details.description!!.contains("Yggdrasil"))
        assertTrue(details.coverUrl!!.startsWith("https://novelfull.com/uploads/"))
        assertEquals(8.9, details.score!!, 0.01)
        assertEquals("377", novelFull.parseNovelId(body))
    }

    @Test
    fun novgoDetails() {
        val body = Fixtures.text("novgo/details.html")
        val details = novgo.parseDetails(body, "/classroom-of-the-elite.html")
        assertEquals("Classroom of the Elite", details.title)
        assertEquals(listOf("Kinugasa Shougo"), details.authors)
        assertEquals("Ongoing", details.status)
        assertTrue(details.genres.containsAll(listOf("Drama", "School Life")))
        assertTrue(details.description!!.contains("Kōdo Ikusei"))
        assertTrue(details.coverUrl!!.startsWith("https://novgo.net/uploads/"))
        assertEquals(10.0, details.score!!, 0.01)
        assertEquals("7245", novgo.parseNovelId(body))
    }

    @Test
    fun chapterListInReadingOrder() {
        val overlord = novelFull.parseChapters(Fixtures.text("novelfull/chapters.html"))
        assertEquals(137, overlord.size)
        assertEquals("/overlord-ln/chapter-1.html", overlord.first().id)
        assertEquals("https://novelfull.com/overlord-ln/chapter-1.html", overlord.first().url)
        assertEquals("1", overlord.first().number)
        assertEquals("Volume 14: Epilogue", overlord.last().title)
        assertEquals("137", overlord.last().number)

        val elite = novgo.parseChapters(Fixtures.text("novgo/chapters.html"))
        assertEquals(980, elite.size)
        assertEquals("Prologue", elite.first().title)
        assertEquals("/classroom-of-the-elite/book-26-afterword.html", elite.last().id)
        assertEquals(elite.size, elite.map { it.id }.toSet().size)
    }

    @Test
    fun chapterTextWithoutAdsOrReportLine() {
        for (engine in listOf(novelFull, novgo)) {
            val site = engine.manifest.id
            val html = engine.parseChapterText(Fixtures.text("$site/chapter.html"))
            assertTrue(html, html.startsWith("<p>The morning was quiet"))
            assertTrue(html, html.contains("long field of grass"))
            assertFalse(html, html.contains("iframe"))
            assertFalse(html, html.contains("report chapter"))
            assertFalse(html, html.contains("<div></div>"))
        }
    }

    @Test
    fun searchPaths() {
        val none = FilterList(emptyList())
        assertEquals("/search?keyword=re+zero", novgo.searchPath(" re zero ", none))
        assertEquals("/most-popular", novgo.searchPath("", none))
        val filters = novgo.getFilters()
        val genre = filters.filters.filterIsInstance<SourceFilter.Select>().first { it.id == "genre" }
        val list = filters.filters.filterIsInstance<SourceFilter.Select>().first { it.id == "list" }
        val withGenre = FilterList(listOf(list.copy(selected = 3), genre.copy(selected = genre.options.indexOf("Slice of Life"))))
        assertEquals("/genre/Slice+of+Life", novgo.searchPath("", withGenre))
        assertEquals("/completed-novel", novgo.searchPath("", FilterList(listOf(list.copy(selected = 3)))))
        assertEquals("/search?keyword=elite", novgo.searchPath("elite", withGenre))
    }

    @Test
    fun sharedLinksOpenTheTitle() {
        assertEquals("/overlord-ln.html", novelFull.titleIdFromUrl("https://novelfull.com/overlord-ln.html"))
        assertEquals("/overlord-ln.html", novelFull.titleIdFromUrl("https://novelfull.com/overlord-ln/chapter-1.html"))
        assertEquals(
            "/classroom-of-the-elite.html",
            novgo.titleIdFromUrl("https://novgo.net/classroom-of-the-elite/book-26-afterword.html"),
        )
        assertNull(novgo.titleIdFromUrl("https://novgo.net/genre/Fantasy"))
        assertNull(novgo.titleIdFromUrl("https://novelfull.com/overlord-ln.html"))
    }
}
