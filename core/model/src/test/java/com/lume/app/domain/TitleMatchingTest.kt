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

    private fun r(id: String, title: String, subtitle: String? = null) = TitleSummary(id = id, title = title, subtitle = subtitle)

    @Test
    fun pickOpensTheOneExactResult() {
        val results = listOf(r("1", "Solo Leveling: Ragnarok"), r("2", "Solo Leveling!"), r("3", "Leveling Up"))
        assertEquals(SearchPick.Open("2"), pickSearchResult("Solo Leveling", results))
    }

    @Test
    fun pickMatchesAnAlternativeNameInTheSubtitle() {
        val results = listOf(r("7", "Поднятие уровня в одиночку", "Solo Leveling / Na Honjaman Level Up"))
        assertEquals(SearchPick.Open("7"), pickSearchResult("Solo Leveling", results))
    }

    @Test
    fun pickLetsTheReaderChooseWhenNothingOrTwoAreExact() {
        assertEquals(SearchPick.Choose, pickSearchResult("Solo Leveling", listOf(r("1", "Solo Leveling: Ragnarok"))))
        val twins = listOf(r("1", "Solo Leveling"), r("2", "Solo Leveling"))
        assertEquals(SearchPick.Choose, pickSearchResult("Solo Leveling", twins))
        // The same result twice (two pages, say) is still one title.
        assertEquals(SearchPick.Open("1"), pickSearchResult("Solo Leveling", listOf(r("1", "Solo Leveling"), r("1", "Solo Leveling"))))
    }

    @Test
    fun pickFindsNothingWithoutResults() {
        assertEquals(SearchPick.None, pickSearchResult("Solo Leveling", emptyList()))
    }
}
