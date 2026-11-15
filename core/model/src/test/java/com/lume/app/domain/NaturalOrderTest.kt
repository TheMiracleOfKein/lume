package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {
    private fun sort(vararg names: String) = names.toList().sortedWith(NaturalOrder)

    @Test
    fun numbersCompareAsNumbers() {
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), sort("10.jpg", "2.jpg", "1.jpg"))
    }

    @Test
    fun paddedNamesKeepTheirOrder() {
        assertEquals(listOf("page001.png", "page002.png", "page010.png"), sort("page010.png", "page001.png", "page002.png"))
    }

    @Test
    fun folderPathsCompareByNumberPerSegment() {
        assertEquals(listOf("ch2/9.jpg", "ch2/10.jpg", "ch10/1.jpg"), sort("ch10/1.jpg", "ch2/10.jpg", "ch2/9.jpg"))
    }

    @Test
    fun caseIsIgnored() {
        assertEquals(listOf("a2.jpg", "B1.jpg", "c3.jpg"), sort("c3.jpg", "B1.jpg", "a2.jpg"))
    }

    @Test
    fun mixedNames() {
        assertEquals(
            listOf("cover.jpg", "p1.jpg", "p2.jpg", "p10.jpg", "z.jpg"),
            sort("z.jpg", "p10.jpg", "cover.jpg", "p2.jpg", "p1.jpg"),
        )
    }

    @Test
    fun hugeNumbersDoNotOverflow() {
        assertTrue(NaturalOrder.compare("p99999999999999999999.jpg", "p100000000000000000000.jpg") < 0)
    }

    @Test
    fun equalNumbersFallBackToText() {
        assertEquals(listOf("p01.jpg", "p1.jpg"), sort("p1.jpg", "p01.jpg"))
        assertEquals(0, NaturalOrder.compare("p1.jpg", "p1.jpg"))
    }
}
