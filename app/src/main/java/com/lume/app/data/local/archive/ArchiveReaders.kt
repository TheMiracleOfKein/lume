package com.lume.app.data.local.archive

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.domain.ImageBytes
import com.lume.app.domain.NaturalOrder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Opens comic archives by their content, and unpacks their pages safely. */
object ArchiveReaders {
    /** Largest single page taken out of an archive. */
    const val MAX_ENTRY_BYTES = 100L * 1024 * 1024

    /** Most bytes unpacked from one archive. */
    const val MAX_TOTAL_BYTES = 1500L * 1024 * 1024

    /** Most pages taken from one archive. */
    const val MAX_ENTRIES = 5000

    private val EXTENSIONS = setOf("cbz", "zip", "cbr", "rar", "cb7", "7z")

    private val RAR_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21) // "Rar!"
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B) // "PK"
    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C) // "7z" BC AF 27 1C

    enum class Format { Zip, Rar, SevenZ }

    fun isArchive(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    fun isPdf(name: String): Boolean = name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

    /** A file that is one chapter: an archive or a PDF. */
    fun isChapterFile(name: String): Boolean = isArchive(name) || isPdf(name)

    /** The format from the first bytes, else from the extension; null when neither fits. */
    fun detect(header: ByteArray, name: String): Format? = when {
        header.startsWith(ZIP_MAGIC) -> Format.Zip
        header.startsWith(RAR_MAGIC) -> Format.Rar
        header.startsWith(SEVEN_Z_MAGIC) -> Format.SevenZ
        else -> when (name.substringAfterLast('.', "").lowercase()) {
            "cbz", "zip" -> Format.Zip
            "cbr", "rar" -> Format.Rar
            "cb7", "7z" -> Format.SevenZ
            else -> null
        }
    }

    fun open(file: File): ArchiveReader {
        val header = ByteArray(8)
        val read = file.inputStream().use { it.read(header).coerceAtLeast(0) }
        val format = detect(header.copyOf(read), file.name)
            ?: throw IOException(appString(R.string.error_archive_unsupported))
        return when (format) {
            Format.Zip -> ZipArchiveReader(file)
            Format.Rar -> RarArchiveReader(file)
            Format.SevenZ -> SevenZArchiveReader(file)
        }
    }

    /** False for names that could leave the folder they are unpacked into. */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.contains(NUL)) return false
        if (name.length > 1 && name[1] == ':') return false
        return name.split('/', '\\').none { it == ".." }
    }

    /** Image entries in reading order (natural order of the full path), unsafe names left out. */
    fun imageEntries(reader: ArchiveReader): List<String> =
        reader.entries()
            .filter { isSafeEntryName(it) && ImageBytes.isImageName(it.substringAfterLast('/')) }
            .sortedWith(compareBy(NaturalOrder) { it })
            .take(MAX_ENTRIES)

    /** Copies [input] to [output], failing when it holds more than [limit] bytes. Returns the count. */
    fun copyLimited(input: InputStream, output: OutputStream, limit: Long = MAX_ENTRY_BYTES): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw IOException("Archive entry is too large")
            output.write(buffer, 0, n)
        }
        return total
    }

    /**
     * Unpacks the image entries of [reader] into [outDir] as `destName(index, fileName)`, skipping
     * files that already exist. Returns the files in page order. Stops at [MAX_TOTAL_BYTES].
     */
    fun extractImages(reader: ArchiveReader, outDir: File, destName: (Int, String) -> String): List<File> {
        outDir.mkdirs()
        var budget = MAX_TOTAL_BYTES
        return imageEntries(reader).mapIndexed { i, entry ->
            val dest = File(outDir, destName(i, entry.substringAfterLast('/')))
            if (!dest.isFile || dest.length() == 0L) {
                val partial = File(outDir, dest.name + ".part")
                try {
                    reader.open(entry).use { input ->
                        partial.outputStream().use { out -> budget -= copyLimited(input, out, minOf(MAX_ENTRY_BYTES, budget)) }
                    }
                    if (!partial.renameTo(dest)) throw IOException("Can't write ${dest.name}")
                } finally {
                    partial.delete()
                }
            } else {
                budget -= dest.length()
            }
            dest
        }
    }

    private const val NUL = '\u0000'

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
