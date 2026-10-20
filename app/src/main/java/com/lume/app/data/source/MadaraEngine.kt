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
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.choose
import com.lume.app.domain.include
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import com.lume.app.util.urlEncode

class MadaraParser {
    fun parseTitleList(html: String, page: Int, baseUrl: String?): PagedResult<TitleSummary> {
        val document = Jsoup.parse(html, baseUrl.orEmpty())
        val nodes = document.select(
            "div.page-item-detail, div.manga, div.col-6.col-md-3, div.item, div.c-tabs-item__content",
        )
        val items = mutableListOf<TitleSummary>()
        val seen = mutableSetOf<String>()
        for (node in nodes) {
            val parsed = parseListItem(node, baseUrl) ?: continue
            if (!seen.add(parsed.id)) continue
            items += parsed
        }
        if (items.isEmpty()) {
            for (a in document.select("div.item-thumb a[href], div.post-title a[href]")) {
                val parsed = fromAnchor(a, scope = null, baseUrl = baseUrl) ?: continue
                if (!seen.add(parsed.id)) continue
                items += parsed
            }
        }
        return PagedResult(items = items, page = page, hasNext = items.size >= 12)
    }

    private fun parseListItem(node: Element, baseUrl: String?): TitleSummary? {
        val anchor = node.selectFirst(
            "div.item-thumb a[href], a[href].img, h3 a[href], h5 a[href], div.post-title a[href]",
        ) ?: node.selectFirst("a[href]") ?: return null
        return fromAnchor(anchor, scope = node, baseUrl = baseUrl)
    }

    private fun fromAnchor(anchor: Element, scope: Element?, baseUrl: String?): TitleSummary? {
        val href = anchor.attr("abs:href").ifBlank { anchor.attr("href") }
        if (href.isBlank() || href.contains("/wp-") || href.contains("#")) return null
        val id = idFromUrl(href)
        if (id.isEmpty()) return null
        val title = (anchor.attr("title").ifBlank {
            anchor.text().ifBlank {
                scope?.selectFirst("h3, h5, .post-title")?.text().orEmpty()
            }
        }).trim()
        if (title.isEmpty()) return null
        val img = scope?.selectFirst("img") ?: anchor.selectFirst("img")
        val cover = absoluteUrl(imageUrl(img), baseUrl)
        val chapter = scope?.selectFirst(
            ".list-chapter .chapter a, .chapter-item .chapter a, span.chapter a, .latest-chap a",
        )?.text()?.trim()
        val updated = scope?.selectFirst(".post-on, .font-meta, span.post-on, .c-new-tag")?.text()?.trim()
        return TitleSummary(
            id = id,
            title = title,
            coverUrl = cover,
            latestChapter = chapter?.takeIf { it.isNotEmpty() },
            updatedLabel = updated?.takeIf { it.isNotEmpty() },
        )
    }

