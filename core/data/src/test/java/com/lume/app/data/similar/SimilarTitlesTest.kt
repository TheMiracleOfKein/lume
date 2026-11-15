package com.lume.app.data.similar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarTitlesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val searchAnswer = """
        {"data":{"Page":{"media":[
          {"id":1,"title":{"romaji":"Ore dake Level Up na Ken: Ragnarok","english":"Solo Leveling: Ragnarok","native":null},
           "synonyms":[],"recommendations":{"nodes":[]}},
          {"id":105398,"title":{"romaji":"Na Honjaman Level Up","english":"Solo Leveling","native":"나 혼자만 레벨업"},
           "synonyms":["Only I Level Up"],
           "recommendations":{"nodes":[
             {"rating":120,"mediaRecommendation":{"id":2,"title":{"romaji":"Jeonjijeok Dokja Sijeom","english":"Omniscient Reader","native":"전지적 독자 시점"},
              "coverImage":{"large":"https://img/omni.jpg"},"siteUrl":"https://anilist.co/manga/2","isAdult":false}},
             {"rating":40,"mediaRecommendation":{"id":3,"title":{"romaji":"Sono Ato","english":null,"native":null},
              "coverImage":{"large":null},"siteUrl":"https://anilist.co/manga/3","isAdult":true}},
             {"rating":-3,"mediaRecommendation":{"id":4,"title":{"romaji":"Downvoted","english":null,"native":null}}},
             {"rating":10,"mediaRecommendation":null}
           ]}}
        ]}}}
    """.trimIndent()

    @Test
    fun parsesAniListSearchWithRecommendations() {
        val candidates = parseAniListSimilar(json, searchAnswer)
        assertEquals(listOf("1", "105398"), candidates.map { it.remoteId })
        val solo = candidates[1]
        assertEquals(listOf("Solo Leveling", "Na Honjaman Level Up", "나 혼자만 레벨업", "Only I Level Up"), solo.names)
        // English name first; downvoted and empty recommendations left out.
        assertEquals(listOf("Omniscient Reader", "Sono Ato"), solo.similar.map { it.title })
        assertEquals("https://img/omni.jpg", solo.similar[0].coverUrl)
        assertEquals("https://anilist.co/manga/2", solo.similar[0].url)
        assertTrue(solo.similar[1].adult)
        assertNull(solo.similar[1].coverUrl)
    }

    @Test
    fun parsesAniListMediaById() {
        val answer = """{"data":{"Media":{"id":7,"title":{"romaji":"Berserk"},"synonyms":null,
            "recommendations":{"nodes":[{"rating":5,"mediaRecommendation":{"id":8,"title":{"romaji":"Vagabond"},"isAdult":false}}]}}}}"""
        val candidates = parseAniListSimilar(json, answer)
        assertEquals(1, candidates.size)
        assertEquals(listOf("Vagabond"), candidates[0].similar.map { it.title })
    }

    @Test
    fun errorsAndOddAnswersGiveNothing() {
        assertTrue(parseAniListSimilar(json, "not json").isEmpty())
        assertTrue(parseAniListSimilar(json, """{"errors":[{"message":"Not Found."}],"data":{"Media":null}}""").isEmpty())
        assertTrue(parseAniListSimilar(json, """{"data":{"Page":{"media":[]}}}""").isEmpty())
    }

    @Test
    fun picksTheSameTitleNotTheFirstResult() {
        val candidates = parseAniListSimilar(json, searchAnswer)
        assertEquals("105398", pickSimilarCandidate(listOf("Solo Leveling"), candidates)?.remoteId)
        // Another name of the title (the Korean one from the source) matches too.
        assertEquals("105398", pickSimilarCandidate(listOf("Поднятие уровня в одиночку", "나 혼자만 레벨업"), candidates)?.remoteId)
    }

    @Test
    fun noCandidateWhenNothingIsClose() {
        val candidates = parseAniListSimilar(json, searchAnswer)
        assertNull(pickSimilarCandidate(listOf("Tower of God"), candidates))
        // A sequel or spin-off shares most words, but not enough.
        assertNull(pickSimilarCandidate(listOf("Solo Leveling"), candidates.take(1)))
        assertNull(pickSimilarCandidate(emptyList(), candidates))
        assertNull(pickSimilarCandidate(listOf("Solo Leveling"), emptyList()))
    }

    @Test
    fun closeNameIsEnoughWhenNoneIsEqual() {
        val candidates = listOf(
            SimilarCandidate("1", listOf("The Beginning After the End: Side Story"), emptyList()),
            SimilarCandidate("2", listOf("Beginning After the End"), emptyList()),
        )
        // Equal but for the article.
        assertEquals("2", pickSimilarCandidate(listOf("The Beginning After The End"), candidates)?.remoteId)
        val partial = listOf(SimilarCandidate("3", listOf("Omniscient Reader's Viewpoint"), emptyList()))
        assertEquals("3", pickSimilarCandidate(listOf("Omniscient Reader Viewpoint"), partial)?.remoteId)
    }

    @Test
    fun cleanDropsAdultDuplicatesAndTheTitleItself() {
        val items = listOf(
            SimilarTitle("Omniscient Reader"),
            SimilarTitle("omniscient reader"),
            SimilarTitle("Sono Ato", adult = true),
            SimilarTitle("Solo Leveling!"),
        ) + (1..30).map { SimilarTitle("Title $it") }
        val clean = cleanSimilar(items, listOf("Solo Leveling"), allowAdult = false)
        assertEquals("Omniscient Reader", clean[0].title)
        assertTrue(clean.none { it.adult || it.title == "Solo Leveling!" || it.title == "omniscient reader" })
        assertEquals(MAX_SIMILAR, clean.size)
        assertTrue(cleanSimilar(items, listOf("Solo Leveling"), allowAdult = true).any { it.title == "Sono Ato" })
    }

    @Test
    fun parsesMyAnimeListRecommendations() {
        val answer = """{"id":121496,"title":"Solo Leveling","recommendations":[
            {"node":{"id":118586,"title":"Tower of God","main_picture":{"medium":"https://m/1.jpg","large":"https://l/1.jpg"}},"num_recommendations":9},
            {"node":{"id":2,"title":"Berserk","main_picture":{"medium":"https://m/2.jpg"}},"num_recommendations":1},
            {"node":{"id":3}}
        ]}"""
        val items = parseMalRecommendations(json, answer)
        assertEquals(listOf("Tower of God", "Berserk"), items.map { it.title })
        assertEquals("https://l/1.jpg", items[0].coverUrl)
        assertEquals("https://m/2.jpg", items[1].coverUrl)
        assertEquals("https://myanimelist.net/manga/2", items[1].url)
        assertTrue(parseMalRecommendations(json, """{"id":1}""").isEmpty())
        assertTrue(parseMalRecommendations(json, "<html>").isEmpty())
    }

    @Test
    fun searchBodyIsValidJsonWithTheQueryEscaped() {
        val body = json.parseToJsonElement(aniListSearchBody(json, "Re:Zero \"Kara\"")).jsonObject
        assertEquals("Re:Zero \"Kara\"", body["variables"]!!.jsonObject["search"]!!.jsonPrimitive.content)
        assertTrue(body["query"]!!.jsonPrimitive.content.contains("recommendations(sort:RATING_DESC"))
        val byId = json.parseToJsonElement(aniListByIdBody(105398)).jsonObject
        assertEquals("105398", byId["variables"]!!.jsonObject["id"]!!.jsonPrimitive.content)
    }
}
