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
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * HiveToons / Iken family (HiveToons, Vortex Scans, …).
 *
 * Catalogue, details and the full chapter list come from the JSON API; only page images are
 * scraped from the chapter HTML (`img[data-reader-page-image]`). The series *page* renders just
 * the newest ~25 chapters, so it must not be used as the chapter list.
 */
class HiveToonsEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase get() = (manifest.configString("apiBase") ?: "https://api.hivetoons.org").trimEnd('/')
    private val siteBase get() = manifest.baseUrl.trimEnd('/')
    private val postIds = ConcurrentHashMap<String, String>()

    // The API expects camelCase sort keys; unknown values silently fall back to default order.
    override suspend fun getPopular(page: Int) = query(page, "totalViews")
    override suspend fun getLatest(page: Int) = query(page, "updatedAt")

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = "orderBy",
                name = appString(R.string.filter_sort),
                options = listOf("totalViews", "updatedAt", "createdAt"),
                labels = listOf(
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_new),
                ),
            ),
        ),
    )

    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val i = parts.indexOf("series")
        return parts.getOrNull(i + 1)?.takeIf { i >= 0 }?.let { "/series/$it" }
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val order = filters.filters.filterIsInstance<SourceFilter.Select>()
            .firstOrNull { it.id == "orderBy" }?.selectedValue ?: "totalViews"
        return query(page, order, search = query.trim())
    }

    private suspend fun query(page: Int, orderBy: String, search: String = ""): PagedResult<TitleSummary> {
        val q = buildString {
            append("page=$page&perPage=$PAGE_SIZE&orderBy=$orderBy")
            if (search.isNotBlank()) append("&searchTerm=${urlEncode(search)}")
        }
        val root = apiObject("$apiBase/api/query?$q")
        val items = root["posts"].arr().orEmpty().mapNotNull { el ->
            val item = el.obj() ?: return@mapNotNull null
            val slug = item.str("slug") ?: return@mapNotNull null
            item.str("id")?.let { postIds[slug] = it }
            TitleSummary(
                id = "/series/$slug",
                title = item.str("postTitle") ?: item.str("title") ?: slug,
                coverUrl = item.str("featuredImage") ?: item.str("cover"),
                latestChapter = item["chapters"].arr()?.firstOrNull().obj().str("number")
                    ?: item.str("chapterCount"),
                typeLabel = item.str("seriesType"),
            )
        }
        val total = root.str("totalCount")?.toIntOrNull()
        val hasNext = total?.let { page * PAGE_SIZE < it } ?: (items.size >= PAGE_SIZE)
        return PagedResult(items = items, page = page, hasNext = hasNext && items.isNotEmpty())
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val post = post(slugOf(id))
        return TitleDetails(
            id = id,
            title = post.str("postTitle") ?: slugOf(id),
            altTitle = post.str("alternativeTitles")?.takeIf { it != post.str("postTitle") },
            coverUrl = post.str("featuredImage"),
            description = htmlToText(post.str("postContent")),
            typeLabel = post.str("seriesType"),
            status = post.str("seriesStatus"),
            authors = listOfNotNull(post.str("author")),
            artists = listOfNotNull(post.str("artist")),
            genres = post["genres"].arr().orEmpty().mapNotNull { it.obj().str("name") },
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = slugOf(titleId)
        val postId = postIds[slug] ?: post(slug).str("id")
            ?: throw SourceFetchException("${manifest.name}: series id not found")
        val out = ArrayList<Chapter>()
        var skip = 0
        while (skip < MAX_CHAPTERS) {
            val root = apiObject("$apiBase/api/chapters?postId=$postId&skip=$skip&take=$CHAPTER_BATCH&order=desc&userid=")
            val batch = root["post"].obj()?.get("chapters").arr().orEmpty()
            for (el in batch) {
                val item = el.obj() ?: continue
                // Paid / timed-unlock chapters render no images for guests.
                val accessible = item["isAccessible"].bool() ?: (item["isLocked"].bool() != true)
                if (!accessible) continue
                val chapterSlug = item.str("slug") ?: continue
                val number = item.str("number")
                val name = item.str("title")
                out += Chapter(
                    id = "/series/$slug/$chapterSlug",
                    title = buildString {
                        append("Chapter ").append(number ?: chapterSlug.substringAfter("chapter-"))
                        if (!name.isNullOrBlank() && !name.equals("Chapter $number", ignoreCase = true)) {
                            append(" — ").append(name)
                        }
                    },
                    url = "/series/$slug/$chapterSlug",
                    number = number,
                    dateLabel = item.str("createdAt")?.take(10),
                )
            }
            val total = root.str("totalChapterCount")?.toIntOrNull()
            skip += CHAPTER_BATCH
            if (batch.size < CHAPTER_BATCH || (total != null && skip >= total)) break
        }
        return out
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val response = http.get(resolveUrl(siteBase, chapter.url.ifBlank { chapter.id }), source = manifest)
        val doc = Jsoup.parse(response.body, siteBase)
        val marked = doc.select("img[data-reader-page-image]")
            .sortedBy { it.attr("data-reader-index").toIntOrNull() ?: Int.MAX_VALUE }
            .map { it.attr("abs:src").ifBlank { it.attr("abs:data-src") } }
        // Fallback for older Iken templates without reader markers.
        val urls = marked.ifEmpty {
            doc.select("main img, img").map { it.attr("abs:src").ifBlank { it.attr("abs:data-src") } }
                .filter { "/upload/series/" in it }
        }
        val pages = urls.filter { it.startsWith("http") }.distinct()
            .mapIndexed { index, url -> ComicPage(index = index, imageUrl = url) }
        if (pages.isEmpty()) throw SourceFetchException(appString(R.string.error_pages_failed_named, manifest.name))
        return pages
    }

    private suspend fun post(slug: String): JsonObject {
        val root = apiObject("$apiBase/api/post?postSlug=${urlEncode(slug)}")
        val post = root["post"].obj() ?: throw SourceFetchException("${manifest.name}: series not found")
        post.str("id")?.let { postIds[slug] = it }
        return post
    }

    private suspend fun apiObject(url: String): JsonObject {
        val response = http.get(URI(url), source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return json.parseToJsonElement(response.body).jsonObject
    }

    private fun slugOf(id: String): String = id.trim('/').removePrefix("series/").substringBefore('/')

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Origin" to siteBase,
        "Referer" to "$siteBase/",
    )

    private companion object {
        const val PAGE_SIZE = 24
        const val CHAPTER_BATCH = 500
        const val MAX_CHAPTERS = 10_000
    }
}