    fun parseTitleDetails(html: String, id: String, baseUrl: String?): TitleDetails {
        val document = Jsoup.parse(html, baseUrl.orEmpty())
        val title = document.selectFirst("div.post-title h1, div.post-title h3, h1")
            ?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val description = document.selectFirst(
            "div.description-summary div.summary__content, div.summary__content, div.dsct, .manga-excerpt",
        )?.text()?.trim().orEmpty()
        val cover = absoluteUrl(
            imageUrl(document.selectFirst("div.summary_image img, .tab-summary img, .profile-manga img")),
            baseUrl,
        )
        val authors = document.select("div.author-content a, div.manga-authors a").map { it.text().trim() }
            .filter { it.isNotEmpty() }
        val artists = document.select("div.artist-content a").map { it.text().trim() }.filter { it.isNotEmpty() }
        val genres = document.select("div.genres-content a, div.wp-manga-tags-list a").map { it.text().trim() }
            .filter { it.isNotEmpty() }
        var status: String? = null
        for (item in document.select("div.post-status .post-content_item, .post-content_item")) {
            val label = item.selectFirst(".summary-heading")?.text()?.lowercase().orEmpty()
            if (label.contains("status") || label.contains("статус")) {
                status = item.selectFirst(".summary-content")?.text()?.trim()
            }
        }
        var typeLabel: String? = null
        var otherNames = emptyList<String>()
        var year: Int? = null
        for (item in document.select("div.post-content_item")) {
            val label = item.selectFirst(".summary-heading")?.text()?.lowercase().orEmpty()
            val content = item.selectFirst(".summary-content")?.text()?.trim().orEmpty()
            when {
                label.contains("type") || label.contains("тип") -> typeLabel = content.ifEmpty { null }
                label.contains("alternative") || label.contains("альтернатив") || label.contains("другие") ->
                    otherNames = content.split(Regex("[,;/]")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                label.contains("release") || label.contains("год") || label.contains("выпуск") ->
                    year = Regex("""\b(19|20)\d{2}\b""").find(content)?.value?.toIntOrNull()
            }
        }
        val score = document.selectFirst(".post-total-rating .score, #averagerate, span.score")
            ?.text()?.trim()?.replace(',', '.')?.toDoubleOrNull()
            // The theme rates out of 5.
            ?.takeIf { it > 0 }?.let { it * 2 }
        return TitleDetails(
            id = id,
            title = title,
            altTitle = otherNames.firstOrNull(),
            otherNames = otherNames,
            year = year,
            score = score,
            coverUrl = cover,
            description = description.ifEmpty { null },
            authors = authors,
            artists = artists,
            genres = genres,
            status = status,
            typeLabel = typeLabel,
        )
    }

    /** The genre boxes of the theme's advanced search form: slug to label. */
    fun parseGenres(html: String): List<Pair<String, String>> {
        val document = Jsoup.parse(html)
        val labels = document.select("label[for]").associate { it.attr("for") to it.text().trim() }
        return document.select("input[name=genre[]]").mapNotNull { input ->
            val slug = input.attr("value").trim().ifEmpty { return@mapNotNull null }
            slug to (labels[input.id()]?.ifEmpty { null } ?: slug)
        }.distinctBy { it.first }
    }

    fun parseChapters(html: String, baseUrl: String? = null): List<Chapter> {
        val document = Jsoup.parse(html, baseUrl.orEmpty())
        val nodes = document.select(
            "li.wp-manga-chapter, li.chapter-item, li.wp-manga-chapter.free-chap, .listing-chapters_wrap li",
        )
        val chapters = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        for (node in nodes) {
            val a = node.selectFirst("a[href]") ?: continue
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (href.isBlank()) continue
            val id = idFromUrl(href)
            if (!seen.add(id)) continue
            val name = a.text().replace(Regex("\\s+"), " ").trim()
            val date = node.selectFirst(".chapter-release-date, span.i-time, .c-new-tag")?.text()?.trim()
            chapters += Chapter(
                id = id,
                title = name.ifEmpty { id },
                url = href,
                dateLabel = date?.takeIf { it.isNotEmpty() },
                number = chapterNumber(name),
                volume = volumeNumber(name),
            )
        }
        return chapters
    }

    fun parsePages(html: String, baseUrl: String?): List<ComicPage> {
        val document = Jsoup.parse(html, baseUrl.orEmpty())
        val images = document.select(
            "div.page-break img, div.reading-content img, .blocks-gallery-item img, img.wp-manga-chapter-img",
        )
        val pages = mutableListOf<ComicPage>()
        val seen = mutableSetOf<String>()
        for (img in images) {
            val url = absoluteUrl(imageUrl(img), baseUrl) ?: continue
            if (url.startsWith("data:")) continue
            if (!seen.add(url)) continue
            pages += ComicPage(index = pages.size, imageUrl = url)
        }
        return pages
    }

    private fun absoluteUrl(url: String?, baseUrl: String?): String? {
        if (url.isNullOrBlank()) return null
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        if (parsed.scheme != null) return url
        if (baseUrl.isNullOrBlank()) return url
        return URI(baseUrl).resolve(url).toString()
    }

    private fun imageUrl(img: Element?): String? {
        if (img == null) return null
        for (attr in listOf("data-src", "data-lazy-src", "data-cfsrc", "data-original", "src")) {
            val value = img.attr(attr).trim()
            if (value.isNotEmpty() && !value.startsWith("data:")) return value.split(" ").first()
        }
        val srcset = img.attr("srcset").ifBlank { img.attr("data-srcset") }
        if (srcset.isNotEmpty()) return srcset.split(",").last().trim().split(" ").first()
        return null
    }

    private fun idFromUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        var path = uri.path.orEmpty()
        if (!path.endsWith("/")) path = "$path/"
        return path
    }

