package com.lume.app.data.tracker

import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleMatch
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerListEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesAniListViewer() {
        assertEquals(5678L, parseAniListViewerId(json, """{"data":{"Viewer":{"id":5678}}}"""))
        assertNull(parseAniListViewerId(json, """{"data":{"Viewer":null}}"""))
    }

    @Test
    fun parsesAniListList() {
        val text = """
            {"data":{"MediaListCollection":{"lists":[
              {"entries":[
                {"progress":42,"score":8.5,"media":{
                  "id":105398,
                  "title":{"romaji":"Na Honjaman Level-Up","english":"Solo Leveling","native":"나 혼자만 레벨업"},
                  "synonyms":["Only I Level Up","Solo Leveling"],
                  "siteUrl":"https://anilist.co/manga/105398",
                  "coverImage":{"large":"https://s4.anilist.co/cover.jpg"},
                  "staff":{"nodes":[{"name":{"full":"Chugong"}},{"name":{"full":"Dubu"}}]}}},
                {"progress":0,"score":0,"media":{
                  "id":30013,
                  "title":{"romaji":"One Piece","english":null,"native":"ONE PIECE"},
                  "synonyms":[],"siteUrl":null,"coverImage":null,"staff":{"nodes":[]}}}
              ]},
              {"entries":[
                {"progress":3,"score":7,"media":{"id":30013,"title":{"romaji":"One Piece","english":null,"native":null},"synonyms":[]}}
              ]}
            ]}}}
        """.trimIndent()
        val list = parseAniListReadingList(json, text)
        assertEquals(2, list.size)
        val solo = list[0]
        assertEquals("105398", solo.remoteId)
        assertEquals("Solo Leveling", solo.title)
        assertEquals(listOf("Na Honjaman Level-Up", "나 혼자만 레벨업", "Only I Level Up"), solo.altTitles)
        assertEquals(42, solo.chaptersRead)
        assertEquals(8.5f, solo.score)
        assertEquals("https://anilist.co/manga/105398", solo.url)
        assertEquals(listOf("Chugong", "Dubu"), solo.authors)
        val piece = list[1]
        assertEquals("One Piece", piece.title)
        // The native name only differs in case: not an extra name.
        assertEquals(emptyList<String>(), piece.altTitles)
        assertNull(piece.score)
        assertNull(piece.coverUrl)
    }

    @Test
    fun emptyAniListAnswerIsEmpty() {
        assertEquals(emptyList<TrackerListEntry>(), parseAniListReadingList(json, """{"data":{"MediaListCollection":{"lists":[]}}}"""))
        assertEquals(emptyList<TrackerListEntry>(), parseAniListReadingList(json, """{"errors":[{"message":"x"}],"data":null}"""))
    }

    @Test
    fun parsesMalPage() {
        val text = """
            {"data":[
              {"node":{"id":2,"title":"Berserk",
                "main_picture":{"medium":"https://cdn.myanimelist.net/a.jpg","large":"https://cdn.myanimelist.net/b.jpg"},
                "alternative_titles":{"synonyms":["Berserk: The Prototype"],"en":"Berserk","ja":"ベルセルク"},
                "authors":[{"node":{"id":1868,"first_name":"Kentarou","last_name":"Miura"},"role":"Story & Art"}]},
               "list_status":{"status":"reading","score":9,"num_chapters_read":364,"is_rereading":false}},
              {"node":{"id":13,"title":"One Piece","alternative_titles":{"synonyms":[],"en":"","ja":"ONE PIECE"}},
               "list_status":{"status":"reading","score":0,"num_chapters_read":0}}
            ],
            "paging":{"next":"https://api.myanimelist.net/v2/users/@me/mangalist?offset=1000&limit=1000"}}
        """.trimIndent()
        val (entries, next) = parseMalReadingList(json, text)
        assertEquals("https://api.myanimelist.net/v2/users/@me/mangalist?offset=1000&limit=1000", next)
        assertEquals(2, entries.size)
        val berserk = entries[0]
        assertEquals("2", berserk.remoteId)
        assertEquals("Berserk", berserk.title)
        assertEquals(listOf("ベルセルク", "Berserk: The Prototype"), berserk.altTitles)
        assertEquals(364, berserk.chaptersRead)
        assertEquals(9f, berserk.score)
        assertEquals(listOf("Kentarou Miura"), berserk.authors)
        assertEquals("https://myanimelist.net/manga/2", berserk.url)
        assertEquals("https://cdn.myanimelist.net/a.jpg", berserk.coverUrl)
        val piece = entries[1]
        assertEquals(emptyList<String>(), piece.altTitles)
        assertNull(piece.score)
        assertNull(piece.coverUrl)
    }

    @Test
    fun lastMalPageHasNoNext() {
        val (entries, next) = parseMalReadingList(json, """{"data":[],"paging":{}}""")
        assertEquals(0, entries.size)
        assertNull(next)
    }

    private val entry = TrackerListEntry("1", "Solo Leveling", altTitles = listOf("Only I Level Up"))

    private fun cand(source: String, title: String, match: TitleMatch = TitleMatch.Same) =
        ListImportCandidate(source, source, "id-$source", title, null, match)

    @Test
    fun onlyClearMatchesAreChosen() {
        assertNull(chooseListCandidate(entry, listOf(cand("A", "Solo Leveling", TitleMatch.Maybe), cand("B", "Other", TitleMatch.Different))))
        assertNull(chooseListCandidate(entry, emptyList()))
    }

    @Test
    fun exactNameBeatsCloseName() {
        val best = chooseListCandidate(entry, listOf(cand("A", "Solo Leveling: Ragnarok"), cand("Z", "solo leveling!")))
        assertEquals("Z", best?.sourceId)
    }

    @Test
    fun otherNameCountsAsExact() {
        val best = chooseListCandidate(entry, listOf(cand("A", "Solo Leveling Side Story"), cand("B", "Only I Level Up")))
        assertEquals("B", best?.sourceId)
    }

    @Test
    fun tiesGoBySourceName() {
        val best = chooseListCandidate(entry, listOf(cand("Beta", "Solo Leveling"), cand("alpha", "Solo Leveling")))
        assertEquals("alpha", best?.sourceId)
    }

    private fun ch(id: String, number: String?, title: String = "Chapter") = Chapter(id = id, title = title, url = "/$id", number = number)

    @Test
    fun coveredChaptersAreNumbersUpToProgress() {
        val chapters = listOf(ch("c4", "4"), ch("c3.5", "3.5"), ch("c3", "3"), ch("c2", "2"), ch("c1", "1"))
        assertEquals(listOf("c3.5", "c3", "c2", "c1"), chaptersCoveredBy(chapters, 3).map { it.id })
        assertEquals(emptyList<Chapter>(), chaptersCoveredBy(chapters, 0))
    }

    @Test
    fun unnumberedChaptersAreNotMarked() {
        val chapters = listOf(ch("extra", null, "Extra"), ch("c1", "1"))
        assertEquals(listOf("c1"), chaptersCoveredBy(chapters, 5).map { it.id })
    }
}
