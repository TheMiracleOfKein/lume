package com.lume.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerDiffTest {
    private val entry = LedgerEntry(size = 100, lastModified = 5_000, destPath = "/local/S/c1.cbz")

    @Test
    fun newFileIsCopied() {
        assertTrue(LedgerDiff.needsCopy(null, 100, 5_000, destExists = false))
    }

    @Test
    fun fileWithoutRowButWithCopyIsKept() {
        assertFalse(LedgerDiff.needsCopy(null, 100, 5_000, destExists = true))
    }

    @Test
    fun unchangedFileIsSkipped() {
        assertFalse(LedgerDiff.needsCopy(entry, 100, 5_000, destExists = true))
    }

    @Test
    fun changedSizeIsCopiedAgain() {
        assertTrue(LedgerDiff.needsCopy(entry, 101, 5_000, destExists = true))
    }

    @Test
    fun changedModifiedTimeIsCopiedAgain() {
        assertTrue(LedgerDiff.needsCopy(entry, 100, 6_000, destExists = true))
    }

    @Test
    fun deletedCopyIsCopiedAgain() {
        assertTrue(LedgerDiff.needsCopy(entry, 100, 5_000, destExists = false))
    }
}
