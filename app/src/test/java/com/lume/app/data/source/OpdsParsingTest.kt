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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OPDS 1.2 Atom and OPDS 2.0 JSON feeds (fixtures/opds). */
class OpdsParsingTest {
    private val base = "http://10.0.2.2:8080"
    private val engine = OpdsEngine(
        SourceManifest(id = "server-test", name = "OPDS", engine = "opds", baseUrl = "$base/opds/v1.2/catalog"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    private fun atom(name: String, path: String) = OpdsParser.parse(Fixtures.text("opds/$name"), "$base$path")

    @Test
    fun rootCatalogueHasNavigationAndLinks() {
        val feed = atom("root.atom.xml", "/opds/v1.2/catalog")
        assertEquals("Home Library", feed.title)
        assertEquals(2, feed.entries.size)
        assertEquals("$base/opds/v1.2/series?sort=new", feed.newest)
        assertEquals("$base/opds/v1.2/opensearch.xml", feed.search?.href)
        val all = feed.entries[0]
        assertEquals("All series", all.title)
        assertEquals("$base/opds/v1.2/series", all.navUrl)
        assertTrue(all.navIsAcquisition)
        assertFalse(all.isBook)
        assertFalse(feed.entries[1].navIsAcquisition)
    }

    @Test
    fun seriesListMakesTitlesFromFoldersAndBooks() {
        val feed = atom("series_list.atom.xml", "/opds/v1.2/series")
        assertEquals("$base/opds/v1.2/series?page=1", feed.next)
        val page = engine.summaries(feed, 1)
        assertTrue(page.hasNext)
        assertEquals(2, page.items.size)
        assertUniqueIds(page.items.map { it.id })
        val series = page.items[0]
        assertEquals("$base/opds/v1.2/series/1", series.id)
        assertEquals("Akira", series.title)
        assertEquals("Katsuhiro Otomo", series.subtitle)
        // The thumbnail wins over the full image in lists.
        assertEquals("$base/api/v1/series/1/thumbnail?small", series.coverUrl)
        assertHttpUrls(listOfNotNull(series.coverUrl))
        // A loose book is a title of its own, found again from its feed.
        val book = page.items[1]
        val ref = OpdsEngine.BookRef.decode(book.id)
        assertEquals("$base/opds/v1.2/series" to "urn:uuid:book-9", ref)
    }

    @Test
    fun booksBecomeChapters() {
        val feed = atom("books.atom.xml", "/opds/v1.2/series/1")
        val chapters = feed.entries.mapNotNull(engine::chapterOf)
        // The EPUB-only book has nothing readable.
        assertEquals(listOf("urn:uuid:book-1", "urn:uuid:book-2"), chapters.map { it.id })
        assertEquals(listOf("1", "2"), chapters.map { it.number })
        // Page streaming wins over the download.
        assertEquals("$base/api/v1/books/1/pages/{pageNumber}?zero_based=true#pse=24", chapters[0].url)
        // Without it: the best readable file, not the EPUB.
        assertEquals("$base/api/v1/books/2/file/akira02.cbr#ext=cbr", chapters[1].url)
        assertEquals("2024-01-10", chapters[0].dateLabel)
    }

    @Test
    fun streamedPagesFillTheTemplate() {
        val pages = engine.streamPages("$base/api/v1/books/1/pages/{pageNumber}?zero_based=true", 3)
        assertSequentialIndexes(pages.map { it.index })
        assertEquals("$base/api/v1/books/1/pages/0?zero_based=true", pages[0].imageUrl)
        assertEquals("$base/api/v1/books/1/pages/2?zero_based=true", pages[2].imageUrl)
    }

    @Test
    fun openSearchDescriptionYieldsTheAtomTemplate() {
        val template = OpdsEngine.parseOpenSearchDescription(Fixtures.text("opds/opensearch.xml"), "$base/opds/v1.2/opensearch.xml")
        assertNotNull(template)
        assertEquals(
            "$base/opds/v1.2/search?query=one%20piece&page=1&size=",
            OpdsEngine.expandSearch(template!!, "one piece").replace("+", "%20"),
        )
    }

    @Test
    fun searchTemplatesOfBothGenerationsExpand() {
        assertEquals("http://x/s?q=a%20b", OpdsEngine.expandSearch("http://x/s?q={searchTerms}", "a b").replace("+", "%20"))
        assertEquals("http://x/search?query=a", OpdsEngine.expandSearch("http://x/search{?query}", "a"))
        assertEquals("http://x/search?lang=en&query=a", OpdsEngine.expandSearch("http://x/search?lang=en{?query}", "a"))
        assertEquals("http://x/search?title=a", OpdsEngine.expandSearch("http://x/search{?title,author}", "a"))
    }

    @Test
    fun opds2FeedMapsNavigationPublicationsAndGroups() {
        val feed = OpdsParser.parse(Fixtures.text("opds/opds2_feed.json"), "$base/opds/v2/catalog")
        assertEquals("Home Library", feed.title)
        assertEquals("$base/opds/v2/catalog?page=2", feed.next)
        assertEquals("$base/opds/v2/search{?query}", feed.search?.href)
        assertTrue(feed.search!!.templated)
        assertEquals(listOf("All series", "Akira 01", "Akira 02"), feed.entries.map { it.title })

        val nav = feed.entries[0]
        assertEquals("$base/opds/v2/series", nav.navUrl)
        assertFalse(nav.isBook)

        val book = feed.entries[1]
        assertEquals("First volume.", book.summary)
        assertEquals(listOf("Katsuhiro Otomo"), book.authors)
        assertEquals("$base/api/v1/books/1/thumbnail", book.cover)
        assertEquals(24, book.stream?.count)
        val chapter = engine.chapterOf(book)
        assertEquals("$base/api/v1/books/1/pages/{pageNumber}#pse=24", chapter?.url)

        val grouped = feed.entries[2]
        assertEquals(listOf("Katsuhiro Otomo"), grouped.authors)
        assertEquals("https://cdn.example.com/akira02.pdf#ext=pdf", engine.chapterOf(grouped)?.url)
    }

    @Test
    fun nonCatalogueBodiesAreRejected() {
        val failed = runCatching { OpdsParser.parse("<html><body>Login</body></html>", "$base/x") }
        assertTrue(failed.isFailure)
    }

    @Test
    fun linkedHostsAreNotMistakenForTitles() {
        assertNull(engine.titleIdFromUrl("$base/series/1"))
        assertNull(engine.webUrl("$base/opds/v1.2/series/1"))
    }
}
