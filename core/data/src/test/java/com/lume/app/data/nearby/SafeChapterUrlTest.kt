package com.lume.app.data.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeChapterUrlTest {
    private val base = "https://example.org/"

    private fun safe(url: String, baseUrl: String? = base) = safeChapterUrl(url, "id1", baseUrl)

    @Test
    fun keepsRelativePaths() {
        assertEquals("chapter/12", safe("chapter/12"))
        assertEquals("12", safe("12"))
    }

    @Test
    fun replacesPathsThatEscapeOrAreAbsolute() {
        assertEquals("id1", safe("/etc/passwd"))
        assertEquals("id1", safe("\\share\\x"))
        assertEquals("id1", safe("../secret"))
        assertEquals("id1", safe("a/../b"))
        assertEquals("id1", safe("a\\..\\b"))
        assertEquals("id1", safe("javascript:alert(1)"))
    }

    @Test
    fun keepsWebLinksOnTheSourceSite() {
        assertEquals("https://example.org/c/1", safe("https://example.org/c/1"))
        assertEquals("http://m.example.org/c/1", safe("http://m.example.org/c/1"))
        assertEquals("https://EXAMPLE.org/c/1", safe("https://EXAMPLE.org/c/1"))
    }

    @Test
    fun replacesWebLinksOnOtherSites() {
        assertEquals("id1", safe("https://evil.com/c/1"))
        assertEquals("id1", safe("https://notexample.org/c/1"))
        assertEquals("id1", safe("https://example.org.evil.com/c/1"))
        assertEquals("id1", safe("https://example.org@evil.com/c/1"))
        assertEquals("id1", safe("file:///sdcard/x"))
        assertEquals("id1", safe("content://media/x"))
    }

    @Test
    fun webLinksNeedASourceToCompareWith() {
        assertEquals("id1", safe("https://example.org/c/1", baseUrl = null))
        assertEquals("c/1", safe("c/1", baseUrl = null))
    }

    @Test
    fun coverUrlsMustBeWebAddresses() {
        assertEquals("https://a.b/c.png", safeCoverUrl("https://a.b/c.png"))
        assertEquals(null, safeCoverUrl("file:///x.png"))
        assertEquals(null, safeCoverUrl("content://x"))
        assertEquals(null, safeCoverUrl(null))
    }

    @Test
    fun serviceNamesAreCutAtACharacterBoundary() {
        assertEquals("abc", truncateUtf8("abcdef", 3))
        assertEquals("ab", truncateUtf8("abé", 3))
        assertEquals("a", truncateUtf8("a😀", 4))
    }
}
