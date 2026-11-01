package com.lume.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ReadCappedTest {
    @Test
    fun readsStreamsUpToTheLimit() {
        val data = ByteArray(200_000) { it.toByte() }
        assertArrayEquals(data, data.inputStream().readCapped(200_000))
    }

    @Test
    fun stopsAtTheLimit() {
        val data = ByteArray(200_001)
        assertThrows(IOException::class.java) { data.inputStream().readCapped(200_000) }
    }

    @Test
    fun capsAGzipBomb() {
        // 10 MB of zeros packs into a few KB; unpacked it must still be refused.
        val packed = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.GZIPOutputStream(out).use { it.write(ByteArray(10_000_000)) }
        }.toByteArray()
        assertThrows(IOException::class.java) {
            java.util.zip.GZIPInputStream(packed.inputStream()).use { it.readCapped(1_000_000) }
        }
    }
}
