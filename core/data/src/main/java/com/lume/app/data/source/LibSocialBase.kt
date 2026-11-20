package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.HttpResult
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.BrowseSection
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.FilterList
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceLoginRequiredException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.choose
import com.lume.app.util.urlEncode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * The MangaLib-family API (`api.cdnlibs.org`) shared by every Lib site, whatever it serves:
 * catalogue, search and filters, title details, chapters with their teams, bookmarks and links.
 * The sites differ by `engineConfig.siteId` (1 MangaLib, 2 SlashLib / YaoiLib, 3 RanobeLib, 4 HentaiLib)
 * and `engineConfig.webPath` (`manga`, or `book` on RanobeLib). Subclasses add the chapter
 * content: image pages ([LibSocialEngine] in the comics module) or text (RanobeLib).
 */
abstract class LibSocialBase(
    final override val manifest: SourceManifest,
    protected val http: AppHttp,
    protected val auth: LibAuthStore? = null,
    protected val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource, LibBookmarkSource {
    protected val apiBase: URI
        get() = URI(manifest.configString("apiBase") ?: "https://api.cdnlibs.org/api")

    protected val apiRoot: String
        get() = apiBase.toString().trimEnd('/')

    protected val siteId: String
        get() = manifest.configString("siteId") ?: "1"

    /** The site's path for a title page: `/ru/<webPath>/<slug>`. */
    private val webPath: String
        get() = manifest.configString("webPath") ?: "manga"

    protected suspend fun apiHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to "application/json, text/plain, */*",
            "Site-Id" to siteId,
            "Client-Time-Zone" to "Europe/Moscow",
            "Origin" to manifest.baseUrl.trimEnd('/'),
        )
        auth?.authorizationHeader(manifest.id)?.let { headers["Authorization"] = it }
        return headers
    }

    override suspend fun getLatest(page: Int) = mangaList(page, "last_chapter_at")
    override suspend fun getPopular(page: Int) = mangaList(page, "rating_score")

    private suspend fun mangaList(page: Int, sortBy: String): PagedResult<TitleSummary> {
        val uri = URI("$apiRoot/manga?page=$page&site_id[]=$siteId&sort_by=$sortBy")
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return parseTitleList(response.body, page)
    }

    override suspend fun search(
        query: String,
        page: Int,
        filters: FilterList,
    ): PagedResult<TitleSummary> {
        val uri = URI("$apiRoot/manga?page=$page&site_id[]=$siteId" + filterQuery(query, filters))
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return parseTitleList(response.body, page)
    }

    /** The catalogue query for [query] and [filters]; the site applies both together. */
    fun filterQuery(query: String, filters: FilterList): String = buildString {
        if (query.isNotBlank()) append("&q=${urlEncode(query.trim())}")
        val sort = filters.sort("sort_by")
        // A text search is ordered by relevance unless a sort was chosen.
        if (sort != null && (query.isBlank() || sort.index != 0 || sort.ascending)) {
            append("&sort_by=${sort.selectedValue}")
            if (sort.ascending) append("&sort_type=asc")
        } else if (query.isBlank()) {
            append("&sort_by=rating_score")
        }
        append(arrayParams("genres", filters.included("genres").map(::filterKey)))
        append(arrayParams("genres_exclude", filters.excluded("genres").map(::filterKey)))
        append(arrayParams("tags", filters.included("tags").map(::filterKey)))
        append(arrayParams("tags_exclude", filters.excluded("tags").map(::filterKey)))
        append(arrayParams("types", filters.checked("types").map(::filterKey)))
        append(arrayParams("status", filters.checked("status").map(::filterKey)))
        append(arrayParams("format", filters.included("format").map(::filterKey)))
        append(arrayParams("format_exclude", filters.excluded("format").map(::filterKey)))
        if (filters.selected("match") == "any") append("&genres_soft_search=1&tags_soft_search=1")
    }

    private fun filterKey(filterId: String) = filterId.substringAfter(':')

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Sort(
                id = "sort_by",
                name = appString(R.string.filter_sort),
                options = listOf("rating_score", "rate_avg", "views", "last_chapter_at", "created_at", "releaseDate", "chap_count", "rus_name"),
                labels = listOf(
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_rating),
                    appString(R.string.section_most_viewed),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.section_latest_titles),
                    appString(R.string.filter_sort_year),
                    appString(R.string.filter_sort_chapters),
                    appString(R.string.filter_sort_title),
                ),
            ),
        ),
    )

    private val siteFilters = SiteFilters(::fetchFilters)

    override suspend fun loadFilters(): FilterList = siteFilters.get(::getFilters)

    /** Genres, tags, types, statuses and formats from `/constants`, only those of this site. */
    private suspend fun fetchFilters(): FilterList {
        val fields = listOf("genres", "tags", "types", "status", "format")
        val uri = URI("$apiRoot/constants?" + fields.joinToString("&") { "fields[]=$it" })
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        val data = asObject(json.parseToJsonElement(response.body))["data"].obj()
            ?: throw SourceFetchException("${manifest.name}: no filters")
        fun entries(field: String): List<Pair<String, String>> = data[field].arr().orEmpty().mapNotNull { el ->
            val item = el.obj() ?: return@mapNotNull null
            val id = item.str("id")?.takeIf { it != "0" } ?: return@mapNotNull null
            val sites = item["site_ids"].arr()?.mapNotNull { it.str() }.orEmpty()
            if (sites.isNotEmpty() && siteId !in sites) return@mapNotNull null
            val name = item.str("label") ?: item.str("name") ?: return@mapNotNull null
            id to name
        }
        fun tri(id: String, name: String, list: List<Pair<String, String>>) = SourceFilter.Group(
            id = id,
            name = name,
            filters = list.sortedBy { it.second.lowercase() }.map { (key, label) -> SourceFilter.TriState("$id:$key", label) },
        )
        fun checks(id: String, name: String, list: List<Pair<String, String>>) = SourceFilter.Group(
            id = id,
            name = name,
            filters = list.map { (key, label) -> SourceFilter.Check("$id:$key", label) },
        )
        val groups = listOf(
            checks("types", appString(R.string.filter_type), entries("types")),
            checks("status", appString(R.string.filter_status), entries("status")),
            tri("genres", appString(R.string.filter_genres), entries("genres")),
            tri("tags", appString(R.string.filter_tags), entries("tags")),
            tri("format", appString(R.string.filter_format), entries("format")),
        ).filter { it.filters.isNotEmpty() }
        if (groups.none { it.id == "genres" }) throw SourceFetchException("${manifest.name}: no genres")
        val match = SourceFilter.Select(
            id = "match",
            name = appString(R.string.filter_match),
            options = listOf("all", "any"),
            labels = listOf(appString(R.string.filter_match_all), appString(R.string.filter_match_any)),
        )
        return FilterList(getFilters().filters + groups + match)
    }

    override fun browseSections(): List<BrowseSection> {
        val base = getFilters()
        return listOf(
            BrowseSection("views", appString(R.string.section_most_viewed), base.choose("sort_by", "views")),
            BrowseSection("new", appString(R.string.section_latest_titles), base.choose("sort_by", "created_at")),
            BrowseSection(
                "completed",
                appString(R.string.section_completed),
                // Status 2 is "completed" on every Lib site.
                FilterList(base.filters + SourceFilter.Group("status", "", listOf(SourceFilter.Check("status:2", "", state = true)))),
            ),
        )
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val base = "$apiRoot/manga/${slug(id)}"
        // Without fields[] the API returns only names/cover — no summary, genres or authors.
        val withFields = base + "?" + TITLE_FIELDS.joinToString("&") { "fields[]=$it" }
        val response = runCatching {
            http.get(URI(withFields), source = manifest, headers = apiHeaders(), checkCloudflare = false)
        }.getOrElse { e ->
            if (e is SourceHttpException && e.code in 400..422) {
                http.get(URI(base), source = manifest, headers = apiHeaders(), checkCloudflare = false)
            } else {
                throw e
            }
        }
        return parseDetails(response.body, id)
    }

    fun parseDetails(body: String, id: String): TitleDetails {
        val slug = slug(id)
        val root = asObject(json.parseToJsonElement(body))
        val data = asObject(root["data"] ?: root)
        val cover = coverUrl(data["cover"])
        val genres = ((data["genres"] as? JsonArray).orEmpty() + (data["tags"] as? JsonArray).orEmpty()).mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }.distinct()
        val artists = (data["artists"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        val authors = (data["authors"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        val name = data.str("name")
        val english = data.str("eng_name")
        return TitleDetails(
            id = if (id.startsWith("/")) id else "/$slug",
            title = data["rus_name"]?.jsonPrimitive?.contentOrNull
                ?: name
                ?: slug,
            altTitle = english ?: name,
            // The English and original names first: other sources search by them.
            otherNames = (listOfNotNull(english, name) + data["otherNames"].arr().orEmpty().mapNotNull { it.str() })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_OTHER_NAMES),
            year = data.str("releaseDate")?.take(4)?.toIntOrNull(),
            score = data["rating"].obj()?.str("average")?.toDoubleOrNull()?.takeIf { it > 0 },
            coverUrl = cover,
            description = summaryText(data["summary"]) ?: summaryText(data["description"]) ?: summaryText(data["rawSummary"]),
            typeLabel = nestedLabel(data["type"]),
            status = nestedLabel(data["status"]),
            authors = authors,
            artists = artists,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val uri = URI("$apiRoot/manga/${slug(titleId)}/chapters")
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return parseChapters(response.body, titleId)
    }

    fun parseChapters(body: String, titleId: String): List<Chapter> {
        val slug = slug(titleId)
        val root = json.parseToJsonElement(body)
        val list = when {
            root is JsonArray -> root
            root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
            root is JsonObject && root["data"] is JsonObject ->
                (root["data"] as JsonObject)["chapters"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val chapters = list.flatMap { el ->
            val item = el as? JsonObject ?: return@flatMap emptyList()
            val volume = stringOrNull(item["volume"])
                ?: stringOrNull(item["chapter_volume"])
                ?: ""
            val number = stringOrNull(item["number"])
                ?: stringOrNull(item["chapter_number"])
                ?: ""
            val name = (stringOrNull(item["name"])
                ?: stringOrNull(item["chapter_name"])
                ?: "").trim()
            val allBranches = (item["branches"] as? JsonArray).orEmpty()
            // Paid early access: a translation that can't be read yet is left out, and a chapter
            // with no readable translation is left out entirely (it shows up once it opens).
            val branches = allBranches.filterNot { isLocked(it as? JsonObject) }
            if (allBranches.isNotEmpty() && branches.isEmpty()) return@flatMap emptyList()
            // One entry per translation team (branch). Do NOT use branches[].id — that is the
            // chapter row id, not a branch.
            val branchIds: List<Pair<String?, String?>> = branches.mapNotNull { branchEl ->
                val branch = branchEl as? JsonObject ?: return@mapNotNull null
                val id = stringOrNull(branch["branch_id"]) ?: return@mapNotNull null
                val teams = (branch["teams"] as? JsonArray).orEmpty().mapNotNull { team ->
                    (team as? JsonObject)?.let { stringOrNull(it["name"]) }?.trim()?.takeIf { it.isNotEmpty() }
                }
                id to teams.joinToString(", ").ifEmpty { null }
            }.distinctBy { it.first }.ifEmpty { listOf(null to null) }
            branchIds.map { (branchId, teamName) ->
                val bidSuffix = if (branchId.isNullOrBlank()) "" else "?bid=$branchId"
                Chapter(
                    id = "/$slug/v$volume/c$number$bidSuffix",
                    title = listOfNotNull(
                        volume.takeIf { it.isNotEmpty() && it != "null" }?.let { appString(R.string.chapter_volume, it) },
                        number.takeIf { it.isNotEmpty() && it != "null" }?.let { appString(R.string.chapter_number, it) },
                        name.takeIf { it.isNotEmpty() },
                    ).joinToString(" ").trim().ifBlank { "Глава $number" },
                    url = "/$slug/v$volume/c$number$bidSuffix",
                    number = number.takeIf { it.isNotEmpty() && it != "null" },
                    volume = volume.takeIf { it.isNotEmpty() && it != "null" },
                    branchId = branchId?.takeIf { it.isNotBlank() },
                    branchName = teamName,
                )
            }
        }
        // API returns oldest → newest; reverse for UI (newest first), reader re-sorts.
        return chapters.asReversed()
    }

    /**
     * A translation behind the site's paywall: `restricted_view.is_open` is false until it opens
     * for everyone (`expired_at`) or the signed-in reader has bought it.
     */
    private fun isLocked(branch: JsonObject?): Boolean {
        val restricted = branch?.get("restricted_view") as? JsonObject ?: return false
        return (restricted["is_open"] as? JsonPrimitive)?.content == "false"
    }

    /**
     * The chapter's own answer (`/manga/<slug>/chapter?number&volume&branch_id`): pages on the
     * manga sites, text on RanobeLib. With `engineConfig.pagesNeedLogin` (HentaiLib), a 404
     * without a sign-in means "sign in first".
     */
    protected suspend fun fetchChapter(chapter: Chapter): HttpResult {
        val volume = chapter.volume ?: "1"
        val number = chapter.number ?: "1"
        val query = buildString {
            append("number=").append(urlEncode(number))
            append("&volume=").append(urlEncode(volume))
            // Empty branch_id is rejected by the API — omit the param entirely when null.
            val bid = chapter.branchId?.takeIf { it.isNotBlank() }
                ?: chapter.id.substringAfter("bid=", "").substringBefore('&').takeIf { it.isNotBlank() }
            if (bid != null) {
                append("&branch_id=").append(urlEncode(bid))
            }
        }
        val uri = URI("$apiRoot/manga/${slug(chapter.id)}/chapter?$query")
        return try {
            http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        } catch (e: SourceHttpException) {
            if (e.code == 404 && manifest.configString("pagesNeedLogin") == "true" && auth?.authorizationHeader(manifest.id) == null) {
                throw SourceLoginRequiredException(appString(R.string.error_source_login_required, manifest.name))
            }
            throw e
        }
    }

    fun parseTitleList(raw: String, page: Int): PagedResult<TitleSummary> {
        val root = asObject(json.parseToJsonElement(raw))
        val data = root["data"]
        val apiHasNext = (root["meta"] as? JsonObject)?.get("has_next_page")?.let { stringOrNull(it)?.toBooleanStrictOrNull() }
        val list = when {
            data is JsonArray -> data
            data is JsonObject && data["data"] is JsonArray -> data["data"]!!.jsonArray
            else -> JsonArray(emptyList())
        }
        val items = list.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val slug = stringOrNull(item["slug_url"])
                ?: stringOrNull(item["slug"])
                ?: stringOrNull(item["id"])
                ?: return@mapNotNull null
            TitleSummary(
                id = "/$slug",
                title = stringOrNull(item["rus_name"])
                    ?: stringOrNull(item["name"])
                    ?: slug,
                coverUrl = coverUrl(item["cover"])
                    ?: stringOrNull(item["image"]),
                typeLabel = nestedLabel(item["type"]),
                latestChapter = stringOrNull(item["last_chapter_number"])
                    ?: stringOrNull(item["last_chapter"]),
            )
        }
        return PagedResult(items = items, page = page, hasNext = apiHasNext ?: (items.size >= 20))
    }

    private fun coverUrl(el: JsonElement?): String? {
        val coverObj = el as? JsonObject ?: return stringOrNull(el)
        return stringOrNull(coverObj["default"])
            ?: stringOrNull(coverObj["md"])
            ?: stringOrNull(coverObj["thumbnail"])
    }

    protected fun slug(id: String): String {
        val path = id.substringBefore('?').trim('/')
        // ids look like "7580--slug" or "7580--slug/v1/c2"
        return path.split('/').firstOrNull().orEmpty().ifEmpty { id.trim('/') }
    }

    private fun nestedLabel(el: JsonElement?): String? = when (el) {
        is JsonObject -> stringOrNull(el["label"]) ?: stringOrNull(el["name"])
        is JsonPrimitive -> el.contentOrNull
        else -> null
    }

    protected fun stringOrNull(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }

    /**
     * A title's description as plain text. The API sends either an HTML string or, for most
     * titles now, a rich-text document (`{"type":"doc",…}`, the format of chapter text).
     */
    private fun summaryText(el: JsonElement?): String? = when (el) {
        is JsonObject, is JsonArray -> LibChapterText.toHtml(JsonObject(mapOf("content" to el)), manifest.baseUrl)
        else -> stringOrNull(el)
    }?.let(::htmlToText)?.takeIf { it.isNotBlank() }

    protected fun asObject(el: JsonElement): JsonObject = el as? JsonObject ?: JsonObject(emptyMap())

    /**
     * The signed-in user's bookmark folders and every title in them. Needs a login.
     *
     * `status=0` returns every bookmark of every status, the user's own folders included, and
     * each entry names its folder in `status`; that is one paged pass. If it gives nothing,
     * the folders are read one by one. Folder names come from `/bookmarks/folder`, with the
     * built-in statuses named locally when that call fails.
     */
    override suspend fun fetchBookmarks(): Pair<List<LibBookmarkFolder>, List<LibBookmark>> {
        val token = auth?.getToken(manifest.id)?.takeIf { it.isValid() }
            ?: throw SourceLoginRequiredException(appString(R.string.error_source_login_required, manifest.name))
        val userId = token.auth?.id?.takeIf { it > 0 }
            ?: throw SourceLoginRequiredException(appString(R.string.error_source_login_required, manifest.name))
        val siteFolders = runCatching {
            val response = http.get(URI("$apiRoot/bookmarks/folder/$userId"), source = manifest, headers = apiHeaders(), checkCloudflare = false)
            parseLibBookmarkFolders(response.body)
        }.getOrDefault(emptyList())

        suspend fun readStatus(status: String?): List<LibBookmark> {
            val items = mutableListOf<LibBookmark>()
            var page = 1
            while (page <= MAX_BOOKMARK_PAGES) {
                val query = status?.let { "&status=${urlEncode(it)}" }.orEmpty()
                val uri = URI("$apiRoot/bookmarks?user_id=$userId$query&sort_by=name&sort_type=asc&page=$page")
                val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
                // With status=0 every entry carries its own folder; no fallback folder id.
                val parsed = parseLibBookmarksPage(response.body, siteId, status?.takeIf { it != LIB_ALL_BOOKMARKS })
                items += parsed.items
                if (!parsed.hasNext) break
                page++
            }
            return items
        }

        var items = runCatching { readStatus(LIB_ALL_BOOKMARKS) }.getOrDefault(emptyList())
        if (items.isEmpty()) {
            items = LIB_IMPORTED_STATUSES.flatMap { readStatus(it) }
        }
        val folders = withDefaultLibFolders(
            siteFolders,
            items.mapNotNullTo(HashSet()) { it.folderId },
            mapOf(
                "1" to appString(R.string.lib_folder_reading),
                "2" to appString(R.string.lib_folder_planned),
                "3" to appString(R.string.lib_folder_dropped),
                "4" to appString(R.string.lib_folder_read),
                "5" to appString(R.string.lib_folder_favorite),
            ),
        )
        return folders to items.distinctBy { it.titleId to it.folderId }
    }

    override fun webUrl(titleId: String): String = "${manifest.baseUrl.trimEnd('/')}/ru/$webPath/${slug(titleId)}"

    /** `/ru/manga/7580--slug`, `/ru/book/7580--slug`, `/ru/7580--slug/read/…` or `/manga/7580--slug`. */
    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val slug = parts.firstOrNull { "--" in it }
            ?: parts.getOrNull(parts.indexOf(webPath) + 1)?.takeIf { webPath in parts }
            ?: return null
        return "/$slug"
    }

    private companion object {
        val TITLE_FIELDS = listOf(
            "summary", "genres", "tags", "authors", "artists", "releaseDate", "status_id", "chap_count",
            // The score (`rating`) comes only with rate_avg and rate.
            "otherNames", "rate_avg", "rate",
        )
        private const val MAX_OTHER_NAMES = 15
        /** A guard against a paging answer that never ends. */
        private const val MAX_BOOKMARK_PAGES = 200
    }
}
