package com.lume.app.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFolderNameTest {
    @Test
    fun keepsOrdinaryIds() {
        assertEquals("chapter-12", safeFolderName("chapter-12"))
        assertEquals("Глава_5", safeFolderName("Глава 5"))
    }

    @Test
    fun replacesSeparatorsAndReservedCharacters() {
        assertEquals("a_b_c", safeFolderName("a/b\\c"))
        assertEquals("x__y", safeFolderName("x:?y"))
        assertEquals("tab_", safeFolderName("tab\t"))
    }

    @Test
    fun dotOnlyNamesNeverPointAtAParentFolder() {
        assertEquals("_.", safeFolderName("."))
        assertEquals("_..", safeFolderName(".."))
        assertEquals("_", safeFolderName(""))
        // "../x" has its slash replaced, so it stays one folder name.
        assertEquals(".._x", safeFolderName("../x"))
    }

    @Test
    fun longNamesAreShortenedAndStayDistinct() {
        val a = safeFolderName("x".repeat(300) + "a")
        val b = safeFolderName("x".repeat(300) + "b")
        assertTrue(a.toByteArray().size <= 200)
        assertNotEquals(a, b)
        assertTrue(safeFolderName("я".repeat(150)).toByteArray().size <= 200)
    }
}
