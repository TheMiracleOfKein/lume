package com.lume.translate.addon

import com.lume.translate.ModelFile
import com.lume.translate.ModelPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class ModelStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val contents = mapOf("m.gz" to "model bytes".repeat(500), "v.gz" to "vocab", "s.gz" to "shortlist".repeat(30))
    private val gz = contents.mapValues { gzip(it.value.toByteArray()) }

    private fun file(path: String, name: String, sha: String? = null): ModelFile {
        val raw = contents.getValue(path).toByteArray()
        return ModelFile(path, name, gz.getValue(path).size.toLong(), raw.size.toLong(), sha ?: sha256(raw))
    }

    private fun pair(modelSha: String? = null) = ModelPair("en", "xx", file("m.gz", "model.bin", modelSha), file("v.gz", "vocab.spm"), file("s.gz", "shortlist.bin"))

    private var requests = 0
    private fun store() = ModelStore(tmp.root) { url: URL ->
        requests++
        ByteArrayInputStream(gz.getValue(url.path.substringAfterLast('/')))
    }

    @Test
    fun downloadsUnpacksAndMarksInstalled() {
        val store = store()
        val pair = pair()
        assertFalse(store.isInstalled(pair))
        val seen = mutableListOf<Long>()
        store.download(pair) { done, total -> seen += done; assertEquals(pair.downloadSize, total) }
        assertTrue(store.isInstalled(pair))
        assertEquals(contents.getValue("v.gz"), File(store.dir(pair), "vocab.spm").readText())
        assertEquals(pair.downloadSize, seen.last())
        assertEquals(seen, seen.sorted())
    }

    @Test
    fun aWrongChecksumLeavesThePairNotInstalled() {
        val store = store()
        val pair = pair(modelSha = "0".repeat(64))
        try {
            store.download(pair)
            fail("expected a checksum error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("checksum"))
        }
        assertFalse(store.isInstalled(pair))
        assertTrue(store.dir(pair).listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun cancelStopsWithoutPartialFiles() {
        val store = store()
        val pair = pair()
        try {
            store.download(pair, cancelled = { true })
            fail("expected cancel")
        } catch (_: ModelStore.CancelledException) {
        }
        assertFalse(store.isInstalled(pair))
        assertTrue(store.dir(pair).listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun aSecondDownloadReusesCheckedFiles() {
        val store = store()
        val pair = pair()
        store.download(pair)
        File(store.dir(pair), "ok").delete()
        requests = 0
        store.download(pair)
        assertEquals(0, requests)
        assertTrue(store.isInstalled(pair))
    }

    @Test
    fun deleteRemovesThePair() {
        val store = store()
        val pair = pair()
        store.download(pair)
        assertTrue(store.delete(pair))
        assertFalse(store.isInstalled(pair))
        assertFalse(store.delete(pair))
    }

    @Test
    fun configQuotesPaths() {
        val yaml = EngineConfig.yaml(File("/data/x\"y"), "model.bin", "vocab.spm", "shortlist.bin")
        assertTrue(yaml, yaml.contains("x\\\"y"))
        assertTrue(yaml.contains("gemm-precision: int8shiftAlphaAll"))
        assertTrue(yaml.contains("alignment: soft"))
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
