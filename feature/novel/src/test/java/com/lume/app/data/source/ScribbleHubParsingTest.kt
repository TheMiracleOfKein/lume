package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.MediaKind
import com.lume.app.domain.NovelSource
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pages saved from scribblehub.com (fixtures/scribblehub), trimmed of scripts. The chapter's
 * text was replaced by three neutral paragraphs in the same markup. `search.html` is the series
 * finder without a query (no results).
 */
class ScribbleHubParsingTest {
    private val manifest = Json { ignoreUnknownKeys = true }
        .decodeFromString(SourceManifest.serializer(), File("src/main/assets/sources/scribblehub.json").readText())
    private val engine = ScribbleHubEngine(manifest, AppHttp(CookieJar.NO_COOKIES))

    @Test
    fun bundledManifestIsAnEnglishNovelSource() {
        assertEquals(MediaKind.Novel, manifest.kind)
        assertEquals("en", manifest.language)
        assertTrue(SourceEngines.create(manifest, AppHttp(CookieJar.NO_COOKIES), modules = listOf(NovelModule)) is NovelSource)
    }

    @Test
    fun rankingList() {
        val page = engine.parseTitleList(Fixtures.text("scribblehub/popular.html"), 1)
        assertTrue(page.items.size >= 20)
        assertTrue(page.hasNext)
        assertEquals(page.items.size, page.items.map { it.id }.toSet().size)
        val first = page.items.first()
        assertEquals("/series/664073/rebirth-of-the-nephilim/", first.id)
        assertEquals("Rebirth of the Nephilim", first.title)
        assertTrue(first.coverUrl!!.startsWith("https://cdn.scribblehub.com/"))
        assertTrue(page.items.all { it.id.matches(Regex("""/series/\d+/[^/]+/""")) })
    }

    @Test
    fun latestList() {
        val page = engine.parseTitleList(Fixtures.text("scribblehub/latest.html"), 1)
        assertTrue(page.items.size >= 20)
        assertTrue(page.hasNext)
        assertEquals("/series/2690176/the-truth-seekers/", page.items.first().id)
    }

    @Test
    fun lastPageHasNoNext() {
        val body = Fixtures.text("scribblehub/latest.html")
        val items = Regex("""items:\s*(\d+)""").find(body)!!.groupValues[1].toInt()
        assertFalse(engine.parseTitleList(body, items).hasNext)
    }

    @Test
    fun emptySeriesFinderIsAnEmptyPage() {
        val page = engine.parseTitleList(Fixtures.text("scribblehub/search.html"), 1)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasNext)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("scribblehub/details.html"), "/series/664073/rebirth-of-the-nephilim/")
        assertEquals("Rebirth of the Nephilim", details.title)
        assertEquals(listOf("Agdistis"), details.authors)
        assertEquals("Ongoing", details.status)
        assertTrue(details.genres.containsAll(listOf("Adventure", "Fantasy")))
        assertTrue(details.description!!.startsWith("Jadis has no idea how she died."))
        assertTrue(details.coverUrl!!.startsWith("https://cdn.scribblehub.com/"))
        assertEquals(9.6, details.score!!, 0.01)
    }

    @Test
    fun tableOfContentsInReadingOrder() {
        // The title page shows the newest 15; the admin-ajax answer has the same rows for all.
        val chapters = engine.parseChapters(Fixtures.text("scribblehub/details.html"))
        assertEquals(15, chapters.size)
        val last = chapters.last()
        assertEquals("/read/664073-rebirth-of-the-nephilim/chapter/2686988/", last.id)
        assertEquals("https://www.scribblehub.com" + last.id, last.url)
        assertEquals("Chapter 793: Drawn to Beauty", last.title)
        assertEquals("798", last.number)
        assertEquals("Nov 17, 2026", last.dateLabel)
        assertEquals(chapters.sortedBy { it.number!!.toInt() }, chapters)
    }

    @Test
    fun chapterText() {
        val html = engine.parseChapterText(Fixtures.text("scribblehub/chapter.html"))
        assertTrue(html, html.startsWith("<p>Jadis walked"))
        assertTrue(html.contains("<strong><em>Morning</em></strong>"))
        assertFalse(html.contains("Favorite This Chapter"))
    }

    @Test
    fun chapterTextKeepsPicturesAndDropsAuthorNotes() {
        val body = """<div id="chp_raw"><p>Text</p><p><img data-src="/wp-content/uploads/a.png" src="data:image/gif;base64,R0"></p>
            <div class="wi_authornotes"><p>Thanks for reading!</p></div></div>"""
        val html = engine.parseChapterText(body)
        assertTrue(html, html.contains("""<img src="https://www.scribblehub.com/wp-content/uploads/a.png">"""))
        assertFalse(html.contains("Thanks for reading"))
    }

    @Test
    fun sharedLinksOpenTheSeries() {
        assertEquals(
            "/series/664073/rebirth-of-the-nephilim/",
            engine.titleIdFromUrl("https://www.scribblehub.com/series/664073/rebirth-of-the-nephilim/"),
        )
        assertEquals(
            "/series/664073/rebirth-of-the-nephilim/",
            engine.titleIdFromUrl("https://www.scribblehub.com/read/664073-rebirth-of-the-nephilim/chapter/2686988/"),
        )
        assertNull(engine.titleIdFromUrl("https://www.scribblehub.com/series-ranking/"))
    }
}
