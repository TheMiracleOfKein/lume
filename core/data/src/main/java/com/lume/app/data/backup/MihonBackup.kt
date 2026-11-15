@file:OptIn(ExperimentalSerializationApi::class)

package com.lume.app.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/*
 * The parts of a Mihon / Tachiyomi backup (`.tachibk`, `.proto.gz`) that Lume imports. Field
 * numbers follow Mihon's `eu.kanade.tachiyomi.data.backup.models`; unknown fields are skipped.
 */

@Serializable
data class MihonBackup(
    @ProtoNumber(1) val backupManga: List<MihonManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<MihonCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<MihonSource> = emptyList(),
)

@Serializable
data class MihonManga(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(16) val chapters: List<MihonChapter> = emptyList(),
    /** Category *order* values (Mihon matches categories by order). */
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<MihonTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(104) val history: List<MihonHistory> = emptyList(),
)

@Serializable
data class MihonChapter(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0f,
)

@Serializable
data class MihonCategory(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val order: Long = 0,
)

@Serializable
data class MihonSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
)

@Serializable
data class MihonTracking(
    @ProtoNumber(1) val syncId: Int = 0,
    @ProtoNumber(2) val libraryId: Long = 0,
    @ProtoNumber(3) val mediaIdInt: Int = 0,
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastChapterRead: Float = 0f,
    @ProtoNumber(8) val score: Float = 0f,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(100) val mediaId: Long = 0,
)

@Serializable
data class MihonHistory(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val lastRead: Long = 0,
)

object MihonBackupFormat {
    /** True for gzip data (Mihon backups are gzipped; Lume's own JSON backups are not). */
    fun isGzip(bytes: ByteArray): Boolean =
        bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    fun parse(bytes: ByteArray): MihonBackup {
        val raw = if (isGzip(bytes)) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        return ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), raw)
    }

    /** Lume's tracker id for a Mihon tracker (`syncId`), or null for trackers Lume doesn't have. */
    fun trackerId(syncId: Int): String? = when (syncId) {
        1 -> "myanimelist"
        2 -> "anilist"
        3 -> "kitsu"
        4 -> "shikimori"
        7 -> "mangaupdates"
        else -> null
    }

    /** Mihon's manga status codes as the status text Lume shows. */
    fun status(code: Int): String? = when (code) {
        1 -> "Ongoing"
        2, 4 -> "Completed"
        5 -> "Cancelled"
        6 -> "On hiatus"
        else -> null
    }

    /** Lowercase letters and digits only: "Asura Scans" and "asurascans" compare equal. */
    fun normalName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Picks the Lume source for a Mihon source name: exact (normalised) name first, then a Lume
     * name that starts the Mihon one ("MangaDex" for "MangaDex (EN)"), then the other way round.
     */
    fun matchSource(mihonName: String, lume: List<Pair<String, String>>): String? {
        val wanted = normalName(mihonName.substringBefore('(').ifBlank { mihonName })
        if (wanted.isEmpty()) return null
        val byName = lume.map { (id, name) -> id to normalName(name) }
        byName.firstOrNull { it.second == wanted }?.let { return it.first }
        byName.firstOrNull { it.second.isNotEmpty() && wanted.startsWith(it.second) }?.let { return it.first }
        return byName.firstOrNull { it.second.startsWith(wanted) }?.first
    }

    /** Chapter numbers Mihon marked read (numbers ≤ 0 are unknown and skipped). */
    fun readNumbers(manga: MihonManga): Set<Float> =
        manga.chapters.filter { it.read && it.chapterNumber > 0f }.mapTo(HashSet()) { it.chapterNumber }
}
