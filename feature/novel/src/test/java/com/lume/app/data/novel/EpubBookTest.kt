package com.lume.app.data.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubBookTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, ByteArray>): File {
        val file = tmp.newFile(name)
        ZipOutputStream(file.outputStream()).use { out ->
            // The mimetype entry comes first, as in real books.
            out.putNextEntry(ZipEntry("mimetype"))
            out.write("application/epub+zip".toByteArray())
            out.closeEntry()
            for ((path, bytes) in entries) {
                out.putNextEntry(ZipEntry(path))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private fun container(opf: String) = """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="$opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>""".toByteArray()

    private fun xhtml(body: String) =
        """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body>$body</body></html>""".toByteArray()

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)

    private fun epub3(): File = zip(
        "three.epub",
        mapOf(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>The Test Book</dc:title>
                    <dc:creator>Ann Author</dc:creator>
                    <dc:creator>Bob Writer</dc:creator>
                    <dc:language>en</dc:language>
                    <dc:description>&lt;p&gt;A short &lt;b&gt;story&lt;/b&gt;.&lt;/p&gt;</dc:description>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c3" href="text/ch3.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cov" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                    <item id="pic" href="images/pic one.png" media-type="image/png"/>
                  </manifest>
                  <spine><itemref idref="c1"/><itemref idref="c2"/><itemref idref="c3"/></spine>
                </package>""".toByteArray(),
            "OEBPS/nav.xhtml" to """<html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                <nav epub:type="landmarks"><ol><li><a href="text/ch1.xhtml">Start</a></li></ol></nav>
                <nav epub:type="toc"><ol>
                  <li><a href="text/ch1.xhtml">The Beginning</a></li>
                  <li><a href="text/ch2.xhtml#part">The Middle</a></li>
                </ol></nav></body></html>""".toByteArray(),
            "OEBPS/text/ch1.xhtml" to xhtml("<h1>One</h1><p>Hello <i>world</i>.</p><p><img src=\"../images/pic%20one.png\" alt=\"\"/></p>"),
            "OEBPS/text/ch2.xhtml" to xhtml("<p>Second chapter.</p>"),
            "OEBPS/text/ch3.xhtml" to xhtml("<p>Third, no TOC entry.</p>"),
            "OEBPS/images/cover.png" to png,
            "OEBPS/images/pic one.png" to png,
        ),
    )

    private fun epub2(): File = zip(
        "two.epub",
        mapOf(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>Старая книга</dc:title>
                    <dc:creator opf:role="aut">Автор</dc:creator>
                    <meta name="cover" content="cover-img"/>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="a" href="a.html" media-type="application/xhtml+xml"/>
                    <item id="b" href="b.html" media-type="application/xhtml+xml"/>
                    <item id="cover-img" href="cover.jpg" media-type="image/jpeg"/>
                  </manifest>
                  <spine toc="ncx"><itemref idref="a"/><itemref idref="b"/></spine>
                </package>""".toByteArray(),
            "toc.ncx" to """<?xml version="1.0"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <navMap>
                    <navPoint id="n1" playOrder="1"><navLabel><text>Глава первая</text></navLabel><content src="a.html"/></navPoint>
                    <navPoint id="n2" playOrder="2"><navLabel><text>Глава вторая</text></navLabel><content src="b.html#x"/></navPoint>
                  </navMap>
                </ncx>""".toByteArray(),
            "a.html" to xhtml("<p>Текст.</p>"),
            "b.html" to xhtml("<p>Ещё текст.</p>"),
            "cover.jpg" to png,
        ),
    )

    @Test
    fun epub3MetadataCoverAndSpine() {
        val book = EpubBook.open(epub3())
        assertEquals("The Test Book", book.title)
        assertEquals("Ann Author, Bob Writer", book.author)
        assertEquals("en", book.language)
        assertEquals("A short story.", book.description)
        assertEquals("OEBPS/images/cover.png", book.coverEntry)
        assertEquals(listOf("OEBPS/text/ch1.xhtml", "OEBPS/text/ch2.xhtml", "OEBPS/text/ch3.xhtml"), book.chapters.map { it.entry })
    }

    @Test
    fun epub3TocNamesWithAChapterNFallback() {
        val book = EpubBook.open(epub3())
        assertEquals(listOf("The Beginning", "The Middle", "Chapter 3"), book.chapters.map { it.title })
    }

    @Test
    fun epub2UsesTheNcxAndTheCoverMeta() {
        val book = EpubBook.open(epub2())
        assertEquals("Старая книга", book.title)
        assertEquals("Автор", book.author)
        assertNull(book.description)
        assertEquals("cover.jpg", book.coverEntry)
        assertEquals(listOf("Глава первая", "Глава вторая"), book.chapters.map { it.title })
        assertEquals(listOf("a.html", "b.html"), book.chapters.map { it.entry })
    }

    @Test
    fun withoutAnyTocEveryChapterIsNumbered() {
        val file = zip(
            "plain.epub",
            mapOf(
                "META-INF/container.xml" to container("book.opf"),
                "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><metadata/>
                    <manifest><item id="x" href="x.html" media-type="text/html"/><item id="y" href="y.html" media-type="text/html"/></manifest>
                    <spine><itemref idref="x"/><itemref idref="y"/></spine></package>""".toByteArray(),
                "x.html" to xhtml("<p>x</p>"),
                "y.html" to xhtml("<p>y</p>"),
            ),
        )
        val book = EpubBook.open(file)
        assertNull(book.title)
        assertNull(book.coverEntry)
        assertNull(book.extractCover(tmp.newFolder("nocover")))
        assertEquals(listOf("Chapter 1", "Chapter 2"), book.chapters.map { it.title })
    }

    @Test
    fun chapterImagesAreUnpackedAndTheSrcPointsAtThem() {
        val book = EpubBook.open(epub3())
        val cache = tmp.newFolder("novel_img")
        val html = book.chapterHtml(0, cache)
        val src = NovelBlocks.parse(html).filterIsInstance<NovelBlock.Image>().single().src
        assertTrue(src, src.startsWith("file:///"))
        assertTrue(src, src.contains("novel_img"))
        val unpacked = cache.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, unpacked.size)
        assertEquals(png.toList(), unpacked.single().readBytes().toList())
        assertTrue(src.endsWith(unpacked.single().name))
        // Unpacking again reuses the file.
        book.chapterHtml(0, cache)
        assertEquals(1, cache.walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun chapterTextKeepsFormatting() {
        val book = EpubBook.open(epub3())
        val blocks = NovelBlocks.parse(book.chapterHtml(0, tmp.newFolder("img2")))
        assertEquals(NovelBlock.Heading(1, listOf(Span("One", bold = true))), blocks[0])
        assertEquals(NovelBlock.Paragraph(listOf(Span("Hello "), Span("world", italic = true), Span("."))), blocks[1])
    }

    @Test
    fun coverIsCopiedWithItsExtension() {
        val book = EpubBook.open(epub3())
        val cover = book.extractCover(tmp.newFolder("covers"))!!
        assertEquals("cover.png", cover.name)
        assertEquals(png.toList(), cover.readBytes().toList())
    }

    @Test
    fun aFileThatIsNotAnEpubFails() {
        val file = tmp.newFile("bad.epub").apply { writeText("not a zip") }
        val failure = runCatching { EpubBook.open(file) }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun hrefsResolveAgainstTheirFolder() {
        assertEquals("OEBPS/images/a b.png", EpubBook.resolve("OEBPS/text", "../images/a%20b.png"))
        assertEquals("x.html", EpubBook.resolve("", "./x.html"))
        assertEquals("a/b.html", EpubBook.resolve("c", "/a/b.html"))
    }
}
