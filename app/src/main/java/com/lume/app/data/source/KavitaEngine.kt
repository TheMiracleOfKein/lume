package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ServerAccess
import com.lume.app.data.network.SourceHttpException
import com.lume.app.data.server.ServerCredentials
import com.lume.app.domain.BrowseSection
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.PagedResult
import com.lume.app.domain.ReadProgressSink
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceLoginRequiredException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.SourceSetting
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.choose
import com.lume.app.util.urlEncode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * A Kavita server. It signs in once (API key through `Plugin/authenticate`, or user name and
 * password through `Account/login`), keeps the JWT and signs in again when a request answers 401.
 * The token reaches covers and pages through the shared HTTP client; page and cover URLs carry
 * the placeholder `apiKey=-` that the client swaps for the real key (see [ServerAccess]).
 */
class KavitaEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val credentials: ServerCredentials?,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource, ReadProgressSink {
    private val base get() = manifest.baseUrl.trimEnd('/')
    private val libraryId get() = manifest.configString("libraryId")?.trim()?.takeIf { it.isNotEmpty() }

    private val authLock = Mutex()

    @Volatile
    private var token: String? = null

    private val chapterInfo = ConcurrentHashMap<String, ChapterInfo>()

    internal data class ChapterInfo(val seriesId: Int, val volumeId: Int, val libraryId: Int, val pages: Int, val format: Int)

    override fun settings(): List<SourceSetting> = listOf(
        SourceSetting.Text("libraryId", appString(R.string.server_setting_library_id), ""),
    )

