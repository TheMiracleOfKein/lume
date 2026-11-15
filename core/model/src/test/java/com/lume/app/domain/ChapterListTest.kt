package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterListTest {
    private fun ch(id: String, number: String, branch: String? = null) =
        Chapter(id = id, title = "Ch $number", url = id, number = number, volume = "1", branchId = branch)

    // Source order: newest first. Team A has 1-3, team B has 2-3.
    private val chapters = listOf(
        ch("3a", "3", "A"), ch("3b", "3", "B"),
        ch("2a", "2", "A"), ch("2b", "2", "B"),
        ch("1a", "1", "A"),
    )

    private fun view(options: ChapterListOptions, readIds: Set<String> = emptySet(), downloaded: Set<String> = emptySet()) =
        chapterListView(chapters, options, ChapterReadState(chapters, readIds), downloaded, emptySet()).map { it.id }

    @Test
    fun defaultIsOnePerChapterNewestFirst() {
        assertEquals(listOf("3a", "2a", "1a"), view(ChapterListOptions()))
    }

    @Test
    fun ascendingAndDuplicates() {
        assertEquals(listOf("1a", "2a", "2b", "3a", "3b"), view(ChapterListOptions(ascending = true, showDuplicates = true)))
    }

    @Test
    fun readingAnotherTeamsCopyCountsAsRead() {
        val unread = view(ChapterListOptions(filter = ChapterFilter.UNREAD), readIds = setOf("2b"))
        assertEquals(listOf("3a", "1a"), unread)
        val read = view(ChapterListOptions(filter = ChapterFilter.READ, branch = "B"), readIds = setOf("2a"))
        assertEquals(listOf("2b"), read)
    }

    @Test
    fun filtersCombine() {
        val options = ChapterListOptions(filter = ChapterFilter.UNREAD or ChapterFilter.DOWNLOADED)
        assertEquals(listOf("3a"), view(options, readIds = setOf("1a"), downloaded = setOf("3a", "1a")))
    }

    @Test
    fun previousChaptersAndNextUnread() {
        val list = dedupeChapters(chapters)
        assertEquals(listOf("1a", "2a"), chaptersBefore(list, list.first { it.id == "3a" }).map { it.id })
        assertEquals(emptyList<String>(), chaptersBefore(list, list.first { it.id == "1a" }).map { it.id })
        val read = ChapterReadState(chapters, setOf("1a"))
        assertEquals(listOf("2a"), nextUnread(list, read, 1).map { it.id })
        assertEquals(listOf("2a", "3a"), nextUnread(list, read, null).map { it.id })
    }
}
