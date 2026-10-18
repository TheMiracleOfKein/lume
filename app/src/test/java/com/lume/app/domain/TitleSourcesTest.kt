package com.lume.app.domain

import com.lume.app.data.db.LibraryChapterEntity
import com.lume.app.data.library.findNewChapters
import com.lume.app.data.library.removedChapterIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSourcesTest {
    private fun ch(id: String, number: String?, volume: String? = null, title: String = "Ch $number", branch: String? = null) =
        Chapter(id = id, title = title, url = "/$id", number = number, volume = volume, branchId = branch, branchName = branch)

    @Test
    fun linkedIdsRoundTripWithAnyCharacters() {
        val id = LinkedChapterId.encode("mangadex", "/title/a|b%c", "https://x.org/ch/1?a=b|c")
        val ref = LinkedChapterId.decode(id)!!
        assertEquals("mangadex", ref.sourceId)
        assertEquals("/title/a|b%c", ref.titleId)
        assertEquals("https://x.org/ch/1?a=b|c", ref.chapterId)
        assertTrue(LinkedChapterId.isLinked(id))
        assertNull(LinkedChapterId.decode("/manga/x/chapter-1"))
        assertEquals("main", LinkedChapterId.sourceOf("/manga/x/chapter-1", "main"))
        assertEquals("mangadex", LinkedChapterId.sourceOf(id, "main"))
    }

    @Test
    fun numbersComeFromTheSourceOrTheTitle() {
        assertEquals("12", chapterNumber(ch("a", "12.0")))
        assertEquals("12.5", chapterNumber(ch("a", null, title = "Vol. 2 Chapter 12,5")))
        assertEquals("7", chapterNumber(ch("a", null, title = "Глава 7: Начало")))
        assertEquals("40", chapterNumber(ch("a", null, title = "Episode 40")))
        assertEquals("3", chapterNumber(ch("a", null, title = "The 3rd day")))
        assertNull(chapterNumber(ch("a", null, title = "Prologue")))
        assertNull(chapterNumber(ch("a", null, title = "Vol 2 extra 5")))
    }

    @Test
    fun mergeFillsGapsFromLinkedSources() {
        val own = listOf(ch("m3", "3", "1"), ch("m1", "1", "1"))
        val linked = SourceChapters("dex", "t", listOf(ch("d4", "4"), ch("d3", "3"), ch("d2", "2"), ch("dx", null, title = "Extra")))
        val merged = mergeSourceChapters(own, listOf(linked))
        // Every numbered copy is kept; the unnumbered extra can't be placed.
        assertEquals(5, merged.size)
        val d3 = merged.first { LinkedChapterId.decode(it.id)?.chapterId == "d3" }
        // Same chapter as the main source's: takes its volume, so the copies share a key.
        assertEquals(dedupeKey(own[0]), dedupeKey(d3))

        val shown = dedupeChapters(merged).inReadingOrder()
        assertEquals(listOf("1", "2", "3", "4"), shown.map { it.number })
        // Chapter 3 comes from the main source, 2 and 4 from the linked one.
        assertEquals("m3", shown[2].id)
        assertTrue(LinkedChapterId.isLinked(shown[1].id))
        assertTrue(LinkedChapterId.isLinked(shown[3].id))
    }

    @Test
    fun noLinksLeavesTheListAlone() {
        val own = listOf(ch("a", null, title = "Chapter 2"), ch("b", null, title = "Chapter 1"))
        assertEquals(own, mergeSourceChapters(own, emptyList()))
        assertEquals(own, mergeSourceChapters(own, listOf(SourceChapters("x", "y", emptyList()))))
    }

    @Test
    fun earlierLinkedSourceWins() {
        val own = listOf(ch("m1", "1"))
        val first = SourceChapters("a", "t", listOf(ch("a2", "2")))
        val second = SourceChapters("b", "t", listOf(ch("b2", "2"), ch("b3", "3")))
        val shown = dedupeChapters(mergeSourceChapters(own, listOf(first, second))).inReadingOrder()
        assertEquals(listOf("1", "2", "3"), shown.map { it.number })
        assertEquals("a", LinkedChapterId.decode(shown[1].id)!!.sourceId)
        assertEquals("b", LinkedChapterId.decode(shown[2].id)!!.sourceId)
    }

    @Test
    fun linkedChaptersAreNotTeams() {
        val own = listOf(ch("m2a", "2", branch = "A"), ch("m2b", "2", branch = "B"), ch("m1a", "1", branch = "A"))
        val merged = mergeSourceChapters(own, listOf(SourceChapters("dex", "t", listOf(ch("d3", "3"), ch("d2", "2")))))
        assertEquals(listOf("A", "B"), chapterBranches(merged).map { it.id })
        val shown = dedupeChapters(merged, preferredBranch = "B").inReadingOrder()
        assertEquals(listOf("m1a", "m2b"), shown.take(2).map { it.id })
        assertEquals("d3", LinkedChapterId.decode(shown[2].id)!!.chapterId)
    }

    @Test
    fun readOnOneSourceIsReadOnAll() {
        val own = listOf(ch("m1", "1", "1"))
        val merged = mergeSourceChapters(own, listOf(SourceChapters("dex", "t", listOf(ch("d1", "1")))))
        val linkedCopy = merged.first { LinkedChapterId.isLinked(it.id) }
        val read = ChapterReadState(merged, setOf(linkedCopy.id))
        assertTrue(read.isRead(own[0]))
    }

    @Test
    fun alternativesPutOtherSourcesFirst() {
        val own = listOf(ch("m1a", "1", branch = "A"), ch("m1b", "1", branch = "B"))
        val merged = mergeSourceChapters(own, listOf(SourceChapters("dex", "t", listOf(ch("d1", "1"), ch("d2", "2")))))
        val alternatives = alternativeCopies(merged, merged[0])
        assertEquals(2, alternatives.size)
        assertTrue(LinkedChapterId.isLinked(alternatives[0].id))
        assertEquals("m1b", alternatives[1].id)
        assertTrue(alternativeCopies(merged, merged.last()).isEmpty())
    }

    @Test
    fun idsMoveWithTheMainSource() {
        val oldMain = "lib" to "t1"
        val newMain = "dex" to "t2"
        val own = "/ch/1"
        val onNew = LinkedChapterId.encode("dex", "t2", "c9")
        val onThird = LinkedChapterId.encode("other", "t3", "c5")
        assertEquals(LinkedChapterId.encode("lib", "t1", "/ch/1"), chapterIdForNewMain(own, oldMain, newMain))
        assertEquals("c9", chapterIdForNewMain(onNew, oldMain, newMain))
        assertEquals(onThird, chapterIdForNewMain(onThird, oldMain, newMain))
        // Making the old main the main source again restores the original ids.
        assertEquals(own, chapterIdForNewMain(chapterIdForNewMain(own, oldMain, newMain), newMain, oldMain))
        assertFalse(LinkedChapterId.isLinked(chapterIdForNewMain(onNew, oldMain, newMain)))
    }

    private fun row(c: Chapter) = LibraryChapterEntity("lib", "t", c.id, dedupeKey(c), c.title)

    @Test
    fun aNewlyLinkedSourceReportsNothingNew() {
        val own = listOf(ch("m2", "2"), ch("m1", "1"))
        val known = own.map(::row)
        val merged = mergeSourceChapters(own, listOf(SourceChapters("dex", "x", listOf(ch("d3", "3"), ch("d2", "2")))))
        assertTrue(findNewChapters(known, merged).isEmpty())
    }

    @Test
    fun theSourceThatHasItFirstGetsTheNewChapter() {
        val dex = { list: List<Chapter> -> SourceChapters("dex", "x", list) }
        val before = mergeSourceChapters(listOf(ch("m1", "1")), listOf(dex(listOf(ch("d1", "1")))))
        val known = before.map(::row)
        // Chapter 2 appears on the linked source only: it is new, and it is the linked copy.
        val now = mergeSourceChapters(listOf(ch("m1", "1")), listOf(dex(listOf(ch("d2", "2"), ch("d1", "1")))))
        val fresh = findNewChapters(known, now)
        assertEquals(1, fresh.size)
        assertEquals("d2", LinkedChapterId.decode(fresh[0].id)!!.chapterId)
        // Later the main source gets chapter 2 too: not new again.
        val later = mergeSourceChapters(listOf(ch("m2", "2"), ch("m1", "1")), listOf(dex(listOf(ch("d2", "2"), ch("d1", "1")))))
        assertTrue(findNewChapters((now).map(::row), later).isEmpty())
    }

    @Test
    fun aSourceThatFailedLosesNoChapters() {
        val merged = mergeSourceChapters(listOf(ch("m1", "1")), listOf(SourceChapters("dex", "x", listOf(ch("d2", "2")))))
        val known = merged.map(::row)
        // This time only the main source answered.
        assertTrue(removedChapterIds(known, listOf(ch("m1", "1"))).isEmpty())
    }
}