    // ---- Browse -------------------------------------------------------------------------------

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> =
        parseSeriesList(post("/api/Series/recently-added-v2?${pageQuery(page)}", filterBody(null, SORT_ADDED, false)), page)

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        parseSeriesList(post("/api/Series/all-v2?${pageQuery(page)}", filterBody(null, SORT_NAME, true)), page)

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = "sort",
                name = appString(R.string.filter_sort),
                options = listOf(SORT_NAME.toString(), SORT_ADDED.toString(), SORT_UPDATED.toString()),
                labels = listOf(
                    appString(R.string.filter_sort_title),
                    appString(R.string.filter_sort_new),
                    appString(R.string.filter_sort_latest),
                ),
            ),
        ),
    )

    override fun browseSections(): List<BrowseSection> =
        listOf(BrowseSection("new", appString(R.string.section_latest_titles), getFilters().choose("sort", SORT_ADDED.toString())))

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val sort = filters.selected("sort")?.toIntOrNull() ?: SORT_NAME
        val body = filterBody(query.trim().takeIf { it.isNotEmpty() }, sort, ascending = sort == SORT_NAME)
        return parseSeriesList(post("/api/Series/all-v2?${pageQuery(page)}", body), page)
    }

    private fun pageQuery(page: Int) = "pageNumber=${page.coerceAtLeast(1)}&pageSize=$PAGE_SIZE"

    /** A `SeriesFilterV2Dto`: an optional name match, an optional library, and the sort. */
    internal fun filterBody(query: String?, sortField: Int, ascending: Boolean): String = buildJsonObject {
        put(
            "statements",
            buildJsonArray {
                if (query != null) add(statement(FIELD_SERIES_NAME, CMP_MATCHES, query))
                libraryId?.let { add(statement(FIELD_LIBRARIES, CMP_CONTAINS, it)) }
            },
        )
        put("combination", COMBINATION_AND)
        put("sortOptions", buildJsonObject { put("sortField", sortField); put("isAscending", ascending) })
        put("limitTo", 0)
    }.toString()

    private fun statement(field: Int, comparison: Int, value: String) =
        buildJsonObject { put("comparison", comparison); put("field", field); put("value", value) }

    // ---- Details ------------------------------------------------------------------------------

    override suspend fun getTitle(id: String): TitleDetails {
        val series = get("/api/Series/${pathSegment(id)}")
        val metadata = runCatching { get("/api/Series/metadata?seriesId=${urlEncode(id)}") }.getOrNull()
        return parseDetails(series, metadata)
    }

    override suspend fun getChapters(titleId: String): List<Chapter> =
        parseChapters(titleId, get("/api/Series/series-detail?seriesId=${urlEncode(titleId)}"))

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val info = parseChapterInfo(get("/api/Reader/chapter-info?chapterId=${urlEncode(chapter.id)}"))
        chapterInfo[chapter.id] = info
        return pageUrls(chapter.id, info)
    }

    // ---- Progress -----------------------------------------------------------------------------

    override suspend fun reportProgress(chapter: Chapter, page: Int, finished: Boolean) {
        val info = chapterInfo[chapter.id]
            ?: parseChapterInfo(get("/api/Reader/chapter-info?chapterId=${urlEncode(chapter.id)}")).also { chapterInfo[chapter.id] = it }
        val chapterId = chapter.id.toIntOrNull() ?: return
        if (finished) {
            post(
                "/api/Reader/mark-chapter-read",
                buildJsonObject { put("seriesId", info.seriesId); put("chapterId", chapterId) }.toString(),
            )
        } else {
            post(
                "/api/Reader/progress",
                buildJsonObject {
                    put("volumeId", info.volumeId)
                    put("chapterId", chapterId)
                    put("pageNum", page.coerceIn(0, (info.pages - 1).coerceAtLeast(0)))
                    put("seriesId", info.seriesId)
                    put("libraryId", info.libraryId)
                }.toString(),
            )
        }
    }

    // ---- Parsing (internal for the fixture tests) -------------------------------------------------

    internal fun parseSeriesList(body: String, page: Int): PagedResult<TitleSummary> {
        val items = parse(body).arr().orEmpty().mapNotNull { el ->
            val s = el.obj() ?: return@mapNotNull null
            val id = s.intStr("id") ?: return@mapNotNull null
            TitleSummary(
                id = id,
                title = s.str("name") ?: s.str("localizedName") ?: id,
                coverUrl = coverUrl(id),
                subtitle = s.str("libraryName"),
            )
        }
        // The page count is in a response header; a full page means there may be one more.
        return PagedResult(items = items, page = page, hasNext = items.size >= PAGE_SIZE)
    }

    internal fun parseDetails(seriesBody: String, metadataBody: String?): TitleDetails {
        val s = parse(seriesBody).obj() ?: throw SourceFetchException("Kavita: unexpected series answer")
        val id = s.intStr("id") ?: throw SourceFetchException("Kavita: series without id")
        val m = metadataBody?.let { runCatching { parse(it).obj() }.getOrNull() }
        fun people(key: String) = m?.get(key).arr().orEmpty().mapNotNull { it.obj().str("name") }
        val localized = s.str("localizedName")?.takeIf { it != s.str("name") }
        return TitleDetails(
            id = id,
            title = s.str("name") ?: id,
            altTitle = localized ?: s.str("originalName")?.takeIf { it != s.str("name") },
            coverUrl = coverUrl(id),
            description = m.str("summary"),
            status = when (m?.get("publicationStatus")?.let { (it as? JsonPrimitive)?.intOrNull }) {
                0 -> "Ongoing"
                1 -> "On hiatus"
                2, 4 -> "Completed"
                3 -> "Cancelled"
                else -> null
            },
            authors = people("writers"),
            artists = (people("pencillers") + people("inkers") + people("colorists") + people("coverArtists")).distinct(),
            genres = m?.get("genres").arr().orEmpty().mapNotNull { it.obj().str("title") },
            year = (m?.get("releaseYear") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
        )
    }

    /**
     * Volumes with their chapters, then loose chapters and specials; newest first like every
     * other source. EPUB books are skipped (they are not comics).
     */
    internal fun parseChapters(seriesId: String, body: String): List<Chapter> {
        val root = parse(body).obj() ?: throw SourceFetchException("Kavita: unexpected series detail")
        val seen = HashSet<String>()
        val out = ArrayList<Chapter>()

        fun add(chapter: JsonObject, volume: JsonObject?, special: Boolean) {
            val id = chapter.intStr("id") ?: return
            if (!seen.add(id)) return
            if (chapter["files"].arr()?.firstOrNull()?.obj()?.get("format").let { (it as? JsonPrimitive)?.intOrNull } == FORMAT_EPUB) return
            val range = chapter.str("range").orEmpty()
            val volumeOnly = range.startsWith(LOOSE_LEAF)
            val volumeName = volume.str("name")?.takeIf { it != LOOSE_LEAF && !it.startsWith(LOOSE_LEAF) }
            val named = chapter.str("titleName")
            val title = when {
                special -> named ?: chapter.str("title") ?: range
                volumeOnly -> named ?: appString(R.string.chapter_volume, volumeName ?: "?")
                else -> named ?: appString(R.string.chapter_number, range)
            }
            val date = (chapter.str("releaseDate") ?: chapter.str("createdUtc"))?.take(10)?.takeIf { !it.startsWith("0001") }
            out += Chapter(
                id = id,
                title = title,
                url = "$base/api/Reader/chapter-info?chapterId=$id",
                number = range.takeIf { !special && !volumeOnly && it.toDoubleOrNull() != null },
                volume = volumeName?.takeIf { !special },
                dateLabel = date,
            )
        }

        root["volumes"].arr().orEmpty().mapNotNull { it.obj() }
            .sortedBy { (it["minNumber"] as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
            .forEach { v -> v["chapters"].arr().orEmpty().mapNotNull { it.obj() }.sortedBy { it.sortOrder() }.forEach { add(it, v, false) } }
        root["chapters"].arr().orEmpty().mapNotNull { it.obj() }.sortedBy { it.sortOrder() }.forEach { add(it, null, false) }
        root["specials"].arr().orEmpty().mapNotNull { it.obj() }.sortedBy { it.sortOrder() }.forEach { add(it, null, true) }
        return out.reversed()
    }

    private fun JsonObject.sortOrder(): Double = (this["sortOrder"] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    internal fun parseChapterInfo(body: String): ChapterInfo {
        val o = parse(body).obj() ?: throw SourceFetchException("Kavita: unexpected chapter info")
        fun int(key: String) = (o[key] as? JsonPrimitive)?.intOrNull ?: 0
        return ChapterInfo(
            seriesId = int("seriesId"),
            volumeId = int("volumeId"),
            libraryId = int("libraryId"),
            pages = int("pages"),
            format = int("seriesFormat"),
        )
    }

    internal fun pageUrls(chapterId: String, info: ChapterInfo): List<ComicPage> {
        val extract = if (info.format == FORMAT_PDF) "&extractPdf=true" else ""
        return (0 until info.pages).map { i ->
            ComicPage(index = i, imageUrl = "$base/api/Reader/image?chapterId=${urlEncode(chapterId)}&page=$i&apiKey=$KEY_PLACEHOLDER$extract")
        }
    }

    private fun coverUrl(seriesId: String) = "$base/api/image/series-cover?seriesId=${urlEncode(seriesId)}&apiKey=$KEY_PLACEHOLDER"

    private fun JsonObject.intStr(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun parse(body: String): JsonElement =
        runCatching { json.parseToJsonElement(body) }.getOrElse { throw SourceFetchException("Kavita: not JSON") }

    // ---- Requests with sign-in -----------------------------------------------------------------

    private suspend fun get(path: String): String = authed { headers -> http.get(URI(base + path), manifest, headers, checkCloudflare = false).body }

    private suspend fun post(path: String, body: String): String =
        authed { headers -> http.sendJson("POST", URI(base + path), body, manifest, headers).body }

    /** Runs [block] with the Bearer header; one 401 means the token ran out, so sign in again and retry once. */
    private suspend fun <T> authed(block: suspend (Map<String, String>) -> T): T {
        val current = token ?: signIn(null)
        return try {
            block(bearer(current))
        } catch (e: SourceHttpException) {
            if (e.code != 401) throw e
            block(bearer(signIn(current)))
        }
    }

    private fun bearer(value: String) = mapOf("Accept" to "application/json", "Authorization" to "Bearer $value")

    /** Signs in unless another request already did (compared with [stale]). Returns the token. */
    private suspend fun signIn(stale: String?): String = authLock.withLock {
        token?.takeIf { it != stale }?.let { return it }
        val creds = credentials?.takeIf { !it.isEmpty }
            ?: throw SourceLoginRequiredException(appString(R.string.server_error_no_signin, manifest.name))
        val answer = if (creds.hasApiKey) {
            http.sendJson(
                "POST",
                URI("$base/api/Plugin/authenticate?apiKey=${urlEncode(creds.apiKey)}&pluginName=${urlEncode(PLUGIN_NAME)}"),
                "{}",
                manifest,
            )
        } else {
            http.sendJson(
                "POST",
                URI("$base/api/Account/login"),
                buildJsonObject { put("username", creds.username); put("password", creds.password) }.toString(),
                manifest,
            )
        }
        val user = parse(answer.body).obj()
        val jwt = user.str("token") ?: throw SourceLoginRequiredException(appString(R.string.server_error_no_signin, manifest.name))
        val key = user.str("apiKey") ?: creds.apiKey.takeIf { it.isNotEmpty() }
        token = jwt
        // Covers and pages go through the shared client: it adds the token and the real key.
        http.servers.register(
            manifest.baseUrl,
            ServerAccess(
                headers = mapOf("Authorization" to "Bearer $jwt"),
                query = key?.let { mapOf("apiKey" to it) }.orEmpty(),
            ),
        )
        jwt
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val PLUGIN_NAME = "Lume"
        const val KEY_PLACEHOLDER = "-"
        const val LOOSE_LEAF = "-100000"

        // SeriesFilterField / FilterComparison / SeriesSortField / FilterCombination values.
        const val FIELD_SERIES_NAME = 1
        const val FIELD_LIBRARIES = 19
        const val CMP_CONTAINS = 5
        const val CMP_MATCHES = 7
        const val COMBINATION_AND = 1
        const val SORT_NAME = 1
        const val SORT_ADDED = 2
        const val SORT_UPDATED = 4

        // MangaFormat
        const val FORMAT_EPUB = 3
        const val FORMAT_PDF = 4
    }
}
