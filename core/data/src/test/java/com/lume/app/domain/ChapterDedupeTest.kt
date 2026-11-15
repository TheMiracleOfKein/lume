package com.lume.app.domain

import com.lume.app.data.db.LibraryChapterEntity
import com.lume.app.data.library.findNewChapters
import com.lume.app.data.library.removedChapterIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDedupeTest {
    private fun ch(id: String, number: String?, branch: String? = null, team: String? = null, volume: String? = "1") =
        Chapter(id = id, title = "Ch $number", url = id, number = number, volume = volume, branchId = branch, branchName = team)

    // LibSocial-style list: chapters 3..1, team A has all, team B has 3 and 2.
    private val list = listOf(
        ch("3a", "3", "A", "Team A"),
        ch("3b", "3", "B", "Team B"),
        ch("2b", "2", "B", "Team B"),
        ch("2a", "2", "A", "Team A"),
        ch("1a", "1", "A", "Team A"),
    )

    @Test
    fun keyIgnoresTeamAndNumberFormatting() {
        assertEquals(dedupeKey(ch("x", "5", "A")), dedupeKey(ch("y", "5.0", "B")))
        assertEquals(dedupeKey(ch("x", "5,5")), dedupeKey(ch("y", "5.5")))
        assertNotEquals(dedupeKey(ch("x", "5", volume = "1")), dedupeKey(ch("y", "5", volume = "2")))
        // Without a number nothing is merged.
        assertNotEquals(dedupeKey(ch("x", null)), dedupeKey(ch("y", null)))
    }

    @Test
    fun branchesAreListedMostChaptersFirst() {
        val branches = chapterBranches(list)
        assertEquals(listOf("A", "B"), branches.map { it.id })
        assertEquals("Team B", branches[1].name)
        assertEquals(3, branches[0].chapterCount)
        assertTrue(chapterBranches(list.filter { it.branchId == "A" }).isEmpty())
    }

    @Test
    fun perChapterBranchIdsAreNotTeams() {
        // Asura keeps each chapter's slug in branchId: no chapter has two copies, so no teams.
        val list = listOf(ch("2", "2", "slug-2"), ch("1", "1", "slug-1"))
        assertTrue(chapterBranches(list).isEmpty())
        assertEquals(listOf("2", "1"), dedupeChapters(list).map { it.id })
    }

    @Test
    fun dedupeKeepsOneCopyFromTheBiggestTeam() {
        assertEquals(listOf("3a", "2a", "1a"), dedupeChapters(list).map { it.id })
    }

    @Test
    fun preferredTeamWinsWhereItHasTheChapter() {
        assertEquals(listOf("3b", "2b", "1a"), dedupeChapters(list, preferredBranch = "B").map { it.id })
    }

    @Test
    fun singleTeamListOnlyLosesExactDuplicates() {
        val single = listOf(ch("1", "1"), ch("1", "1"), ch("x", null), ch("y", null))
        assertEquals(listOf("1", "x", "y"), dedupeChapters(single).map { it.id })
    }

    private fun known(id: String, number: String) =
        LibraryChapterEntity("s", "t", id, dedupeKey(ch(id, number)), "Ch $number")

    @Test
    fun newChaptersAreFoundByIdNotCount() {
        val before = listOf(known("1a", "1"), known("2a", "2"))
        // The site dropped chapter 1 and added chapter 3: the count is unchanged, 3 is still new.
        val now = listOf(ch("3a", "3", "A"), ch("2a", "2", "A"))
        assertEquals(listOf("3a"), findNewChapters(before, now).map { it.id })
    }

    @Test
    fun anotherTeamsCopyIsNotNew() {
        val before = listOf(known("2a", "2"))
        val now = listOf(ch("3b", "3", "B"), ch("3a", "3", "A"), ch("2b", "2", "B"), ch("2a", "2", "A"))
        assertEquals(listOf("3b"), findNewChapters(before, now).map { it.id })
    }

    @Test
    fun removalsAreIgnoredWhenMostOfTheListVanished() {
        val before = (1..10).map { known("c$it", "$it") }
        assertEquals(listOf("c1"), removedChapterIds(before, (2..10).map { ch("c$it", "$it") }))
        assertTrue(removedChapterIds(before, listOf(ch("c10", "10"))).isEmpty())
    }
}
