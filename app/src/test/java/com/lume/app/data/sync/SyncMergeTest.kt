package com.lume.app.data.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val lib = SyncMerge.LIBRARY
    private val pos = SyncMerge.FURTHEST_WINS
    private val cont = SyncMerge.LAST_READ_WINS

    private fun title(name: String): JsonElement = buildJsonObject { put("titleName", name) }

    private fun spot(page: Int, offset: Float = 0f, done: Boolean = false): JsonElement = buildJsonObject {
        put("pageIndex", page)
        put("pageOffset", offset)
        put("completed", done)
    }

    private fun state(name: String, vararg entries: SyncEntry): SyncState = mapOf(name to entries.associateBy { it.key })

    @Test
    fun stampKeepsTimesOfUnchangedRecordsAndMarksRemovals() {
        val previous = state(lib, SyncEntry("a", 100, data = title("A")), SyncEntry("b", 100, data = title("B")))
        val current = mapOf(lib to mapOf("a" to title("A"), "c" to title("C")))
        val stamped = SyncMerge.stamp(current, previous, now = 500)[lib]!!
        assertEquals(100L, stamped["a"]!!.changedAt)
        assertEquals(500L, stamped["c"]!!.changedAt)
        assertTrue(stamped["b"]!!.deleted)
        assertEquals(500L, stamped["b"]!!.changedAt)
    }

    @Test
    fun stampGivesChangedRecordsANewTime() {
        val previous = state(lib, SyncEntry("a", 100, data = title("A")))
        val stamped = SyncMerge.stamp(mapOf(lib to mapOf("a" to title("A2"))), previous, now = 500)
        assertEquals(500L, stamped[lib]!!["a"]!!.changedAt)
    }

    @Test
    fun aRecordAddedAgainAfterItsRemovalIsNew() {
        val previous = state(lib, SyncEntry("a", 100, deleted = true))
        val stamped = SyncMerge.stamp(mapOf(lib to mapOf("a" to title("A"))), previous, now = 500)[lib]!!["a"]!!
        assertFalse(stamped.deleted)
        assertEquals(500L, stamped.changedAt)
    }

    @Test
    fun newestChangeWinsAndRemovalsSpread() {
        val own = state(lib, SyncEntry("a", 100, data = title("A")), SyncEntry("b", 300, data = title("B")))
        val other = state(lib, SyncEntry("a", 200, deleted = true), SyncEntry("b", 250, data = title("B old")), SyncEntry("c", 50, data = title("C")))
        val merged = SyncMerge.merge(own, listOf(other), now = 1_000)[lib]!!
        assertTrue(merged["a"]!!.deleted)
        assertEquals(title("B"), merged["b"]!!.data)
        assertEquals(title("C"), merged["c"]!!.data)

        val changes = SyncMerge.changes(mapOf(lib to mapOf("a" to title("A"), "b" to title("B"))), mapOf(lib to merged))
        assertEquals(
            setOf<SyncChange>(SyncChange.Remove(lib, "a"), SyncChange.Put(lib, "c", title("C"))),
            changes.toSet(),
        )
    }

    @Test
    fun theFurthestChapterSpotWins() {
        val own = state(pos, SyncEntry("ch", 900, data = spot(3)))
        val other = state(pos, SyncEntry("ch", 500, data = spot(12, 0.4f)))
        val merged = SyncMerge.merge(own, listOf(other), now = 1_000)[pos]!!["ch"]!!
        assertEquals(spot(12, 0.4f), merged.data)
        assertEquals(900L, merged.changedAt)
    }

    @Test
    fun aFinishedChapterBeatsAnyPage() {
        val own = state(pos, SyncEntry("ch", 100, data = spot(0, done = true)))
        val other = state(pos, SyncEntry("ch", 200, data = spot(30)))
        assertEquals(spot(0, done = true), SyncMerge.merge(own, listOf(other), now = 1_000)[pos]!!["ch"]!!.data)
    }

    @Test
    fun aResetChapterSpotStaysReset() {
        // Marked unread here (the spot was dropped) after the other device read further.
        val own = state(pos, SyncEntry("ch", 700, deleted = true))
        val other = state(pos, SyncEntry("ch", 600, data = spot(20)))
        assertTrue(SyncMerge.merge(own, listOf(other), now = 1_000)[pos]!!["ch"]!!.deleted)

        // Read again after the reset: the newer spots count, the old furthest one doesn't.
        val later = state(pos, SyncEntry("ch", 800, data = spot(2)))
        val merged = SyncMerge.merge(own, listOf(other, later), now = 1_000)[pos]!!["ch"]!!
        assertEquals(spot(2), merged.data)
    }

    @Test
    fun continueGoesToTheTitleReadLast() {
        fun pointer(chapter: String, readAt: Long) = buildJsonObject {
            put("chapterId", chapter)
            put("updatedAt", readAt)
        }
        // Read here at 400 but synced at 900; read there at 600 and synced at 650.
        val own = state(cont, SyncEntry("t", 900, data = pointer("5", 400)))
        val other = state(cont, SyncEntry("t", 650, data = pointer("7", 600)))
        assertEquals(pointer("7", 600), SyncMerge.merge(own, listOf(other), now = 1_000)[cont]!!["t"]!!.data)
    }

    @Test
    fun oldRemovalsAreForgotten() {
        val day = 24L * 60 * 60 * 1000
        val own = state(lib, SyncEntry("gone", 0, deleted = true), SyncEntry("recent", 199 * day, deleted = true))
        val merged = SyncMerge.merge(own, emptyList(), now = 200 * day)[lib]!!
        assertFalse("gone" in merged)
        assertTrue("recent" in merged)
    }

    @Test
    fun aWipedDatabaseIsNotTakenForRemovals() {
        val previous = state(lib, SyncEntry("a", 100, data = title("A")))
        assertTrue(SyncMerge.looksWiped(emptyMap(), previous))
        assertFalse(SyncMerge.looksWiped(mapOf(lib to mapOf("b" to title("B"))), previous))
        assertFalse(SyncMerge.looksWiped(emptyMap(), emptyMap()))
    }

    @Test
    fun unchangedRecordsNeedNoChanges() {
        val data = title("A")
        val merged = mapOf(lib to mapOf("a" to SyncEntry("a", 1, data = data)))
        assertTrue(SyncMerge.changes(mapOf(lib to mapOf("a" to title("A"))), merged).isEmpty())
        assertEquals(listOf(SyncChange.Put(lib, "a", data)), SyncMerge.changes(mapOf(lib to mapOf("a" to JsonPrimitive("old"))), merged))
    }
}
