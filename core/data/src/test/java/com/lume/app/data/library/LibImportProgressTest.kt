package com.lume.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibImportProgressTest {
    @Test
    fun onlyTheProgressStageHasCounts() {
        assertNull(LibImportStage.Fetching.counts())
        assertNull(LibImportStage.Adding(10).counts())
        assertNull(LibImportStage.Finished.counts())
        assertNull(LibImportStage.Progress(0, 0).counts())
        assertEquals(3 to 8, LibImportStage.Progress(3, 8).counts())
    }

    @Test
    fun percentIsClamped() {
        assertEquals(0, libImportPercent(0, 0))
        assertEquals(50, libImportPercent(1, 2))
        assertEquals(33, libImportPercent(1, 3))
        assertEquals(100, libImportPercent(9, 4))
        assertEquals(0, libImportPercent(-1, 4))
    }

    @Test
    fun workNameIsPerSource() {
        assertEquals("lib_bookmark_import:mangalib", LibBookmarkImportWorker.workName("mangalib"))
    }
}
