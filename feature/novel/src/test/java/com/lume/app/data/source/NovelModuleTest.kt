package com.lume.app.data.source

import com.lume.app.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelModuleTest {
    @Test
    fun servesNovelsAndHasNoEnginesYet() {
        assertEquals(MediaKind.Novel, NovelModule.kind)
        assertTrue(NovelModule.engines.isEmpty())
        assertTrue(NovelModule.preview)
    }
}
