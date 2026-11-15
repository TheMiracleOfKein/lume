package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheTrimTest {
    private fun e(name: String, size: Long, modified: Long) = CacheEntry(name, size, modified)

    @Test
    fun underTheCapNothingGoes() {
        assertEquals(emptyList<String>(), selectCacheTrim(listOf(e("a", 10, 1), e("b", 10, 2)), 20))
    }

    @Test
    fun oldestGoFirstUntilItFits() {
        val entries = listOf(e("new", 100, 30), e("old", 100, 10), e("mid", 100, 20))
        assertEquals(listOf("old"), selectCacheTrim(entries, 200))
        assertEquals(listOf("old", "mid"), selectCacheTrim(entries, 100))
    }

    @Test
    fun theKeptFolderSurvivesEvenWhenOldest() {
        val entries = listOf(e("fresh", 100, 1), e("other", 100, 2))
        assertEquals(listOf("other"), selectCacheTrim(entries, 50, keep = "fresh"))
    }
}
