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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Royal Road (royalroad.com), original English web fiction. Plain HTML pages behind Cloudflare.
 *
 * - Lists: `.fiction-list-item` on the weekly popular, latest updates and search pages, paged
 *   with `?page=N`.
 * - Title ids are paths (`/fiction/21220/mother-of-learning`); chapter ids are chapter paths.
 * - The title page carries every chapter as `window.chapters = [...]`; chapters a reader must
 *   pay for (`isUnlocked: false`) are left out.
 * - Chapter text is `.chapter-inner.chapter-content`. Every chapter has a line hidden by a class
 *   from the page's `<style>` ("this copy was not approved"), which is removed.
 */
class RoyalRoadEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : NovelSource {
    private val siteBase get() = manifest.baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> = list("/fictions/weekly-popular", page)

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> = list("/fictions/latest-updates", page)

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Sort(
                id = SORT,
                name = appString(R.string.filter_sort),
                options = SORT_VALUES,
                labels = listOf(
                    appString(R.string.filter_sort_relevance),
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_rating),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_new),
                    appString(R.string.filter_sort_followers),
                    appString(R.string.filter_sort_pages),
                    appString(R.string.filter_sort_views),
                    appString(R.string.filter_sort_title),
                    appString(R.string.filter_sort_author),
                ),
            ),
            SourceFilter.Select(
                id = STATUS,
                name = appString(R.string.filter_status),
                options = listOf("", "ONGOING", "COMPLETED", "HIATUS", "DROPPED", "INACTIVE", "STUB"),
                labels = listOf(
                    appString(R.string.filter_any),
                    appString(R.string.filter_status_ongoing),
                    appString(R.string.filter_status_completed),
                    appString(R.string.filter_status_hiatus),
                    appString(R.string.filter_status_dropped),
                    appString(R.string.filter_status_inactive),
                    appString(R.string.filter_status_stub),
                ),
            ),
            SourceFilter.Select(
                id = TYPE,
                name = appString(R.string.filter_type),
                options = listOf("", "original", "fanfiction"),
                labels = listOf(
                    appString(R.string.filter_any),
                    appString(R.string.filter_type_original),
                    appString(R.string.filter_type_fanfiction),
                ),
            ),
            tagGroup(GENRES, appString(R.string.filter_genres), GENRE_TAGS),
            tagGroup(WARNINGS, appString(R.string.filter_content_warnings), WARNING_TAGS),
            tagGroup(TAGS, appString(R.string.filter_tags), EXTRA_TAGS),
        ),
    )

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val path = searchPath(query, filters)
        return if (path == null) getPopular(page) else list(path, page)
    }

    /**
     * Search page path for [query] and [filters] (without the page number), or null when there is
     * neither text nor a filter set, which is the popular list. Genres and tags the title must have
     * go into `tagsAdd` (the site matches all of them), the excluded ones into `tagsRemove`.
     */
    internal fun searchPath(query: String, filters: FilterList): String? {
        val params = ArrayList<String>()
        if (query.isNotBlank()) params += "title=${urlEncode(query.trim())}"
        filters.included().forEach { params += "tagsAdd=${urlEncode(it.substringAfter(':'))}" }
        filters.excluded().forEach { params += "tagsRemove=${urlEncode(it.substringAfter(':'))}" }
        filters.selected(STATUS)?.takeIf { it.isNotEmpty() }?.let { params += "status=$it" }
        filters.selected(TYPE)?.takeIf { it.isNotEmpty() }?.let { params += "type=$it" }
        filters.sort(SORT)?.let { sort ->
            if (sort.index != 0 || sort.ascending) {
                params += "orderBy=${sort.selectedValue}"
                params += "dir=${if (sort.ascending) "asc" else "desc"}"
            }
        }
        if (params.isEmpty()) return null
        return "/fictions/search?" + params.joinToString("&")
    }

    override suspend fun getTitle(id: String): TitleDetails = parseDetails(fetch(id), id)

    override suspend fun getChapters(titleId: String): List<Chapter> = parseChapters(fetch(titleId))

    override suspend fun getChapterText(chapter: Chapter): NovelChapter =
        NovelChapter(chapter.id, parseChapterText(fetch(chapter.url.ifBlank { chapter.id })))

    override fun titleIdFromUrl(url: String): String? {
        val path = SourceLinks.pathOnHost(url, manifest.baseUrl) ?: return null
        return TITLE_PATH.find(path)?.value
    }

    internal fun parseTitleList(body: String, page: Int): PagedResult<TitleSummary> {
        val doc = Jsoup.parse(body, "$siteBase/")
        val seen = HashSet<String>()
        val items = doc.select(".fiction-list-item").mapNotNull { node ->
            val link = node.selectFirst(".fiction-title a[href]") ?: return@mapNotNull null
            val id = titlePath(link.attr("abs:href")) ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            TitleSummary(
                id = id,
                title = link.text().trim().ifEmpty { return@mapNotNull null },
                coverUrl = cover(node.selectFirst("figure img")),
                typeLabel = labels(node).firstOrNull { it.lowercase() !in STATUSES },
                latestChapter = node.selectFirst("li.list-item a span")?.text()?.trim()?.ifEmpty { null },
                subtitle = labels(node).firstOrNull { it.lowercase() in STATUSES }?.let(::statusLabel),
            )
        }
        val hasNext = doc.select("ul.pagination a[data-page]").any { (it.attr("data-page").toIntOrNull() ?: 0) > page }
        return PagedResult(items, page, hasNext)
    }

    internal fun parseDetails(body: String, id: String): TitleDetails {
        val doc = Jsoup.parse(body, "$siteBase/")
        val header = doc.selectFirst(".fic-header")
        val info = doc.selectFirst(".fiction-info") ?: doc
        val title = header?.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        val labels = labels(info)
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id },
            coverUrl = cover(header?.selectFirst("img[data-type=cover], img"))
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")?.takeIf { it.startsWith("http") },
            description = info.selectFirst(".description .hidden-content, .description")?.let { htmlToText(it.html()) },
            typeLabel = labels.firstOrNull { it.lowercase() !in STATUSES },
            status = labels.firstOrNull { it.lowercase() in STATUSES }?.let(::statusLabel),
            authors = header?.select("h4 a[href*=/profile/]")?.map { it.text().trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            genres = info.select("a.fiction-tag").map { it.text().trim() }.filter { it.isNotEmpty() }.distinct(),
            score = doc.selectFirst("meta[property=books:rating:value]")?.attr("content")?.toDoubleOrNull()
                ?.let { (it * 2 * 100).toLong() / 100.0 },
        )
    }

    internal fun parseChapters(body: String): List<Chapter> {
        val fromScript = CHAPTERS_SCRIPT.find(body)?.groupValues?.get(1)?.let { array ->
            runCatching { json.parseToJsonElement(array).arr().orEmpty() }.getOrNull()
        }
        if (fromScript != null) {
            return fromScript.mapIndexedNotNull { index, el ->
                val item = el.obj() ?: return@mapIndexedNotNull null
                if (item["isUnlocked"].bool() == false) return@mapIndexedNotNull null
                val url = item.str("url") ?: return@mapIndexedNotNull null
                val order = (item["order"] as? JsonPrimitive)?.intOrNull ?: index
                Chapter(
                    id = url,
                    title = item.str("title")?.trim().orEmpty().ifEmpty { "Chapter ${order + 1}" },
                    url = siteBase + url,
                    number = (order + 1).toString(),
                    dateLabel = item.str("date")?.take(10),
                )
            }
        }
        // Older layout: the chapter table only.
        val doc = Jsoup.parse(body, "$siteBase/")
        return doc.select("tr.chapter-row[data-url]").mapIndexed { index, row ->
            val url = row.attr("data-url")
            Chapter(
                id = url,
                title = row.selectFirst("td a")?.text()?.trim().orEmpty().ifEmpty { "Chapter ${index + 1}" },
                url = siteBase + url,
                number = (index + 1).toString(),
                dateLabel = row.selectFirst("time[datetime]")?.attr("datetime")?.take(10),
            )
        }
    }

    internal fun parseChapterText(body: String): String {
        val doc = Jsoup.parse(body, "$siteBase/")
        val content = doc.selectFirst(".chapter-inner.chapter-content") ?: doc.selectFirst(".chapter-content")
            ?: throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        val hidden = NovelHtml.hiddenClasses(doc.select("style").map { it.data() })
        content.allElements.filter { el -> el.classNames().any { it in hidden } }.forEach { it.remove() }
        val html = NovelHtml.clean(content)
        if (!NovelHtml.hasContent(html)) throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
        return html
    }

    private suspend fun list(path: String, page: Int): PagedResult<TitleSummary> {
        val url = if (page <= 1) siteBase + path else withQuery(siteBase + path, "page=$page")
        return parseTitleList(http.get(URI(url), source = manifest).body, page)
    }

    private suspend fun fetch(pathOrUrl: String): String = http.get(resolveUrl(siteBase, pathOrUrl), source = manifest).body

    private fun labels(node: Element): List<String> =
        node.select("span.label").map { it.text().trim() }.filter { it.isNotEmpty() }

    private fun cover(img: Element?): String? {
        val src = img?.attr("abs:src")?.takeIf { it.startsWith("http") } ?: return null
        return src.takeUnless { "nocover" in it }
    }

    private fun titlePath(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        return TITLE_PATH.find(path)?.value
    }

    private fun statusLabel(raw: String): String = raw.lowercase().replaceFirstChar { it.uppercase() }

    private fun tagGroup(id: String, name: String, tags: List<Pair<String, String>>) =
        SourceFilter.Group(id, name, tags.map { (slug, label) -> SourceFilter.TriState("$id:$slug", label) })

    private companion object {
        const val SORT = "sort"
        const val STATUS = "status"
        const val TYPE = "type"
        const val GENRES = "genres"
        const val WARNINGS = "warnings"
        const val TAGS = "tags"

        /** `orderBy` values of the search form; the first (relevance) is the site's default. */
        val SORT_VALUES = listOf(
            "relevance", "popularity", "rating", "last_update", "release_date",
            "followers", "length", "views", "title", "author",
        )

        // Slugs and names from the search form (genre buttons and the additional tags list).
        val GENRE_TAGS = listOf(
            "action" to "Action", "adventure" to "Adventure", "comedy" to "Comedy", "contemporary" to "Contemporary",
            "drama" to "Drama", "fantasy" to "Fantasy", "historical" to "Historical", "horror" to "Horror",
            "mystery" to "Mystery", "psychological" to "Psychological", "romance_main" to "Romance",
            "satire" to "Satire", "sci_fi" to "Sci-fi", "one_shot" to "Short Story", "thriller" to "Thriller",
            "tragedy" to "Tragedy",
        )
        val WARNING_TAGS = listOf(
            "ai_assisted" to "AI-Assisted", "ai_generated" to "AI-Generated Content", "ai_translated" to "AI-Translated",
            "graphic_violence" to "Graphic Violence", "profanity" to "Profanity", "sensitive" to "Sensitive Content",
            "sexuality" to "Sexual Content",
        )
        val EXTRA_TAGS = listOf(
            "anti-hero_lead" to "Anti-Hero Lead", "antivillain_lead" to "Anti-Villain Lead", "apocalypse" to "Apocalypse",
            "artificial_intelligence" to "Artificial Intelligence", "attractive_lead" to "Attractive Lead",
            "chivalry" to "Chivalry", "competing_love" to "Competing Love Interest", "cozy" to "Cozy",
            "crafting" to "Crafting", "cultivation" to "Cultivation", "cyberpunk" to "Cyberpunk",
            "deck_building" to "Deck Building", "dungeon_core" to "Dungeon Core", "dungeon_crawler" to "Dungeon Crawler",
            "dystopia" to "Dystopia", "female_lead" to "Female Lead", "first_contact" to "First Contact",
            "gamelit" to "GameLit", "gender_bender" to "Gender Bender", "genetically_engineered" to "Genetically Engineered",
            "grimdark" to "Grimdark", "hard_sci-fi" to "Hard Sci-fi", "high_fantasy" to "High Fantasy",
            "kingdom_building" to "Kingdom Building", "lesbian_romance" to "Lesbian Romance", "litrpg" to "LitRPG",
            "local_protagonist" to "Local Protagonist", "low_fantasy" to "Low Fantasy", "magic" to "Magic",
            "magical_girl" to "Magical Girl", "magitech" to "Magitech", "gay_romance" to "Male Gay Romance",
            "male_lead" to "Male Lead", "martial_arts" to "Martial Arts", "mecha" to "Mecha",
            "modern_knowledge" to "Modern Knowledge", "monster_evolution" to "Monster Evolution",
            "multiple_lead" to "Multiple Lead Characters", "harem" to "Multiple Lovers", "mythos" to "Mythos",
            "non-human_lead" to "Non-Human Lead", "nonhumanoid_lead" to "Non-Humanoid Lead", "otome" to "Otome",
            "summoned_hero" to "Portal Fantasy / Isekai", "post_apocalyptic" to "Post Apocalyptic",
            "progression" to "Progression", "reader_interactive" to "Reader Interactive",
            "reincarnation" to "Reincarnation", "romance" to "Romance Subplot", "ruling_class" to "Ruling Class",
            "school_life" to "School Life", "secret_identity" to "Secret Identity", "slice_of_life" to "Slice of Life",
            "soft_sci-fi" to "Soft Sci-fi", "space_opera" to "Space Opera", "sports" to "Sports",
            "steampunk" to "Steampunk", "strategy" to "Strategy", "strong_lead" to "Strong Lead",
            "super_heroes" to "Super Heroes", "supernatural" to "Supernatural", "survival" to "Survival",
            "system_invasion" to "System Invasion", "technologically_engineered" to "Technologically Engineered",
            "loop" to "Time Loop", "time_travel" to "Time Travel", "tower" to "Tower", "urban_fantasy" to "Urban Fantasy",
            "villainous_lead" to "Villainous Lead", "virtual_reality" to "Virtual Reality",
            "war_and_military" to "War and Military", "wuxia" to "Wuxia",
        )

        val TITLE_PATH = Regex("""^/fiction/\d+(?:/[^/?#]+)?""")
        val CHAPTERS_SCRIPT = Regex("""window\.chapters\s*=\s*(\[.*?]);""", RegexOption.DOT_MATCHES_ALL)
        val STATUSES = setOf("ongoing", "completed", "hiatus", "stub", "dropped", "inactive")
    }
}