    private fun chapterNumber(title: String): String? =
        Regex("(?:chapter|ch\\.?|глава|гл\\.?)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.getOrNull(1)

    private fun volumeNumber(title: String): String? =
        Regex("(?:volume|vol\\.?|том)\\s*([0-9]+)", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.getOrNull(1)
}

class MadaraEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val parser: MadaraParser = MadaraParser(),
) : CatalogSource {
    private val mangaPath get() = manifest.configString("mangaPath") ?: "manga"
    private val useAjax get() = manifest.configBool("useAjaxLoadMore", fallback = true)
    private val chapterSuffix get() = manifest.configString("chapterUrlSuffix") ?: "?style=list"

    private fun abs(pathOrUrl: String): URI {
        val parsed = URI(pathOrUrl)
        return if (parsed.scheme != null) parsed else manifest.baseUri.resolve(pathOrUrl)
    }

    override suspend fun getLatest(page: Int) =
        browse(page = page, orderBy = "latest", ajaxMetaKey = "_latest_update")

    override suspend fun getPopular(page: Int) =
        browse(page = page, orderBy = "views", ajaxMetaKey = "_wp_manga_views")

    private suspend fun browse(page: Int, orderBy: String, ajaxMetaKey: String): PagedResult<TitleSummary> {
        if (useAjax) {
            runCatching { return ajaxList(page, ajaxMetaKey) }
        }
        val path = if (page > 1) "$mangaPath/page/$page/" else "$mangaPath/"
        val uri = URI(
            manifest.baseUri.scheme,
            manifest.baseUri.authority,
            "/$path".replace("//", "/"),
            "m_orderby=$orderBy",
            null,
        )
        val response = http.get(uri, source = manifest)
        return parser.parseTitleList(response.body, page, manifest.baseUrl)
    }

    private suspend fun ajaxList(page: Int, metaKey: String): PagedResult<TitleSummary> {
        val uri = manifest.baseUri.resolve("/wp-admin/admin-ajax.php")
        val response = http.postForm(
            uri,
            source = manifest,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to manifest.baseUrl.trimEnd('/'),
                "Referer" to "${manifest.baseUrl.trimEnd('/')}/",
            ),
            form = mapOf(
                "action" to "madara_load_more",
                "page" to "${page - 1}",
                "template" to "madara-core/content/content-archive",
                "vars[paged]" to "1",
                "vars[orderby]" to "meta_value_num",
                "vars[template]" to "archive",
                "vars[sidebar]" to "full",
                "vars[post_type]" to "wp-manga",
                "vars[post_status]" to "publish",
                "vars[meta_key]" to metaKey,
                "vars[order]" to "desc",
                "vars[meta_query][relation]" to "AND",
                "vars[manga_archives_item_layout]" to "default",
            ),
        )
        val html = response.body
        if (html.trim().isEmpty() || html.trim() == "0") {
            throw SourceFetchException(com.lume.app.data.locale.appString(com.lume.app.R.string.error_madara_ajax_empty))
        }
        return parser.parseTitleList(html, page, manifest.baseUrl)
    }

