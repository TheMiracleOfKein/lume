package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.FilterList
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Search URLs built from the catalogue filters of Royal Road and Scribble Hub. */
class NovelSearchFiltersTest {
    private fun manifest(name: String) = Json { ignoreUnknownKeys = true }
        .decodeFromString(SourceManifest.serializer(), File("src/main/assets/sources/$name.json").readText())

    private val royalRoad = RoyalRoadEngine(manifest("royalroad"), AppHttp(CookieJar.NO_COOKIES))
    private val scribbleHub = ScribbleHubEngine(manifest("scribblehub"), AppHttp(CookieJar.NO_COOKIES))

    private fun tri(id: String, state: Int) = SourceFilter.TriState(id, id, state)

    private fun rrFilters(
        sort: Int = 0,
        ascending: Boolean = false,
        status: Int = 0,
        tags: List<SourceFilter.TriState> = emptyList(),
    ) = FilterList(
        listOf(
            SourceFilter.Sort("sort", "Sort", listOf("relevance", "popularity", "rating"), index = sort, ascending = ascending),
            SourceFilter.Select("status", "Status", listOf("", "ONGOING", "COMPLETED"), selected = status),
            SourceFilter.Select("type", "Type", listOf("", "original")),
            SourceFilter.Group("genres", "Genres", tags),
        ),
    )

    @Test
    fun royalRoadWithoutTextOrFiltersIsThePopularList() {
        assertNull(royalRoad.searchPath("", rrFilters()))
        assertNull(royalRoad.searchPath("  ", FilterList()))
    }

    @Test
    fun royalRoadTextOnlyKeepsTheTitleSearch() {
        assertEquals("/fictions/search?title=dragon", royalRoad.searchPath("dragon", FilterList()))
        assertEquals("/fictions/search?title=dragon", royalRoad.searchPath("dragon", rrFilters()))
    }

    @Test
    fun royalRoadIncludesAndExcludesTagsStatusAndSort() {
        val filters = rrFilters(
            sort = 2, ascending = true, status = 1,
            tags = listOf(tri("genres:litrpg", 1), tri("genres:progression", 1), tri("genres:harem", 2), tri("genres:comedy", 0)),
        )
        assertEquals(
            "/fictions/search?tagsAdd=litrpg&tagsAdd=progression&tagsRemove=harem&status=ONGOING&orderBy=rating&dir=asc",
            royalRoad.searchPath("", filters),
        )
    }

    @Test
    fun royalRoadCombinesTextWithFilters() {
        val path = royalRoad.searchPath("dragon", rrFilters(sort = 1, tags = listOf(tri("genres:action", 1))))!!
        assertTrue(path, path.startsWith("/fictions/search?title=dragon&tagsAdd=action"))
        assertTrue(path, path.endsWith("&orderBy=popularity&dir=desc"))
    }

    @Test
    fun royalRoadSortOnlyBrowsesTheSearchPage() {
        assertEquals("/fictions/search?orderBy=popularity&dir=desc", royalRoad.searchPath("", rrFilters(sort = 1)))
    }

    private fun shFilters(
        sort: Int = 0,
        ascending: Boolean = false,
        status: Int = 0,
        match: Int = 0,
        genres: List<SourceFilter.TriState> = emptyList(),
        tags: List<SourceFilter.TriState> = emptyList(),
    ) = FilterList(
        listOf(
            SourceFilter.Sort("sort", "Sort", listOf("pageviews", "ratings", "lastchpdate"), index = sort, ascending = ascending),
            SourceFilter.Select("status", "Status", listOf("", "ongoing", "completed", "hiatus"), selected = status),
            SourceFilter.Select("match", "Match", listOf("or", "and"), selected = match),
            SourceFilter.Group("genres", "Genres", genres),
            SourceFilter.Group("tags", "Tags", tags),
        ),
    )

    @Test
    fun scribbleHubWithoutTextOrFiltersIsThePopularList() {
        assertNull(scribbleHub.searchPath("", shFilters()))
        assertNull(scribbleHub.searchPath("", FilterList()))
    }

    @Test
    fun scribbleHubTextOnlyKeepsTheRatingSortedUrl() {
        assertEquals(
            "/series-finder/?sf=1&sh=hello+world&order=desc&sort=ratings",
            scribbleHub.searchPath("hello world", shFilters()),
        )
    }

    @Test
    fun scribbleHubBuildsGenreTagStatusAndSortParams() {
        val filters = shFilters(
            sort = 1, ascending = true, status = 2, match = 1,
            genres = listOf(tri("genres:9", 1), tri("genres:8", 1), tri("genres:1015", 2), tri("genres:7", 0)),
            tags = listOf(tri("tags:119", 1), tri("tags:120", 2)),
        )
        assertEquals(
            "/series-finder/?sf=1&gi=9,8&mgi=and&ge=1015&tgi=119&mtgi=and&tge=120&cp=completed&sort=ratings&order=asc",
            scribbleHub.searchPath("", filters),
        )
    }

    @Test
    fun scribbleHubCombinesTextWithFilters() {
        val path = scribbleHub.searchPath("dragon", shFilters(genres = listOf(tri("genres:19", 1))))
        assertEquals("/series-finder/?sf=1&sh=dragon&gi=19&sort=pageviews&order=desc", path)
    }

    @Test
    fun scribbleHubReadsTheTagListOfTheFinderForm() {
        val tags = scribbleHub.parseTags(Fixtures.text("scribblehub/search.html"))
        assertEquals(764, tags.size)
        assertEquals(SourceFilter.TriState("tags:119", "Abandoned Children"), tags.first())
        assertEquals(tags.size, tags.map { it.id }.toSet().size)
    }
}
