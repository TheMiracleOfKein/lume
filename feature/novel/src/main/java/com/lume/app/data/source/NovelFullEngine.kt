package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.Chapter
import com.lume.app.domain.FilterList
import com.lume.app.domain.NovelChapter
import com.lume.app.domain.NovelSource
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceLinks
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.util.urlEncode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Translated novel sites on the "NovelFull" theme: NovelFull (novelfull.com) and NOVGO (novgo.net,
 * formerly AllNovelFull). Chinese, Korean and Japanese novels in English, light novels among them.
 * Both keep the same URLs and endpoints; the list and title page markup differs, so every
 * selector names both layouts (`.list-truyen .row` / `.li-row`, `.info` / `.m-imgtxt`).
 *
 * - Lists: `/most-popular`, `/latest-release-novel`, `/hot-novel`, `/completed-novel`,
 *   `/genre/<Name>` and `/search?keyword=…`, paged with `?page=N`.
 * - Title ids are paths (`/overlord-ln.html`); chapter ids are chapter paths
 *   (`/overlord-ln/chapter-1.html`).
 * - The title page shows 50 chapters a page; the whole list, in reading order, is the `<select>` of
 *   `/ajax-chapter-option?novelId=<id>`, the id being the title page's `data-novel-id`.
 * - Chapter text is `#chapter-content`, with ad frames and a "report chapter" line removed.
 */
class NovelFullEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : NovelSource {
    private val siteBase get() = manifest.baseUrl.trimEnd('/')

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> = list(LISTS.first(), page)

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> = list("/latest-release-novel", page)

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = LIST,
                name = appString(R.string.filter_sort),
                options = LISTS,
                labels = listOf(
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_trending),
                    appString(R.string.filter_status_completed),
                ),
            ),
            SourceFilter.Select(
                id = GENRE,
                name = appString(R.string.filter_genres),
                options = listOf("") + GENRES,
                labels = listOf(appString(R.string.filter_any)) + GENRES,
            ),
        ),
    )

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> =
        list(searchPath(query, filters), page)

    /**
     * List path for [query] and [filters]: the site searches by text only, and a genre page has no
     * other filter, so text wins over the genre and the genre over the list choice.
     */
    internal fun searchPath(query: String, filters: FilterList): String {
        val text = query.trim()
        if (text.isNotEmpty()) return "/search?keyword=${urlEncode(text)}"
        filters.selected(GENRE)?.takeIf { it.isNotEmpty() }?.let { return "/genre/${urlEncode(it)}" }
        return filters.selected(LIST)?.takeIf { it.isNotEmpty() } ?: LISTS.first()
    }

    override suspend fun getTitle(id: String): TitleDetails = parseDetails(fetch(id), id)

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val novelId = parseNovelId(fetch(titleId))
            ?: throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        return parseChapters(fetch("/ajax-chapter-option?novelId=$novelId"))
    }

    override suspend fun getChapterText(chapter: Chapter): NovelChapter =
        NovelChapter(chapter.id, parseChapterText(fetch(chapter.url.ifBlank { chapter.id })))

    override fun titleIdFromUrl(url: String): String? {
        val path = SourceLinks.pathOnHost(url, manifest.baseUrl) ?: return null
        TITLE_PATH.matchEntire(path)?.let { return path }
        // A chapter link: /<slug>/chapter-1.html → the title page.
        return CHAPTER_PATH.matchEntire(path)?.let { "/${it.groupValues[1]}.html" }
    }

    internal fun parseTitleList(body: String, page: Int): PagedResult<TitleSummary> {
        val doc = Jsoup.parse(body, "$siteBase/")
        val seen = HashSet<String>()
        val items = doc.select(".list-truyen .row, .li-row").mapNotNull { node ->
            val link = node.selectFirst("h3.truyen-title a[href], h3.tit a[href]") ?: return@mapNotNull null
            val id = titlePath(link.attr("abs:href")) ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val author = node.selectFirst(".author")?.text()?.trim()?.ifEmpty { null }
            val genres = node.select(".desc a[href*=/genre/]").take(3).joinToString(", ") { it.text().trim() }.ifEmpty { null }
            TitleSummary(
                id = id,
                title = link.attr("title").trim().ifEmpty { link.text().trim() }.ifEmpty { return@mapNotNull null },
                coverUrl = node.selectFirst("img.cover, .pic img")?.attr("abs:src")?.takeIf { it.startsWith("http") },
                subtitle = author ?: genres,
            )
        }
        val hasNext = doc.selectFirst("li.next:not(.disabled) a[href], a[rel=next]") != null
        return PagedResult(items, page, hasNext = items.isNotEmpty() && hasNext)
    }

    internal fun parseDetails(body: String, id: String): TitleDetails {
        val doc = Jsoup.parse(body, "$siteBase/")
        val info = doc.selectFirst(".info, .m-imgtxt")
        val title = doc.selectFirst(".desc h3.title, .m-desc h1.tit")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id },
            coverUrl = doc.selectFirst(".book img, .m-imgtxt .pic img")?.attr("abs:src")?.takeIf { it.startsWith("http") }
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")?.takeIf { it.startsWith("http") },
            description = doc.selectFirst(".desc-text, #novel-summary-inner")?.let { htmlToText(it.html()) },
            status = status(doc),
            authors = info?.select("a[href*=/author/]")?.map { it.text().trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty(),
            genres = info?.select("a[href*=/genre/]")?.map { it.text().trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty(),
            score = score(doc),
        )
    }

    /** The site's own id of the title, needed for the chapter list. */
    internal fun parseNovelId(body: String): String? =
        Jsoup.parse(body).select("[data-novel-id]").map { it.attr("data-novel-id").trim() }
            .firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }

    /** `<option value="/slug/chapter.html">Title</option>` rows, already in reading order. */
    internal fun parseChapters(body: String): List<Chapter> {
        val doc = Jsoup.parseBodyFragment(body, "$siteBase/")
        val seen = HashSet<String>()
        return doc.select("option[value]").mapNotNull { option ->
            val path = runCatching { URI(option.attr("abs:value")).path }.getOrNull()
                ?.takeIf { it.endsWith(".html") } ?: return@mapNotNull null
            if (!seen.add(path)) return@mapNotNull null
            path to option.text().trim()
        }.mapIndexed { index, (path, name) ->
            Chapter(
                id = path,
                title = name.ifEmpty { "Chapter ${index + 1}" },
                url = siteBase + path,
                number = (index + 1).toString(),
            )
        }
    }

    internal fun parseChapterText(body: String): String {
        val doc = Jsoup.parse(body, "$siteBase/")
        val content = doc.selectFirst("#chapter-content")
            ?: throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        // Ad frames sit in their own divs; the closing "If you find any errors … report chapter" line too.
        content.select("iframe").forEach { frame ->
            val box = frame.parent()
            if (box != null && box !== content && box.tagName() == "div" && box.text().isBlank()) box.remove() else frame.remove()
        }
        content.select("div").filter { REPORT_LINE.containsMatchIn(it.ownText()) }.forEach { it.remove() }
        val html = NovelHtml.clean(content)
        if (!NovelHtml.hasContent(html)) throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        return html
    }

    private fun status(doc: Document): String? {
        doc.selectFirst(".info a[href*=/status/]")?.text()?.trim()?.ifEmpty { null }?.let { return it }
        // NOVGO: the status row's icon is titled "Status", its link names it.
        val row = doc.select(".m-imgtxt .item").firstOrNull { it.selectFirst("[title=Status]") != null }
        return row?.selectFirst(".right a, .right span")?.text()?.trim()?.ifEmpty { null }
    }

    /** NovelFull rates out of 10 (`#rateVal`), NOVGO out of 5 (`data-avg`). */
    private fun score(doc: Document): Double? {
        doc.selectFirst("#rateVal")?.attr("value")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { return it }
        return doc.selectFirst("#novel-score[data-avg]")?.attr("data-avg")?.toDoubleOrNull()
            ?.takeIf { it > 0 }?.let { it * 2 }
    }

    private suspend fun list(path: String, page: Int): PagedResult<TitleSummary> {
        val url = if (page <= 1) siteBase + path else withQuery(siteBase + path, "page=$page")
        return parseTitleList(http.get(URI(url), source = manifest).body, page)
    }

    private suspend fun fetch(pathOrUrl: String): String = http.get(resolveUrl(siteBase, pathOrUrl), source = manifest).body

    private fun titlePath(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        return path.takeIf { TITLE_PATH.matches(it) }
    }

    private companion object {
        const val LIST = "list"
        const val GENRE = "genre"

        /** List pages; the first (most popular) is the default. */
        val LISTS = listOf("/most-popular", "/latest-release-novel", "/hot-novel", "/completed-novel")

        /** Genre names as the sites use them in `/genre/<Name>` (spaces become `+`). */
        val GENRES = listOf(
            "Action", "Adult", "Adventure", "Comedy", "Drama", "Eastern", "Ecchi", "Fan-fic", "Fantasy", "Game",
            "Gender Bender", "Harem", "Historical", "Horror", "Josei", "Magical Realism", "Martial Arts", "Mature",
            "Mecha", "Mystery", "Psychological", "Reincarnation", "Romance", "School Life", "Sci-fi", "Seinen",
            "Shoujo", "Shounen", "Shounen Ai", "Slice of Life", "Smut", "Sports", "Supernatural", "System",
            "Tragedy", "Wuxia", "Xianxia", "Xuanhuan", "Yaoi", "Yuri",
        )

        val TITLE_PATH = Regex("""/[a-z0-9][a-z0-9-]*\.html""")
        val CHAPTER_PATH = Regex("""/([a-z0-9][a-z0-9-]*)/[^/?#]+\.html""")
        val REPORT_LINE = Regex("""report chapter|If you find any errors""", RegexOption.IGNORE_CASE)
    }
}
