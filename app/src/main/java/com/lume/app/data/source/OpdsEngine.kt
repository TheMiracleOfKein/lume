package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.ArchiveCache
import com.lume.app.data.local.archive.ArchiveReaders
import com.lume.app.data.local.pdf.PdfPageUrl
import com.lume.app.data.local.pdf.PdfRendererPool
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.SourceHttpException
import com.lume.app.data.server.ServerCredentials
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
import com.lume.app.domain.chapterSortKey
import com.lume.app.util.urlEncode
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Any OPDS catalogue (1.x Atom or 2.0 JSON), for example Calibre-Web, Kavita's or Komga's OPDS.
 *
 * - A feed entry that opens another feed is a title; a book is a chapter. A catalogue of loose
 *   books shows every book as a title with one chapter.
 * - Pages come from the page-streaming link (OPDS-PSE) when the entry has one. Otherwise the
 *   CBZ / CBR / CB7 / PDF acquisition link is downloaded to the cache and read like a local file.
 * - Sign-in (Basic or API key) is added by the shared HTTP client.
 *
 * @param cacheDir where downloaded books are kept; null disables downloading (parser tests).
 */
class OpdsEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    @Suppress("unused") private val credentials: ServerCredentials? = null,
    private val cacheDir: File? = null,
) : CatalogSource {
    private val root get() = manifest.baseUrl.trimEnd('/')

    private val feeds = ConcurrentHashMap<String, Pair<Long, OpdsFeed>>()
    private val chains = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val descended = ConcurrentHashMap<String, String>()

    @Volatile
    private var rootNav: List<OpdsEntry> = emptyList()

    @Volatile
    private var searchTemplate: String? = null

    override val filtersWithQuery: Boolean get() = false

    // ---- Browse -------------------------------------------------------------------------------

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> = listing(startOf(root), page)

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> {
        val rootFeed = fetchFeed(root)
        val newest = rootFeed.newest
            ?: rootFeed.entries.firstOrNull { it.navUrl != null && NEWEST_TITLE.containsMatchIn(it.title) }?.navUrl
        return listing(startOf(newest ?: root), page)
    }

    override fun browseSections(): List<BrowseSection> = rootNav.take(MAX_SECTIONS).map { entry ->
        BrowseSection(
            id = entry.navUrl.orEmpty(),
            name = entry.title,
            filters = FilterList(listOf(SourceFilter.Text(id = FEED_FILTER, name = "", state = entry.navUrl.orEmpty()))),
        )
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        val feed = filters.text(FEED_FILTER)
        if (query.isBlank()) return listing(startOf(feed ?: root), page)
        val url = searchUrl(query.trim()) ?: throw SourceFetchException(appString(R.string.server_error_opds_no_search))
        return listing(url, page)
    }

    /**
     * The first page to list for [url]. Only the catalogue root is opened further: it is a menu,
     * so we go down to the series or book list (for example "All series"). Any other feed, such as
     * a section the user picked, is listed as it is: its sub-entries are the titles.
     */
    private suspend fun startOf(url: String): String {
        if (url != root) return url
        descended[url]?.let { return it }
        var feed = fetchFeed(url)
        rootNav = feed.entries.filter { it.navUrl != null && !it.isBook }
        for (step in 0 until MAX_DESCENT) {
            val listsTitles = feed.listsItems || feed.entries.any { it.isBook || (it.navUrl != null && it.navIsAcquisition) }
            val folders = feed.entries.filter { it.navUrl != null }
            if (listsTitles || folders.isEmpty()) break
            val series = folders.firstOrNull { SERIES_TITLE.containsMatchIn(it.title) }
            val pick = series ?: folders.firstOrNull { NEWEST_TITLE.containsMatchIn(it.title) }
                ?: folders.firstOrNull { ALL_TITLE.containsMatchIn(it.title) } ?: folders.first()
            feed = fetchFeed(pick.navUrl!!)
            // Servers like Komga mark a list of series as navigation: once we reached the list
            // named "series", its entries are the titles, not more menus.
            if (series != null) break
        }
        return feed.url.also { descended[url] = it }
    }

    private suspend fun listing(startUrl: String, page: Int): PagedResult<TitleSummary> {
        val chain = chains.getOrPut(startUrl) { CopyOnWriteArrayList(listOf(startUrl)) }
        while (chain.size < page) {
            val next = fetchFeed(chain.last()).next ?: return PagedResult(emptyList(), page, false)
            chain.add(next)
        }
        return summaries(fetchFeed(chain[page - 1]), page)
    }

    internal fun summaries(feed: OpdsFeed, page: Int): PagedResult<TitleSummary> {
        val items = feed.entries.mapNotNull { entry ->
            val id = when {
                entry.navUrl != null && !entry.isBook -> entry.navUrl
                entry.isBook -> BookRef.encode(feed.url, entry.id)
                else -> null
            } ?: return@mapNotNull null
            TitleSummary(
                id = id,
                title = entry.title,
                coverUrl = entry.cover,
                subtitle = entry.authors.joinToString(", ").ifEmpty { null },
            )
        }
        return PagedResult(items = items, page = page, hasNext = feed.next != null)
    }

    // ---- Search -------------------------------------------------------------------------------

    private suspend fun searchUrl(query: String): String? {
        val template = searchTemplate ?: findSearchTemplate()?.also { searchTemplate = it } ?: return null
        return expandSearch(template, query)
    }

    private suspend fun findSearchTemplate(): String? {
        val link = fetchFeed(root).search ?: return null
        val type = link.type.orEmpty()
        // Either the template itself, or an OpenSearch description that holds it.
        if (type.contains("opensearchdescription")) {
            return parseOpenSearchDescription(fetchText(link.href), link.href)
        }
        return link.href
    }

    // ---- Details ------------------------------------------------------------------------------

    override suspend fun getTitle(id: String): TitleDetails {
        BookRef.decode(id)?.let { (feedUrl, entryId) ->
            val entry = fetchFeed(feedUrl).entries.firstOrNull { it.id == entryId }
                ?: throw SourceFetchException("OPDS: book not found")
            return TitleDetails(
                id = id,
                title = entry.title,
                coverUrl = entry.cover,
                description = entry.summary,
                authors = entry.authors,
                genres = entry.categories,
            )
        }
        val feed = fetchFeed(id)
        return TitleDetails(
            id = id,
            title = feed.title ?: id,
            coverUrl = feed.entries.firstNotNullOfOrNull { it.cover },
            description = feed.subtitle,
            authors = feed.entries.flatMap { it.authors }.distinct().take(5),
            genres = feed.entries.flatMap { it.categories }.distinct().take(12),
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        BookRef.decode(titleId)?.let { (feedUrl, entryId) ->
            val entry = fetchFeed(feedUrl).entries.firstOrNull { it.id == entryId } ?: return emptyList()
            return listOfNotNull(chapterOf(entry))
        }
        val books = ArrayList<OpdsEntry>()
        collectBooks(titleId, books, depth = 0, folders = intArrayOf(0))
        val chapters = books.mapNotNull(::chapterOf).distinctBy { it.id }
        return newestFirst(chapters)
    }

    /** Books of a feed and its following pages; folders inside it are read one level deep. */
    private suspend fun collectBooks(url: String, out: MutableList<OpdsEntry>, depth: Int, folders: IntArray) {
        var feed = fetchFeed(url)
        var pages = 0
        while (true) {
            for (entry in feed.entries) {
                if (out.size >= MAX_BOOKS) return
                when {
                    entry.isBook -> out += entry
                    entry.navUrl != null && depth < MAX_NESTING && folders[0] < MAX_FOLDERS -> {
                        folders[0]++
                        collectBooks(entry.navUrl, out, depth + 1, folders)
                    }
                }
            }
            val next = feed.next
            if (next == null || ++pages >= MAX_FEED_PAGES) return
            feed = fetchFeed(next)
        }
    }

    internal fun chapterOf(entry: OpdsEntry): Chapter? {
        val stream = entry.stream
        val url = if (stream != null && stream.count != null && stream.count > 0) {
            "${stream.href}#pse=${stream.count}"
        } else {
            val acquisition = pickAcquisition(entry) ?: return null
            "${acquisition.href}#ext=${extensionOf(acquisition)}"
        }
        val title = cleanBookTitle(entry.title)
        return Chapter(
            id = entry.id,
            title = title,
            url = url,
            number = (chapterNumberFrom(title) ?: TRAILING_NUMBER.find(title)?.groupValues?.get(1))?.let(::cleanNumber),
            dateLabel = entry.updated?.take(10),
        )
    }

    /** Feeds list books oldest first (volume 1, 2, ...); the reader wants the newest first. */
    private fun newestFirst(chapters: List<Chapter>): List<Chapter> {
        val first = chapters.firstOrNull()?.let(::chapterSortKey)
        val last = chapters.lastOrNull()?.let(::chapterSortKey)
        return if (first != null && last != null && first > last) chapters else chapters.reversed()
    }

    // ---- Pages --------------------------------------------------------------------------------

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val href = chapter.url.substringBefore('#')
        val fragment = chapter.url.substringAfter('#', "")
        fragment.removePrefix("pse=").takeIf { fragment.startsWith("pse=") }?.toIntOrNull()?.let { count ->
            return streamPages(href, count)
        }
        val ext = fragment.removePrefix("ext=").ifEmpty { "cbz" }
        return downloadedPages(href, ext)
    }

    internal fun streamPages(template: String, count: Int): List<ComicPage> =
        (0 until count).map { i ->
            ComicPage(
                index = i,
                imageUrl = template
                    .replace("{pageNumber}", i.toString())
                    .replace("{maxWidth}", PSE_MAX_WIDTH.toString())
                    .replace("{maxHeight}", ""),
            )
        }

    private suspend fun downloadedPages(url: String, ext: String): List<ComicPage> {
        val dir = cacheDir ?: throw SourceFetchException("OPDS: downloads are not available")
        val file = downloadBook(File(dir, "opds_dl"), url, ext)
        if (ArchiveReaders.isPdf(file.name)) {
            val count = try {
                PdfRendererPool.pageCount(file)
            } catch (e: IOException) {
                throw SourceFetchException(appString(R.string.error_pdf_unreadable))
            } catch (e: SecurityException) {
                throw SourceFetchException(appString(R.string.error_pdf_unreadable))
            }
            return (0 until count).map { ComicPage(it, PdfPageUrl.build(file.absolutePath, it)) }
        }
        val outDir = File(dir, "opds_zip/${ArchiveCache.dirKey(file)}")
        val images = ArchiveReaders.open(file).use { reader ->
            ArchiveReaders.extractImages(reader, outDir) { i, name -> "%04d_%s".format(i, name.ifBlank { "page_$i" }) }
        }
        ArchiveCache.touch(outDir)
        ArchiveCache.trim(outDir.parentFile ?: outDir, keep = outDir)
        return images.mapIndexed { i, f -> ComicPage(i, f.absolutePath) }
    }

    /** Downloads [url] once; later calls reuse the file. Old downloads are removed past the cache limit. */
    private fun downloadBook(folder: File, url: String, ext: String): File {
        folder.mkdirs()
        val target = File(folder, sha1(url) + "." + ext)
        if (target.isFile && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        val part = File(folder, target.name + ".part")
        val request = Request.Builder().url(url).header("User-Agent", http.userAgent).build()
        http.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SourceHttpException(response.code, url)
            response.body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        trimDownloads(folder, keep = target)
        return target
    }

    private fun trimDownloads(folder: File, keep: File) {
        runCatching {
            val files = folder.listFiles { f -> f.isFile && !f.name.endsWith(".part") }.orEmpty().sortedByDescending { it.lastModified() }
            var total = 0L
            for (f in files) {
                total += f.length()
                if (total > MAX_DOWNLOAD_CACHE && f != keep) f.delete()
            }
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(10).joinToString("") { "%02x".format(it) }

    // ---- Feed access ----------------------------------------------------------------------------

    private suspend fun fetchFeed(url: String): OpdsFeed {
        feeds[url]?.let { (at, feed) -> if (System.currentTimeMillis() - at < FEED_TTL_MS) return feed }
        val feed = OpdsParser.parse(fetchText(url), url)
        if (feeds.size > MAX_CACHED_FEEDS) feeds.clear()
        feeds[url] = System.currentTimeMillis() to feed
        return feed
    }

    /** Replaces the network in tests. */
    internal var fetcher: (suspend (String) -> String)? = null

    private suspend fun fetchText(url: String): String =
        fetcher?.invoke(url) ?: http.get(URI(url), manifest, mapOf("Accept" to ACCEPT), checkCloudflare = false).body

    override fun webUrl(titleId: String): String? = null

    override fun titleIdFromUrl(url: String): String? = null

    /** A book that is listed without a series: the feed it was found in and its entry id. */
    internal object BookRef {
        private const val PREFIX = "opdsbook:"

        fun encode(feedUrl: String, entryId: String): String =
            PREFIX + URLEncoder.encode(feedUrl, "UTF-8") + ":" + URLEncoder.encode(entryId, "UTF-8")

        fun decode(id: String): Pair<String, String>? {
            if (!id.startsWith(PREFIX)) return null
            val parts = id.removePrefix(PREFIX).split(':', limit = 2)
            if (parts.size != 2) return null
            return URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }
    }

    internal companion object {
        const val FEED_FILTER = "feed"
        const val ACCEPT = "application/atom+xml, application/opds+json, application/xml;q=0.9, application/json;q=0.8, */*;q=0.5"
        const val PSE_MAX_WIDTH = 1600
        const val MAX_SECTIONS = 12
        const val MAX_DESCENT = 3
        const val MAX_NESTING = 2
        const val MAX_FOLDERS = 12
        const val MAX_FEED_PAGES = 20
        const val MAX_BOOKS = 600
        const val MAX_CACHED_FEEDS = 40
        const val FEED_TTL_MS = 60_000L
        const val MAX_DOWNLOAD_CACHE = 600L * 1024 * 1024

        val NEWEST_TITLE = Regex("""new|recent|latest|свеж|нов|недавн""", RegexOption.IGNORE_CASE)
        val SERIES_TITLE = Regex("""series|titles|books|серии|книги""", RegexOption.IGNORE_CASE)
        val ALL_TITLE =Regex("""\ball\b|series|library|titles|все|серии|книги""", RegexOption.IGNORE_CASE)
        val TRAILING_NUMBER = Regex("""(\d+(?:[.,]\d+)?)\s*$""")

        /** Readable formats, best first. */
        private val READABLE = listOf("cbz", "zip", "cbr", "rar", "cb7", "7z", "pdf")

        /** Kavita lists a started book twice and decorates titles: "Continue Reading from: ◕ Series - Chapter 1". */
        fun cleanBookTitle(title: String): String =
            title.removePrefix("Continue Reading from:").trim().trimStart('◕', '●', '○', '⭘', '◔', '◑', '◕').trim().ifEmpty { title }

        /** "01" -> "1", "2,5" -> "2.5". */
        fun cleanNumber(raw: String): String {
            val value = raw.replace(',', '.').toDoubleOrNull() ?: return raw
            return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        }

        fun extensionOf(link: OpdsLink): String {
            val type = link.type.orEmpty().lowercase()
            val byType = when {
                "comicbook+zip" in type || "x-cbz" in type -> "cbz"
                "comicbook-rar" in type || "x-cbr" in type -> "cbr"
                "x-cb7" in type || "x-7z" in type -> "cb7"
                "pdf" in type -> "pdf"
                type == "application/zip" -> "zip"
                "rar" in type -> "rar"
                else -> null
            }
            if (byType != null) return byType
            val path = link.href.substringBefore('?').substringBefore('#')
            val fromUrl = path.substringAfterLast('.', "").lowercase()
            return fromUrl.takeIf { it in READABLE } ?: "cbz"
        }

        /** The acquisition link in the best readable format; EPUB and other book formats are ignored. */
        fun pickAcquisition(entry: OpdsEntry): OpdsLink? =
            entry.acquisitions
                .filter { link ->
                    val type = link.type.orEmpty().lowercase()
                    NOT_COMICS.none { it in type }
                }
                .minByOrNull { READABLE.indexOf(extensionOf(it)) }

        private val NOT_COMICS = listOf("epub", "mobi", "kindle", "fb2", "djvu", "html", "atom", "audio", "text/")

        /** Fills an OpenSearch / OPDS 2.0 URL template with [query] and drops the optional parameters. */
        fun expandSearch(template: String, query: String): String {
            val encoded = urlEncode(query)
            var url = template
            // OPDS 2.0 / RFC 6570: {?query} or {?query,author}
            val form = Regex("""\{\?([^}]*)\}""").find(url)
            if (form != null) {
                val names = form.groupValues[1].split(',').map { it.trim() }
                val name = names.firstOrNull { it.equals("query", true) || it.equals("q", true) || it.equals("searchTerms", true) }
                    ?: names.firstOrNull().orEmpty()
                val sep = if ('?' in url.substringBefore(form.value)) "&" else "?"
                url = url.replace(form.value, if (name.isEmpty()) "" else "$sep$name=$encoded")
            }
            // OpenSearch 1.1: {searchTerms}; {startPage?}, {count?} and friends are optional.
            url = url.replace("{searchTerms}", encoded).replace("{searchTerms?}", encoded)
            url = url.replace(Regex("""\{startPage\??\}"""), "1")
            url = url.replace(Regex("""\{[^}]*\?\}"""), "")
            return url
        }

        /** The Atom search `Url` template of an OpenSearch description document. */
        fun parseOpenSearchDescription(body: String, base: String): String? {
            val doc = org.jsoup.Jsoup.parse(body, base, org.jsoup.parser.Parser.xmlParser())
            val urls = doc.getAllElements().filter { it.tagName().substringAfter(':') == "Url" }
            val atom = urls.firstOrNull { it.attr("type").contains("atom+xml") } ?: urls.firstOrNull()
            return atom?.attr("template")?.takeIf { it.isNotEmpty() }?.let { OpdsParser.absolute(base, it) }
        }
    }
}
