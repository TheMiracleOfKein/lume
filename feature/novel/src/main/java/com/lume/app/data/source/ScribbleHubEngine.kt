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
import java.net.URI

/**
 * Scribble Hub (scribblehub.com), original English web novels on WordPress, behind Cloudflare.
 *
 * - Lists: `.search_main_box` on the ranking, latest and series-finder pages, paged with `pg=N`;
 *   the page count is only in the pagination script (`items: N`).
 * - Title ids are paths (`/series/664073/rebirth-of-the-nephilim/`); chapter ids are chapter
 *   paths (`/read/664073-…/chapter/2686988/`).
 * - The title page shows 15 chapters; the whole list comes from WordPress' `admin-ajax.php`
 *   (`action=wi_getreleases_pagination`, `pagenum=-1`), in the same `li.toc_w` markup.
 * - Chapter text is `#chp_raw`; author's notes inside it are left out.
 */
class ScribbleHubEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : NovelSource {
    private val siteBase get() = manifest.baseUrl.trimEnd('/')

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        list("/series-ranking/?sort=1&order=1", page)

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> = list("/latest-series/", page)

    private val siteFilters = SiteFilters(::fetchFilters)

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Sort(
                id = SORT,
                name = appString(R.string.filter_sort),
                options = SORT_VALUES,
                labels = listOf(
                    appString(R.string.filter_sort_views),
                    appString(R.string.filter_sort_rating),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_added),
                    appString(R.string.filter_sort_favorites),
                    appString(R.string.filter_sort_readers),
                    appString(R.string.filter_sort_reviews),
                    appString(R.string.filter_sort_votes),
                    appString(R.string.filter_sort_chapters),
                    appString(R.string.filter_sort_frequency),
                    appString(R.string.filter_sort_pages),
                    appString(R.string.filter_sort_words),
                ),
            ),
            SourceFilter.Select(
                id = STATUS,
                name = appString(R.string.filter_status),
                options = listOf("", "ongoing", "completed", "hiatus"),
                labels = listOf(
                    appString(R.string.filter_any),
                    appString(R.string.filter_status_ongoing),
                    appString(R.string.filter_status_completed),
                    appString(R.string.filter_status_hiatus),
                ),
            ),
            matchFilter(),
            SourceFilter.Group(
                GENRES,
                appString(R.string.filter_genres),
                GENRE_IDS.map { (id, name) -> SourceFilter.TriState("$GENRES:$id", name) },
            ),
        ),
    )

    override suspend fun loadFilters(): FilterList = siteFilters.get(::getFilters)

    /** The base filters plus the site's tag list, read from the series finder form. */
    private suspend fun fetchFilters(): FilterList {
        val tags = parseTags(fetch("/series-finder/"))
        if (tags.isEmpty()) throw SourceFetchException("Scribble Hub: no tags")
        return FilterList(getFilters().filters + SourceFilter.Group(TAGS, appString(R.string.filter_tags), tags))
    }

    /** Tags of the finder's include list (`<option value="id">name</option>`). */
    internal fun parseTags(body: String): List<SourceFilter.TriState> =
        Jsoup.parse(body).select("select.tagsalledit_include option[value]").mapNotNull { option ->
            val id = option.attr("value").trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return@mapNotNull null
            val name = option.text().trim().ifEmpty { return@mapNotNull null }
            SourceFilter.TriState("$TAGS:$id", name)
        }

    private fun matchFilter() = SourceFilter.Select(
        id = MATCH,
        name = appString(R.string.filter_match),
        options = listOf("or", "and"),
        labels = listOf(appString(R.string.filter_match_any), appString(R.string.filter_match_all)),
    )

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val path = searchPath(query, filters)
        return if (path == null) getPopular(page) else list(path, page)
    }

    /**
     * Series finder path for [query] and [filters] (without the page number), or null when there
     * is neither text nor a filter set. A plain text search keeps its rating-sorted URL. Genre and
     * tag ids go comma separated into `gi`/`tgi` (included) and `ge`/`tge` (excluded).
     */
    internal fun searchPath(query: String, filters: FilterList): String? {
        val text = query.trim()
        val genresIn = filters.included(GENRES).map { it.substringAfter(':') }
        val genresOut = filters.excluded(GENRES).map { it.substringAfter(':') }
        val tagsIn = filters.included(TAGS).map { it.substringAfter(':') }
        val tagsOut = filters.excluded(TAGS).map { it.substringAfter(':') }
        val status = filters.selected(STATUS).orEmpty()
        val sort = filters.sort(SORT)
        val customSort = sort != null && (sort.index != 0 || sort.ascending)
        val any = genresIn.isNotEmpty() || genresOut.isNotEmpty() || tagsIn.isNotEmpty() || tagsOut.isNotEmpty() ||
            status.isNotEmpty() || customSort
        if (!any) {
            if (text.isEmpty()) return null
            return "/series-finder/?sf=1&sh=${urlEncode(text)}&order=desc&sort=ratings"
        }
        val params = arrayListOf("sf=1")
        if (text.isNotEmpty()) params += "sh=${urlEncode(text)}"
        if (genresIn.isNotEmpty()) {
            params += "gi=${genresIn.joinToString(",")}"
            if (filters.selected(MATCH) == "and") params += "mgi=and"
        }
        if (genresOut.isNotEmpty()) params += "ge=${genresOut.joinToString(",")}"
        if (tagsIn.isNotEmpty()) {
            params += "tgi=${tagsIn.joinToString(",")}"
            if (filters.selected(MATCH) == "and") params += "mtgi=and"
        }
        if (tagsOut.isNotEmpty()) params += "tge=${tagsOut.joinToString(",")}"
        if (status.isNotEmpty()) params += "cp=$status"
        params += "sort=${sort?.selectedValue ?: SORT_VALUES.first()}"
        params += "order=${if (sort?.ascending == true) "asc" else "desc"}"
        return "/series-finder/?" + params.joinToString("&")
    }

    override suspend fun getTitle(id: String): TitleDetails = parseDetails(fetch(id), id)

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val postId = POST_ID.find(titleId)?.groupValues?.get(1)
            ?: Jsoup.parse(fetch(titleId)).selectFirst("#mypostid")?.attr("value")
            ?: throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        val response = http.postForm(
            URI("$siteBase/wp-admin/admin-ajax.php"),
            source = manifest,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to siteBase + titleId),
            form = mapOf("action" to "wi_getreleases_pagination", "pagenum" to "-1", "mypostid" to postId),
        )
        return parseChapters(response.body)
    }

    override suspend fun getChapterText(chapter: Chapter): NovelChapter =
        NovelChapter(chapter.id, parseChapterText(fetch(chapter.url.ifBlank { chapter.id })))

    override fun titleIdFromUrl(url: String): String? {
        val path = SourceLinks.pathOnHost(url, manifest.baseUrl) ?: return null
        TITLE_PATH.find(path)?.let { return it.value.trimEnd('/') + "/" }
        // A chapter link: /read/<id>-<slug>/chapter/<n>/ → the series page.
        return READ_PATH.find(path)?.let { "/series/${it.groupValues[1]}/${it.groupValues[2]}/" }
    }

    internal fun parseTitleList(body: String, page: Int): PagedResult<TitleSummary> {
        val doc = Jsoup.parse(body, "$siteBase/")
        val seen = HashSet<String>()
        val items = doc.select(".search_main_box").mapNotNull { node ->
            val link = node.selectFirst(".search_title a[href]") ?: return@mapNotNull null
            val id = titlePath(link.attr("abs:href")) ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            TitleSummary(
                id = id,
                title = link.text().trim().ifEmpty { return@mapNotNull null },
                coverUrl = node.selectFirst(".search_img img")?.attr("abs:src")?.takeIf { it.startsWith("http") },
                subtitle = node.select(".search_genre a, a.fic_genre").take(3).joinToString(", ") { it.text().trim() }
                    .ifEmpty { null },
            )
        }
        val pages = PAGE_COUNT.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: page
        return PagedResult(items, page, hasNext = items.isNotEmpty() && page < pages)
    }

    internal fun parseDetails(body: String, id: String): TitleDetails {
        val doc = Jsoup.parse(body, "$siteBase/")
        val title = doc.selectFirst(".fic_title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        val status = doc.select(".widget_fic_similar li span").map { it.ownText().trim() }
            .firstOrNull { text -> STATUSES.any { text.startsWith(it, ignoreCase = true) } }
            ?.substringBefore(" - ")?.trim()
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id },
            coverUrl = doc.selectFirst(".fic_image img")?.attr("abs:src")?.takeIf { it.startsWith("http") }
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.startsWith("http") },
            description = doc.selectFirst(".wi_fic_desc")?.let { htmlToText(it.html()) },
            status = status,
            authors = doc.select(".auth_name_fic").map { it.text().trim() }.filter { it.isNotEmpty() }.distinct(),
            genres = doc.select(".wi_fic_genre a.fic_genre").map { it.text().trim() }.filter { it.isNotEmpty() }.distinct(),
            score = RATING.find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * 2 },
        )
    }

    /** `li.toc_w` rows, newest first on the site; `order` is the chapter's place from 1. */
    internal fun parseChapters(body: String): List<Chapter> {
        val doc = Jsoup.parseBodyFragment(body, "$siteBase/")
        val seen = HashSet<String>()
        return doc.select("li.toc_w").mapNotNull { row ->
            val link = row.selectFirst("a.toc_a[href]") ?: row.selectFirst("a[href]") ?: return@mapNotNull null
            val path = runCatching { URI(link.attr("abs:href")).path }.getOrNull() ?: return@mapNotNull null
            if (!seen.add(path)) return@mapNotNull null
            Chapter(
                id = path,
                title = link.text().trim().ifEmpty { path },
                url = siteBase + path,
                number = row.attr("order").trim().ifEmpty { null },
                dateLabel = row.selectFirst(".fic_date_pub")?.text()?.trim()?.ifEmpty { null },
            )
        }.sortedBy { it.number?.toDoubleOrNull() ?: Double.MAX_VALUE }
    }

    internal fun parseChapterText(body: String): String {
        val doc = Jsoup.parse(body, "$siteBase/")
        val content = doc.selectFirst("#chp_raw")
            ?: throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        content.select(".wi_authornotes, .wi_news").remove()
        val html = NovelHtml.clean(content)
        if (!NovelHtml.hasContent(html)) throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        return html
    }

    private suspend fun list(path: String, page: Int): PagedResult<TitleSummary> {
        val url = if (page <= 1) siteBase + path else withQuery(siteBase + path, "pg=$page")
        return parseTitleList(http.get(URI(url), source = manifest).body, page)
    }

    private suspend fun fetch(pathOrUrl: String): String = http.get(resolveUrl(siteBase, pathOrUrl), source = manifest).body

    private fun titlePath(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        return TITLE_PATH.find(path)?.value?.trimEnd('/')?.plus("/")
    }

    private companion object {
        const val SORT = "sort"
        const val STATUS = "status"
        const val MATCH = "match"
        const val GENRES = "genres"
        const val TAGS = "tags"

        /** `sort` values of the series finder; the first (pageviews) is the site's default. */
        val SORT_VALUES = listOf(
            "pageviews", "ratings", "lastchpdate", "dateadded", "favorites", "readers",
            "reviews", "numofrate", "chapters", "frequency", "pages", "totalwords",
        )

        /** Genre ids and names from the series finder form. */
        val GENRE_IDS = listOf(
            "9" to "Action", "902" to "Adult", "8" to "Adventure", "891" to "Boys Love", "7" to "Comedy",
            "903" to "Drama", "904" to "Ecchi", "38" to "Fanfiction", "19" to "Fantasy", "905" to "Gender Bender",
            "892" to "Girls Love", "1015" to "Harem", "21" to "Historical", "22" to "Horror", "37" to "Isekai",
            "906" to "Josei", "1180" to "LitRPG", "907" to "Martial Arts", "20" to "Mature", "908" to "Mecha",
            "909" to "Mystery", "910" to "Psychological", "6" to "Romance", "911" to "School Life",
            "912" to "Sci-fi", "913" to "Seinen", "914" to "Slice of Life", "915" to "Smut", "916" to "Sports",
            "5" to "Supernatural", "901" to "Tragedy",
        )

        val TITLE_PATH = Regex("""^/series/\d+/[^/?#]+/?""")
        val READ_PATH = Regex("""^/read/(\d+)-([^/?#]+)/chapter/""")
        val POST_ID = Regex("""/series/(\d+)/""")
        val PAGE_COUNT = Regex("""items:\s*(\d+)""")
        val RATING = Regex(""""ratingValue"\s*:\s*"?([0-9.]+)""")
        val STATUSES = listOf("Ongoing", "Completed", "Hiatus", "Dropped")
    }
}
