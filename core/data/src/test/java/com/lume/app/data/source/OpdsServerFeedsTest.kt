package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feeds recorded from a real Komga (OPDS 1.2) and a real Kavita (the key is replaced by APIKEY). */
class OpdsServerFeedsTest {
    private val komga = "http://localhost:25600/opds/v1.2"
    private val kavita = "http://localhost:5000/api/opds/APIKEY"

    private fun engine(root: String, feeds: Map<String, String>): OpdsEngine =
        OpdsEngine(
            SourceManifest(id = "server-test", name = "OPDS", engine = "opds", baseUrl = root),
            AppHttp(CookieJar.NO_COOKIES),
        ).also { e ->
            e.fetcher = { url -> Fixtures.text("opds/" + (feeds[url] ?: error("Unexpected request: $url"))) }
        }

    private fun komgaEngine() = engine(
        "$komga/catalog",
        mapOf(
            "$komga/catalog" to "komga_root.atom.xml",
            "$komga/series" to "komga_series.atom.xml",
            "$komga/series/latest" to "komga_series.atom.xml",
            "$komga/series/0S5V0YNG1VD3R" to "komga_books.atom.xml",
        ),
    )

    @Test
    fun komgaPopularListsSeriesNotTheFirstSeriesChapters() = runBlocking {
        val e = komgaEngine()
        val page = e.getPopular(1)
        assertEquals(listOf("Lume Test One", "Lume Test Two"), page.items.map { it.title })
        assertEquals("$komga/series/0S5V0YNG1VD3R", page.items[0].id)
    }

    @Test
    fun komgaLatestAndSectionsListSeriesToo() = runBlocking {
        val e = komgaEngine()
        assertEquals(listOf("Lume Test One", "Lume Test Two"), e.getLatest(1).items.map { it.title })
        e.getPopular(1)
        val sections = e.browseSections()
        assertTrue(sections.any { it.name == "All series" })
        val all = sections.first { it.name == "All series" }
        assertEquals(2, e.search("", 1, all.filters).items.size)
    }

    @Test
    fun komgaSeriesOpensIntoChapters() = runBlocking {
        val chapters = komgaEngine().getChapters("$komga/series/0S5V0YNG1VD3R")
        assertEquals(listOf("3", "2", "1"), chapters.map { it.number })
        assertTrue(chapters.all { it.url.endsWith("#pse=4") })
    }

    @Test
    fun komgaFeedKinds() {
        val root = OpdsParser.parse(Fixtures.text("opds/komga_root.atom.xml"), "$komga/catalog")
        assertTrue(root.entries.none { it.isBook })
        assertEquals("$komga/series", root.entries.first { it.title == "All series" }.navUrl)
        assertEquals("$komga/search", root.search?.href)
        val series = OpdsParser.parse(Fixtures.text("opds/komga_series.atom.xml"), "$komga/series")
        assertTrue(series.entries.all { it.navUrl != null && !it.isBook })
        val books = OpdsParser.parse(Fixtures.text("opds/komga_books.atom.xml"), "$komga/series/0S5V0YNG1VD3R")
        assertTrue(books.entries.all { it.isBook })
        assertEquals(4, books.entries[0].stream?.count)
    }

    @Test
    fun kavitaPopularListsSeriesAndChaptersAreCleaned() = runBlocking {
        val e = engine(
            kavita,
            mapOf(
                kavita to "kavita_root.atom.xml",
                "$kavita/recently-updated" to "kavita_recently-updated.atom.xml",
                "$kavita/series/2" to "kavita_series_2.atom.xml",
            ),
        )
        val page = e.getPopular(1)
        assertEquals(listOf("Lume Test One (3)", "Lume Test Two (2)"), page.items.map { it.title })
        assertEquals("$kavita/series/2", page.items[0].id)

        // The series feed lists the started book twice ("Continue Reading from: ...").
        val chapters = e.getChapters("$kavita/series/2")
        assertEquals(3, chapters.size)
        assertEquals(listOf("Lume Test One - Chapter 3", "Lume Test One - Chapter 2", "Lume Test One - Chapter 1"), chapters.map { it.title })
        assertEquals(listOf("3", "2", "1"), chapters.map { it.number })
        // The page count sits on a `p5:count` attribute; the stream wins over the download.
        assertTrue(chapters.last().url.endsWith("pageNumber={pageNumber}#pse=4"))
        assertFalse(chapters.last().url.contains("download"))
    }
}
