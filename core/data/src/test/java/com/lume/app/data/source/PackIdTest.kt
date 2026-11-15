package com.lume.app.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackIdTest {
    @Test
    fun acceptsPlainIds() {
        assertTrue(isValidPackId("likemanga"))
        assertTrue(isValidPackId("toon_god-2"))
    }

    @Test
    fun rejectsIdsThatCouldEscapeTheirFolder() {
        assertFalse(isValidPackId(""))
        assertFalse(isValidPackId(".."))
        assertFalse(isValidPackId("a/b"))
        assertFalse(isValidPackId("a.b"))
        assertFalse(isValidPackId("x".repeat(65)))
    }
}