    override suspend fun search(
        query: String,
        page: Int,
        filters: com.lume.app.domain.FilterList,
    ): PagedResult<TitleSummary> {
        val order = filters.selected("orderby") ?: "latest"
        val statuses = filters.checked("status")
        val adult = filters.selected("adult").orEmpty()
        val genres = filters.checked("genre").map { it.substringAfter(':') }
        val author = filters.text("author")
        val artist = filters.text("artist")
        val release = filters.text("release")
        val narrowed = statuses.isNotEmpty() || adult.isNotEmpty() || genres.isNotEmpty() ||
            author != null || artist != null || release != null
        // Without a text or a filter the archive pages are faster and sorted the same way.
        if (query.isBlank() && !narrowed && order in setOf("latest", "views")) {
            return browse(
                page = page,
                orderBy = order,
                ajaxMetaKey = if (order == "views") "_wp_manga_views" else "_latest_update",
            )
        }
        // The theme's search page takes every filter as a query parameter, with an empty `s=` too.
        val path = if (page > 1) "/page/$page/" else "/"
        val params = buildString {
            append("s=${urlEncode(query.trim())}&post_type=wp-manga")
            val mOrder = when (order) {
                "views" -> "views"
                "trending" -> "trending"
                "new" -> "new-manga"
                "rating" -> "rating"
                "alphabet" -> "alphabet"
                else -> "latest"
            }
            if (query.isBlank() || order != "latest") append("&m_orderby=$mOrder")
            for (status in statuses) append("&status%5B%5D=$status")
            if (adult.isNotEmpty()) append("&adult=$adult")
            for (genre in genres) append("&genre%5B%5D=${urlEncode(genre)}")
            // op=1: a title must have every chosen genre; empty: any of them.
            if (genres.size > 1 && filters.selected("match") == "all") append("&op=1")
            author?.let { append("&author=${urlEncode(it)}") }
            artist?.let { append("&artist=${urlEncode(it)}") }
            release?.let { append("&release=${urlEncode(it)}") }
        }
        val uri = URI("${manifest.baseUri.scheme}://${manifest.baseUri.rawAuthority}$path?$params")
        val response = http.get(uri, source = manifest)
        return parser.parseTitleList(response.body, page, manifest.baseUrl)
    }

    private val siteFilters = SiteFilters(::fetchFilters)

    override suspend fun loadFilters(): FilterList = siteFilters.get(::getFilters)

    /** The genres of the site's advanced search form, plus author, artist and year fields. */
    private suspend fun fetchFilters(): FilterList {
        val uri = URI("${manifest.baseUri.scheme}://${manifest.baseUri.rawAuthority}/?s=&post_type=wp-manga")
        val genres = parser.parseGenres(http.get(uri, source = manifest).body)
        if (genres.isEmpty()) throw SourceFetchException("${manifest.name}: no genres")
        return FilterList(
            getFilters().filters + listOf(
                SourceFilter.Group(
                    id = "genre",
                    name = appString(R.string.filter_genres),
                    filters = genres.map { (slug, label) -> SourceFilter.Check("genre:$slug", label) },
                ),
                SourceFilter.Select(
                    id = "match",
                    name = appString(R.string.filter_match),
                    options = listOf("any", "all"),
                    labels = listOf(appString(R.string.filter_match_any), appString(R.string.filter_match_all)),
                ),
                SourceFilter.Text("author", appString(R.string.filter_author)),
                SourceFilter.Text("artist", appString(R.string.filter_artist)),
                SourceFilter.Text("release", appString(R.string.filter_year)),
            ),
        )
    }

    override fun browseSections(): List<BrowseSection> {
        val base = getFilters()
        return listOf(
            BrowseSection("trending", appString(R.string.section_trending), base.choose("orderby", "trending")),
            BrowseSection("new", appString(R.string.section_new_titles), base.choose("orderby", "new")),
            BrowseSection("top", appString(R.string.section_top_rated), base.choose("orderby", "rating")),
            BrowseSection("completed", appString(R.string.section_completed), base.choose("orderby", "views").include("end")),
        )
    }

