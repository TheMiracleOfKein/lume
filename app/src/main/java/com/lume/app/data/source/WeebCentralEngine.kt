package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import org.jsoup.Jsoup
import java.net.URI
import com.lume.app.util.urlEncode

/** WeebCentral catalogue (search/data + full-chapter-list + /images). */
class WeebCentralEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : CatalogSource {
    private val limit = 32

    override suspend fun getPopular(page: Int) =
        searchData(page = page, text = "", sort = "Popularity", order = "Descending")

    override suspend fun getLatest(page: Int) =
        searchData(page = page, text = "", sort = "Latest Updates", order = "Descending")

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> =
        searchData(page = page, text = query, sort = "Popularity", order = "Descending")

    private suspend fun searchData(
        page: Int,
        text: String,
        sort: String,
        order: String,
    ): PagedResult<TitleSummary> {
        val offset = (page - 1) * limit
        val q = buildString {
            append("text=${enc(text)}")
            append("&sort=${enc(sort)}")
            append("&order=${enc(order)}")
            append("&official=Any&anime=Any&adult=Any")
            append("&display_mode=${enc("Full Display")}")
            append("&limit=$limit&offset=$offset")
        }
        val response = http.get(
            URI("${manifest.baseUrl.trimEnd('/')}/search/data?$q"),
            source = manifest,
        )
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val items = doc.select("article > section > a").mapNotNull { a ->
            val href = a.attr("abs:href")
            if (!href.contains("/series/")) return@mapNotNull null
            val path = URI(href).path.orEmpty()
            val title = a.selectFirst("div:not([class]):last-child")?.text()?.trim()
                ?: a.text().trim()
            if (title.isEmpty()) return@mapNotNull null
            val cover = a.selectFirst("source")?.attr("srcset")?.replace("small", "normal")
                ?: a.selectFirst("img")?.attr("abs:src")
            TitleSummary(id = path, title = title, coverUrl = cover)
        }
        val hasNext = doc.selectFirst("button") != null || items.size >= limit
        return PagedResult(items = items, page = page, hasNext = hasNext)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val response = http.get(abs(id), source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val sections = doc.select("section[x-data] > section")
        val meta = sections.getOrNull(0)
        val body = sections.getOrNull(1)
        val title = body?.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val cover = meta?.selectFirst("source")?.attr("srcset")?.replace("small", "normal")
            ?: meta?.selectFirst("img")?.attr("abs:src")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = body?.selectFirst("li:has(strong:contains(Description)) > p")?.text()?.trim()
        val authors = meta?.select("ul > li:has(strong:contains(Author)) > span > a")
            ?.map { it.text().trim() }.orEmpty()
        val genres = meta?.select("ul > li:has(strong:contains(Tag),strong:contains(Type)) a")
            ?.map { it.text().trim() }.orEmpty()
        val status = meta?.selectFirst("ul > li:has(strong:contains(Status)) > a")?.text()?.trim()
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id },
            coverUrl = cover,
            description = description,
            authors = authors,
            genres = genres,
            status = status,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val seriesId = titleId.trim('/').split('/').getOrNull(1)
            ?: throw SourceFetchException("WeebCentral: нет series id")
        val response = http.get(
            manifest.baseUri.resolve("/series/$seriesId/full-chapter-list"),
            source = manifest,
        )
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        return doc.select("div[x-data] > a, a[href*=/chapters/]").mapNotNull { a ->
            val href = a.attr("abs:href").ifBlank {
                val rel = a.attr("href")
                if (rel.startsWith("/")) manifest.baseUri.resolve(rel).toString() else return@mapNotNull null
            }
            if (!href.contains("/chapters/")) return@mapNotNull null
            val path = URI(href).path.orEmpty()
            val name = a.selectFirst("span.flex > span")?.text()?.trim()
                ?: a.text().replace(Regex("\\s+"), " ").trim()
            Chapter(
                id = path,
                title = name.ifEmpty { path.substringAfterLast('/') },
                url = path,
                dateLabel = a.selectFirst("time[datetime]")?.attr("datetime")?.take(10),
            )
        }.distinctBy { it.id }
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val chapterPath = chapter.url.ifBlank { chapter.id }.trimEnd('/')
        val uri = manifest.baseUri.resolve(
            "$chapterPath/images?is_prev=False&reading_style=long_strip",
        )
        val response = http.get(uri, source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val imgs = doc.select("section[x-data*=scroll] > img, section[x-data] img, img[src*=manga]")
        val pages = imgs.mapNotNull { img ->
            val url = img.attr("abs:src").ifBlank { img.attr("src") }
            url.takeIf { it.startsWith("http") }
        }.distinct().mapIndexed { index, url -> ComicPage(index = index, imageUrl = url) }
        if (pages.isEmpty()) throw SourceFetchException("WeebCentral: пустые страницы")
        return pages
    }

    private fun abs(pathOrUrl: String): URI {
        val parsed = URI(pathOrUrl)
        return if (parsed.scheme != null) parsed else manifest.baseUri.resolve(pathOrUrl.trimStart('/'))
    }

    private fun enc(value: String): String = urlEncode(value)
}
