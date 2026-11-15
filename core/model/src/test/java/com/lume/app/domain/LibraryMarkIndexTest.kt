package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryMarkIndexTest {
    private val index = LibraryMarkIndex(
        entries = listOf(
            LibraryMarkIndex.Entry("mangalib", "7965--solo-leveling", "Поднятие уровня в одиночку", LibraryStatus.Reading),
            LibraryMarkIndex.Entry("mangadex", "abc", "The Beginning After the End", LibraryStatus.Planned),
            LibraryMarkIndex.Entry("asura", "x1", "X", LibraryStatus.Dropped),
        ),
        links = listOf(
            LibraryMarkIndex.Link("mangalib", "7965--solo-leveling", "mangakatana", "solo-leveling.21708", "Solo Leveling"),
            // A link of a title that isn't in the library marks nothing.
            LibraryMarkIndex.Link("gone", "g", "asura", "orphan", "Orphan Title"),
        ),
    )

    @Test
    fun `same source and id`() {
        assertEquals(LibraryStatus.Planned, index.statusOf("mangadex", "abc", "anything"))
    }

    @Test
    fun `a linked source marks its title`() {
        assertEquals(LibraryStatus.Reading, index.statusOf("mangakatana", "solo-leveling.21708", "Something else"))
    }

    @Test
    fun `a title saved from another source is found by name`() {
        assertEquals(LibraryStatus.Planned, index.statusOf("asura", "tbate", "the beginning after the end!"))
        assertEquals(LibraryStatus.Reading, index.statusOf("remanga", "solo", "Поднятие уровня в одиночку"))
        // The name a linked source uses counts too.
        assertEquals(LibraryStatus.Reading, index.statusOf("asura", "sl", "Solo Leveling"))
    }

    @Test
    fun `no mark for other titles, very short names or orphan links`() {
        assertNull(index.statusOf("asura", "other", "Another Title"))
        assertNull(index.statusOf("mangadex", "y", "X"))
        assertNull(index.statusOf("asura", "orphan", "Orphan Title"))
        assertNull(index.statusOf("asura", "none", null))
    }
}
