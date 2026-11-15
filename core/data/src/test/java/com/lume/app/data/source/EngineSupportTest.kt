package com.lume.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineSupportTest {
    @Test
    fun htmlToTextKeepsParagraphsAndDecodesEntities() {
        val html = "<p>[By the <strong>Studio</strong> that brought you &lt;Lookism&gt;]</p><p>Line one<br>Line two</p>"
        assertEquals("[By the Studio that brought you <Lookism>]\n\nLine one\nLine two", htmlToText(html))
    }

    @Test
    fun htmlToTextPassesPlainTextThrough() {
        assertEquals("Plain", htmlToText("  Plain "))
        assertNull(htmlToText("   "))
    }

    @Test
    fun withQueryAppendsToExistingQuery() {
        assertEquals("https://a.b/c/?p=1&style=list", withQuery("https://a.b/c/?p=1", "?style=list"))
        assertEquals("https://a.b/c/?style=list", withQuery("https://a.b/c/", "?style=list"))
    }

    @Test
    fun resolveUrlHandlesPathsAndAbsoluteUrls() {
        assertEquals("https://site.org/series/x", resolveUrl("https://site.org", "/series/x").toString())
        assertEquals("https://cdn.org/a", resolveUrl("https://site.org", "//cdn.org/a").toString())
        assertEquals("https://other.org/z", resolveUrl("https://site.org/", "https://other.org/z").toString())
    }

    @Test
    fun chapterNumberFromTitles() {
        assertEquals("12.5", chapterNumberFrom("Chapter 12.5: Foo"))
        assertEquals("3", chapterNumberFrom("Глава 3"))
        assertNull(chapterNumberFrom("Prologue"))
    }
}
