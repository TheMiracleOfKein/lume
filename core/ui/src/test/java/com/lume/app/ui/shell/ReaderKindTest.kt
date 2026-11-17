package com.lume.app.ui.shell

import com.lume.app.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderKindTest {
    @Test
    fun sourceKindWins() {
        assertEquals(MediaKind.Novel, readerKindFor(MediaKind.Novel, "comics"))
    }

    @Test
    fun bookmarkKindWhenTheSourceIsGone() {
        assertEquals(MediaKind.Novel, readerKindFor(null, "novel"))
    }

    @Test
    fun progressKindWhenThereIsNoBookmark() {
        assertEquals(MediaKind.Novel, readerKindFor(null, null, "novel"))
        assertEquals(MediaKind.Comics, readerKindFor(null, "comics", "novel"))
        assertEquals(MediaKind.Comics, readerKindFor(null, null, "unknown"))
    }

    @Test
    fun comicsByDefault() {
        assertEquals(MediaKind.Comics, readerKindFor(null, null))
        assertEquals(MediaKind.Comics, readerKindFor(null, "unknown"))
    }
}
