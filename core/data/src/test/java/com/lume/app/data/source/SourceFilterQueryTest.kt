package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.FilterList
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.include
import com.lume.app.domain.updated
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filters → the query parameters each site expects, and the site lists the engines read. */
class SourceFilterQueryTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)

    private fun manifest(engine: String, baseUrl: String) =
        SourceManifest(id = engine, name = engine, engine = engine, baseUrl = baseUrl)

    private fun tri(group: String, vararg ids: String) =
        SourceFilter.Group(group, group, ids.map { SourceFilter.TriState("$group:$it", it) })

    private fun checks(group: String, vararg ids: String) =
        SourceFilter.Group(group, group, ids.map { SourceFilter.Check("$group:$it", it) })

    private fun FilterList.exclude(id: String) = updated(id) { (it as SourceFilter.TriState).copy(state = 2) }

    @Test
    fun libSocialSendsGenresTagsAndTypesWithTheText() {
        val engine = LibSocialEngine(manifest("libsocial", "https://mangalib.me"), http, null)
        val filters = FilterList(
            listOf(
                SourceFilter.Sort("sort_by", "Sort", options = listOf("rating_score", "views")),
                tri("genres", "32", "34"),
                tri("tags", "641"),
                checks("types", "5"),
                SourceFilter.Select("match", "Match", options = listOf("all", "any")),
            ),
        ).include("genres:32").exclude("genres:34").include("tags:641").include("types:5")
        val q = engine.filterQuery("solo", filters)
        assertTrue(q, q.startsWith("&q=solo"))
        // A text search with the default sort is left in the site's relevance order.
        assertTrue(q, "sort_by" !in q)
        assertTrue(q, "&genres[]=32" in q)
        assertTrue(q, "&genres_exclude[]=34" in q)
        assertTrue(q, "&tags[]=641" in q)
        assertTrue(q, "&types[]=5" in q)
        assertTrue(q, "soft_search" !in q)

        val any = filters.updated("match") { (it as SourceFilter.Select).copy(selected = 1) }
            .updated("sort_by") { (it as SourceFilter.Sort).copy(index = 1, ascending = true) }
        val browse = engine.filterQuery("", any)
        assertTrue(browse, "&sort_by=views&sort_type=asc" in browse)
        assertTrue(browse, "&genres_soft_search=1" in browse)
    }

    @Test
    fun libSocialWithoutFiltersSortsByPopularity() {
        val engine = LibSocialEngine(manifest("libsocial", "https://mangalib.me"), http, null)
        assertEquals("&sort_by=rating_score", engine.filterQuery("", FilterList()))
        assertEquals("&q=a+b", engine.filterQuery(" a b ", FilterList()))
    }

    @Test
    fun remangaRepeatsEachParameter() {
        val engine = RemangaEngine(manifest("remanga", "https://remanga.org"), http)
        val filters = FilterList(
            listOf(
                tri("genres", "3", "5"),
                tri("categories", "47"),
                tri("types", "1"),
                checks("age", "0", "1"),
                SourceFilter.Text("year", "Year", state = " 2020 "),
            ),
        ).include("genres:3").include("genres:5").exclude("categories:47").exclude("types:1").include("age:1")
        assertEquals(
            "&genres=3&genres=5&exclude_categories=47&exclude_types=1&age_limit=1&issue_year_gte=2020",
            engine.catalogQuery(filters),
        )
    }

    @Test
    fun madaraReadsTheGenreBoxesOfTheSearchForm() {
        val html = """
            <form class="search-advanced-form">
              <div class="checkbox"><input id="18" value="18" name="genre[]" type="checkbox" /><label for="18"> +18 </label></div>
              <div class="checkbox"><input id="fantasy" value="fantasy" name="genre[]" type="checkbox" /><label for="fantasy">Fantasy</label></div>
              <div class="checkbox"><input id="no-label" value="no-label" name="genre[]" type="checkbox" /></div>
              <div class="checkbox"><input id="fantasy" value="fantasy" name="genre[]" type="checkbox" /></div>
            </form>
        """.trimIndent()
        assertEquals(
            listOf("18" to "+18", "fantasy" to "Fantasy", "no-label" to "no-label"),
            MadaraParser().parseGenres(html),
        )
    }

    @Test
    fun madaraDetailsHaveOtherNamesYearAndScore() {
        val html = """
            <div class="post-title"><h1>Some Title</h1></div>
            <div class="post-total-rating"><span class="score">4.5</span></div>
            <div class="post-content_item"><div class="summary-heading">Alternative</div><div class="summary-content">Name One, Name Two; Name One</div></div>
            <div class="post-content_item"><div class="summary-heading">Release</div><div class="summary-content"><a>2019</a></div></div>
        """.trimIndent()
        val d = MadaraParser().parseTitleDetails(html, "/manga/some/", "https://example.org")
        assertEquals(listOf("Name One", "Name Two"), d.otherNames)
        assertEquals("Name One", d.altTitle)
        assertEquals(2019, d.year)
        assertEquals(9.0, d.score!!, 0.001)
    }

    @Test
    fun madaraDetailsWithoutTheExtrasStayEmpty() {
        val d = MadaraParser().parseTitleDetails("<h1>Bare</h1>", "/manga/bare/", "https://example.org")
        assertNull(d.year)
        assertNull(d.score)
        assertEquals(emptyList<String>(), d.otherNames)
    }
}
