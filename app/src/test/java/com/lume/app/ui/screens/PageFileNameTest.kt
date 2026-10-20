package com.lume.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PageFileNameTest {
    @Test
    fun namesAreSafeForFileSystems() {
        assertEquals("Solo Leveling - Ch. 1_ Start - 3", pageFileName("Solo Leveling", "Ch. 1: Start", 2))
        assertEquals("A_B - c_d - 1", pageFileName("A/B", "c\\d", 0))
    }

    @Test
    fun longNamesAreCut() {
        assertEquals(120, pageFileName("x".repeat(200), "y", 0).length)
    }
}
