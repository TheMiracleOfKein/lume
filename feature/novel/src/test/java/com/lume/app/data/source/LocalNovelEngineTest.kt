package com.lume.app.data.source

import com.lume.app.domain.MediaKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalNovelEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var files: File
    private lateinit var cache: File
    private lateinit var engine: LocalNovelEngine

    @Before
    fun setUp() {
        files = tmp.newFolder("files")
        cache = tmp.newFolder("cache")
        engine = LocalNovelEngine(files, cache)
    }

    private fun bookDir(name: String) = File(files, "local-novel/$name").also { it.mkdirs() }

    private fun txt(folder: String, text: String, modified: Long = 0L): File =
        File(bookDir(folder), "$folder.txt").also {
            it.writeText(text)
            if (modified > 0) it.setLastModified(modified)
        }

    private fun epub(folder: String, title: String, modified: Long = 0L): File {
        val file = File(bookDir(folder), "$folder.epub")
        ZipOutputStream(file.outputStream()).use { out ->
            fun put(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="o.opf"/></rootfiles></container>""")
            put(
                "o.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title><dc:creator>Someone</dc:creator><meta name="cover" content="img"/></metadata>
                    <manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/><item id="img" href="cover.png" media-type="image/png"/></manifest>
                    <spine><itemref idref="c"/></spine></package>""",
            )
            put("c.xhtml", "<html><body><p>Epub text</p></body></html>")
            put("cover.png", "png")
        }
        if (modified > 0) file.setLastModified(modified)
        return file
    }

    @Test
    fun manifestIsALocalNovelSource() {
        assertEquals(MediaKind.Novel, engine.manifest.kind)
        assertEquals("local-novel", engine.manifest.id)
    }

    @Test
    fun popularIsByNameAndLatestByModifiedTime() = runBlocking {
        txt("b book", "x", modified = 3_000_000L)
        txt("a book", "x", modified = 1_000_000L)
        txt("c book", "x", modified = 2_000_000L)
        assertEquals(listOf("a book", "b book", "c book"), engine.getPopular(1).items.map { it.id })
        assertEquals(listOf("b book", "c book", "a book"), engine.getLatest(1).items.map { it.id })
    }

    @Test
    fun foldersWithoutABookAreIgnored() = runBlocking {
        bookDir("empty")
        File(bookDir("notes"), "readme.md").writeText("x")
        txt("real", "x")
        assertEquals(listOf("real"), engine.getPopular(1).items.map { it.id })
    }

    @Test
    fun searchMatchesTheTitleCaseInsensitively() = runBlocking {
        epub("file1", "The Sword Saint")
        txt("Other Tale", "x")
        assertEquals(listOf("file1"), engine.search("sword", 1).items.map { it.id })
        assertEquals(listOf("Other Tale"), engine.search("tale", 1).items.map { it.id })
        assertEquals(2, engine.search("", 1).items.size)
    }

    @Test
    fun epubTitleComesFromItsMetadataWithACover() = runBlocking {
        epub("file1", "The Sword Saint")
        val summary = engine.getPopular(1).items.single()
        assertEquals("The Sword Saint", summary.title)
        assertEquals("EPUB", summary.typeLabel)
        val cover = File(summary.coverUrl!!)
        assertTrue(cover.isFile)
        assertTrue(cover.path.contains("local_covers"))
        val details = engine.getTitle("file1")
        assertEquals("The Sword Saint", details.title)
        assertEquals(listOf("Someone"), details.authors)
        assertEquals(summary.coverUrl, details.coverUrl)
    }

    @Test
    fun txtTitleIsTheFileNameWithoutACover() = runBlocking {
        txt("Plain Book", "Chapter 1\n\nText")
        val details = engine.getTitle("Plain Book")
        assertEquals("Plain Book", details.title)
        assertNull(details.coverUrl)
        assertEquals("TXT", details.typeLabel)
    }

    @Test
    fun chaptersAreNewestFirstWithNumbersAndSelfContainedIds() = runBlocking {
        txt("Tale", "Chapter 1\n\nA\n\nChapter 2\n\nB\n\nChapter 3\n\nC")
        val chapters = engine.getChapters("Tale")
        assertEquals(listOf("Chapter 3", "Chapter 2", "Chapter 1"), chapters.map { it.title })
        assertEquals(listOf("Tale/2", "Tale/1", "Tale/0"), chapters.map { it.id })
        assertEquals(listOf("3", "2", "1"), chapters.map { it.number })
    }

    @Test
    fun txtChapterTextIsEscapedParagraphs() = runBlocking {
        txt("Tale", "Chapter 1\n\nFish & <chips>\n\nSecond\n\nChapter 2\n\nB")
        val chapter = engine.getChapters("Tale").last()
        assertEquals("<p>Fish &amp; &lt;chips&gt;</p>\n<p>Second</p>", engine.getChapterText(chapter).html)
    }

    @Test
    fun epubChapterTextComesFromTheBook() = runBlocking {
        epub("file1", "T")
        val chapter = engine.getChapters("file1").single()
        assertEquals("file1/0", chapter.id)
        assertTrue(engine.getChapterText(chapter).html.contains("Epub text"))
    }

    @Test
    fun chapterTextWorksFromTheIdAlone() = runBlocking {
        txt("Tale", "Chapter 1\n\nA\n\nChapter 2\n\nB")
        val bare = com.lume.app.domain.Chapter(id = "Tale/1", title = "", url = "")
        assertEquals("<p>B</p>", engine.getChapterText(bare).html)
    }

    @Test
    fun changedFilesAreReadAgain() = runBlocking {
        val file = txt("Tale", "Chapter 1\n\nA", modified = 1_000_000L)
        assertEquals(1, engine.getChapters("Tale").size)
        file.writeText("Chapter 1\n\nA\n\nChapter 2\n\nB")
        file.setLastModified(2_000_000L)
        assertEquals(2, engine.getChapters("Tale").size)
    }

    @Test
    fun badIdsDoNotLeaveTheBooksFolder() {
        val failure = runCatching { runBlocking { engine.getTitle("..") } }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(Pair("a", 3), LocalNovelEngine.parseChapterId("a/3"))
    }
}
