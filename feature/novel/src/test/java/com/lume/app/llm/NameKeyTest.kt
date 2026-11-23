package com.lume.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameKeyTest {
    @Test
    fun formsOfOneNameShareAKey() {
        val key = nameKey("Yu San A")
        assertEquals("yu san a", key)
        for (form in listOf("Yu San A-san", "Yu San A's", "Yu San A’s", "  yu  san  a ", "Yu San A-SAN", "Yu San A–sama")) {
            assertEquals(form, key, nameKey(form))
        }
        assertEquals(nameKey("Лин"), nameKey("Лин-сама"))
        assertEquals(nameKey("James"), nameKey("James'"))
        assertEquals(nameKey("ユウ"), nameKey("ユウさん"))
        assertEquals(nameKey("ユウ"), nameKey("ユウ様"))
        assertEquals(nameKey("김민수"), nameKey("김민수님"))
        assertEquals(nameKey("Jean-Luc"), nameKey("Jean Luc"))
    }

    @Test
    fun namesThatOnlyLookLikeHonorificsStay() {
        // With a space it is part of the name: Chinese given names, "Yu San".
        assertNotEquals(nameKey("Lin"), nameKey("Lin Yang"))
        assertNotEquals(nameKey("Zhao"), nameKey("Zhao Kun"))
        assertNotEquals(nameKey("Yu"), nameKey("Yu San"))
        assertNotEquals(nameKey("Kun"), nameKey("Kun Lun"))
        // Hyphenated pinyin keeps its syllables.
        assertNotEquals(nameKey("Ou"), nameKey("Ou-yang"))
        // Chinese kinship words and a bare honorific stay.
        assertNotEquals(nameKey("林"), nameKey("林兄"))
        assertEquals("san", nameKey("San"))
        assertEquals("さん", nameKey("さん"))
    }

    @Test
    fun baseNameKeepsTheWrittenForm() {
        assertEquals("Yu San A", baseName("Yu San A-san"))
        assertEquals("Yu San A", baseName("Yu San A's"))
        assertEquals("Ю Сан А", baseName("Ю Сан А-сан"))
        assertEquals("Kirito", baseName("Kirito"))
        assertTrue(isBaseName("Yu San A"))
        assertFalse(isBaseName("Yu San A-san"))
    }
}
