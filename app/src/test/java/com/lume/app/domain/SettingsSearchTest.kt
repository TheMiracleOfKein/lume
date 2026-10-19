package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val settings = listOf(
        SearchableSetting("theme", "Theme", "Appearance", keywords = "dark, light, night"),
        SearchableSetting("pure_black", "Pure black", "Appearance", hint = "Black background in the dark theme, for OLED screens"),
        SearchableSetting("doh", "DNS over HTTPS", "Network and storage", hint = "Helps when your provider blocks sites by DNS"),
        SearchableSetting("cache", "Clear cache", "Network and storage", hint = "Removes saved covers and pages"),
        SearchableSetting("dark_ru", "Тёмная тема", "Оформление"),
    )

    private fun ids(query: String) = searchSettings(query, settings).map { it.id }

    @Test
    fun blankQueryFindsNothing() {
        assertEquals(emptyList<String>(), ids(""))
        assertEquals(emptyList<String>(), ids("  , "))
    }

    @Test
    fun titleMatchesComeBeforeHintMatches() {
        assertEquals(listOf("theme", "pure_black"), ids("theme"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf("pure_black"), ids("black oled"))
        assertEquals(emptyList<String>(), ids("black wifi"))
    }

    @Test
    fun caseAndPunctuationDoNotMatter() {
        assertEquals(listOf("doh"), ids("dns-over-HTTPS"))
    }

    @Test
    fun sectionNameFindsItsSettings() {
        assertTrue(ids("network").containsAll(listOf("doh", "cache")))
    }

    @Test
    fun yoAndYeAreTheSame() {
        assertEquals(listOf("dark_ru"), ids("темная"))
        assertEquals(listOf("dark_ru"), ids("ТЁМН"))
    }

    @Test
    fun titleStartingWithTheQueryWins() {
        val list = listOf(
            SearchableSetting("a", "Download only on Wi-Fi", "Downloads"),
            SearchableSetting("b", "Only on Wi-Fi", "Library"),
        )
        assertEquals(listOf("b", "a"), searchSettings("only on", list).map { it.id })
    }

    @Test
    fun normalizesAccentsAndSpaces() {
        assertEquals("cafe creme", normalizeForSearch("  Café — Crème! "))
    }
}
