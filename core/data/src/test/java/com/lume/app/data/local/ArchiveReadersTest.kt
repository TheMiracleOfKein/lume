package com.lume.app.data.local

import com.lume.app.data.local.archive.ArchiveReaders
import com.lume.app.data.local.archive.DamagedArchiveException
import com.lume.app.data.local.archive.RarArchiveReader
import com.lume.app.data.local.archive.SevenZArchiveReader
import com.lume.app.data.local.archive.UnsupportedRarVersionException
import com.lume.app.data.local.archive.ZipArchiveReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ArchiveReadersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val expectedOrder = listOf("p1.png", "p2.png", "p10.png")

    private fun resource(name: String): File {
        val stream = checkNotNull(javaClass.getResourceAsStream("/archives/$name")) { "missing fixture $name" }
        return tmp.newFile(name).also { f -> stream.use { s -> f.outputStream().use { s.copyTo(it) } } }
    }

    @Test
    fun `names are recognised as archives or PDFs`() {
        for (n in listOf("a.cbz", "a.ZIP", "a.cbr", "a.rar", "a.cb7", "a.7z")) assertTrue(n, ArchiveReaders.isArchive(n))
        for (n in listOf("a.pdf", "a.epub", "a.png", "cbz")) assertFalse(n, ArchiveReaders.isArchive(n))
        assertTrue(ArchiveReaders.isPdf("Vol 1.PDF"))
        assertTrue(ArchiveReaders.isChapterFile("a.pdf") && ArchiveReaders.isChapterFile("a.cb7"))
        assertFalse(ArchiveReaders.isChapterFile("cover.jpg"))
    }

    @Test
    fun `format is detected from magic bytes before the extension`() {
        val zip = byteArrayOf(0x50, 0x4B, 3, 4)
        val rar = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        val sevenZ = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4)
        assertEquals(ArchiveReaders.Format.Zip, ArchiveReaders.detect(zip, "x.cbr"))
        assertEquals(ArchiveReaders.Format.Rar, ArchiveReaders.detect(rar, "x.cbz"))
        assertEquals(ArchiveReaders.Format.SevenZ, ArchiveReaders.detect(sevenZ, "x.zip"))
        assertEquals(ArchiveReaders.Format.Rar, ArchiveReaders.detect(ByteArray(0), "x.cbr"))
        assertEquals(ArchiveReaders.Format.SevenZ, ArchiveReaders.detect(ByteArray(2), "x.CB7"))
        assertNull(ArchiveReaders.detect(ByteArray(8), "x.txt"))
    }

    @Test
    fun `open picks the reader by content`() {
        val misnamed = tmp.newFile("book.cbr").also { it.writeBytes(ArchiveFixtures.zip(ArchiveFixtures.samplePages())) }
        ArchiveReaders.open(misnamed).use { assertTrue(it is ZipArchiveReader) }
        ArchiveReaders.open(resource("sample.cbr")).use { assertTrue(it is RarArchiveReader) }
        ArchiveReaders.open(resource("sample.cb7")).use { assertTrue(it is SevenZArchiveReader) }
    }

    @Test
    fun `cbz lists images in natural order`() = checkArchive(tmp.newFile("a.cbz").also { it.writeBytes(ArchiveFixtures.zip(ArchiveFixtures.samplePages())) })

    @Test
    fun `cbr lists images in natural order`() = checkArchive(resource("sample.cbr"))

    @Test
    fun `cb7 lists images in natural order`() = checkArchive(resource("sample.cb7"))

    private fun checkArchive(file: File) {
        ArchiveReaders.open(file).use { reader ->
            assertEquals(expectedOrder, ArchiveReaders.imageEntries(reader))
            assertTrue(reader.entries().contains("ComicInfo.xml"))
            val info = reader.open("ComicInfo.xml").use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals(ArchiveFixtures.COMIC_INFO, info)
            val out = tmp.newFolder()
            val files = ArchiveReaders.extractImages(reader, out) { i, name -> "%04d_%s".format(i, name) }
            assertEquals(listOf("0000_p1.png", "0001_p2.png", "0002_p10.png"), files.map { it.name })
            assertArrayEquals(ArchiveFixtures.png(0xFF0000), files[0].readBytes())
            assertArrayEquals(ArchiveFixtures.png(0x0000FF), files[2].readBytes())
            assertTrue(out.listFiles()!!.none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun `entries that could leave the folder are rejected`() {
        for (bad in listOf("../evil.png", "a/../../evil.png", "/abs/evil.png", "\\evil.png", "C:/evil.png", "a\\..\\evil.png", "")) {
            assertFalse(bad, ArchiveReaders.isSafeEntryName(bad))
        }
        for (ok in listOf("p1.png", "ch 1/p1.png", "a..b/p1.png", "..hidden.png")) assertTrue(ok, ArchiveReaders.isSafeEntryName(ok))

        val rar = tmp.newFile("evil.cbr").also {
            it.writeBytes(ArchiveFixtures.rar4(listOf("../evil.png" to ArchiveFixtures.png(0), "p1.png" to ArchiveFixtures.png(1))))
        }
        ArchiveReaders.open(rar).use { assertEquals(listOf("p1.png"), ArchiveReaders.imageEntries(it)) }

        val zip = tmp.newFile("evil.zip").also {
            it.writeBytes(ArchiveFixtures.zip(listOf("../../evil.png" to ArchiveFixtures.png(0), "p1.png" to ArchiveFixtures.png(1))))
        }
        ArchiveReaders.open(zip).use { reader ->
            val out = tmp.newFolder("out")
            ArchiveReaders.extractImages(reader, out) { i, name -> "%04d_%s".format(i, name) }
            assertEquals(listOf("0000_p1.png"), out.listFiles()!!.map { it.name })
            assertFalse(File(out.parentFile.parentFile, "evil.png").exists())
        }
    }

    @Test
    fun `an entry over the size limit is refused`() {
        val input = java.io.ByteArrayInputStream(ByteArray(2048))
        try {
            ArchiveReaders.copyLimited(input, java.io.ByteArrayOutputStream(), limit = 1000)
            fail("expected the limit to apply")
        } catch (expected: IOException) {
            // zip bombs stop here
        }
        assertEquals(10L, ArchiveReaders.copyLimited(ByteArray(10).inputStream(), java.io.ByteArrayOutputStream()))
    }

    @Test
    fun `an unsupported RAR fails with an error, not a crash`() {
        // RAR5 signature followed by nothing useful.
        val rar5 = tmp.newFile("v5.cbr").also {
            it.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00) + ByteArray(32))
        }
        try {
            ArchiveReaders.open(rar5).close()
            fail("expected an error")
        } catch (e: IOException) {
            // The reader turns junrar's own exceptions into IOExceptions the source error path shows.
            assertNotNull(e)
        }
        assertTrue(UnsupportedRarVersionException() is IOException)
    }

    @Test
    fun `RAR5 signature is reported as unsupported`() {
        val rar5 = tmp.newFile("v5.cbr").also {
            it.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00) + ByteArray(32))
        }
        try {
            ArchiveReaders.open(rar5)
            fail("expected UnsupportedRarVersionException")
        } catch (e: UnsupportedRarVersionException) {
            // expected
        }
    }

    @Test
    fun `garbage in a cbz is reported as a damaged archive`() {
        val bad = tmp.newFile("bad.cbz").also { it.writeBytes(ByteArray(64) { 7 }) }
        try {
            ArchiveReaders.open(bad)
            fail("expected DamagedArchiveException")
        } catch (e: DamagedArchiveException) {
            // expected
        }
    }
}
