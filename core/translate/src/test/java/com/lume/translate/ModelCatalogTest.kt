package com.lume.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun everyFileIsPinned() {
        for (pair in ModelCatalog.pairs) {
            for (file in pair.files) {
                assertTrue(file.path, file.path.startsWith("models/${pair.id}/") && file.path.endsWith(".gz"))
                assertEquals(file.path, 64, file.sha256.length)
                assertTrue(file.path, file.sha256.all { it in '0'..'9' || it in 'a'..'f' })
                assertTrue(file.path, file.compressedSize in 1 until file.size)
            }
            assertEquals(pair.files.size, pair.files.map { it.name }.toSet().size)
        }
    }

    @Test
    fun catalogUsesHttps() {
        assertTrue(ModelCatalog.BASE_URL.startsWith("https://storage.googleapis.com/"))
        assertTrue(ModelCatalog.BASE_URL.endsWith("/"))
    }

    @Test
    fun findsPairs() {
        assertNotNull(ModelCatalog.find("en", "ru"))
        assertEquals("en-ru", ModelCatalog.find("en-ru")?.id)
        assertNull(ModelCatalog.find("ru", "en"))
        assertNull(ModelCatalog.find("../etc"))
    }

    @Test
    fun englishRussianSizes() {
        val pair = ModelCatalog.EN_RU_PAIR
        assertEquals(23_786_432L, pair.downloadSize)
        assertEquals(35_240_782L, pair.installedSize)
    }
}
