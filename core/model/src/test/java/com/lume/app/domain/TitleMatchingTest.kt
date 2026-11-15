package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchingTest {
    private fun t(title: String, alt: String? = null, authors: List<String> = emptyList()) =
        TitleDetails(id = title, title = title, altTitle = alt, authors = authors)

    @Test
    fun alternativeNamesAreSplit() {
        assertEquals(
            listOf("Поднятие уровня в одиночку", "Solo Leveling", "나 혼자만 레벨업"),
            titleNames("Поднятие уровня в одиночку", "Solo Leveling / 나 혼자만 레벨업; Solo Leveling"),
        )
    }

    @Test
    fun equalNamesMatchAcrossLanguagesThroughAltTitles() {
        val ru = t("Поднятие уровня в одиночку", "Solo Leveling", listOf("Chugong"))
        assertEquals(TitleMatch.Same, matchTitle(ru, t("Solo Leveling!", authors = listOf("chugong"))))
        // No authors on one side: the name alone decides.
        assertEquals(TitleMatch.Same, matchTitle(ru, t("solo leveling")))
    }

    @Test
    fun differentAuthorsMakeAnEqualNameUncertain() {
        assertEquals(
            TitleMatch.Maybe,
            matchTitle(t("Monster", authors = listOf("Naoki Urasawa")), t("Monster", authors = listOf("Someone Else"))),
        )
    }

    @Test
    fun articlesDoNotMatter() {
        val wanted = t("The Beginning After the End", authors = listOf("TurtleMe"))
        assertEquals(TitleMatch.Same, matchTitle(wanted, t("Beginning After the End", authors = listOf("turtleme"))))
        assertEquals(TitleMatch.Different, matchTitle(wanted, t("Omniscient Reader")))
    }

    @Test
    fun spinOffsAreNeverTheSameTitle() {
        val wanted = t("Solo Leveling", authors = listOf("Chugong"))
        assertEquals(TitleMatch.Maybe, matchTitle(wanted, t("Solo Leveling: Arise", authors = listOf("Chugong"))))
        assertEquals(TitleMatch.Maybe, matchTitle(wanted, t("Solo Leveling: Ragnarok", authors = listOf("Chugong"))))
        assertEquals(
            TitleMatch.Maybe,
            matchTitle(t("Поднятие уровня в одиночку"), t("Поднятие уровня в одиночку: Истоки Охотника")),
        )
    }

    @Test
    fun authorWordOrderIsIgnored() {
        assertEquals(true, authorsOverlap(listOf("Oh Seong-dae"), listOf("Seong-Dae Oh")))
        assertEquals(true, authorsOverlap(listOf("Chu-Gong"), listOf("Chugong")))
        assertEquals(true, authorsOverlap(listOf("DUBU (Redice Studio)"), listOf("Dubu")))
        assertEquals(false, authorsOverlap(listOf("Naoki Urasawa"), listOf("Someone Else")))
        assertEquals(false, authorsOverlap(listOf("A B"), listOf("C D")))
        assertNull(authorsOverlap(emptyList(), listOf("C D")))
    }

    @Test
    fun onlyCloseResultsAreCompared() {
        val names = listOf("Solo Leveling")
        assertTrue(worthComparing(names, TitleSummary(id = "1", title = "Solo Leveling: Ragnarok")))
        assertFalse(worthComparing(names, TitleSummary(id = "2", title = "Tower of God")))
    }

    @Test
    fun otherNamesMatchAndAreSearchedFirst() {
        val dex = TitleDetails(
            id = "1",
            title = "Na Honjaman Level-Up",
            altTitle = "나 혼자만 레벨업",
            otherNames = listOf("Solo Leveling", "Поднятие уровня в одиночку", "solo leveling!"),
        )
        assertEquals(
            listOf("Na Honjaman Level-Up", "Solo Leveling", "Поднятие уровня в одиночку", "나 혼자만 레벨업"),
            searchNames(dex),
        )
        assertEquals(TitleMatch.Same, matchTitle(dex, t("Поднятие уровня в одиночку", "Solo Leveling")))
    }
}
