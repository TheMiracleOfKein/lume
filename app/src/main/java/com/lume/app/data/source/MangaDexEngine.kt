package com.lume.app.data.source

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

    override suspend fun getPopular(page: Int) =
        mangaList(page, orderKey = "followedCount")

    override suspend fun getLatest(page: Int) =
        mangaList(page, orderKey = "latestUploadedChapter")

    private suspend fun mangaList(page: Int, orderKey: String): PagedResult<TitleSummary> {
        val offset = (page - 1) * PAGE_SIZE
        val query = buildString {
            append("limit=$PAGE_SIZE&offset=$offset")
            append("&order[$orderKey]=desc")
            append("&includes[]=cover_art")
            for (rating in listOf("safe", "suggestive", "erotica")) {
                append("&contentRating[]=$rating")
            }
            for (lang in languages) {
                append("&availableTranslatedLanguage[]=${enc(lang)}")
            }
            append("&hasAvailableChapters=true")
        }
        val response = http.get(URI("$apiBase/manga?$query"), source = manifest, checkCloudflare = false)
        return parseMangaList(response.body, page)
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) return getPopular(page)
        val langs = filters.filters.filterIsInstance<SourceFilter.Select>()
            .firstOrNull { it.id == "lang" }?.selectedValue
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: languages
        val offset = (page - 1) * PAGE_SIZE
        val q = buildString {
            append("limit=$PAGE_SIZE&offset=$offset")
            append("&title=${enc(query)}")
            append("&includes[]=cover_art")
            append("&order[relevance]=desc")
            for (rating in listOf("safe", "suggestive", "erotica")) {
                append("&contentRating[]=$rating")
            }
            for (lang in langs) {
                append("&availableTranslatedLanguage[]=${enc(lang)}")
            }
        }
        val response = http.get(URI("$apiBase/manga?$q"), source = manifest, checkCloudflare = false)
        return parseMangaList(response.body, page)
    }

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = "lang",
                name = com.lume.app.data.locale.appString(com.lume.app.R.string.filter_language),
                options = listOf(languages.joinToString(","), "en", "ru").distinct(),
            ),
        ),
    )

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
        val attrs = data["attributes"]?.jsonObject ?: JsonObject(emptyMap())
        val relationships = data["relationships"]?.jsonArray ?: JsonArray(emptyList())
        return TitleDetails(
            id = mangaId,
            title = pickTitle(attrs["title"]?.jsonObject) ?: mangaId,
            altTitle = attrs["altTitles"]?.jsonArray
                ?.mapNotNull { (it as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull }
                ?.firstOrNull(),
            coverUrl = coverUrl(mangaId, relationships),
            description = pickLocalized(attrs["description"]?.jsonObject),
            status = attrs["status"]?.jsonPrimitive?.contentOrNull,
            authors = relationships.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "author" }
                .mapNotNull { it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull },
            artists = relationships.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "artist" }
                .mapNotNull { it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull },
            genres = attrs["tags"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.let { name -> pickTitle(name) }
            },
        )
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
                for (rating in listOf("safe", "suggestive", "erotica", "pornographic")) {
                    append("&contentRating[]=$rating")
                }
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
                typeLabel = attrs["originalLanguage"]?.jsonPrimitive?.contentOrNull,
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
        private const val PAGE_SIZE = 20
        private const val MAX_FEED = 10_000
    }

    override fun webUrl(titleId: String): String = "${manifest.baseUrl.trimEnd('/')}/title/${titleId.trim('/')}"
}
