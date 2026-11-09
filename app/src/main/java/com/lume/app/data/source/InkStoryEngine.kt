package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ImageDecrypt
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
import com.lume.app.util.urlEncode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/**
 * InkStory (inkstory.net): the site has no open JSON API, its pages carry the data in a
 * `$R[n]=…` script ([InkStoryHydration]). Titles are told apart by slug; a chapter's id is its
 * UUID and its `url` the reader path `/content/<slug>/<chapterId>`.
 *
 * Titles the site shows only to signed-in readers, library members or subscribers
 * (`readingRestriction` other than `NONE`) are left out of the lists; one opened anyway
 * (a link, the library) fails with a clear message instead of an empty chapter list. Paid
 * chapters (`donut`) are skipped, like ReManga's. Adult-flagged titles are left out too: the
 * site serves them to signed-in readers only. All translation teams' chapters are returned
 * with their team, the chapter list picks the team to show.
 */
class InkStoryEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : CatalogSource {
    private val siteBase: String
        get() = manifest.baseUrl.trimEnd('/')

    private val headers: Map<String, String>
        get() = mapOf("Referer" to "$siteBase/")

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> = catalogPage(page, search = null, sort = SORT_LATEST)

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> = catalogPage(page, search = null, sort = null)

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val sort = filters.selected("sort")?.takeIf { it != SORT_POPULAR }
        return catalogPage(page, query.trim().takeIf { it.isNotEmpty() }, sort, filters)
    }

    private suspend fun catalogPage(
        page: Int,
        search: String?,
        sort: String?,
        filters: FilterList = FilterList(),
    ): PagedResult<TitleSummary> {
        val response = http.get(
            URI("$siteBase/content${catalogQuery(page, search, sort, filters)}"),
            source = manifest,
            headers = headers,
        )
        return parseTitleList(response.body, page)
    }

    /** The query string of a catalogue page; the site counts pages from 0 and redirects `page=0`, so it is left out. */
    internal fun catalogQuery(page: Int, search: String?, sort: String?, filters: FilterList): String {
        val params = ArrayList<String>()
        if (!search.isNullOrEmpty()) params += "search=${urlEncode(search)}"
        if (!sort.isNullOrEmpty()) params += "sort=$sort&direction=desc"
        fun labels(name: String, ids: List<String>) {
            if (ids.isEmpty()) return
            // The site reads a JSON array; a single bare value is an error page.
            params += "$name=" + urlEncode(ids.joinToString(",", "[", "]") { "\"${it.substringAfter(':')}\"" })
        }
        labels("labelsInclude", filters.included("genres") + filters.included("tags"))
        labels("labelsExclude", filters.excluded("genres") + filters.excluded("tags"))
        if (page > 1) params += "page=${page - 1}"
        return if (params.isEmpty()) "" else params.joinToString("&", "?")
    }

    internal fun parseTitleList(body: String, page: Int): PagedResult<TitleSummary> {
        val root = hydration(body)
        val holder = root.walk().filterIsInstance<JsonObject>().firstOrNull { it["books"] is JsonArray }
            ?: throw changed()
        val books = holder["books"].arr().orEmpty()
        val items = books.mapNotNull { el ->
            val book = el.obj() ?: return@mapNotNull null
            val slug = book.str("slug") ?: return@mapNotNull null
            if (!isReadable(book)) return@mapNotNull null
            TitleSummary(
                id = slug,
                title = titleOf(book, slug),
                coverUrl = book.str("poster"),
                typeLabel = typeLabel(book.str("country")),
                latestChapter = book.str("chaptersCount")?.takeIf { it != "0" },
            )
        }
        val total = holder.str("total")?.toIntOrNull()
        val hasNext = if (total != null) page * PAGE_SIZE < total else books.size >= PAGE_SIZE
        return PagedResult(items = items, page = page, hasNext = hasNext && books.isNotEmpty())
    }

    /** Free to read without an account, and not adult-flagged (the site shows those to signed-in readers only). */
    private fun isReadable(book: JsonObject): Boolean =
        (book.str("readingRestriction") ?: "NONE") == "NONE" && book.str("contentStatus") !in ADULT

    private val siteFilters = SiteFilters(::fetchFilters)

    override suspend fun loadFilters(): FilterList = siteFilters.get(::getFilters)

    /** Genres and tags: the label dictionary every catalogue page carries. */
    private suspend fun fetchFilters(): FilterList {
        val response = http.get(URI("$siteBase/content"), source = manifest, headers = headers)
        return parseFilters(response.body)
    }

    internal fun parseFilters(body: String): FilterList {
        val labels = hydration(body).walk().filterIsInstance<JsonObject>()
            .mapNotNull { it["labels"].arr() }
            .firstOrNull { list -> list.any { it.obj()?.str("kind") != null } }
            .orEmpty()
            .mapNotNull { el ->
                val label = el.obj() ?: return@mapNotNull null
                Triple(label.str("kind"), label.str("id") ?: return@mapNotNull null, label.str("name") ?: return@mapNotNull null)
            }
        fun group(id: String, kind: String, title: String) = SourceFilter.Group(
            id = id,
            name = title,
            filters = labels.filter { it.first == kind }
                .sortedBy { it.third.lowercase() }
                .map { (_, key, name) -> SourceFilter.TriState("$id:$key", name.replaceFirstChar { it.uppercase() }) },
        )
        val genres = group("genres", "GENRE", appString(R.string.filter_genres))
        if (genres.filters.isEmpty()) throw SourceFetchException("InkStory: no genres")
        return FilterList(getFilters().filters + listOf(genres, group("tags", "TAG", appString(R.string.filter_tags))).filter { it.filters.isNotEmpty() })
    }

    override fun getFilters(): FilterList = FilterList(
        listOf(
            SourceFilter.Select(
                id = "sort",
                name = appString(R.string.filter_sort),
                options = listOf(SORT_POPULAR, SORT_LATEST, "averageRating", "createdAt", "chaptersCount"),
                labels = listOf(
                    appString(R.string.filter_sort_popular),
                    appString(R.string.filter_sort_latest),
                    appString(R.string.filter_sort_rating),
                    appString(R.string.filter_sort_new),
                    appString(R.string.filter_sort_chapters),
                ),
            ),
        ),
    )

    override suspend fun getTitle(id: String): TitleDetails {
        val slug = slug(id)
        val response = http.get(URI("$siteBase/content/${pathSegment(slug)}"), source = manifest, headers = headers)
        return parseDetails(response.body, slug)
    }

    internal fun parseDetails(body: String, slug: String): TitleDetails {
        val book = findBook(hydration(body), slug) ?: throw changed()
        val names = book["name"].obj()
        val title = titleOf(book, slug)
        val english = names.str("en")
        val labels = book["labels"].arr().orEmpty().mapNotNull { it.obj() }
        return TitleDetails(
            id = slug,
            title = title,
            altTitle = (english ?: names.str("original"))?.takeIf { it != title },
            coverUrl = book.str("poster"),
            description = book.str("description"),
            typeLabel = typeLabel(book.str("country")),
            status = when (book.str("status")) {
                "ONGOING" -> appString(R.string.filter_status_ongoing)
                "DONE" -> appString(R.string.filter_status_completed)
                "FROZEN" -> appString(R.string.filter_status_hiatus)
                else -> ""
            }.ifBlank { null },
            genres = (labels.filter { it.str("kind") == "GENRE" } + labels.filter { it.str("kind") != "GENRE" })
                .mapNotNull { it.str("name") },
            otherNames = (listOfNotNull(english, names.str("original")) +
                book["altNames"].arr().orEmpty().mapNotNull { it.obj().str("name") })
                .filter { it != title }
                .distinct()
                .take(MAX_OTHER_NAMES),
            year = book.str("year")?.toIntOrNull(),
            score = book.str("averageRating")?.toDoubleOrNull()?.takeIf { it > 0 },
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = slug(titleId)
        val response = http.get(URI("$siteBase/content/${pathSegment(slug)}/chapters"), source = manifest, headers = headers)
        return parseChapters(response.body, slug)
    }

    /**
     * The chapters of every translation team, in the site's order (newest first). An empty list of a
     * restricted title is an error, not a quiet empty list.
     */
    internal fun parseChapters(body: String, slug: String): List<Chapter> {
        val root = hydration(body)
        val book = findBook(root, slug)
        val list = root.walk().filterIsInstance<JsonObject>()
            .mapNotNull { it["chapters"].arr() }
            .firstOrNull { rows -> rows.all { it.obj()?.containsKey("number") == true } }
            ?: if (book == null) throw changed() else JsonArray(emptyList())
        val chapters = list.mapNotNull { el ->
            val item = el.obj() ?: return@mapNotNull null
            val id = item.str("id") ?: return@mapNotNull null
            if (item["donut"].bool() == true) return@mapNotNull null
            val number = item.str("number").orEmpty()
            val volume = item.str("volume")?.takeIf { it != "0" }
            val name = item.str("name").orEmpty()
            Chapter(
                id = id,
                title = buildString {
                    if (volume != null) append("Том $volume ")
                    append("Глава $number")
                    if (name.isNotBlank()) append(" — $name")
                }.trim(),
                url = chapterPath(slug, id),
                number = number.takeIf { it.isNotEmpty() },
                volume = volume,
                branchId = item.str("branchId"),
                branchName = item.str("publisherNames"),
                dateLabel = item.str("createdAt")?.take(10),
            )
        }
        if (chapters.isEmpty()) {
            val restriction = book?.str("readingRestriction") ?: "NONE"
            if (restriction != "NONE") throw SourceFetchException(appString(R.string.error_inkstory_restricted, manifest.name))
        }
        return chapters
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val path = chapter.url.takeIf { it.startsWith("/content/") }
            ?: throw SourceFetchException(appString(R.string.error_pages_failed_named, manifest.name))
        val response = http.get(URI("$siteBase$path"), source = manifest, headers = headers)
        return parsePages(response.body)
    }

    /** The reader page's images in order. Their files are masked, the key travels with each URL ([ImageDecrypt]). */
    internal fun parsePages(body: String): List<ComicPage> {
        val root = hydration(body)
        val chapter = root.walk().filterIsInstance<JsonObject>().firstNotNullOfOrNull { it["initialChapter"].obj() }
            ?: throw SourceFetchException(appString(R.string.error_pages_failed_named, manifest.name))
        val key = root.walk().filterIsInstance<JsonObject>().firstNotNullOfOrNull { it.str("decryptionKey") }
        val urls = chapter["pages"].arr().orEmpty()
            .mapNotNull { it.obj() }
            .sortedBy { it.str("index")?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { it.str("image") }
        if (urls.isEmpty()) throw SourceFetchException(appString(R.string.error_pages_failed_named, manifest.name))
        return urls.mapIndexed { i, url -> ComicPage(index = i, imageUrl = ImageDecrypt.tag(url, key)) }
    }

    private fun hydration(body: String): JsonElement = InkStoryHydration.parse(body) ?: throw changed()

    private fun changed() = SourceFetchException(appString(R.string.error_source_changed, manifest.name))

    /** The book object of [slug]; the detail copy (with its description) wins over the shorter ones. */
    private fun findBook(root: JsonElement, slug: String): JsonObject? {
        val books = root.walk().filterIsInstance<JsonObject>()
            .filter { it.containsKey("readingRestriction") && it["name"] is JsonObject }
            .toList()
        val own = books.filter { it.str("slug") == slug }.ifEmpty { books }
        return own.firstOrNull { it.containsKey("description") } ?: own.firstOrNull()
    }

    private fun titleOf(book: JsonObject, slug: String): String {
        val names = book["name"].obj()
        return names.str("ru") ?: names.str("en") ?: names.str("original") ?: slug
    }

    private fun typeLabel(country: String?): String? = when (country) {
        "JAPAN" -> appString(R.string.type_manga)
        "CHINA" -> appString(R.string.type_manhua)
        "KOREA" -> appString(R.string.type_manhwa)
        else -> ""
    }.ifBlank { null }

    private fun chapterPath(slug: String, chapterId: String) = "/content/${pathSegment(slug)}/$chapterId"

    private fun slug(id: String): String = id.trim('/').substringBefore('/').ifBlank { id }

    override fun webUrl(titleId: String): String = "$siteBase/content/${pathSegment(slug(titleId))}"

    override fun titleIdFromUrl(url: String): String? {
        val parts = com.lume.app.domain.SourceLinks.segments(url, manifest.baseUrl) ?: return null
        val i = parts.indexOf("content")
        return parts.getOrNull(i + 1)?.takeIf { i >= 0 }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private companion object {
        const val PAGE_SIZE = 40
        const val MAX_OTHER_NAMES = 15
        const val SORT_POPULAR = "viewsCount"
        const val SORT_LATEST = "latestChapterAt"
        val ADULT = setOf("EROTIC", "PORNOGRAPHIC")
    }
}

/**
 * Reads the hydration script of an InkStory page: `$R[0]={…}` is a JavaScript expression, not JSON.
 * Objects, arrays, strings, numbers, `!0`/`!1` booleans, `null`, `$R[n]=value` definitions and bare
 * `$R[n]` references are understood; anything else (dates, maps) reads as null. The result is a plain
 * [JsonElement] tree and callers find what they need by field name, since the indices change on deploys.
 */
internal object InkStoryHydration {
    private val START = Regex("\\\$R\\[\\d+]=")

    /** The tree of the first `$R[n]=` expression in [html] (or of a bare script text); null when there is none or it is cut short. */
    fun parse(html: String): JsonElement? {
        val start = START.find(html)?.range?.first ?: return null
        return try {
            Reader(html, start).root()
        } catch (e: IllegalStateException) {
            null
        }
    }

    private class Reader(private val s: String, private var i: Int) {
        private val refs = HashMap<Int, JsonElement>()

        fun root(): JsonElement = value()

        private fun fail(): Nothing = throw IllegalStateException("Unexpected hydration text at $i")

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun value(): JsonElement {
            skipSpace()
            if (i >= s.length) fail()
            val c = s[i]
            return when {
                c == '{' -> obj()
                c == '[' -> arr()
                c == '"' || c == '\'' -> JsonPrimitive(string())
                c == '!' -> {
                    val on = s.startsWith("!0", i)
                    if (!on && !s.startsWith("!1", i)) fail()
                    i += 2
                    JsonPrimitive(on)
                }
                c == '-' || c.isDigit() -> number()
                c == '(' -> skipExpression() // the stream function of the query cache
                s.startsWith("\$R[", i) -> reference()
                else -> word()
            }
        }

        private fun reference(): JsonElement {
            val begin = i
            i += 3
            val digits = i
            while (i < s.length && s[i].isDigit()) i++
            if (i == digits || s.getOrNull(i) != ']') {
                i = begin
                return skipExpression()
            }
            val n = s.substring(digits, i).toInt()
            i++
            skipSpace()
            if (s.getOrNull(i) == '=' && s.getOrNull(i + 1) != '=') {
                i++
                return value().also { refs[n] = it }
            }
            return refs[n] ?: JsonNull
        }

        private fun obj(): JsonElement {
            i++
            val map = LinkedHashMap<String, JsonElement>()
            while (true) {
                skipSpace()
                when (s.getOrNull(i) ?: fail()) {
                    '}' -> { i++; return JsonObject(map) }
                    ',' -> { i++; continue }
                }
                val key = if (s[i] == '"' || s[i] == '\'') string() else {
                    val from = i
                    while (i < s.length && s[i] != ':') i++
                    s.substring(from, i).trim()
                }
                skipSpace()
                if (s.getOrNull(i) != ':') fail()
                i++
                map[key] = value()
            }
        }

        private fun arr(): JsonElement {
            i++
            val list = ArrayList<JsonElement>()
            while (true) {
                skipSpace()
                when (s.getOrNull(i) ?: fail()) {
                    ']' -> { i++; return JsonArray(list) }
                    ',' -> { i++; continue }
                }
                list += value()
            }
        }

        private fun number(): JsonElement {
            val from = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val raw = s.substring(from, i)
            if (s.getOrNull(i) == 'n') i++ // BigInt
            return raw.toLongOrNull()?.let { JsonPrimitive(it) }
                ?: raw.toDoubleOrNull()?.let { JsonPrimitive(it) }
                ?: JsonNull
        }

        private fun string(): String {
            val quote = s[i++]
            val out = StringBuilder()
            while (true) {
                if (i >= s.length) fail()
                val c = s[i++]
                when {
                    c == quote -> return out.toString()
                    c != '\\' -> out.append(c)
                    else -> {
                        if (i >= s.length) fail()
                        when (val e = s[i++]) {
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'v' -> out.append('\u000B')
                            '0' -> out.append('\u0000')
                            'x' -> out.append(hex(2).toChar())
                            'u' -> if (s.getOrNull(i) == '{') {
                                val end = s.indexOf('}', i)
                                if (end < 0) fail()
                                out.appendCodePoint(s.substring(i + 1, end).toInt(16))
                                i = end + 1
                            } else {
                                out.append(hex(4).toChar())
                            }
                            '\n' -> Unit // line continuation
                            else -> out.append(e)
                        }
                    }
                }
            }
        }

        private fun hex(count: Int): Int {
            if (i + count > s.length) fail()
            val v = s.substring(i, i + count).toIntOrNull(16) ?: fail()
            i += count
            return v
        }

        /** `null`, `true`, `false`, `void 0`; any other expression (`new Date(…)`, …) is skipped and reads as null. */
        private fun word(): JsonElement {
            val from = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '$')) i++
            return when (s.substring(from, i)) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                "null", "undefined", "NaN", "Infinity" -> JsonNull
                "" -> fail()
                else -> {
                    i = from
                    skipExpression()
                }
            }
        }

        /** Skips to the next `,` `}` `]` outside brackets and strings. */
        private fun skipExpression(): JsonElement {
            var depth = 0
            while (i < s.length) {
                when (s[i]) {
                    '"', '\'' -> { string(); continue }
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> if (depth == 0) return JsonNull else depth--
                    ',' -> if (depth == 0) return JsonNull
                }
                i++
            }
            fail()
        }
    }
}

/** Every object and array of the tree, parents before children, each shared node once. */
internal fun JsonElement.walk(): Sequence<JsonElement> = sequence {
    val seen = java.util.IdentityHashMap<JsonElement, Boolean>()
    val stack = ArrayDeque<JsonElement>()
    stack.addLast(this@walk)
    while (stack.isNotEmpty()) {
        val el = stack.removeLast()
        if ((el !is JsonObject && el !is JsonArray) || seen.put(el, true) != null) continue
        yield(el)
        when (el) {
            is JsonObject -> el.values.reversed().forEach(stack::addLast)
            is JsonArray -> el.reversed().forEach(stack::addLast)
        }
    }
}
