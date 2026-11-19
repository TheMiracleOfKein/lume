package com.lume.app.data.tracker

import com.lume.app.domain.MediaKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelTrackersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aniListSearchFiltersByFormat() {
        val novel = aniListSearchBody(MediaKind.Novel, "\"Mushoku\"")
        assertTrue(novel.contains("type:MANGA,format_in:[NOVEL]"))
        val comics = aniListSearchBody(MediaKind.Comics, "\"Mushoku\"")
        assertTrue(comics.contains("format_not_in:[NOVEL]"))
        // The body stays valid JSON.
        json.parseToJsonElement(novel)
        json.parseToJsonElement(comics)
    }

    private val aniListList = """
        {"data":{"MediaListCollection":{"lists":[{"entries":[
          {"progress":5,"score":8,"media":{"id":1,"format":"NOVEL","title":{"romaji":"Mushoku Tensei","english":null,"native":null},"synonyms":[]}},
          {"progress":9,"score":0,"media":{"id":2,"format":"MANGA","title":{"romaji":"One Piece","english":null,"native":null},"synonyms":[]}},
          {"progress":1,"score":0,"media":{"id":3,"format":null,"title":{"romaji":"Unknown","english":null,"native":null},"synonyms":[]}}
        ]}]}}}
    """.trimIndent()

    @Test
    fun aniListListSplitsNovelsFromComics() {
        assertEquals(listOf("1"), parseAniListReadingList(json, aniListList, MediaKind.Novel).map { it.remoteId })
        assertEquals(listOf("2", "3"), parseAniListReadingList(json, aniListList, MediaKind.Comics).map { it.remoteId })
        assertEquals(listOf("2", "3"), parseAniListReadingList(json, aniListList).map { it.remoteId })
    }

    private val malSearch = """
        {"data":[
          {"node":{"id":10,"title":"Overlord","media_type":"light_novel","main_picture":{"medium":"https://c/1.jpg"},
            "alternative_titles":{"synonyms":["Overlord LN"],"en":"Overlord","ja":"オーバーロード"}}},
          {"node":{"id":11,"title":"Overlord Manga","media_type":"manga"}},
          {"node":{"id":12,"title":"Some Novel","media_type":"novel"}},
          {"node":{"id":13,"title":"No Type"}}
        ]}
    """.trimIndent()

    @Test
    fun malSearchFiltersByMediaType() {
        val novels = parseMalSearch(json, malSearch, MediaKind.Novel)
        assertEquals(listOf("10", "12"), novels.map { it.remoteId })
        assertEquals("https://myanimelist.net/manga/10", novels[0].url)
        assertEquals(listOf("Overlord", "オーバーロード", "Overlord LN"), novels[0].altTitles)
        assertEquals(listOf("11", "13"), parseMalSearch(json, malSearch, MediaKind.Comics).map { it.remoteId })
    }

    @Test
    fun malListFiltersByMediaType() {
        val text = """
            {"data":[
              {"node":{"id":10,"title":"Overlord","media_type":"light_novel"},"list_status":{"num_chapters_read":4,"score":9}},
              {"node":{"id":11,"title":"Berserk","media_type":"manga"},"list_status":{"num_chapters_read":3}}
            ],"paging":{"next":"https://api.myanimelist.net/next"}}
        """.trimIndent()
        val (novels, next) = parseMalReadingList(json, text, MediaKind.Novel)
        assertEquals(listOf("10"), novels.map { it.remoteId })
        assertEquals(4, novels[0].chaptersRead)
        assertEquals("https://api.myanimelist.net/next", next)
        assertEquals(listOf("11"), parseMalReadingList(json, text).first.map { it.remoteId })
        assertTrue(isMalNovel("novel") && isMalNovel("light_novel") && !isMalNovel("manhwa") && !isMalNovel(null))
    }

    @Test
    fun shikimoriSearchUsesRanobeEndpoint() {
        assertEquals(
            "https://shikimori.io/api/ranobe?search=abc&limit=10",
            shikimoriSearchUrl("https://shikimori.io/api", MediaKind.Novel, "abc"),
        )
        assertEquals(
            "https://shikimori.io/api/mangas?search=abc&limit=10",
            shikimoriSearchUrl("https://shikimori.io/api", MediaKind.Comics, "abc"),
        )
    }

    @Test
    fun shikimoriSearchParsesRanobe() {
        val text = """[{"id":5,"name":"Mushoku Tensei","russian":"Реинкарнация безработного","image":{"original":"/system/a.jpg"}}]"""
        val r = parseShikimoriSearch(json, text, MediaKind.Novel, "https://shikimori.io").single()
        assertEquals("5", r.remoteId)
        assertEquals("Реинкарнация безработного", r.title)
        assertEquals("https://shikimori.io/ranobe/5", r.url)
        assertEquals("https://shikimori.io/system/a.jpg", r.coverUrl)
    }

    @Test
    fun shikimoriListSplitsRanobeFromManga() {
        val text = """
            [{"id":1,"score":8,"chapters":12,"manga":{"id":100,"name":"Ranobe A","russian":"","kind":"light_novel","image":{"original":"/a.jpg"}}},
             {"id":2,"score":0,"chapters":3,"manga":{"id":200,"name":"Manga B","russian":"Манга Б","kind":"manga"}},
             {"id":3,"score":0,"chapters":1,"manga":{"id":300,"name":"Novel C","kind":"novel"}}]
        """.trimIndent()
        val base = "https://shikimori.io"
        val novels = parseShikimoriReadingList(json, text, MediaKind.Novel, base)
        assertEquals(listOf("100", "300"), novels.map { it.remoteId })
        assertEquals("$base/ranobe/100", novels[0].url)
        assertEquals(12, novels[0].chaptersRead)
        assertEquals(8f, novels[0].score)
        val comics = parseShikimoriReadingList(json, text, MediaKind.Comics, base)
        assertEquals(listOf("200"), comics.map { it.remoteId })
        assertEquals("Манга Б", comics[0].title)
        assertEquals(listOf("Manga B"), comics[0].altTitles)
        assertEquals("$base/mangas/200", comics[0].url)
    }
}
