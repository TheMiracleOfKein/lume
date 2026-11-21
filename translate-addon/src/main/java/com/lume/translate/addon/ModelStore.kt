package com.lume.translate.addon

import com.lume.translate.ModelCatalog
import com.lume.translate.ModelFile
import com.lume.translate.ModelPair
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Downloaded model files, one folder per pair under [root]. A pair counts as installed only after
 * every file was unpacked and matched its pinned SHA-256; the `ok` marker records that, so later
 * checks don't hash 35 MB again.
 */
internal class ModelStore(
    private val root: File,
    private val open: (URL) -> InputStream = ::httpGet,
) {
    fun dir(pair: ModelPair) = File(root, pair.id)

    fun isInstalled(pair: ModelPair): Boolean {
        val dir = dir(pair)
        return File(dir, MARKER).isFile && pair.files.all { File(dir, it.name).length() == it.size }
    }

    fun installedPairs(): List<ModelPair> = ModelCatalog.pairs.filter(::isInstalled)

    fun delete(pair: ModelPair): Boolean {
        val dir = dir(pair)
        return dir.exists() && dir.deleteRecursively()
    }

    /**
     * Downloads every file of [pair]. [progress] gets compressed bytes done and the total.
     * [cancelled] is polled between reads; a cancel or failure leaves no partial files behind.
     */
    fun download(pair: ModelPair, cancelled: () -> Boolean = { false }, progress: (Long, Long) -> Unit = { _, _ -> }) {
        val dir = dir(pair)
        dir.mkdirs()
        File(dir, MARKER).delete()
        val total = pair.downloadSize
        var done = 0L
        try {
            for (file in pair.files) {
                val target = File(dir, file.name)
                if (target.length() == file.size && sha256(target) == file.sha256) {
                    done += file.compressedSize
                    progress(done, total)
                    continue
                }
                val base = done
                fetch(file, target, cancelled) { read -> progress(base + read, total) }
                done += file.compressedSize
            }
            File(dir, MARKER).writeText("1")
        } catch (e: Exception) {
            dir.listFiles()?.filter { it.name.endsWith(PART) }?.forEach { it.delete() }
            throw e
        }
    }

    private fun fetch(file: ModelFile, target: File, cancelled: () -> Boolean, progress: (Long) -> Unit) {
        val part = File(target.parentFile, target.name + PART)
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        open(URL(ModelCatalog.BASE_URL + file.path)).use { raw ->
            val counting = CountingStream(raw) { progress(it) }
            GZIPInputStream(counting, BUFFER).use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        if (cancelled()) throw CancelledException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        written += n
                        if (written > file.size) throw IOException("${file.name}: larger than expected")
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                    }
                }
            }
        }
        val sha = digest.digest().hex()
        if (written != file.size || sha != file.sha256) {
            part.delete()
            throw IOException("${file.name}: the download doesn't match its checksum")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Can't save ${file.name}")
    }

    class CancelledException : IOException("Cancelled")

    private class CountingStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) report(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) report(it.toLong()) }

        private fun report(n: Long) {
            count += n
            onRead(count)
        }
    }

    companion object {
        private const val MARKER = "ok"
        private const val PART = ".part"
        private const val BUFFER = 64 * 1024

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().hex()
        }

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

        private fun httpGet(url: URL): InputStream {
            require(url.protocol == "https" && url.host == "storage.googleapis.com") { "Unexpected address" }
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "LumeTranslate")
            if (conn.responseCode != 200) {
                conn.disconnect()
                throw IOException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream
        }
    }
}
