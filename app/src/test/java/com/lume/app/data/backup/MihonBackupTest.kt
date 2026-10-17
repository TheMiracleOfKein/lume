@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.lume.app.data.backup

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class MihonBackupTest {
    private val sample = MihonBackup(
        backupManga = listOf(
            MihonManga(
                source = 2499283573021220255,
                url = "/manga/a1c7c817-4e59-43b7-9365-09675a149a6f",
                title = "One Piece",
                genre = listOf("Action"),
                chapters = listOf(
                    MihonChapter(url = "/chapter/1", name = "Ch. 1", read = true, chapterNumber = 1f),
                    MihonChapter(url = "/chapter/2", name = "Ch. 2", read = false, chapterNumber = 2f),
                    MihonChapter(url = "/chapter/x", name = "Extra", read = true, chapterNumber = -1f),
                ),
                categories = listOf(1),
                tracking = listOf(MihonTracking(syncId = 2, mediaId = 30013, trackingUrl = "https://anilist.co/manga/30013")),
                history = listOf(MihonHistory(url = "/chapter/1", lastRead = 1_700_000_000_000)),
            ),
        ),
        backupCategories = listOf(MihonCategory(name = "Reading", order = 1)),
        backupSources = listOf(MihonSource(name = "MangaDex", sourceId = 2499283573021220255)),
    )

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    @Test
    fun parsesGzippedProtobuf() {
        val bytes = gzip(ProtoBuf.encodeToByteArray(MihonBackup.serializer(), sample))
        assertTrue(MihonBackupFormat.isGzip(bytes))
        val parsed = MihonBackupFormat.parse(bytes)
        assertEquals(sample, parsed)
        assertEquals(setOf(1f), MihonBackupFormat.readNumbers(parsed.backupManga.single()))
    }

    @Test
    fun plainJsonIsNotGzip() {
        assertFalse(MihonBackupFormat.isGzip("{\"version\":3}".toByteArray()))
    }

    @Test
    fun sourcesMatchByName() {
        val lume = listOf("mangadex" to "MangaDex", "asura" to "Asura Scans", "mangalib" to "MangaLib", "remanga" to "ReManga")
        assertEquals("mangadex", MihonBackupFormat.matchSource("MangaDex", lume))
        assertEquals("mangadex", MihonBackupFormat.matchSource("MangaDex (EN)", lume))
        assertEquals("asura", MihonBackupFormat.matchSource("AsuraScans", lume))
        assertEquals("remanga", MihonBackupFormat.matchSource("Remanga", lume))
        assertNull(MihonBackupFormat.matchSource("Comick", lume))
    }

    @Test
    fun trackersAndStatus() {
        assertEquals("anilist", MihonBackupFormat.trackerId(2))
        assertEquals("shikimori", MihonBackupFormat.trackerId(4))
        assertNull(MihonBackupFormat.trackerId(6))
        assertEquals("Completed", MihonBackupFormat.status(4))
    }

    @Test
    fun lumeBackupKeepsSettings() {
        val payload = BackupPayload(settings = listOf(SettingDto("dark_theme", "bool", "false")))
        val parsed = BackupPayload.parse(BackupPayload.json.encodeToString(BackupPayload.serializer(), payload))
        assertEquals(3, parsed.version)
        assertEquals("dark_theme", parsed.settings.single().key)
        // Version 2 files have no settings.
        assertTrue(BackupPayload.parse("{\"version\":2}").settings.isEmpty())
    }
}
