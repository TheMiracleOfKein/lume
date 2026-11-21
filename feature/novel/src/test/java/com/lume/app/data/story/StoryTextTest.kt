package com.lume.app.data.story

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryTextTest {
    @Test
    fun findsAllMatchesIgnoringCase() {
        assertEquals(listOf(0..3, 14..17), highlightRanges("Lina and then lina", listOf("LINA")))
    }

    @Test
    fun overlappingTermsMergeIntoOneRange() {
        assertEquals(listOf(5..12), highlightRanges("Meet Lin Feng now", listOf("Lin", "Lin Feng")))
    }

    @Test
    fun cyrillicIgnoresCase() {
        assertEquals(listOf(6..9), highlightRanges("Здесь КИРА тоже", listOf("кира")))
    }

    @Test
    fun noTermsOrNoMatchGiveNothing() {
        assertEquals(emptyList<IntRange>(), highlightRanges("abc", emptyList()))
        assertEquals(emptyList<IntRange>(), highlightRanges("abc", listOf("", "  ", "zzz")))
    }

    @Test
    fun shortTextIsNotCut() {
        assertEquals("Hello Lin" to listOf(6..8), snippetAround("Hello Lin", listOf(6..8)))
    }

    @Test
    fun longTextIsCutAroundTheFirstHitWithShiftedRanges() {
        val text = "x".repeat(100) + "Lin" + "y".repeat(100)
        val (cut, ranges) = snippetAround(text, listOf(100..102), max = 60)
        assertEquals("…", cut.take(1))
        assertEquals("…", cut.takeLast(1))
        assertEquals("Lin", cut.substring(ranges.single().first, ranges.single().last + 1))
    }

    @Test
    fun aHitCutByTheWindowIsShortened() {
        val (cut, ranges) = snippetAround("Lin" + "y".repeat(50), listOf(0..2), max = 2)
        assertEquals("Li…", cut)
        assertEquals(listOf(0..1), ranges)
    }

    @Test
    fun aliasesAreSplitCleanedAndWithoutTheName() {
        assertEquals(listOf("Kirigaya", "Кирито"), parseAliases(" Kirigaya , kirito;Кирито,, kirigaya", "Kirito"))
        assertEquals(emptyList<String>(), parseAliases("", "Kirito"))
    }
}
