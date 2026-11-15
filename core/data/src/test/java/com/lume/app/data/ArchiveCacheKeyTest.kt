package com.lume.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArchiveCacheKeyTest {
    @Test
    fun `same base name in different folders gets different keys`() {
        assertNotEquals(
            ArchiveCache.dirKey("/lib/Series A/04.cbz", 100, 10),
            ArchiveCache.dirKey("/lib/Series B/04.cbz", 100, 10),
        )
    }

    @Test
    fun `a replaced archive gets a new key`() {
        val old = ArchiveCache.dirKey("/lib/a/04.cbz", 100, 10)
        assertNotEquals(old, ArchiveCache.dirKey("/lib/a/04.cbz", 200, 10))
        assertNotEquals(old, ArchiveCache.dirKey("/lib/a/04.cbz", 100, 11))
        assertEquals(old, ArchiveCache.dirKey("/lib/a/04.cbz", 100, 10))
    }
}
