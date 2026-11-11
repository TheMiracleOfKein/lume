package com.lume.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KotatsuBackupTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val categories = """[{"category_id":1,"created_at":1,"sort_key":0,"title":"Reading","order":"NEWEST","track":true,"show_in_lib":true,"extra":5}]"""
    private val favourites = """[
        {"manga_id":10,"category_id":1,"sort_key":0,"pinned":false,"created_at":1700000000000,
         "manga":{"id":10,"title":"Solo","alt_title":null,"url":"/manga/solo","public_url":"https://asurascans.com/series/solo","rating":0.5,"nsfw":false,"cover_url":"https://c/1.jpg","large_cover_url":null,"state":"ONGOING","author":"A","source":"ASURASCANS","tags":[{"id":1,"title":"x","key":"x","source":"ASURASCANS"}],"unknown":1}},
        {"manga_id":10,"category_id":2,"sort_key":1,"created_at":1800000000000,"manga":{"id":10,"title":"Solo","url":"/manga/solo","public_url":"","source":"ASURASCANS"}},
        {"manga_id":11,"category_id":99,"sort_key":2,"created_at":5,"manga":{"id":11,"title":"Other","url":"https://x.com/a","public_url":"","source":"MANGADEX"}},
        {"manga_id":12,"category_id":1,"sort_key":3,"created_at":5}
    ]"""

    @Test
    fun parsesZipAndMergesFavourites() {
        val bytes = zip(
            "index" to """[{"app_version":1}]""",
            "categories" to categories,
            "favourites" to favourites,
            "history" to "not json at all",
            "settings" to "{}",
        )
        assertTrue(KotatsuBackupFormat.isZip(bytes))
        val backup = KotatsuBackupFormat.parse(bytes)
        val titles = backup.titles()
        assertEquals(2, titles.size) // manga 12 has no manga object
        val solo = titles.first { it.manga.id == 10L }
        assertEquals(listOf("Reading"), solo.categoryNames) // category 2 isn't in the file
        assertEquals(1_700_000_000_000, solo.createdAt)
        assertEquals("https://c/1.jpg", solo.manga.coverUrl)
        assertEquals(emptyList<String>(), titles.first { it.manga.id == 11L }.categoryNames)
    }

    @Test
    fun entryNamesMayHaveJsonSuffixAndFolder() {
        val backup = KotatsuBackupFormat.parse(zip("Favourites.json" to favourites, "dir/categories.json" to categories))
        assertEquals(2, backup.titles().size)
        assertEquals(1, backup.categories.size)
    }

    @Test
    fun missingEntriesGiveEmptyLists() {
        val onlyFavourites = KotatsuBackupFormat.parse(zip("favourites" to favourites))
        assertTrue(onlyFavourites.categories.isEmpty())
        assertEquals(2, onlyFavourites.titles().size)
        val onlyCategories = KotatsuBackupFormat.parse(zip("categories" to categories))
        assertTrue(onlyCategories.titles().isEmpty())
    }

    @Test
    fun brokenFavouritesAreEmpty() {
        val backup = KotatsuBackupFormat.parse(zip("categories" to categories, "favourites" to "{oops"))
        assertTrue(backup.titles().isEmpty())
    }

    @Test
    fun zipWithoutLibraryEntriesIsRejected() {
        try {
            KotatsuBackupFormat.parse(zip("history" to "[]", "other.txt" to "hi"))
            fail("expected an error")
        } catch (_: IOException) {
        }
    }

    @Test
    fun oversizedEntryIsRejected() {
        val big = "[" + "0,".repeat(5000) + "0]"
        try {
            KotatsuBackupFormat.parse(zip("favourites" to big), maxBytes = 1000)
            fail("expected an error")
        } catch (_: IOException) {
        }
    }

    @Test
    fun detectsZipOnly() {
        assertFalse(KotatsuBackupFormat.isZip("{\"version\":3}".toByteArray()))
        assertFalse(KotatsuBackupFormat.isZip(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0)))
    }

    @Test
    fun linksRelativeAndFull() {
        val manga = KotatsuManga(url = "/manga/solo", publicUrl = "https://asurascans.com/series/solo")
        assertEquals(
            listOf("https://asurascans.com/series/solo", "https://asurascans.com/manga/solo"),
            KotatsuBackupFormat.linkCandidates(manga, "https://asurascans.com/"),
        )
        assertEquals(
            listOf("https://a.com/b/c"),
            KotatsuBackupFormat.linkCandidates(KotatsuManga(url = "b/c"), "https://a.com"),
        )
        assertEquals(
            listOf("https://x.com/a"),
            KotatsuBackupFormat.linkCandidates(KotatsuManga(url = "https://x.com/a", publicUrl = "https://x.com/a"), "https://a.com"),
        )
        assertTrue(KotatsuBackupFormat.linkCandidates(KotatsuManga(), "https://a.com").isEmpty())
    }

    @Test
    fun sourceNamesMatchLumeSources() {
        val lume = listOf("mangadex" to "MangaDex", "asura" to "Asura Scans", "mangakatana" to "MangaKatana", "mangalib" to "MangaLib")
        assertEquals("mangadex", KotatsuBackupFormat.matchSource("MANGADEX", lume))
        assertEquals("asura", KotatsuBackupFormat.matchSource("ASURASCANS", lume))
        assertEquals("mangakatana", KotatsuBackupFormat.matchSource("MANGA_KATANA", lume))
        assertEquals("mangalib", KotatsuBackupFormat.matchSource("MANGALIB", lume))
        assertNull(KotatsuBackupFormat.matchSource("COMICK", lume))
        assertNull(KotatsuBackupFormat.matchSource("LOCAL", listOf("local" to "Local")))
        assertNull(KotatsuBackupFormat.matchSource("", lume))
    }
}
