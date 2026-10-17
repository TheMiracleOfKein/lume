package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
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
import com.lume.app.util.urlEncode

class LibSocialEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val auth: LibAuthStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase: URI
        get() = URI(manifest.configString("apiBase") ?: "https://api.cdnlibs.org/api")

    private val siteId: String
        get() = manifest.configString("siteId") ?: "1"

    private suspend fun apiHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to "application/json, text/plain, */*",
            "Site-Id" to siteId,
            "Client-Time-Zone" to "Europe/Moscow",
            "Origin" to manifest.baseUrl.trimEnd('/'),
        )
        auth?.authorizationHeader(manifest.id)?.let { headers["Authorization"] = it }
        return headers
    }

    @Volatile
    private var imageServerCache: String? = null

    override suspend fun getLatest(page: Int) = mangaList(page, "last_chapter_at")
    override suspend fun getPopular(page: Int) = mangaList(page, "rating_score")

    private suspend fun mangaList(page: Int, sortBy: String): PagedResult<TitleSummary> {
        val uri = URI(
            "${apiBase.toString().trimEnd('/')}/manga?page=$page&site_id[]=$siteId&sort_by=$sortBy",
        )
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return parseList(response.body, page)
    }

    override suspend fun search(
        query: String,
        page: Int,
        filters: com.lume.app.domain.FilterList,
    ): PagedResult<TitleSummary> {
        val sort = filters.selected("sort_by")
        if (query.isBlank()) return mangaList(page, sort ?: "rating_score")
        val q = urlEncode(query)
        val sortPart = sort?.let { "&sort_by=$it" }.orEmpty()
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga?q=$q&page=$page&site_id[]=$siteId$sortPart")
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return parseList(response.body, page)
    }

    override fun getFilters(): com.lume.app.domain.FilterList = com.lume.app.domain.FilterList(
        listOf(
            com.lume.app.domain.SourceFilter.Select(
                id = "sort_by",
                name = com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort),
                options = listOf("rating_score", "last_chapter_at"),
                labels = listOf(
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_rating),
                    com.lume.app.data.locale.appString(com.lume.app.R.string.filter_sort_latest),
                ),
            ),
        ),
    )

    override suspend fun getTitle(id: String): TitleDetails {
        val slug = slug(id)
        val base = "${apiBase.toString().trimEnd('/')}/manga/$slug"
        // Without fields[] the API returns only names/cover — no summary, genres or authors.
        val withFields = base + "?" + TITLE_FIELDS.joinToString("&") { "fields[]=$it" }
        val response = runCatching {
            http.get(URI(withFields), source = manifest, headers = apiHeaders(), checkCloudflare = false)
        }.getOrElse { e ->
            if (e is com.lume.app.data.network.SourceHttpException && e.code in 400..422) {
                http.get(URI(base), source = manifest, headers = apiHeaders(), checkCloudflare = false)
            } else {
                throw e
            }
        }
        val root = asObject(json.parseToJsonElement(response.body))
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
        return TitleDetails(
            id = if (id.startsWith("/")) id else "/$slug",
            title = data["rus_name"]?.jsonPrimitive?.contentOrNull
                ?: data["name"]?.jsonPrimitive?.contentOrNull
                ?: slug,
            altTitle = data["name"]?.jsonPrimitive?.contentOrNull,
            coverUrl = cover,
            description = htmlToText(stringOrNull(data["summary"]) ?: stringOrNull(data["description"])),
            typeLabel = nestedLabel(data["type"]),
            status = nestedLabel(data["status"]),
            authors = authors,
            artists = artists,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = slug(titleId)
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga/$slug/chapters")
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        val root = json.parseToJsonElement(response.body)
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
            val branches = (item["branches"] as? JsonArray).orEmpty()
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
                        volume.takeIf { it.isNotEmpty() && it != "null" }?.let {
                            com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_volume, it)
                        },
                        number.takeIf { it.isNotEmpty() && it != "null" }?.let {
                            com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_number, it)
                        },
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

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val slug = slug(chapter.id)
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
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga/$slug/chapter?$query")
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["data"] ?: root)
        if (data["toast"] != null || data["pages"] == null) {
            val toast = (data["toast"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            throw SourceFetchException(
                toast ?: com.lume.app.data.locale.appString(
                    com.lume.app.R.string.error_pages_failed_named,
                    manifest.name,
                ),
            )
        }
        val server = resolveImageServer(data)
        val pages = mutableListOf<ComicPage>()
        val rawPages = data["pages"] as? JsonArray ?: JsonArray(emptyList())
        for (page in rawPages) {
            var url: String? = when (page) {
                is JsonPrimitive -> page.contentOrNull
                is JsonObject -> stringOrNull(page["url"])
                    ?: stringOrNull(page["link"])
                    ?: stringOrNull(page["src"])
                else -> null
            }
            if (url.isNullOrBlank()) continue
            url = absolutizePageUrl(url, server)
            pages += ComicPage(index = pages.size, imageUrl = url)
        }
        if (pages.isEmpty()) {
            throw SourceFetchException(
                com.lume.app.data.locale.appString(
                    com.lume.app.R.string.error_pages_failed_named,
                    manifest.name,
                ),
            )
        }
        return pages
    }

    private suspend fun resolveImageServer(chapterData: JsonObject): String {
        stringOrNull(chapterData["imageServer"])?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
        (chapterData["servers"] as? JsonObject)?.let { servers ->
            stringOrNull(servers["main"])?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
        }
        imageServerCache?.let { return it }
        val site = siteId.toIntOrNull() ?: 1
        val fallback = when (site) {
            2 -> "https://img2.hentaicdn.org"
            4 -> "https://img2h.hentaicdn.org"
            else -> "https://img2.imglib.info"
        }
        val resolved = runCatching {
            val uri = URI(
                "${apiBase.toString().trimEnd('/')}/constants?fields[]=imageServers",
            )
            val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
            val root = asObject(json.parseToJsonElement(response.body))
            val data = asObject(root["data"] ?: root)
            val servers = data["imageServers"] as? JsonArray ?: return@runCatching fallback
            val matching = servers.mapNotNull { it as? JsonObject }.filter { obj ->
                val ids = (obj["site_ids"] as? JsonArray).orEmpty().mapNotNull {
                    it.jsonPrimitive.contentOrNull?.toIntOrNull()
                }
                site in ids
            }
            matching.firstOrNull { stringOrNull(it["id"]) == "main" }
                ?.let { stringOrNull(it["url"]) }
                ?: matching.firstOrNull()?.let { stringOrNull(it["url"]) }
                ?: fallback
        }.getOrDefault(fallback).trimEnd('/')
        imageServerCache = resolved
        return resolved
    }

    private fun absolutizePageUrl(raw: String, server: String): String {
        val trimmed = raw.trim()
        when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> return trimmed
            trimmed.startsWith("//") && trimmed.count { it == '/' } >= 3 &&
                !trimmed.startsWith("//manga/") && !trimmed.startsWith("//uploads/") -> {
                // Protocol-relative host: //cdn.example/path
                return "https:$trimmed"
            }
            else -> {
                val path = when {
                    trimmed.startsWith("//") -> "/" + trimmed.removePrefix("//")
                    trimmed.startsWith("/") -> trimmed
                    else -> "/$trimmed"
                }
                return server.trimEnd('/') + path
            }
        }
    }

    private fun parseList(raw: String, page: Int): PagedResult<TitleSummary> {
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

    private fun slug(id: String): String {
        val path = id.substringBefore('?').trim('/')
        // ids look like "7580--slug" or "7580--slug/v1/c2"
        return path.split('/').firstOrNull().orEmpty().ifEmpty { id.trim('/') }
    }

    private fun nestedLabel(el: JsonElement?): String? = when (el) {
        is JsonObject -> stringOrNull(el["label"]) ?: stringOrNull(el["name"])
        is JsonPrimitive -> el.contentOrNull
        else -> null
    }

    private fun stringOrNull(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }

    private fun asObject(el: JsonElement): JsonObject = el as? JsonObject ?: JsonObject(emptyMap())

    override fun webUrl(titleId: String): String = "${manifest.baseUrl.trimEnd('/')}/ru/manga/${slug(titleId)}"

    /** `/ru/manga/7580--slug`, `/ru/7580--slug/read/…` or `/manga/7580--slug`. */
    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val slug = parts.firstOrNull { "--" in it }
            ?: parts.getOrNull(parts.indexOf("manga") + 1)?.takeIf { "manga" in parts }
            ?: return null
        return "/$slug"
    }

    private companion object {
        val TITLE_FIELDS = listOf(
            "summary", "genres", "tags", "authors", "artists", "releaseDate", "status_id", "chap_count",
        )
    }
}
