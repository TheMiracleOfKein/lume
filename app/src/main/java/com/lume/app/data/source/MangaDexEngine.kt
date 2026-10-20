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
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceLinks
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.SourceSetting
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.choose
import com.lume.app.domain.include
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import com.lume.app.util.urlEncode

/**
 * MangaDex official API (https://api.mangadex.org).
 */
class MangaDexEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase get() = (manifest.configString("apiBase") ?: "https://api.mangadex.org").trimEnd('/')
    private val languages: List<String>
        get() = (manifest.configString("languages") ?: "en,ru")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** `engineConfig.dataSaver = true` serves compressed pages (smaller downloads, lower quality). */
    private val dataSaver get() = manifest.configBool("dataSaver")

    /** Content ratings shown in lists and search (Sources → MangaDex → Settings). */
    private val contentRatings: List<String>
        get() = (manifest.configString("contentRating") ?: DEFAULT_RATINGS.joinToString(","))
            .split(',')
            .map { it.trim() }
            .filter { it in ALL_RATINGS }
            .ifEmpty { DEFAULT_RATINGS }

    override suspend fun getPopular(page: Int) =
        mangaList(page, query = "", filters = FilterList(), orderKey = "followedCount")

    override suspend fun getLatest(page: Int) =
        mangaList(page, query = "", filters = FilterList(), orderKey = "latestUploadedChapter")

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val sort = filters.sort("sort")
        val orderKey = sort?.selectedValue ?: if (query.isBlank()) "followedCount" else "relevance"
        return mangaList(page, query, filters, orderKey, ascending = sort?.ascending == true)
    }

    private suspend fun mangaList(
        page: Int,
        query: String,
        filters: FilterList,
        orderKey: String,
        ascending: Boolean = false,
    ): PagedResult<TitleSummary> {
        val offset = (page - 1) * PAGE_SIZE
        val langs = filters.selected("lang")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: languages
        val ratings = filters.checked("rating").ifEmpty { contentRatings }
        // Relevance only means something with a search text.
        val order = if (orderKey == "relevance" && query.isBlank()) "followedCount" else orderKey
        val q = buildString {
            append("limit=$PAGE_SIZE&offset=$offset")
            if (query.isNotBlank()) append("&title=${enc(query.trim())}")
            append("&order[$order]=${if (ascending) "asc" else "desc"}")
            append("&includes[]=cover_art")
            for (rating in ratings) append("&contentRating[]=$rating")
            for (lang in langs) append("&availableTranslatedLanguage[]=${enc(lang)}")
            for (status in filters.checked("status")) append("&status[]=$status")
            for (demo in filters.checked("demographic")) append("&publicationDemographic[]=$demo")
            for (origin in filters.checked("origin")) append("&originalLanguage[]=$origin")
            val included = TAG_GROUPS.flatMap { filters.included(it) }.map(::tagId)
            val excluded = TAG_GROUPS.flatMap { filters.excluded(it) }.map(::tagId)
            for (tag in included) append("&includedTags[]=$tag")
            for (tag in excluded) append("&excludedTags[]=$tag")
            if (included.size > 1) append("&includedTagsMode=${filters.selected("tagMode") ?: "AND"}")
            if (query.isBlank()) append("&hasAvailableChapters=true")
        }
        val response = http.get(URI("$apiBase/manga?$q"), source = manifest, checkCloudflare = false)
        return parseMangaList(response.body, page)
    }

    private val siteFilters = SiteFilters { getFilters().withTags(fetchTags()) }

    override suspend fun loadFilters(): FilterList = siteFilters.get(::getFilters)

    /** MangaDex tags by their group (genre, theme, format, content), names in English. */
    private suspend fun fetchTags(): Map<String, List<Pair<String, String>>> {
        val response = http.get(URI("$apiBase/manga/tag"), source = manifest, checkCloudflare = false)
        val data = json.parseToJsonElement(response.body).jsonObject["data"]?.jsonArray.orEmpty()
        return data.mapNotNull { el ->
            val item = el.jsonObject
            val id = item["id"].str() ?: return@mapNotNull null
            val attrs = item["attributes"].obj() ?: return@mapNotNull null
            val name = attrs["name"].obj()?.let { pickTitle(it) } ?: return@mapNotNull null
            Triple(attrs.str("group") ?: "genre", id, name)
        }.groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, tags) -> tags.sortedBy { it.second.lowercase() } }
    }

    /** Adds the tag groups to the static filters, before the content rating. */
    private fun FilterList.withTags(tags: Map<String, List<Pair<String, String>>>): FilterList {
        if (tags.isEmpty()) throw SourceFetchException("MangaDex: no tags")
        val groups = TAG_GROUPS.mapNotNull { group ->
            val list = tags[group].orEmpty().ifEmpty { return@mapNotNull null }
            SourceFilter.Group(
                id = group,
                name = when (group) {
                    "genre" -> appString(R.string.filter_genres)
                    "theme" -> appString(R.string.filter_tags)
                    "format" -> appString(R.string.filter_format)
                    else -> appString(R.string.filter_content_warnings)
                },
                filters = list.map { (id, name) -> SourceFilter.TriState("$group:$id", name) },
            )
        }
        val mode = SourceFilter.Select(
            id = "tagMode",
            name = appString(R.string.filter_match),
            options = listOf("AND", "OR"),
            labels = listOf(appString(R.string.filter_match_all), appString(R.string.filter_match_any)),
        )
        val at = filters.indexOfFirst { it.id == "rating" }.let { if (it < 0) filters.size else it }
        return FilterList(filters.take(at) + groups + mode + filters.drop(at))
    }

    private fun tagId(filterId: String) = filterId.substringAfter(':')

    override fun browseSections(): List<BrowseSection> {
        val base = getFilters()
        return listOf(
            BrowseSection("new", appString(R.string.section_new_titles), base.choose("sort", "createdAt")),
            BrowseSection("top", appString(R.string.section_top_rated), base.choose("sort", "rating")),
            BrowseSection("completed", appString(R.string.section_completed), base.choose("sort", "followedCount").include("completed")),
            BrowseSection("manhwa", appString(R.string.type_manhwa), base.choose("sort", "followedCount").include("ko")),
        )
    }

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Sort(
                id = "sort",
                name = appString(R.string.filter_sort),
                options = listOf("relevance", "followedCount", "latestUploadedChapter", "rating", "createdAt", "title", "year"),
                labels = listOf(
                    appString(R.string.filter_sort_relevance),
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_rating),
                    appString(R.string.filter_sort_new),
                    appString(R.string.filter_sort_title),
                    appString(R.string.filter_sort_year),
                ),
            ),
            SourceFilter.Select(
                id = "lang",
                name = appString(R.string.filter_language),
                options = (listOf(languages.joinToString(",")) + CHAPTER_LANGUAGES).distinct(),
                labels = (listOf(languages.joinToString(", ")) + CHAPTER_LANGUAGES).distinct().map { it.uppercase() },
            ),
            SourceFilter.Group(
                id = "status",
                name = appString(R.string.filter_status),
                filters = listOf(
                    SourceFilter.Check("ongoing", appString(R.string.filter_status_ongoing)),
                    SourceFilter.Check("completed", appString(R.string.filter_status_completed)),
                    SourceFilter.Check("hiatus", appString(R.string.filter_status_hiatus)),
                    SourceFilter.Check("cancelled", appString(R.string.filter_status_cancelled)),
                ),
            ),
            SourceFilter.Group(
                id = "origin",
                name = appString(R.string.filter_type),
                filters = listOf(
                    SourceFilter.Check("ja", appString(R.string.type_manga)),
                    SourceFilter.Check("ko", appString(R.string.type_manhwa)),
                    SourceFilter.Check("zh", appString(R.string.type_manhua)),
                    SourceFilter.Check("en", appString(R.string.type_comic)),
                ),
            ),
            SourceFilter.Group(
                id = "demographic",
                name = appString(R.string.filter_demographic),
                filters = listOf("shounen", "shoujo", "seinen", "josei").map {
                    SourceFilter.Check(it, it.replaceFirstChar(Char::uppercase))
                } + SourceFilter.Check("none", appString(R.string.filter_none)),
            ),
            SourceFilter.Group(
                id = "rating",
                name = appString(R.string.filter_content_rating),
                filters = ALL_RATINGS.map { SourceFilter.Check(it, ratingLabel(it), state = it in contentRatings) },
            ),
        ),
    )

    override fun settings(): List<SourceSetting> = listOf(
        SourceSetting.MultiChoice(
            key = "languages",
            title = appString(R.string.source_setting_languages),
            options = CHAPTER_LANGUAGES.map { it to it.uppercase() },
            default = setOf("en", "ru"),
        ),
        SourceSetting.MultiChoice(
            key = "contentRating",
            title = appString(R.string.filter_content_rating),
            options = ALL_RATINGS.map { it to ratingLabel(it) },
            default = DEFAULT_RATINGS.toSet(),
        ),
        SourceSetting.Toggle(
            key = "dataSaver",
            title = appString(R.string.source_setting_data_saver),
            default = false,
        ),
    )

    override fun titleIdFromUrl(url: String): String? {
        val parts = SourceLinks.segments(url, manifest.baseUrl) ?: return null
        // Website links use /title/<id>; Mihon backups store /manga/<id>.
        val i = parts.indexOfFirst { it == "title" || it == "manga" }
        if (i < 0) return null
        return parts.getOrNull(i + 1)?.takeIf { UUID.matches(it) }
    }

    private fun ratingLabel(rating: String): String = when (rating) {
        "safe" -> appString(R.string.filter_rating_safe)
        "suggestive" -> appString(R.string.filter_rating_suggestive)
        "erotica" -> appString(R.string.filter_rating_erotica)
        else -> appString(R.string.filter_rating_pornographic)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val mangaId = id.trim('/')
        val response = http.get(
            URI("$apiBase/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist"),
            source = manifest,
            checkCloudflare = false,
        )
        val root = json.parseToJsonElement(response.body).jsonObject
        val data = root["data"]?.jsonObject
            ?: throw SourceFetchException("MangaDex: " + com.lume.app.data.locale.appString(com.lume.app.R.string.error_empty_response))
        val score = score(mangaId)
        val attrs = data["attributes"]?.jsonObject ?: JsonObject(emptyMap())
        val relationships = data["relationships"]?.jsonArray ?: JsonArray(emptyList())
        return TitleDetails(
            id = mangaId,
            title = pickTitle(attrs["title"]?.jsonObject) ?: mangaId,
            altTitle = attrs["altTitles"]?.jsonArray
                ?.mapNotNull { (it as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull }
                ?.firstOrNull(),
            // English and Russian names first: they are the ones other sources search by.
            otherNames = attrs["altTitles"]?.jsonArray.orEmpty()
                .mapNotNull { el -> (el as? JsonObject)?.entries?.firstOrNull() }
                .sortedBy { (lang, _) -> OTHER_NAME_LANGS.indexOf(lang).let { if (it < 0) OTHER_NAME_LANGS.size else it } }
                .mapNotNull { (_, value) -> value.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }
                .distinct()
                .take(MAX_OTHER_NAMES),
            coverUrl = coverUrl(mangaId, relationships),
            description = pickLocalized(attrs["description"]?.jsonObject),
            status = attrs["status"]?.jsonPrimitive?.contentOrNull,
            typeLabel = originLabel(attrs.str("originalLanguage")),
            year = attrs.str("year")?.toIntOrNull(),
            score = score,
            authors = relationships.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "author" }
                .mapNotNull { it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull },
            artists = relationships.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "artist" }
                .mapNotNull { it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull },
            genres = attrs["tags"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.let { name -> pickTitle(name) }
            },
        )
    }

    /** The users' average score (0–10); a failed request only leaves it out. */
    private suspend fun score(mangaId: String): Double? = try {
        val response = http.get(URI("$apiBase/statistics/manga/$mangaId"), source = manifest, checkCloudflare = false)
        json.parseToJsonElement(response.body).jsonObject["statistics"].obj()?.get(mangaId).obj()
            ?.get("rating").obj()?.str("average")?.toDoubleOrNull()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun originLabel(lang: String?): String? = when (lang) {
        "ja" -> appString(R.string.type_manga)
        "ko" -> appString(R.string.type_manhwa)
        "zh", "zh-hk" -> appString(R.string.type_manhua)
        "en" -> appString(R.string.type_comic)
        else -> lang
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val mangaId = titleId.trim('/')
        val chapters = mutableListOf<Chapter>()
        var offset = 0
        // The feed endpoint allows up to 500 per request; long series have thousands of entries.
        val limit = 500
        while (true) {
            val q = buildString {
                append("limit=$limit&offset=$offset")
                append("&order[chapter]=desc&order[volume]=desc")
                append("&includeEmptyPages=0&includeFuturePublishAt=0&includeExternalUrl=0")
                for (lang in languages) append("&translatedLanguage[]=${enc(lang)}")
                for (rating in ALL_RATINGS) append("&contentRating[]=$rating")
            }
            val response = http.get(
                URI("$apiBase/manga/$mangaId/feed?$q"),
                source = manifest,
                checkCloudflare = false,
            )
            val root = json.parseToJsonElement(response.body).jsonObject
            val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
            for (el in data) {
                val item = el.jsonObject
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val attrs = item["attributes"]?.jsonObject ?: continue
                val number = attrs["chapter"]?.jsonPrimitive?.contentOrNull
                val volume = attrs["volume"]?.jsonPrimitive?.contentOrNull
                val title = attrs["title"]?.jsonPrimitive?.contentOrNull
                val lang = attrs["translatedLanguage"]?.jsonPrimitive?.contentOrNull
                val name = buildString {
                    if (!volume.isNullOrBlank()) {
                        append(com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_volume, volume))
                        append(' ')
                    }
                    if (!number.isNullOrBlank()) {
                        append(com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_number, number))
                    } else {
                        append(com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_generic))
                    }
                    if (!title.isNullOrBlank()) append(" — $title")
                    if (!lang.isNullOrBlank()) append(" [$lang]")
                }.trim()
                chapters += Chapter(
                    id = id,
                    title = name,
                    url = id,
                    number = number,
                    volume = volume,
                    dateLabel = attrs["publishAt"]?.jsonPrimitive?.contentOrNull?.take(10),
                )
            }
            val total = root["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: chapters.size
            offset += limit
            if (data.isEmpty() || offset >= total || offset >= MAX_FEED) break
        }
        return chapters
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val response = http.get(
            URI("$apiBase/at-home/server/${chapter.id}"),
            source = manifest,
            checkCloudflare = false,
        )
        val root = json.parseToJsonElement(response.body).jsonObject
        val baseUrl = root["baseUrl"]?.jsonPrimitive?.contentOrNull
            ?: throw SourceFetchException("MangaDex: нет baseUrl")
        val chapterObj = root["chapter"]?.jsonObject
            ?: throw SourceFetchException("MangaDex: нет chapter")
        val hash = chapterObj["hash"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val quality = if (dataSaver) "data-saver" else "data"
        val data = chapterObj[if (dataSaver) "dataSaver" else "data"]?.jsonArray ?: JsonArray(emptyList())
        val pages = data.mapIndexedNotNull { index, el ->
            val file = el.jsonPrimitive.contentOrNull ?: return@mapIndexedNotNull null
            ComicPage(index = index, imageUrl = "$baseUrl/$quality/$hash/$file")
        }
        if (pages.isEmpty()) throw SourceFetchException("MangaDex: пустые страницы")
        return pages
    }

    private fun parseMangaList(raw: String, page: Int): PagedResult<TitleSummary> {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        val items = data.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attrs = item["attributes"]?.jsonObject ?: JsonObject(emptyMap())
            val relationships = item["relationships"]?.jsonArray ?: JsonArray(emptyList())
            TitleSummary(
                id = id,
                title = pickTitle(attrs["title"]?.jsonObject) ?: id,
                coverUrl = coverUrl(id, relationships),
                latestChapter = attrs["lastChapter"]?.jsonPrimitive?.contentOrNull,
                typeLabel = originLabel(attrs.str("originalLanguage")),
            )
        }
        val total = root["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        return PagedResult(
            items = items,
            page = page,
            hasNext = total?.let { page * PAGE_SIZE < it } ?: (items.size >= PAGE_SIZE),
        )
    }

    private fun coverUrl(mangaId: String, relationships: JsonArray): String? {
        val cover = relationships.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "cover_art"
        }?.jsonObject ?: return null
        val file = cover["attributes"]?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
            ?: return null
        return "https://uploads.mangadex.org/covers/$mangaId/$file.256.jpg"
    }

    private fun pickTitle(obj: JsonObject?): String? {
        if (obj == null) return null
        for (lang in languages + listOf("en", "ja-ro", "ja", "ko-ro", "zh")) {
            obj[lang]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return obj.values.firstOrNull()?.jsonPrimitive?.contentOrNull
    }

    private fun pickLocalized(obj: JsonObject?): String? {
        if (obj == null) return null
        for (lang in languages + listOf("en")) {
            obj[lang]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return obj.values.firstOrNull()?.jsonPrimitive?.contentOrNull
    }

    private fun enc(value: String): String = urlEncode(value)

    companion object {
        private val OTHER_NAME_LANGS = listOf("en", "ru", "ja-ro", "ko-ro", "zh-ro", "uk")
        private const val MAX_OTHER_NAMES = 15
        private const val PAGE_SIZE = 20
        private const val MAX_FEED = 10_000
        private val ALL_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")
        private val DEFAULT_RATINGS = listOf("safe", "suggestive", "erotica")
        private val CHAPTER_LANGUAGES =
            listOf("en", "ru", "uk", "ja", "ko", "zh", "es", "es-la", "pt-br", "fr", "de", "it", "id", "vi", "tr", "pl")
        private val UUID = Regex("[0-9a-fA-F-]{36}")
        private val TAG_GROUPS = listOf("genre", "theme", "format", "content")
    }

    override fun webUrl(titleId: String): String = "${manifest.baseUrl.trimEnd('/')}/title/${titleId.trim('/')}"
}
