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

/** Saved Demonic Scans pages (fixtures/demonicscans). */
class DemonicParsingTest {
    private val engine = DemonicEngine(
        SourceManifest(id = "demonicscans", name = "Demonic Scans", engine = "demonic", baseUrl = "https://demonicscans.org"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun popularList() {
        val page = engine.parseTitleList(Fixtures.text("demonicscans/popular.html"), 1)
        assertEquals(20, page.items.size)
        assertFalse(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertTrue(page.items.all { it.id.startsWith("/manga/") && it.title.isNotBlank() })
        assertEquals("Mechanic: Slayer", page.items[1].title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val page = engine.parseTitleList(Fixtures.text("demonicscans/search.html"), 1)
        assertTrue(page.items.size > 20)
        assertUniqueIds(page.items.map { it.id })
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("demonicscans/details.html"), "/manga/x")
        assertEquals("The Corporate Slave Reincarnates as the Strongest Demon Dragon and Becomes the Hero's Master", details.title)
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chapters() {
        val chapters = engine.parseChapters(Fixtures.text("demonicscans/details.html"))
        assertEquals(20, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertTrue(chapters.all { it.id.contains("chaptered.php?manga=14468&chapter=") })
        // "Read First Chap" is chapter 1, then the list runs 20 down to 2.
        assertEquals(listOf("1", "20", "19"), chapters.take(3).map { it.number })
        assertEquals("2", chapters.last().number)
    }

    @Test
    fun pagesIncludeTheChapterImages() {
        val pages = engine.parsePages(Fixtures.text("demonicscans/pages.html"))
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.any { it.imageUrl.startsWith("https://cdn2.demoniclibs.com/") && it.imageUrl.contains("/0.webp") })
    }
}
