package com.lume.app.data.local

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Builds the tiny comic archives the archive tests use, with no archiver installed. */
object ArchiveFixtures {
    const val COMIC_INFO = "<ComicInfo><Series>Fixture Series</Series><Summary>From an archive</Summary></ComicInfo>"

    /** A 4 x 4 PNG in a single colour; different colours give different bytes. */
    fun png(rgb: Int): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 4) for (y in 0 until 4) image.setRGB(x, y, rgb)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** Pages in an order that shows natural sorting: `p10` sorts after `p2`. */
    fun samplePages(): List<Pair<String, ByteArray>> = listOf(
        "p10.png" to png(0x0000FF),
        "p2.png" to png(0x00FF00),
        "p1.png" to png(0xFF0000),
        "ComicInfo.xml" to COMIC_INFO.toByteArray(),
    )

    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun sevenZ(entries: List<Pair<String, ByteArray>>, into: File) {
        SevenZOutputFile(into).use { out ->
            for ((name, data) in entries) {
                out.putArchiveEntry(SevenZArchiveEntry().also { it.name = name })
                out.write(data)
                out.closeArchiveEntry()
            }
        }
    }

    /** A RAR 4 archive with the files stored uncompressed (method 0x30). */
    fun rar4(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        // Main header: flags 0, size 13, 6 reserved bytes.
        out.write(block(type = 0x73, flags = 0, body = ByteArray(6)))
        for ((name, data) in entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = CRC32().also { it.update(data) }.value
            val body = ByteBuffer.allocate(25 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(data.size) // packed size
                putInt(data.size) // unpacked size
                put(3) // host OS: Unix
                putInt(crc.toInt())
                putInt(0x5A21_0000) // DOS time
                put(29) // unpack version
                put(0x30) // method: store
                putShort(nameBytes.size.toShort())
                putInt(0x20) // attributes
                put(nameBytes)
            }.array()
            out.write(block(type = 0x74, flags = 0x8000, body = body))
            out.write(data)
        }
        // End of archive.
        out.write(byteArrayOf(0xC4.toByte(), 0x3D, 0x7B, 0x00, 0x40, 0x07, 0x00))
        return out.toByteArray()
    }

    private fun block(type: Int, flags: Int, body: ByteArray): ByteArray {
        val size = 7 + body.size
        val tail = ByteBuffer.allocate(size - 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(type.toByte())
            putShort(flags.toShort())
            putShort(size.toShort())
            put(body)
        }.array()
        val crc = (CRC32().also { it.update(tail) }.value and 0xFFFF).toInt()
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(crc.toShort())
            put(tail)
        }.array()
    }
}
