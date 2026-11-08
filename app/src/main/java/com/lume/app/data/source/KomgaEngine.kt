package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.BrowseSection
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.PagedResult
import com.lume.app.domain.ReadProgressSink
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceLinks
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.SourceSetting
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.choose
import com.lume.app.util.urlEncode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.net.URI

/**
 * A Komga server (`/api/v1`). Sign-in (Basic or API key) is added by the shared HTTP client, so
 * covers and pages load with it too. Pages are 1-based on the server.
 */
class KomgaEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource, ReadProgressSink {
    private val base get() = manifest.baseUrl.trimEnd('/')
    private val api get() = "$base/api/v1"
    private val libraryId get() = manifest.configString("libraryId")?.trim()?.takeIf { it.isNotEmpty() }
    private val jsonHeaders = mapOf("Accept" to "application/json")

    override fun settings(): List<SourceSetting> = listOf(
        SourceSetting.Text("libraryId", appString(R.string.server_setting_library_id), ""),
    )

    // ---- Browse -------------------------------------------------------------------------------

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> =
        fetchSeries("/series/latest?${pageQuery(page)}${libraryParam()}", page)

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        seriesList(page, "metadata.titleSort,asc", "", "", "")

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = "sort",
                name = appString(R.string.filter_sort),
                options = listOf("metadata.titleSort,asc", "createdDate,desc", "lastModifiedDate,desc"),
                labels = listOf(
                    appString(R.string.filter_sort_title),
                    appString(R.string.filter_sort_new),
                    appString(R.string.filter_sort_latest),
                ),
            ),
            SourceFilter.Select(
                id = "status",
                name = appString(R.string.filter_status),
                options = listOf("", "ONGOING", "ENDED", "HIATUS", "ABANDONED"),
                labels = listOf(
                    appString(R.string.filter_any),
                    appString(R.string.filter_status_ongoing),
                    appString(R.string.filter_status_completed),
                    appString(R.string.filter_status_hiatus),
                    appString(R.string.filter_status_cancelled),
                ),
            ),
            SourceFilter.Select(
                id = "read",
                name = appString(R.string.filter_read_status),
                options = listOf("", "UNREAD", "IN_PROGRESS", "READ"),
                labels = listOf(
                    appString(R.string.filter_any),
                    appString(R.string.filter_read_unread),
                    appString(R.string.filter_read_in_progress),
                    appString(R.string.filter_read_done),
                ),
            ),
        ),
    )

    override fun browseSections(): List<BrowseSection> {
        val base = getFilters()
        return listOf(
            BrowseSection("new", appString(R.string.section_latest_titles), base.choose("sort", "createdDate,desc")),
            BrowseSection("reading", appString(R.string.section_in_progress), base.choose("read", "IN_PROGRESS")),
            BrowseSection("completed", appString(R.string.section_completed), base.choose("status", "ENDED")),
        )
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> =
        seriesList(
            page,
            filters.selected("sort") ?: "metadata.titleSort,asc",
            filters.selected("status").orEmpty(),
            filters.selected("read").orEmpty(),
            query.trim(),
        )

    private suspend fun seriesList(page: Int, sort: String, status: String, read: String, query: String): PagedResult<TitleSummary> {
        val params = buildString {
            append(pageQuery(page))
            append("&sort=").append(urlEncode(sort))
            if (query.isNotEmpty()) append("&search=").append(urlEncode(query))
            if (status.isNotEmpty()) append("&status=").append(status)
            if (read.isNotEmpty()) append("&read_status=").append(read)
            append(libraryParam())
        }
        return fetchSeries("/series?$params", page)
    }

    private suspend fun fetchSeries(path: String, page: Int): PagedResult<TitleSummary> =
        parseSeriesPage(get(path), page)

    private fun pageQuery(page: Int) = "page=${(page - 1).coerceAtLeast(0)}&size=$PAGE_SIZE"

    private fun libraryParam() = libraryId?.let { "&library_id=${urlEncode(it)}" }.orEmpty()

    // ---- Details ------------------------------------------------------------------------------

    override suspend fun getTitle(id: String): TitleDetails = parseDetails(get("/series/${pathSegment(id)}"))

    override suspend fun getChapters(titleId: String): List<Chapter> =
        parseChapters(get("/series/${pathSegment(titleId)}/books?unpaged=true&sort=metadata.numberSort,asc"))

    override suspend fun getPages(chapter: Chapter): List<ComicPage> =
        parsePages(chapter.id, get("/books/${pathSegment(chapter.id)}/pages"))

    override fun webUrl(titleId: String): String = "$base/series/${pathSegment(titleId)}"

    override fun titleIdFromUrl(url: String): String? {
        val parts = SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val i = parts.indexOf("series")
        return parts.getOrNull(i + 1).takeIf { i >= 0 }
    }

    // ---- Progress -----------------------------------------------------------------------------

    override suspend fun reportProgress(chapter: Chapter, page: Int, finished: Boolean) {
        val body = buildJsonObject {
            put("page", page + 1)
            if (finished) put("completed", true)
        }
        http.sendJson("PATCH", URI("$api/books/${pathSegment(chapter.id)}/read-progress"), body.toString(), manifest)
    }

    // ---- Parsing (internal for the fixture tests) -------------------------------------------------

    internal fun parseSeriesPage(body: String, page: Int): PagedResult<TitleSummary> {
        val root = parse(body)
        val items = (root.obj()?.get("content") ?: root).arr().orEmpty().mapNotNull { el ->
            val s = el.obj() ?: return@mapNotNull null
            val id = s.str("id") ?: return@mapNotNull null
            val meta = s["metadata"].obj()
            val count = (s["booksCount"] as? JsonPrimitive)?.intOrNull
            TitleSummary(
                id = id,
                title = meta.str("title") ?: s.str("name") ?: id,
                coverUrl = "$api/series/$id/thumbnail",
                subtitle = count?.let { appString(R.string.server_books_count, it) },
            )
        }
        val last = (root.obj()?.get("last") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true
        return PagedResult(items = items, page = page, hasNext = !last && items.isNotEmpty())
    }

    internal fun parseDetails(body: String): TitleDetails {
        val s = parse(body).obj() ?: throw SourceFetchException("Komga: unexpected series answer")
        val id = s.str("id") ?: throw SourceFetchException("Komga: series without id")
        val meta = s["metadata"].obj()
        val agg = s["booksMetadata"].obj()
        val authors = agg?.get("authors").arr().orEmpty().mapNotNull { it.obj() }
        fun names(vararg roles: String) =
            authors.filter { it.str("role")?.lowercase() in roles }.mapNotNull { it.str("name") }.distinct()
        return TitleDetails(
            id = id,
            title = meta.str("title") ?: s.str("name") ?: id,
            altTitle = meta?.get("alternateTitles").arr().orEmpty().firstNotNullOfOrNull { it.obj().str("title") },
            coverUrl = "$api/series/$id/thumbnail",
            description = meta.str("summary") ?: agg.str("summary"),
            status = when (meta.str("status")) {
                "ONGOING" -> "Ongoing"
                "ENDED" -> "Completed"
                "HIATUS" -> "On hiatus"
                "ABANDONED" -> "Cancelled"
                else -> null
            },
            authors = names("writer"),
            artists = names("penciller", "inker", "colorist", "cover"),
            genres = (meta?.get("genres").arr().orEmpty().mapNotNull { it.str() }),
            year = agg.str("releaseDate")?.take(4)?.toIntOrNull(),
        )
    }

    /** Newest first, like every other source. Books the server cannot read (EPUB, failed) are left out. */
    internal fun parseChapters(body: String): List<Chapter> {
        val root = parse(body)
        val books = (root.obj()?.get("content") ?: root).arr().orEmpty()
        return books.mapNotNull { el ->
            val b = el.obj() ?: return@mapNotNull null
            val id = b.str("id") ?: return@mapNotNull null
            val media = b["media"].obj()
            val status = media.str("status")
            if (status != null && status != "READY") return@mapNotNull null
            if (media.str("mediaType")?.contains("epub", ignoreCase = true) == true) return@mapNotNull null
            val meta = b["metadata"].obj()
            val number = meta.str("number")?.takeIf { it.toDoubleOrNull() != null }
                ?: (b["number"] as? JsonPrimitive)?.contentOrNull
            Chapter(
                id = id,
                title = meta.str("title") ?: b.str("name") ?: id,
                url = "$base/book/$id",
                number = number,
                dateLabel = (meta.str("releaseDate") ?: b.str("created"))?.take(10),
            )
        }.reversed()
    }

    internal fun parsePages(bookId: String, body: String): List<ComicPage> =
        parse(body).arr().orEmpty().mapIndexedNotNull { i, el ->
            val p = el.obj() ?: return@mapIndexedNotNull null
            val number = (p["number"] as? JsonPrimitive)?.intOrNull ?: (i + 1)
            // Browsers and Coil decode these; anything else is converted by the server.
            val type = p.str("mediaType").orEmpty().lowercase()
            val convert = if (type in NATIVE_IMAGE_TYPES) "" else "?convert=jpeg"
            ComicPage(index = i, imageUrl = "$api/books/${pathSegment(bookId)}/pages/$number$convert")
        }

    private fun parse(body: String): JsonElement =
        runCatching { json.parseToJsonElement(body) }.getOrElse { throw SourceFetchException("Komga: not JSON") }

    private suspend fun get(path: String): String = http.get(URI(api + path), manifest, jsonHeaders, checkCloudflare = false).body

    private companion object {
        const val PAGE_SIZE = 30
        val NATIVE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
