package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.domain.BrowseSection
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
import com.lume.app.domain.choose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import com.lume.app.util.urlEncode

class RemangaEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase: String
        get() = (manifest.configString("apiBase") ?: "https://api.remanga.org/api").trimEnd('/')

    private val siteBase: String
        get() = manifest.baseUrl.trimEnd('/')

    private val headers: Map<String, String>
        get() = mapOf(
            "Accept" to "application/json",
            "Referer" to "$siteBase/",
            "Origin" to siteBase,
        )

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> =
        titles(page, ordering = "-chapter_date")

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        titles(page, ordering = "-rating")

    /** `/titles/` ignores `ordering`; the catalog endpoint is what the site uses for sorting. */
    private suspend fun titles(page: Int, ordering: String, extra: String = ""): PagedResult<TitleSummary> {
        val uri = URI("$apiBase/search/catalog/?ordering=$ordering&count=$PAGE_SIZE&page=$page$extra")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        return parseTitleList(response.body, page)
    }

    override suspend fun search(
        query: String,
        page: Int,
        filters: com.lume.app.domain.FilterList,
    ): PagedResult<TitleSummary> {
        // The catalogue sorts and filters but has no text search; a text search ignores filters.
        if (query.isBlank()) return titles(page, filters.selected("ordering") ?: "-rating", catalogQuery(filters))
        val q = urlEncode(query.trim())
        val uri = URI("$apiBase/search/?query=$q&count=$PAGE_SIZE&page=$page")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        return parseTitleList(response.body, page)
    }

    override val filtersWithQuery: Boolean get() = false

    /** Catalogue parameters for [filters]: repeated `genres=`, `exclude_genres=` and so on. */
    internal fun catalogQuery(filters: FilterList): String = buildString {
        fun add(name: String, ids: List<String>) = ids.forEach { append("&$name=${it.substringAfter(':')}") }
        add("genres", filters.included("genres"))
        add("exclude_genres", filters.excluded("genres"))
        add("categories", filters.included("categories"))
        add("exclude_categories", filters.excluded("categories"))
        add("types", filters.included("types"))
        add("exclude_types", filters.excluded("types"))
        add("age_limit", filters.checked("age"))
        filters.text("year")?.toIntOrNull()?.let { append("&issue_year_gte=$it") }
    }

    private val siteFilters = SiteFilters(::fetchFilters)

    override suspend fun loadFilters(): FilterList = siteFilters.get(::getFilters)

    /** Genres, categories (tags), types and age limits from the site's catalogue form. */
    private suspend fun fetchFilters(): FilterList {
        val uri = URI("$apiBase/forms/titles/?get=genres&get=categories&get=types&get=age_limit")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        val content = asObject(json.parseToJsonElement(response.body))["content"] as? JsonObject
            ?: throw SourceFetchException("ReManga: no filters")
        fun entries(field: String): List<Pair<String, String>> = (content[field] as? JsonArray).orEmpty().mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val id = stringOrNull(item["id"]) ?: return@mapNotNull null
            val name = stringOrNull(item["name"]) ?: return@mapNotNull null
            id to name
        }
        fun tri(id: String, name: String) = SourceFilter.Group(
            id = id,
            name = name,
            filters = entries(id).sortedBy { it.second.lowercase() }.map { (key, label) -> SourceFilter.TriState("$id:$key", label) },
        )
        val genres = tri("genres", appString(R.string.filter_genres))
        if (genres.filters.isEmpty()) throw SourceFetchException("ReManga: no genres")
        val age = SourceFilter.Group(
            id = "age",
            name = appString(R.string.filter_age),
            filters = entries("age_limit").map { (key, label) -> SourceFilter.Check("age:$key", label) },
        )
        return FilterList(
            getFilters().filters +
                listOf(tri("types", appString(R.string.filter_type)), genres, tri("categories", appString(R.string.filter_tags)), age)
                    .filter { it.filters.isNotEmpty() } +
                SourceFilter.Text("year", appString(R.string.filter_year_from)),
        )
    }

    override fun browseSections(): List<BrowseSection> {
        val base = getFilters()
        return listOf(
            BrowseSection("views", appString(R.string.section_most_viewed), base.choose("ordering", "-views")),
            BrowseSection("new", appString(R.string.section_latest_titles), base.choose("ordering", "-id")),
        )
    }

    override fun getFilters(): com.lume.app.domain.FilterList = com.lume.app.domain.FilterList(
        listOf(
            com.lume.app.domain.SourceFilter.Select(
                id = "ordering",
                name = com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort),
                options = listOf("-rating", "-chapter_date", "-views", "-id", "-votes", "-count_chapters"),
                labels = listOf(
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_rating),
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_latest),
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_popular),
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_new),
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_votes),
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_chapters),
                ),
            ),
        ),
    )

    override suspend fun getTitle(id: String): TitleDetails {
        val dir = slug(id)
        val uri = URI("$apiBase/titles/${pathSegment(dir)}/")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["content"] ?: root["data"] ?: root)
        val genres = (data["genres"] as? JsonArray).orEmpty().mapNotNull { stringOrNull((it as? JsonObject)?.get("name")) }
        val creators = data["creators"]
        val authors = when (creators) {
            is JsonArray -> creators.mapNotNull { stringOrNull((it as? JsonObject)?.get("name")) }
            is JsonObject -> creators.values.flatMap { el ->
                when (el) {
                    is JsonArray -> el.mapNotNull { stringOrNull((it as? JsonObject)?.get("name")) }
                    is JsonObject -> listOfNotNull(stringOrNull(el["name"]))
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }.distinct()
        return TitleDetails(
            id = dir,
            title = stringOrNull(data["main_name"]) ?: stringOrNull(data["secondary_name"]) ?: dir,
            altTitle = stringOrNull(data["secondary_name"]),
            // English name first: other sources search by it.
            otherNames = (listOfNotNull(stringOrNull(data["secondary_name"]), stringOrNull(data["en_name"])) +
                stringOrNull(data["another_name"]).orEmpty().split(" / "))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_OTHER_NAMES),
            year = stringOrNull(data["issue_year"])?.toIntOrNull(),
            score = stringOrNull(data["avg_rating"])?.toDoubleOrNull()?.takeIf { it > 0 },
            coverUrl = coverUrl(data["cover"]),
            description = htmlToText(stringOrNull(data["description"])),
            typeLabel = nestedName(data["type"]),
            status = nestedName(data["status"]) ?: nestedName(data["translate_status"]),
            authors = authors,
            genres = genres + (data["categories"] as? JsonArray).orEmpty().mapNotNull { stringOrNull((it as? JsonObject)?.get("name")) },
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val dir = slug(titleId)
        val detailUri = URI("$apiBase/titles/${pathSegment(dir)}/")
        val detail = http.get(detailUri, source = manifest, headers = headers, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(detail.body))
        val data = asObject(root["content"] ?: root)
        val branches = data["branches"] as? JsonArray ?: JsonArray(emptyList())
        val branchId = (branches.firstOrNull() as? JsonObject)?.let { stringOrNull(it["id"]) }
            ?: throw SourceFetchException("ReManga: нет веток перевода")
        val out = ArrayList<Chapter>()
        var page = 1
        while (page <= 50) {
            val uri = URI("$apiBase/titles/chapters/?branch_id=$branchId&page=$page&count=100&ordering=-index")
            val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
            val chRoot = asObject(json.parseToJsonElement(response.body))
            val list = chRoot["content"] as? JsonArray ?: break
            if (list.isEmpty()) break
            for (el in list) {
                val item = el as? JsonObject ?: continue
                val id = stringOrNull(item["id"]) ?: continue
                val tome = stringOrNull(item["tome"]) ?: ""
                val number = stringOrNull(item["chapter"]) ?: ""
                val name = stringOrNull(item["name"]).orEmpty()
                val paid = item["is_paid"]?.jsonPrimitive?.contentOrNull == "true"
                if (paid) continue
                out += Chapter(
                    id = id,
                    title = buildString {
                        if (tome.isNotBlank()) append("Том $tome ")
                        append("Глава $number")
                        if (name.isNotBlank()) append(" — $name")
                    }.trim(),
                    url = id,
                    number = number,
                    volume = tome.takeIf { it.isNotBlank() },
                    branchId = branchId,
                    dateLabel = stringOrNull(item["upload_date"])?.take(10),
                )
            }
            if (list.size < 100) break
            page++
        }
        return out
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val id = chapter.id
        val uri = URI("$apiBase/titles/chapters/$id/")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["content"] ?: root)
        val pagesEl = data["pages"] ?: throw SourceFetchException("ReManga: нет страниц")
        val urls = mutableListOf<String>()
        when (pagesEl) {
            is JsonArray -> {
                for (row in pagesEl) {
                    when (row) {
                        is JsonArray -> row.forEach { collectPageUrl(it, urls) }
                        else -> collectPageUrl(row, urls)
                    }
                }
            }
            else -> Unit
        }
        if (urls.isEmpty()) throw SourceFetchException("ReManga: пустые страницы")
        return urls.mapIndexed { i, u -> ComicPage(index = i, imageUrl = u) }
    }

    private fun collectPageUrl(el: JsonElement, out: MutableList<String>) {
        val obj = el as? JsonObject ?: return
        val link = stringOrNull(obj["link"]) ?: stringOrNull(obj["url"]) ?: return
        out += if (link.startsWith("http")) link else "$siteBase$link"
    }

    private fun parseTitleList(raw: String, page: Int): PagedResult<TitleSummary> {
        val root = asObject(json.parseToJsonElement(raw))
        val list = root["content"] as? JsonArray ?: JsonArray(emptyList())
        val totalPages = ((root["props"] as? JsonObject)?.get("total_pages")).let { stringOrNull(it)?.toIntOrNull() }
        val items = list.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val dir = stringOrNull(item["dir"]) ?: return@mapNotNull null
            TitleSummary(
                id = dir,
                title = stringOrNull(item["main_name"]) ?: stringOrNull(item["secondary_name"]) ?: dir,
                coverUrl = coverUrl(item["cover"]),
                typeLabel = stringOrNull(item["type"]) ?: nestedName(item["type"]),
                latestChapter = stringOrNull(item["count_chapters"]),
            )
        }
        val hasNext = totalPages?.let { page < it } ?: (items.size >= PAGE_SIZE)
        return PagedResult(items = items, page = page, hasNext = hasNext && items.isNotEmpty())
    }

    private fun coverUrl(el: JsonElement?): String? {
        val obj = el as? JsonObject ?: return stringOrNull(el)?.let { absMedia(it) }
        val path = stringOrNull(obj["mid"]) ?: stringOrNull(obj["high"]) ?: stringOrNull(obj["low"])
        return path?.let { absMedia(it) }
    }

    private fun absMedia(path: String): String {
        if (path.startsWith("http")) return path
        val p = path.removePrefix("/")
        return if (p.startsWith("media/")) "$siteBase/$p"
        else "$siteBase/media/$p"
    }

    private fun slug(id: String): String = id.trim('/').substringBefore('/').ifBlank { id }

    private fun nestedName(el: JsonElement?): String? = when (el) {
        is JsonObject -> stringOrNull(el["name"]) ?: stringOrNull(el["label"])
        is JsonPrimitive -> el.contentOrNull
        else -> null
    }

    private fun stringOrNull(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }

    private fun asObject(el: JsonElement): JsonObject = el as? JsonObject ?: JsonObject(emptyMap())

    override fun webUrl(titleId: String): String = "$siteBase/manga/${pathSegment(slug(titleId))}"

    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val i = parts.indexOf("manga")
        return parts.getOrNull(i + 1)?.takeIf { i >= 0 }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val MAX_OTHER_NAMES = 15
    }
}
