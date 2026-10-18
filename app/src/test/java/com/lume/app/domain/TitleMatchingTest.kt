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
    fun closeNamesNeedAnAuthor() {
        val wanted = t("The Beginning After the End", authors = listOf("TurtleMe"))
        assertEquals(TitleMatch.Same, matchTitle(wanted, t("Beginning After the End", authors = listOf("turtleme"))))
        assertEquals(TitleMatch.Maybe, matchTitle(wanted, t("Beginning After the End")))
        assertEquals(TitleMatch.Different, matchTitle(wanted, t("Omniscient Reader")))
    }

    @Test
    fun authorWordOrderIsIgnored() {
        assertEquals(true, authorsOverlap(listOf("Oh Seong-dae"), listOf("Seong-Dae Oh")))
        assertEquals(false, authorsOverlap(listOf("A B"), listOf("C D")))
        assertNull(authorsOverlap(emptyList(), listOf("C D")))
    }

    @Test
    fun onlyCloseResultsAreCompared() {
        val names = listOf("Solo Leveling")
        assertTrue(worthComparing(names, TitleSummary(id = "1", title = "Solo Leveling: Ragnarok")))
        assertFalse(worthComparing(names, TitleSummary(id = "2", title = "Tower of God")))
    }
}
