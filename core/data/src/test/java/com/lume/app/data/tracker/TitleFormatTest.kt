package com.lume.app.data.tracker

import com.lume.app.domain.MediaKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsEveryTrackersFormatNames() {
        // AniList: one MANGA format, the country tells manhwa and manhua apart.
        assertEquals(TitleFormat.Manga, TitleFormat.parse("MANGA", "JP"))
        assertEquals(TitleFormat.Manhwa, TitleFormat.parse("MANGA", "KR"))
        assertEquals(TitleFormat.Manhua, TitleFormat.parse("MANGA", "CN"))
        assertEquals(TitleFormat.Novel, TitleFormat.parse("NOVEL"))
        assertEquals(TitleFormat.OneShot, TitleFormat.parse("ONE_SHOT"))
        // MyAnimeList and Shikimori.
        assertEquals(TitleFormat.LightNovel, TitleFormat.parse("light_novel"))
        assertEquals(TitleFormat.Manhwa, TitleFormat.parse("manhwa"))
        assertEquals(TitleFormat.Doujinshi, TitleFormat.parse("doujin"))
        // Kitsu and MangaUpdates.
        assertEquals(TitleFormat.OneShot, TitleFormat.parse("oneshot"))
        assertEquals(TitleFormat.Comic, TitleFormat.parse("OEL"))
        assertEquals(TitleFormat.Novel, TitleFormat.parse("Novel"))
        assertNull(TitleFormat.parse(null))
        assertNull(TitleFormat.parse("music"))
    }

    @Test
    fun readsTheYear() {
        assertEquals(2018, TitleFormat.year("2018-01-12"))
        assertEquals(2018, TitleFormat.year("2018"))
        assertNull(TitleFormat.year(""))
        assertNull(TitleFormat.year(null))
    }

    @Test
    fun aManhwaIsNeverOfferedForANovel() {
        val manhwa = TrackerSearchResult("1", "Omniscient Reader", format = TitleFormat.Manhwa)
        val novel = TrackerSearchResult("2", "Omniscient Reader", format = TitleFormat.WebNovel)
        val unknown = TrackerSearchResult("3", "Omniscient Reader")
        assertFalse(manhwa.matchesKind(MediaKind.Novel))
        assertTrue(novel.matchesKind(MediaKind.Novel))
        assertTrue(manhwa.matchesKind(MediaKind.Comics))
        assertFalse(novel.matchesKind(MediaKind.Comics))
        assertTrue(unknown.matchesKind(MediaKind.Novel) && unknown.matchesKind(MediaKind.Comics))
    }

    @Test
    fun searchResultsCarryFormatAndYear() {
        val mal = """{"data":[{"node":{"id":10,"title":"Overlord","media_type":"light_novel","start_date":"2010-07-30"}}]}"""
        val r = parseMalSearch(json, mal, MediaKind.Novel).single()
        assertEquals(TitleFormat.LightNovel, r.format)
        assertEquals(2010, r.year)

        val shiki = """[{"id":5,"name":"Mushoku Tensei","kind":"light_novel","aired_on":"2014-01-23"}]"""
        val s = parseShikimoriSearch(json, shiki, MediaKind.Novel, "https://shikimori.io").single()
        assertEquals(TitleFormat.LightNovel, s.format)
        assertEquals(2014, s.year)
    }
}
