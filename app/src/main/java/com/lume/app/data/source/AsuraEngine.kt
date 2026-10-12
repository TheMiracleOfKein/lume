package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.util.urlEncode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URI

/**
 * Asura Scans via api.asurascans.com (front: asurascans.com, formerly asuracomic.net).
 *
 * The API paginates with `limit`/`offset` and sorts with `sort=popular|latest|rating`;
 * `page`, `order` and `orderBy` are silently ignored.
 */
class AsuraEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase get() = (manifest.configString("apiBase") ?: "https://api.asurascans.com/api").trimEnd('/')
    private val siteBase get() = manifest.baseUrl.trimEnd('/')

    override suspend fun getPopular(page: Int) = seriesList(page, "popular")

    override suspend fun getLatest(page: Int) = seriesList(page, "latest")

    private suspend fun seriesList(page: Int, sort: String): PagedResult<TitleSummary> =
        fetchList("$apiBase/series?sort=$sort&${pageQuery(page)}", page)

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = "sort",
                name = appString(R.string.filter_sort),
                options = listOf("popular", "latest", "rating"),
            ),
        ),
    )

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) {
            val sort = filters.filters.filterIsInstance<SourceFilter.Select>()
                .firstOrNull { it.id == "sort" }?.selectedValue ?: "popular"
            return seriesList(page, sort)
        }
        return fetchList("$apiBase/search?q=${urlEncode(query.trim())}&${pageQuery(page)}", page)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val slug = publicSlug(id)
        val root = apiObject("$apiBase/series/$slug")
        val series = root["series"].obj() ?: root["data"].obj() ?: root
        val genres = series["genres"].arr().orEmpty().mapNotNull { it.obj().str("name") ?: it.str() }
        return TitleDetails(
            id = slug,
            title = series.str("title") ?: slug,
            altTitle = series["alt_titles"].arr()?.firstNotNullOfOrNull { it.str() },
            coverUrl = series.str("cover") ?: series.str("cover_url"),
            description = htmlToText(series.str("description")),
            status = series.str("status"),
            typeLabel = series.str("type"),
            authors = listOfNotNull(series.str("author")),
            artists = listOfNotNull(series.str("artist")),
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = publicSlug(titleId)
        val data = apiObject("$apiBase/series/$slug/chapters")["data"].arr().orEmpty()
        return data.mapNotNull { el ->
            val item = el.obj() ?: return@mapNotNull null
            // Premium / early-access chapters return no pages until they become free.
            if (item["is_premium"].bool() == true) return@mapNotNull null
            val number = item.str("number") ?: return@mapNotNull null
            // Keep the historical id shape so saved progress and downloads still match.
            val path = "/comics/$slug/chapter/$number"
            val name = item.str("title")
            Chapter(
                id = path,
                title = if (name != null) "Chapter $number — $name" else "Chapter $number",
                url = path,
                number = number,
                dateLabel = item.str("published_at")?.take(10),
                branchId = item.str("slug"),
                branchName = item.str("series_slug"),
            )
        }
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val slug = publicSlug(chapter.id.substringBefore("/chapter"))
            .ifBlank { chapter.branchName.orEmpty() }
            .ifBlank { throw SourceFetchException("Asura: missing series slug") }
        val number = chapter.number
            ?: chapter.id.substringAfterLast('/').ifBlank { null }
            ?: throw SourceFetchException("Asura: missing chapter number")
        val root = apiObject("$apiBase/series/$slug/chapters/$number")
        val data = root["data"].obj() ?: root
        val chapterObj = data["chapter"].obj() ?: data
        val pages = chapterObj["pages"].arr().orEmpty().mapIndexedNotNull { index, el ->
            val url = el.obj().str("url") ?: el.str() ?: return@mapIndexedNotNull null
            ComicPage(index = index, imageUrl = url)
        }
        if (pages.isEmpty()) {
            if (data["is_locked"].bool() == true) {
                throw SourceFetchException(appString(R.string.error_chapter_locked, manifest.name))
            }
            throw SourceFetchException(appString(R.string.error_pages_failed_named, manifest.name))
        }
        return pages
    }

    override fun webUrl(titleId: String): String = "$siteBase/comics/${publicSlug(titleId)}"

    private suspend fun fetchList(url: String, page: Int): PagedResult<TitleSummary> {
        val root = apiObject(url)
        val items = root["data"].arr().orEmpty().mapNotNull { el ->
            val item = el.obj() ?: return@mapNotNull null
            val baseSlug = item.str("slug") ?: return@mapNotNull null
            // Prefer the public slug (`name-hash`): it is what older library entries stored,
            // and the API accepts both forms.
            val slug = item.str("public_url")?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: baseSlug
            val latest = item["latest_chapters"].arr()?.firstOrNull().obj().str("number")
            TitleSummary(
                id = slug,
                title = item.str("title") ?: baseSlug,
                coverUrl = item.str("cover") ?: item.str("cover_url"),
                latestChapter = latest ?: item.str("chapter_count"),
                typeLabel = item.str("type"),
            )
        }
        val meta = root["meta"].obj()
        val total = meta.str("total")?.toIntOrNull()
        val hasNext = when {
            total != null -> (page - 1) * PAGE_SIZE + items.size < total
            else -> meta?.get("has_more").bool() ?: (items.size >= PAGE_SIZE)
        }
        return PagedResult(items = items, page = page, hasNext = hasNext && items.isNotEmpty())
    }

    private suspend fun apiObject(url: String): JsonObject {
        val response = http.get(URI(url), source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return json.parseToJsonElement(response.body).jsonObject
    }

    private fun pageQuery(page: Int) = "limit=$PAGE_SIZE&offset=${(page - 1).coerceAtLeast(0) * PAGE_SIZE}"

    private fun publicSlug(id: String): String {
        val trimmed = id.trim('/')
        return when {
            trimmed.startsWith("comics/") -> trimmed.removePrefix("comics/").substringBefore('/')
            trimmed.startsWith("series/") -> trimmed.removePrefix("series/").substringBefore('/')
            else -> trimmed.substringBefore('/')
        }
    }

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Origin" to siteBase,
        "Referer" to "$siteBase/",
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
