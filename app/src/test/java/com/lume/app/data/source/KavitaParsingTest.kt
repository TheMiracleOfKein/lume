package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import com.lume.app.testing.assertHttpUrls
import com.lume.app.testing.assertSequentialIndexes
import com.lume.app.testing.assertUniqueIds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kavita JSON shapes (fixtures/kavita), written from the server's DTOs. */
class KavitaParsingTest {
    private val engine = KavitaEngine(
        SourceManifest(id = "server-test", name = "Kavita", engine = "kavita", baseUrl = "http://10.0.2.2:5000"),
        AppHttp(CookieJar.NO_COOKIES),
        credentials = null,
    )

    @Test
    fun seriesList() {
        val page = engine.parseSeriesList(Fixtures.text("kavita/series_list.json"), 1)
        assertEquals(listOf("12", "31"), page.items.map { it.id })
        assertEquals("One Piece", page.items[0].title)
        assertEquals("Manga", page.items[0].subtitle)
        assertFalse(page.hasNext)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
        // The key is a placeholder; the HTTP client swaps in the real one.
        assertEquals("http://10.0.2.2:5000/api/image/series-cover?seriesId=12&apiKey=-", page.items[0].coverUrl)
    }

    @Test
    fun details() {
        val d = engine.parseDetails(Fixtures.text("kavita/series.json"), Fixtures.text("kavita/metadata.json"))
        assertEquals("One Piece", d.title)
        assertEquals("ワンピース", d.altTitle)
        assertEquals("Ongoing", d.status)
        assertEquals(listOf("Eiichiro Oda"), d.authors)
        assertEquals(listOf("Eiichiro Oda"), d.artists)
        assertEquals(listOf("Adventure", "Shounen"), d.genres)
        assertEquals(1997, d.year)
        assertEquals("A pirate crew searches for the ultimate treasure.", d.description)
    }

    @Test
    fun detailsWithoutMetadataStillWork() {
        val d = engine.parseDetails(Fixtures.text("kavita/series.json"), null)
        assertEquals("One Piece", d.title)
        assertTrue(d.genres.isEmpty())
    }

    @Test
    fun chaptersSkipEpubAndListNewestFirst() {
        val chapters = engine.parseChapters("12", Fixtures.text("kavita/series_detail.json"))
        // Volumes in order, then loose chapters, then specials; reversed for newest first.
        assertEquals(listOf("902", "903", "802", "801"), chapters.map { it.id })
        assertUniqueIds(chapters.map { it.id })
        assertEquals("Romance Dawn", chapters.last().title)
        assertEquals("1", chapters.last().volume)
        assertEquals("1100", chapters[1].number)
        assertEquals("2024-05-01", chapters[1].dateLabel)
        assertEquals(null, chapters[2].number)
    }

    @Test
    fun chapterInfoGivesTheIdsForProgress() {
        val info = engine.parseChapterInfo(Fixtures.text("kavita/chapter_info.json"))
        assertEquals(12, info.seriesId)
        assertEquals(79, info.volumeId)
        assertEquals(2, info.libraryId)
        assertEquals(4, info.pages)
    }

    @Test
    fun pagesAreZeroBasedWithAPlaceholderKey() {
        val info = engine.parseChapterInfo(Fixtures.text("kavita/chapter_info.json"))
        val pages = engine.pageUrls("801", info)
        assertEquals(4, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertEquals("http://10.0.2.2:5000/api/Reader/image?chapterId=801&page=0&apiKey=-", pages[0].imageUrl)
        assertEquals("http://10.0.2.2:5000/api/Reader/image?chapterId=801&page=3&apiKey=-", pages[3].imageUrl)
    }

    @Test
    fun pdfChaptersAskForRenderedPages() {
        val info = KavitaEngine.ChapterInfo(seriesId = 1, volumeId = 2, libraryId = 3, pages = 2, format = 4)
        assertTrue(engine.pageUrls("5", info).all { "extractPdf=true" in it.imageUrl })
    }

    @Test
    fun filterBodyHasTheServerShape() {
        val body = Json.parseToJsonElement(engine.filterBody("one piece", sortField = 1, ascending = true)).jsonObject
        val statement = body["statements"]!!.jsonArray.single().jsonObject
        assertEquals(1, statement["field"]!!.jsonPrimitive.int)
        assertEquals(7, statement["comparison"]!!.jsonPrimitive.int)
        assertEquals("one piece", statement["value"]!!.jsonPrimitive.content)
        assertEquals(1, body["combination"]!!.jsonPrimitive.int)
        assertEquals(1, body["sortOptions"]!!.jsonObject["sortField"]!!.jsonPrimitive.int)
        val empty = Json.parseToJsonElement(engine.filterBody(null, 2, false)).jsonObject
        assertEquals(JsonArray(emptyList()), empty["statements"])
    }
}
