package com.lume.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookFilesTest {
    private val exts = setOf("epub", "txt")

    @Test
    fun booksAreRecognisedByExtension() {
        assertTrue(BookFiles.isBook("Dune.epub", exts))
        assertTrue(BookFiles.isBook("notes.TXT", exts))
        assertFalse(BookFiles.isBook("chapter1.cbz", exts))
        assertFalse(BookFiles.isBook("cover.jpg", exts))
        assertFalse(BookFiles.isBook("noextension", exts))
    }

    @Test
    fun noBooksWithoutANovelModule() {
        assertFalse(BookFiles.isBook("Dune.epub", emptySet()))
    }

    @Test
    fun aBookGoesToItsOwnFolderNamedAfterTheFile() {
        val root = File("local-novel")
        assertEquals(File(File(root, "Dune"), "Dune.epub"), BookFiles.destination(root, "Dune.epub"))
        assertEquals(File(File(root, "My Book. Part 2"), "My Book. Part 2.txt"), BookFiles.destination(root, "My Book. Part 2.txt"))
    }

    @Test
    fun unsafeCharactersAreReplaced() {
        assertEquals("a_b_c", BookFiles.folderName("a:b?c.epub"))
        assertEquals("_", BookFiles.folderName(".epub"))
    }
}
