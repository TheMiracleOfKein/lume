package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    private fun t(title: String, subtitle: String? = null) = TitleSummary(id = title, title = title, subtitle = subtitle)

    @Test
    fun nameMatchesComeBeforeDescriptionMatches() {
        // The source found "hero" in the descriptions of the first two.
        val results = listOf(t("Tower of God"), t("Omniscient Reader"), t("The Hero Returns"), t("Hero"))
        val ranked = rankSearchResults("hero", results).map { it.title }
        assertEquals(listOf("Hero", "The Hero Returns", "Tower of God", "Omniscient Reader"), ranked)
    }

    @Test
    fun exactBeforePrefixBeforeWordBeforeSubstring() {
        assertEquals(0, nameMatchRank("solo leveling", listOf("Solo Leveling!")))
        assertEquals(1, nameMatchRank("solo", listOf("Solo Leveling")))
        assertEquals(2, nameMatchRank("leveling", listOf("Solo Leveling: Ragnarok")))
        assertEquals(3, nameMatchRank("hero", listOf("Superheroes")))
    }

    @Test
    fun alternativeNameCounts() {
        val results = listOf(t("Something else"), t("Na Honjaman Level-up", subtitle = "Solo Leveling"))
        assertEquals("Na Honjaman Level-up", rankSearchResults("solo leveling", results).first().title)
    }

    @Test
    fun sourceOrderKeptWithinARank() {
        val results = listOf(t("Hero B"), t("Hero A"), t("Hero C"))
        assertEquals(results, rankSearchResults("hero", results))
    }

    @Test
    fun wordsInAnyOrderRankAboveNothing() {
        assertTrue(nameMatchRank("leveling solo", listOf("Solo Leveling")) < nameMatchRank("leveling solo", listOf("Tower of God")))
    }

    @Test
    fun emptyQueryKeepsOrder() {
        val results = listOf(t("B"), t("A"))
        assertEquals(results, rankSearchResults("  ", results))
    }

    @Test
    fun cyrillicWorks() {
        val results = listOf(t("Башня бога"), t("Герой"))
        assertEquals("Герой", rankSearchResults("герой", results).first().title)
    }
}
