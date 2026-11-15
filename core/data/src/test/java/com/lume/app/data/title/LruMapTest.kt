package com.lume.app.data.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruMapTest {
    @Test
    fun oldestEntryGoesWhenFull() {
        val m = LruMap<Int>(2)
        m["a"] = 1
        m["b"] = 2
        m["c"] = 3
        assertNull(m["a"])
        assertEquals(2, m["b"])
        assertEquals(2, m.size)
    }

    @Test
    fun readingKeepsAnEntryAlive() {
        val m = LruMap<Int>(2)
        m["a"] = 1
        m["b"] = 2
        m["a"]
        m["c"] = 3
        assertEquals(1, m["a"])
        assertNull(m["b"])
    }

    @Test
    fun removeDropsTheEntry() {
        val m = LruMap<Int>(2)
        m["a"] = 1
        assertEquals(1, m.remove("a"))
        assertNull(m["a"])
    }
}
