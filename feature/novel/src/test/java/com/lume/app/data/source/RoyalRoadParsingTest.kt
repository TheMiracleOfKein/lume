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

/** Pages saved from royalroad.com (fixtures/royalroad), trimmed of scripts and most chapter text. */
class RoyalRoadParsingTest {
    private val manifest = Json { ignoreUnknownKeys = true }
        .decodeFromString(SourceManifest.serializer(), File("src/main/assets/sources/royalroad.json").readText())
    private val engine = RoyalRoadEngine(manifest, AppHttp(CookieJar.NO_COOKIES))

    @Test
    fun bundledManifestIsAnEnglishNovelSource() {
        assertEquals(MediaKind.Novel, manifest.kind)
        assertEquals("en", manifest.language)
        assertTrue(SourceEngines.create(manifest, AppHttp(CookieJar.NO_COOKIES), modules = listOf(NovelModule)) is NovelSource)
    }

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("royalroad/popular.html"), 1)
        assertEquals(20, page.items.size)
        assertTrue(page.hasNext)
        assertEquals(page.items.size, page.items.map { it.id }.toSet().size)
        val first = page.items.first()
        assertEquals("/fiction/181303/gifted", first.id)
        assertEquals("Gifted", first.title)
        assertEquals("Original", first.typeLabel)
        assertEquals("Ongoing", first.subtitle)
        assertTrue(page.items.all { it.coverUrl == null || it.coverUrl!!.startsWith("https://") })
        assertTrue(page.items.all { it.id.matches(Regex("""/fiction/\d+/[^/]+""")) })
    }

    @Test
    fun secondPageHasOtherTitles() {
        val first = engine.parseTitleList(Fixtures.text("royalroad/popular.html"), 1).items.map { it.id }.toSet()
        val second = engine.parseTitleList(Fixtures.text("royalroad/popular-page2.html"), 2)
        assertEquals(20, second.items.size)
        assertTrue(second.hasNext)
        assertTrue(second.items.none { it.id in first })
    }

    @Test
    fun latestListNamesTheNewChapter() {
        val page = engine.parseTitleList(Fixtures.text("royalroad/latest.html"), 1)
        assertEquals(20, page.items.size)
        assertTrue(page.items.all { !it.latestChapter.isNullOrBlank() })
    }

    @Test
    fun searchFindsTheTitle() {
        val page = engine.parseTitleList(Fixtures.text("royalroad/search.html"), 1)
        assertEquals("/fiction/21220/mother-of-learning", page.items.first().id)
        assertEquals("Mother of Learning", page.items.first().title)
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("royalroad/details.html"), "/fiction/21220/mother-of-learning")
        assertEquals("Mother of Learning", details.title)
        assertEquals(listOf("nobody103"), details.authors)
        assertEquals("Completed", details.status)
        assertEquals("Original", details.typeLabel)
        assertTrue(details.genres.containsAll(listOf("Time Loop", "Fantasy")))
        assertTrue(details.description!!.startsWith("Zorian is a teenage mage"))
        assertTrue(details.coverUrl!!.startsWith("https://www.royalroadcdn.com/"))
        assertEquals(9.65, details.score!!, 0.01)
    }

    @Test
    fun chaptersComeFromThePageScriptInReadingOrder() {
        val chapters = engine.parseChapters(Fixtures.text("royalroad/details.html"))
        assertEquals(109, chapters.size)
        val first = chapters.first()
        assertEquals("/fiction/21220/mother-of-learning/chapter/301778/1-good-morning-brother", first.id)
        assertEquals("https://www.royalroad.com" + first.id, first.url)
        assertEquals("1. Good Morning Brother", first.title)
        assertEquals("1", first.number)
        assertEquals("2018-10-28", first.dateLabel)
        assertEquals((1..109).map { it.toString() }, chapters.map { it.number })
    }

    @Test
    fun lockedChaptersAreLeftOut() {
        val body = """<script>window.chapters = [
            {"id":1,"title":"Free","order":0,"isUnlocked":true,"url":"/fiction/1/x/chapter/1/free","date":"2026-01-01T00:00:00Z"},
            {"id":2,"title":"Paid","order":1,"isUnlocked":false,"url":"/fiction/1/x/chapter/2/paid","date":"2026-01-02T00:00:00Z"}
        ];</script>"""
        assertEquals(listOf("Free"), engine.parseChapters(body).map { it.title })
    }

    @Test
    fun chapterTextDropsTheHiddenAntiCopyLine() {
        val html = engine.parseChapterText(Fixtures.text("royalroad/chapter.html"))
        assertTrue(html, html.contains("Good morning, brother!"))
        assertTrue(html.contains("<em>MORNING</em>"))
        assertFalse(html, html.contains("not approved"))
        assertFalse(html.contains("<script"))
        // The author's note under the chapter is not part of the text.
        assertFalse(html.contains("Kindle"))
    }

    @Test
    fun hiddenClassesComeFromThePageStyles() {
        val css = ".abc{\n display: none;\n speak: never;\n}\n.shown { color: red }\n.x, .y{display:none}"
        assertEquals(setOf("abc", "x", "y"), NovelHtml.hiddenClasses(listOf(css)))
    }

    @Test
    fun sharedLinksOpenTheTitle() {
        assertEquals(
            "/fiction/21220/mother-of-learning",
            engine.titleIdFromUrl("https://www.royalroad.com/fiction/21220/mother-of-learning/chapter/301778/1-good-morning-brother"),
        )
        assertEquals("/fiction/21220/mother-of-learning", engine.titleIdFromUrl("https://www.royalroad.com/fiction/21220/mother-of-learning"))
        assertNull(engine.titleIdFromUrl("https://www.royalroad.com/fictions/weekly-popular"))
        assertNull(engine.titleIdFromUrl("https://example.com/fiction/21220/mother-of-learning"))
    }
}
