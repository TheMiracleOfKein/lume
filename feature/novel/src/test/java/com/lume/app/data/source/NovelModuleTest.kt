package com.lume.app.data.source

import com.lume.app.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelModuleTest {
    @Test
    fun servesNovelsWithLocalBooksAndStaysPreview() {
        assertEquals(MediaKind.Novel, NovelModule.kind)
        assertEquals(setOf(SourceEngines.LOCAL_NOVEL), NovelModule.engines.keys)
        assertEquals(setOf("epub", "txt"), NovelModule.localBookExtensions)
        assertTrue(NovelModule.preview)
    }

    @Test
    fun localNovelManifestIsANovelSource() {
        assertEquals(MediaKind.Novel, SourceEngines.localNovelManifest.kind)
        assertTrue(SourceEngines.isKnown(SourceEngines.LOCAL_NOVEL))
    }
}
