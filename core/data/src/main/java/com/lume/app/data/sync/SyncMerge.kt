package com.lume.app.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * One record in a sync file: a library entry, a read mark, a reading position and so on.
 * [changedAt] is when some device last saw it change; [deleted] marks a removal, kept for a while
 * so the other devices remove it too. [device] is the id of the device that stamped it; it settles
 * ties between equal times the same way on every device.
 */
@Serializable
data class SyncEntry(
    val key: String,
    val changedAt: Long,
    val deleted: Boolean = false,
    val data: JsonElement? = null,
    val device: String = "",
)

/**
 * What one device writes to the shared place: its whole merged view, collection by collection.
 * Each device writes only its own file, so two devices never write the same file.
 */
@Serializable
data class SyncFile(
    val format: Int = FORMAT,
    val deviceId: String,
    val deviceName: String = "",
    val writtenAt: Long = 0L,
    /** Marker of the database this state was made from (see [SyncMerge.databaseRecreated]); 0 when unknown. */
    val databaseId: Int = 0,
    val collections: Map<String, List<SyncEntry>> = emptyMap(),
) {
    companion object {
        const val FORMAT = 1
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}

/** Collection name → record key → entry. */
typealias SyncState = Map<String, Map<String, SyncEntry>>

/** A change to make in the local database after a merge. */
sealed interface SyncChange {
    val collection: String
    val key: String

    data class Put(override val collection: String, override val key: String, val data: JsonElement) : SyncChange
    data class Remove(override val collection: String, override val key: String) : SyncChange
}

/**
 * The merge rules, kept free of Android so they can be tested on the JVM.
 *
 * - [stamp] compares what the database holds now with what this device wrote last time: new or
 *   changed records get the current time, unchanged ones keep theirs, missing ones become removals.
 *   On the first sync of a place ([stamp]'s `bootstrap`) records without a history get time 0, so a
 *   removal or edit made on another device wins over this device's untouched copy. So on a first
 *   sync, records another device already has keep that device's version.
 * - [merge] picks one winner per record across every device: the newest change wins, and equal
 *   times go to the larger device id so every device picks the same winner. Chapter
 *   positions are different: the furthest position wins, unless the newest change resets it; "Continue" goes to the title read last.
 * - [changes] lists what the database needs so it matches the winners.
 */
object SyncMerge {
    /**
     * Removals are remembered this long. A removal is a few bytes, so it is kept for ten years: a
     * device that comes back online after months must not bring deleted records back.
     */
    const val TOMBSTONE_DAYS = 3650L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Chapter positions: the furthest one wins (see [merge]). */
    const val FURTHEST_WINS = "chapter_progress"

    /**
     * "Continue" per title: the one read last wins, by its own reading time, since a device may
     * sync hours after reading.
     */
    const val LAST_READ_WINS = "progress"

    const val LIBRARY = "library"

    /**
     * The time to stamp local changes with: the clock, but always after every stamp seen so far
     * ([lastSeen]: this device's saved state and the other devices' files). A device whose clock is
     * behind then still wins over what it has already seen.
     */
    fun stampTime(now: Long, lastSeen: Long): Long = maxOf(now, lastSeen + 1)

    /** The highest [SyncEntry.changedAt] in the states; 0 when they are empty. */
    fun highestStamp(vararg states: SyncState): Long =
        states.maxOfOrNull { s -> s.values.maxOfOrNull { c -> c.values.maxOfOrNull { it.changedAt } ?: 0L } ?: 0L } ?: 0L

    fun stamp(
        current: Map<String, Map<String, JsonElement>>,
        previous: SyncState,
        now: Long,
        bootstrap: Boolean,
        device: String,
    ): SyncState {
        val fresh = if (bootstrap) 0L else now
        val collections = current.keys + previous.keys
        return collections.associateWith { name ->
            val live = current[name].orEmpty()
            val before = previous[name].orEmpty()
            val out = HashMap<String, SyncEntry>()
            for ((key, data) in live) {
                val old = before[key]
                out[key] = if (old != null && !old.deleted && old.data == data) old else SyncEntry(key, fresh, data = data, device = device)
            }
            for ((key, old) in before) {
                if (key in live) continue
                out[key] = if (old.deleted) old else SyncEntry(key, now, deleted = true, device = device)
            }
            out
        }.filterValues { it.isNotEmpty() }
    }

    fun merge(own: SyncState, others: List<SyncState>, now: Long): SyncState {
        val all = listOf(own) + others
        val names = all.flatMap { it.keys }.toSet()
        val cutoff = now - TOMBSTONE_DAYS * DAY_MS
        return names.associateWith { name ->
            val keys = all.flatMap { it[name].orEmpty().keys }.toSet()
            keys.mapNotNull { key ->
                val candidates = all.mapNotNull { it[name]?.get(key) }
                val winner = when (name) {
                    FURTHEST_WINS -> furthest(candidates)
                    LAST_READ_WINS -> candidates.maxWithOrNull(compareBy<SyncEntry>({ readAt(it) }, { it.changedAt }, { if (it.deleted) 1 else 0 }, { it.device }))
                    else -> newest(candidates)
                }
                winner?.takeUnless { it.deleted && it.changedAt < cutoff }
            }.associateBy { it.key }
        }.filterValues { it.isNotEmpty() }
    }

    fun changes(local: Map<String, Map<String, JsonElement>>, merged: SyncState): List<SyncChange> {
        val out = mutableListOf<SyncChange>()
        for ((name, entries) in merged) {
            val have = local[name].orEmpty()
            for (entry in entries.values) {
                val data = entry.data
                when {
                    entry.deleted -> if (entry.key in have) out += SyncChange.Remove(name, entry.key)
                    data != null && have[entry.key] != data -> out += SyncChange.Put(name, entry.key, data)
                }
            }
        }
        return out
    }

    /**
     * True when the database file was recreated since the last sync (a reinstall, cleared data, a
     * failed upgrade): its marker differs from the one saved. Then its records must not turn into
     * removals on every device. A state saved before markers existed (0) is never taken for that.
     */
    fun databaseRecreated(savedDatabaseId: Int, currentDatabaseId: Int): Boolean =
        savedDatabaseId != 0 && savedDatabaseId != currentDatabaseId

    private fun newest(candidates: List<SyncEntry>): SyncEntry? =
        candidates.maxWithOrNull(compareBy<SyncEntry> { it.changedAt }.thenBy { if (it.deleted) 1 else 0 }.thenBy { it.device })

    private fun furthest(candidates: List<SyncEntry>): SyncEntry? {
        val latest = newest(candidates) ?: return null
        if (latest.deleted) return latest
        val lastReset = candidates.filter { it.deleted }.maxOfOrNull { it.changedAt } ?: Long.MIN_VALUE
        val live = candidates.filter { !it.deleted && it.changedAt > lastReset }
        val best = live.maxWithOrNull(compareBy<SyncEntry>({ position(it).first }, { position(it).second }, { position(it).third }, { it.changedAt }, { it.device }))
            ?: return latest
        // The winner carries the newest time, so devices agree it is current.
        return best.copy(changedAt = live.maxOf { it.changedAt })
    }

    /** (finished, page, offset inside the page) of a chapter position. */
    private fun position(entry: SyncEntry): Triple<Int, Int, Float> {
        val obj = entry.data as? JsonObject ?: return Triple(0, 0, 0f)
        val done = obj["completed"]?.jsonPrimitive?.booleanOrNull == true
        val page = obj["pageIndex"]?.jsonPrimitive?.intOrNull ?: 0
        val offset = obj["pageOffset"]?.jsonPrimitive?.floatOrNull ?: 0f
        return Triple(if (done) 1 else 0, page, offset)
    }

    /** When a "Continue" pointer was read; a removal counts from when it happened. */
    private fun readAt(entry: SyncEntry): Long {
        if (entry.deleted) return entry.changedAt
        val obj = entry.data as? JsonObject ?: return entry.changedAt
        return obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: entry.changedAt
    }
}
