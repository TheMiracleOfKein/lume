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
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.util.urlEncode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * MangaKatana HTML catalogue.
 *
 * Chapter images live in an inline JS array whose *name is obfuscated and changes* (there is also
 * a one-image decoy array); the real one is whichever array the `data-src` loader reads from.
 */
class MangaKatanaEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : CatalogSource {
    private val siteBase get() = manifest.baseUrl.trimEnd('/')

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> =
        listPage(if (page <= 1) "/latest" else "/latest/page/$page", page)

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        listPage(if (page <= 1) "/manga" else "/manga/page/$page", page)

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) return getPopular(page)
        val q = "search=${urlEncode(query.trim())}&search_by=book_name"
        val path = if (page <= 1) "/?$q" else "/page/$page?$q"
        val response = http.get(URI("$siteBase$path"), source = manifest)
        val doc = Jsoup.parse(response.body, response.finalUrl)
        // An exact match redirects straight to the title page.
        if (doc.selectFirst("#single_book") != null) {
            val details = parseDetails(doc, idFromUrl(response.finalUrl))
            val item = TitleSummary(id = details.id, title = details.title, coverUrl = details.coverUrl)
            return PagedResult(listOf(item), page = 1, hasNext = false)
        }
        return parseList(doc, page)
    }

    override suspend fun getTitle(id: String): TitleDetails = parseDetails(fetchDoc(id), id)

    private fun parseDetails(doc: Document, id: String): TitleDetails {
        val info = doc.selectFirst("#single_book") ?: doc
        val title = info.selectFirst("h1.heading")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        val cover = imageOf(info.selectFirst(".cover img"))
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id },
            altTitle = info.selectFirst(".alt_name")?.text()?.trim()?.takeIf { it.isNotEmpty() },
            coverUrl = cover?.takeIf { it.isNotBlank() },
            description = info.selectFirst(".summary > p")?.let { htmlToText(it.html()) },
            authors = info.select(".authors a.author, .authors a").map { it.text().trim() }
                .filter { it.isNotEmpty() }.distinct(),
            genres = info.select(".genres a").map { it.text().trim() }.filter { it.isNotEmpty() },
            status = info.selectFirst(".value.status")?.text()?.trim(),
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val doc = fetchDoc(titleId)
        val rows = doc.select("div.chapters tr").ifEmpty { doc.select(".chapters .chapter") }
        val seen = HashSet<String>()
        return rows.mapNotNull { row ->
            val a = row.selectFirst(".chapter a[href]") ?: row.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href")
            val path = runCatching { URI(href).path }.getOrNull().orEmpty()
            if (!CHAPTER_PATH.containsMatchIn(path) || !seen.add(path)) return@mapNotNull null
            val name = a.ownText().replace(Regex("\\s+"), " ").trim().ifEmpty { path.substringAfterLast('/') }
            Chapter(
                id = path,
                title = name,
                url = href,
                number = CHAPTER_PATH.find(path)?.groupValues?.getOrNull(1),
                dateLabel = row.selectFirst(".update_time")?.text()?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val html = http.get(resolveUrl(siteBase, chapter.url.ifBlank { chapter.id }), source = manifest).body
        val arrayName = Regex("""data-src['"]\s*,\s*(\w+)\s*\[""").find(html)?.groupValues?.get(1)
        val candidates = buildList {
            arrayName?.let { add(it) }
            // Fallback: the largest inline URL array on the page.
            Regex("""var\s+(\w+)\s*=\s*\[\s*'https?://""").findAll(html).forEach { add(it.groupValues[1]) }
        }.distinct()
        val urls = candidates
            .map { name ->
                val body = Regex("""var\s+$name\s*=\s*\[([\s\S]*?)];""").find(html)?.groupValues?.get(1).orEmpty()
                Regex("""['"](https?://[^'"]+)['"]""").findAll(body).map { it.groupValues[1] }.toList()
            }
            .let { lists -> if (arrayName != null && lists.first().isNotEmpty()) lists.first() else lists.maxByOrNull { it.size } }
            .orEmpty()
        if (urls.isEmpty()) throw SourceFetchException(appString(R.string.error_pages_failed_named, manifest.name))
        return urls.mapIndexed { index, url -> ComicPage(index = index, imageUrl = url) }
    }

    override fun webUrl(titleId: String): String = resolveUrl(siteBase, titleId).toString()

    private suspend fun listPage(path: String, page: Int): PagedResult<TitleSummary> {
        val response = http.get(URI("$siteBase$path"), source = manifest)
        return parseList(Jsoup.parse(response.body, siteBase), page)
    }

    private fun parseList(doc: Document, page: Int): PagedResult<TitleSummary> {
        val seen = HashSet<String>()
        val items = doc.select("#book_list > .item, #book_list .item").mapNotNull { node ->
            val a = node.selectFirst("h3.title > a[href]") ?: node.selectFirst(".wrap_img a[href]") ?: return@mapNotNull null
            val path = idFromUrl(a.attr("abs:href"))
            if (path.isEmpty() || CHAPTER_PATH.containsMatchIn(path) || !seen.add(path)) return@mapNotNull null
            // h3.title also contains a "- Update chapter …" span; use the link's own text only.
            val title = a.ownText().trim().ifEmpty { a.attr("title").trim() }
            if (title.isEmpty()) return@mapNotNull null
            TitleSummary(
                id = path,
                title = title,
                coverUrl = imageOf(node.selectFirst(".wrap_img img, img")),
                latestChapter = node.selectFirst(".chapter a")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                updatedLabel = node.selectFirst(".date")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                subtitle = node.selectFirst(".status")?.text()?.trim(),
            )
        }
        val hasNext = doc.selectFirst("a.next.page-numbers") != null
        return PagedResult(items = items, page = page, hasNext = hasNext)
    }

    private suspend fun fetchDoc(pathOrUrl: String): Document {
        val response = http.get(resolveUrl(siteBase, pathOrUrl), source = manifest)
        return Jsoup.parse(response.body, siteBase)
    }

    private fun imageOf(img: Element?): String? {
        if (img == null) return null
        return img.attr("abs:data-src").ifBlank { img.attr("abs:src") }.takeIf { it.startsWith("http") }
    }

    private fun idFromUrl(url: String): String = runCatching { URI(url).path }.getOrNull().orEmpty()

    private companion object {
        /** `/manga/<slug>.<id>/c123` or `/c12.5`; `/fc` ("first chapter") is excluded. */
        val CHAPTER_PATH = Regex("""/c([0-9]+(?:\.[0-9]+)?)$""")
    }
}
