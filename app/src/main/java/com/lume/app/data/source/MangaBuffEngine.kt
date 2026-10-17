package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import com.lume.app.util.urlEncode

/**
 * MangaBuff.ru — HTML catalogue + CSRF-protected chapter list.
 */
class MangaBuffEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val siteBase: String
        get() = manifest.baseUrl.trimEnd('/')

    private val headers: Map<String, String>
        get() = mapOf(
            "Referer" to "$siteBase/",
            "Accept" to "text/html,application/json",
        )

    override suspend fun getHome(): HomeFeed {
        val popular = getPopular()
        return HomeFeed(spotlight = popular.items.take(12), latest = popular.items)
    }

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> = catalogPage("/manga", page)

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> = catalogPage("/manga/top", page)

    private suspend fun catalogPage(path: String, page: Int): PagedResult<TitleSummary> {
        val suffix = if (page <= 1) path else "$path?page=$page"
        val response = http.get(URI("$siteBase$suffix"), source = manifest, headers = headers)
        val doc = Jsoup.parse(response.body, siteBase)
        val items = LinkedHashMap<String, TitleSummary>()
        doc.select("a[href*=/manga/]").forEach { a ->
            val href = a.attr("abs:href").substringAfter(siteBase).substringBefore('?')
            val slug = href.trim('/').removePrefix("manga/").substringBefore('/')
            if (slug.isBlank() || '/' in slug || slug in setOf("top", "new")) return@forEach
            if (items.containsKey(slug)) return@forEach
            val card = a.closest(".cards__item, .manga-card, .card, .poster") ?: a.parent()
            val title = a.attr("title").ifBlank {
                card?.selectFirst(".cards__name, .manga-card__name, .card__name, .name")?.text()
            }.orEmpty().ifBlank { a.text() }.ifBlank { slug }
            val img = card?.selectFirst("img")
            val cover = img?.attr("abs:src")?.ifBlank { img.attr("abs:data-src") }
            items[slug] = TitleSummary(
                id = slug,
                title = title.trim(),
                coverUrl = cover?.takeIf { it.isNotBlank() },
            )
        }
        // Fallback: posters from search-like markup with data-id
        if (items.isEmpty()) {
            doc.select("[data-id]").forEach { el ->
                val name = el.selectFirst("a[href*=/manga/]") ?: return@forEach
                val href = name.attr("abs:href")
                val slug = href.substringAfter("/manga/").substringBefore('/').substringBefore('?')
                if (slug.isBlank()) return@forEach
                items.putIfAbsent(
                    slug,
                    TitleSummary(id = slug, title = name.text().ifBlank { slug }),
                )
            }
        }
        return PagedResult(items = items.values.toList(), page = page, hasNext = items.size >= 20)
    }

    override suspend fun search(
        query: String,
        page: Int,
        filters: com.lume.app.domain.FilterList,
    ): PagedResult<TitleSummary> {
        if (page > 1) return PagedResult(emptyList(), page, hasNext = false)
        val q = urlEncode(query)
        val response = http.get(
            URI("$siteBase/search/suggestions?q=$q"),
            source = manifest,
            headers = headers + mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json",
            ),
            checkCloudflare = false,
        )
        val arr = runCatching { json.parseToJsonElement(response.body) as? JsonArray }.getOrNull()
            ?: JsonArray(emptyList())
        val items = arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: slug
            val image = obj["image"]?.jsonPrimitive?.contentOrNull
            val cover = image?.let { if (it.startsWith("http")) it else "$siteBase$it" }
            val type = (obj["type"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            TitleSummary(id = slug, title = title, coverUrl = cover, typeLabel = type)
        }
        return PagedResult(items = items, page = 1, hasNext = false)
    }

    override fun getFilters(): com.lume.app.domain.FilterList = com.lume.app.domain.FilterList()

    override fun webUrl(titleId: String): String = "$siteBase/manga/${titleId.trim('/')}"

    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val i = parts.indexOf("manga")
        return parts.getOrNull(i + 1)?.takeIf { i >= 0 && it !in setOf("top", "new") }
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val slug = id.trim('/')
        val response = http.get(URI("$siteBase/manga/$slug"), source = manifest, headers = headers)
        val doc = Jsoup.parse(response.body, siteBase)
        val title = doc.selectFirst("h1, .manga__title, .manga-title, title")?.text()
            ?.substringBefore('/')?.trim()
            ?: slug
        val cover = doc.selectFirst(".manga__poster img, .poster img, meta[property=og:image]")
            ?.let { it.attr("abs:src").ifBlank { it.attr("content") } }
        val desc = doc.selectFirst(".manga__description, .description, .manga-desc")?.text()
        val type = doc.selectFirst(".manga__type, .type")?.text()
        val status = doc.selectFirst(".manga__status, .status")?.text()
        val genres = doc.select(".manga__genres a, .genres a, a[href*=/genres/]").map { it.text() }.filter { it.isNotBlank() }
        return TitleDetails(
            id = slug,
            title = title,
            coverUrl = cover?.takeIf { it.isNotBlank() },
            description = desc,
            typeLabel = type,
            status = status,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = titleId.trim('/')
        val page = http.get(URI("$siteBase/manga/$slug"), source = manifest, headers = headers)
        val doc = Jsoup.parse(page.body, siteBase)
        val mangaId = doc.selectFirst(".manga[data-id], [data-id]")?.attr("data-id")
            ?: throw SourceFetchException("MangaBuff: нет id тайтла")
        val csrf = doc.selectFirst("meta[name=csrf-token]")?.attr("content").orEmpty()
        val chapters = LinkedHashMap<String, Chapter>()
        fun ingest(html: String) {
            val frag = Jsoup.parseBodyFragment(html, siteBase)
            frag.select("a.chapters__item, a[href*=/manga/$slug/]").forEach { a ->
                val href = a.attr("abs:href")
                val path = href.substringAfter(siteBase)
                if (!path.contains("/manga/$slug/")) return@forEach
                val parts = path.trim('/').split('/')
                // manga / slug / volume / chapter
                if (parts.size < 4) return@forEach
                val volume = parts[2]
                val number = parts[3]
                val id = "$slug/$volume/$number"
                val volLabel = a.selectFirst(".chapters__volume span")?.text() ?: volume
                val chLabel = a.selectFirst(".chapters__value span")?.text() ?: number
                val name = a.selectFirst(".chapters__name")?.text().orEmpty()
                chapters.putIfAbsent(
                    id,
                    Chapter(
                        id = id,
                        title = buildString {
                            append("Том $volLabel Глава $chLabel")
                            if (name.isNotBlank()) append(" — $name")
                        },
                        url = path,
                        number = chLabel,
                        volume = volLabel,
                        dateLabel = a.selectFirst(".chapters__add-date")?.text()
                            ?: a.attr("data-chapter-date").ifBlank { null },
                    ),
                )
            }
        }
        ingest(page.body)
        // Load older chapters via AJAX (returns HTML fragment in JSON.content)
        if (csrf.isNotBlank()) {
            runCatching {
                val ajax = http.postForm(
                    URI("$siteBase/chapters/load"),
                    source = manifest,
                    headers = mapOf(
                        "Referer" to "$siteBase/manga/$slug",
                        "Origin" to siteBase,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Accept" to "application/json",
                        "X-CSRF-TOKEN" to csrf,
                    ),
                    form = mapOf("manga_id" to mangaId),
                    checkCloudflare = false,
                )
                val root = json.parseToJsonElement(ajax.body) as? JsonObject
                val content = root?.get("content")?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrBlank()) ingest(content)
            }
        }
        return chapters.values.toList()
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val path = if (chapter.url.startsWith("http")) {
            chapter.url
        } else if (chapter.url.startsWith("/")) {
            "$siteBase${chapter.url}"
        } else {
            "$siteBase/manga/${chapter.id}"
        }
        val response = http.get(URI(path), source = manifest, headers = headers)
        val doc = Jsoup.parse(response.body, path)
        val urls = LinkedHashSet<String>()
        doc.select("img[data-src], img[src]").forEach { img ->
            val u = img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
            if (u.contains("chapters/") || u.contains("c1.mangabuff") || u.contains("c2.mangabuff") ||
                u.contains("c3.mangabuff") || u.contains("c4.mangabuff")
            ) {
                urls += u
            }
        }
        if (urls.isEmpty()) {
            // Regex fallback for lazy-loaded reader
            val regex = Regex("""https://c\d+\.mangabuff\.ru/chapters/[^"'\s]+""")
            regex.findAll(response.body).forEach { urls += it.value }
        }
        if (urls.isEmpty()) throw SourceFetchException("MangaBuff: нет страниц")
        return urls.mapIndexed { i, u -> ComicPage(index = i, imageUrl = u) }
    }
}
