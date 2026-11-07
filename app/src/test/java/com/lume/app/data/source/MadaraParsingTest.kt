package com.lume.app.data.source

import com.lume.app.testing.Fixtures
import com.lume.app.testing.assertHttpUrls
import com.lume.app.testing.assertSequentialIndexes
import com.lume.app.testing.assertUniqueIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved Madara (WordPress theme) pages from LikeManga (fixtures/likemanga). */
class MadaraParsingTest {
    private val parser = MadaraParser()
    private val base = "https://like-manga.net"

    @Test
    fun popularList() {
        val page = parser.parseTitleList(Fixtures.text("likemanga/popular.html"), 1, base)
        assertEquals(10, page.items.size)
        // Fewer than a full archive page: no next page.
        assertFalse(page.hasNext)
        assertUniqueIds(page.items.map { it.id })
        assertTrue(page.items.all { it.id.startsWith("/manga/") && it.id.endsWith("/") })
        assertEquals("/manga/otherworldly-evil-monarch/", page.items.first().id)
        assertEquals("Otherworldly Evil Monarch", page.items.first().title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
    }

    @Test
    fun searchList() {
        val item = parser.parseTitleList(Fixtures.text("likemanga/search.html"), 1, base).items.single()
        assertEquals("/manga/otherworldly-evil-monarch/", item.id)
    }

    @Test
    fun details() {
        val details = parser.parseTitleDetails(Fixtures.text("likemanga/details.html"), "/manga/otherworldly-evil-monarch/", base)
        assertEquals("Otherworldly Evil Monarch", details.title)
        assertTrue(details.genres.isNotEmpty())
        assertTrue(!details.description.isNullOrBlank())
        assertHttpUrls(listOfNotNull(details.coverUrl))
    }

    @Test
    fun chaptersAreNewestFirst() {
        val chapters = parser.parseChapters(Fixtures.text("likemanga/details.html"), base)
        assertEquals(257, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertEquals("/manga/otherworldly-evil-monarch/258/", chapters.first().id)
        assertEquals("/manga/otherworldly-evil-monarch/1/", chapters.last().id)
        assertHttpUrls(chapters.map { it.url })
    }

    @Test
    fun pages() {
        val pages = parser.parsePages(Fixtures.text("likemanga/pages.html"), base)
        assertEquals(16, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.first().imageUrl.endsWith("/001.jpg"))
        assertTrue(pages.last().imageUrl.endsWith("/016.jpg"))
    }
}