    override fun getFilters(): com.lume.app.domain.FilterList = com.lume.app.domain.FilterList(
        listOf(
            com.lume.app.domain.SourceFilter.Select(
                id = "orderby",
                name = appString(R.string.filter_sort),
                options = listOf("latest", "views", "trending", "new", "rating", "alphabet"),
                labels = listOf(
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_trending),
                    appString(R.string.filter_sort_new),
                    appString(R.string.filter_sort_rating),
                    appString(R.string.filter_sort_title),
                ),
            ),
            com.lume.app.domain.SourceFilter.Group(
                id = "status",
                name = appString(R.string.filter_status),
                filters = listOf(
                    com.lume.app.domain.SourceFilter.Check("on-going", appString(R.string.filter_status_ongoing)),
                    com.lume.app.domain.SourceFilter.Check("end", appString(R.string.filter_status_completed)),
                    com.lume.app.domain.SourceFilter.Check("on-hold", appString(R.string.filter_status_hiatus)),
                    com.lume.app.domain.SourceFilter.Check("canceled", appString(R.string.filter_status_cancelled)),
                ),
            ),
            com.lume.app.domain.SourceFilter.Select(
                id = "adult",
                name = appString(R.string.filter_adult),
                options = listOf("", "0", "1"),
                labels = listOf(
                    appString(R.string.filter_any),
                    appString(R.string.filter_adult_none),
                    appString(R.string.filter_adult_only),
                ),
            ),
        ),
    )

    /** Title pages are `/<manga folder>/<slug>/`; chapter links below them open the title. */
    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        if (parts.size < 2) return null
        return "/${parts[0]}/${parts[1]}/"
    }

    /**
     * The title screen asks for details and chapters back to back; both come from the same page.
     * A short-lived cache halves requests to Cloudflare-fronted Madara sites.
     */
    @Volatile
    private var titlePageCache: Triple<String, Long, String>? = null

    private suspend fun titlePage(id: String): String {
        val url = abs(id).toString()
        titlePageCache?.let { (cachedUrl, at, body) ->
            if (cachedUrl == url && System.currentTimeMillis() - at < TITLE_CACHE_MS) return body
        }
        val body = http.get(URI(url), source = manifest).body
        titlePageCache = Triple(url, System.currentTimeMillis(), body)
        return body
    }

    override suspend fun getTitle(id: String): TitleDetails =
        parser.parseTitleDetails(titlePage(id), id, manifest.baseUrl)

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val html = titlePage(titleId)
        parser.parseChapters(html, manifest.baseUrl).takeIf { it.isNotEmpty() }?.let { return it }
        val ajaxHeaders = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to manifest.baseUrl.trimEnd('/'),
            "Referer" to abs(titleId).toString(),
        )
        // Madara ≥ 1.6.x: chapters are loaded from `<title-url>ajax/chapters/`.
        val titleUrl = abs(titleId).toString().substringBefore('?').trimEnd('/') + "/"
        runCatching {
            val ajax = http.postForm(URI("${titleUrl}ajax/chapters/"), source = manifest, headers = ajaxHeaders, form = emptyMap())
            parser.parseChapters(ajax.body, manifest.baseUrl)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Older Madara: admin-ajax with the numeric post id.
        val postId = postId(html) ?: return emptyList()
        val ajax = http.postForm(
            manifest.baseUri.resolve("/wp-admin/admin-ajax.php"),
            source = manifest,
            headers = ajaxHeaders,
            form = mapOf("action" to "manga_get_chapters", "manga" to postId),
        )
        return parser.parseChapters(ajax.body, manifest.baseUrl)
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        var url = chapter.url
        if (!url.contains("style=")) url = withQuery(url, chapterSuffix)
        val response = http.get(abs(url), source = manifest)
        val pages = parser.parsePages(response.body, manifest.baseUrl)
        if (pages.isEmpty()) throw SourceFetchException(com.lume.app.data.locale.appString(com.lume.app.R.string.error_pages_failed))
        return pages
    }

    private fun postId(html: String): String? =
        Regex("""id=["']manga-chapters-holder["'][^>]*data-id=["'](\d+)""").find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""manga_id["\s:=]+(\d+)""").find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""id=["']post-(\d+)["']""").find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""post=(\d+)""").find(html)?.groupValues?.getOrNull(1)

    private companion object {
        const val TITLE_CACHE_MS = 60_000L
    }
}
