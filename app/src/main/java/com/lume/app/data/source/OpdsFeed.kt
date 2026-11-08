package com.lume.app.data.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI

/* The OPDS catalogue model and its two parsers (OPDS 1.x Atom and OPDS 2.0 JSON). */

internal const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
internal const val REL_IMAGE = "http://opds-spec.org/image"
internal const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
internal const val REL_STREAM = "http://vaemendis.net/opds-pse/stream"
internal const val REL_NEW = "http://opds-spec.org/sort/new"

/** A link with its address already resolved against the feed it came from. */
internal data class OpdsLink(
    val href: String,
    val type: String? = null,
    val rel: String? = null,
    /** Page count of a page-streaming link (`pse:count` / `numberOfItems`). */
    val count: Int? = null,
    val templated: Boolean = false,
)

internal data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val authors: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val updated: String? = null,
    val cover: String? = null,
    /** The feed this entry opens (a folder or a series), if any. */
    val navUrl: String? = null,
    /** Whether that feed lists books (`kind=acquisition`) rather than more folders. */
    val navIsAcquisition: Boolean = false,
    val acquisitions: List<OpdsLink> = emptyList(),
    val stream: OpdsLink? = null,
) {
    val isBook: Boolean get() = acquisitions.isNotEmpty() || stream != null
}

internal data class OpdsFeed(
    val url: String,
    val title: String? = null,
    val subtitle: String? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val next: String? = null,
    val search: OpdsLink? = null,
    val newest: String? = null,
)

internal object OpdsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Some servers start a feed with a byte-order mark. */
    private val BOM = 0xFEFF.toChar()

    /** Picks the parser from the first character: `{` is OPDS 2.0, anything else Atom. */
    fun parse(body: String, url: String): OpdsFeed {
        val text = body.trimStart { it == BOM || it.isWhitespace() }
        return if (text.startsWith("{")) parseJson(text, url) else parseAtom(text, url)
    }

    // ---- OPDS 1.x (Atom) ----------------------------------------------------------------------

    fun parseAtom(body: String, url: String): OpdsFeed {
        val doc = Jsoup.parse(body, url, Parser.xmlParser())
        val feed = doc.children().firstOrNull { it.local() == "feed" }
            ?: throw com.lume.app.domain.SourceFetchException("OPDS: not a catalogue feed")
        val links = feed.kids("link").map { atomLink(it, url) }
        val entries = feed.kids("entry").mapNotNull { atomEntry(it, url) }
        return OpdsFeed(
            url = url,
            title = feed.kids("title").firstOrNull()?.text()?.trim()?.ifEmpty { null },
            subtitle = feed.kids("subtitle").firstOrNull()?.text()?.trim()?.ifEmpty { null },
            entries = entries,
            next = links.firstOrNull { it.rel == "next" }?.href,
            search = links.firstOrNull { it.rel == "search" },
            newest = links.firstOrNull { it.rel == REL_NEW }?.href,
        )
    }

    private fun atomLink(el: Element, base: String): OpdsLink {
        val raw = el.attr("href")
        val templated = '{' in raw
        return OpdsLink(
            href = absolute(base, raw),
            type = el.attr("type").ifEmpty { null },
            rel = el.attr("rel").ifEmpty { null },
            count = (el.attr("pse:count").ifEmpty { el.attr("count") }).toIntOrNull(),
            templated = templated,
        )
    }

    private fun atomEntry(el: Element, base: String): OpdsEntry? {
        val title = el.kids("title").firstOrNull()?.text()?.trim().orEmpty()
        val links = el.kids("link").map { atomLink(it, base) }.filter { it.href.isNotEmpty() }
        val id = el.kids("id").firstOrNull()?.text()?.trim()?.ifEmpty { null }
            ?: links.firstOrNull()?.href ?: return null
        val acquisitions = links.filter { it.rel?.startsWith(REL_ACQUISITION) == true }
        val stream = links.firstOrNull { it.rel == REL_STREAM }
        val nav = links.firstOrNull { link ->
            link.type?.contains("atom+xml") == true &&
                (link.rel == null || link.rel == "subsection" || link.rel == REL_NEW || link.rel == "http://opds-spec.org/sort/popular" ||
                    link.rel == "http://opds-spec.org/featured" || link.rel == "alternate" || link.rel == "http://opds-spec.org/crawlable")
        } ?: links.firstOrNull { it.rel == "subsection" }
        val cover = links.firstOrNull { it.rel == REL_THUMBNAIL || it.rel == "http://opds-spec.org/thumbnail" }?.href
            ?: links.firstOrNull { it.rel == REL_IMAGE }?.href
        val summary = (el.kids("summary").firstOrNull() ?: el.kids("content").firstOrNull())?.text()
        return OpdsEntry(
            id = id,
            title = title.ifEmpty { id },
            summary = htmlToText(summary),
            authors = el.kids("author").mapNotNull { it.kids("name").firstOrNull()?.text()?.trim()?.ifEmpty { null } },
            categories = el.kids("category").mapNotNull { it.attr("label").ifEmpty { it.attr("term") }.ifEmpty { null } },
            updated = el.kids("updated").firstOrNull()?.text()?.trim()?.ifEmpty { null },
            cover = cover,
            navUrl = nav?.href?.takeIf { !it.contains('{') },
            navIsAcquisition = nav?.type?.contains("kind=acquisition") == true,
            acquisitions = acquisitions,
            stream = stream,
        )
    }

    private fun Element.local(): String = tagName().substringAfter(':')

    private fun Element.kids(name: String): List<Element> = children().filter { it.local() == name }

    // ---- OPDS 2.0 (JSON) ----------------------------------------------------------------------

    fun parseJson(body: String, url: String): OpdsFeed {
        val root = json.parseToJsonElement(body).obj()
            ?: throw com.lume.app.domain.SourceFetchException("OPDS: not a catalogue feed")
        val links = root["links"].arr().orEmpty().mapNotNull { jsonLink(it, url) }
        val entries = ArrayList<OpdsEntry>()
        fun collect(container: JsonObject) {
            container["navigation"].arr().orEmpty().mapNotNull { jsonNavigation(it, url) }.let(entries::addAll)
            container["publications"].arr().orEmpty().mapNotNull { jsonPublication(it, url) }.let(entries::addAll)
        }
        collect(root)
        root["groups"].arr().orEmpty().mapNotNull { it.obj() }.forEach(::collect)
        val meta = root["metadata"].obj()
        return OpdsFeed(
            url = url,
            title = meta.str("title"),
            subtitle = meta.str("subtitle"),
            entries = entries,
            next = links.firstOrNull { it.rel == "next" }?.href,
            search = links.firstOrNull { it.rel == "search" },
            newest = links.firstOrNull { it.rel == "http://opds-spec.org/sort/new" || it.rel == "new" }?.href,
        )
    }

    private fun jsonLink(el: JsonElement, base: String): OpdsLink? {
        val o = el.obj() ?: return null
        val href = o.str("href") ?: return null
        val rel = o["rel"].let { r -> r.str() ?: r.arr()?.firstNotNullOfOrNull { it.str() } }
        return OpdsLink(
            href = absolute(base, href),
            type = o.str("type"),
            rel = rel,
            count = (o["properties"].obj()?.get("numberOfItems") as? JsonPrimitive)?.intOrNull
                ?: (o["properties"].obj()?.get("pse:count") as? JsonPrimitive)?.intOrNull,
            templated = (o["templated"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    private fun jsonNavigation(el: JsonElement, base: String): OpdsEntry? {
        val link = jsonLink(el, base) ?: return null
        val title = el.obj().str("title") ?: return null
        return OpdsEntry(
            id = link.href,
            title = title,
            navUrl = link.href,
            // OPDS 2 feeds of series / books have no kind marker; the engine looks at the content.
            navIsAcquisition = link.type?.contains("kind=acquisition") == true,
        )
    }

    private fun jsonPublication(el: JsonElement, base: String): OpdsEntry? {
        val pub = el.obj() ?: return null
        val meta = pub["metadata"].obj() ?: return null
        val links = pub["links"].arr().orEmpty().mapNotNull { jsonLink(it, base) }
        val images = pub["images"].arr().orEmpty().mapNotNull { jsonLink(it, base) }
        val title = meta.str("title") ?: return null
        val id = meta.str("identifier") ?: links.firstOrNull()?.href ?: title
        fun names(key: String): List<String> = meta[key].let { v ->
            v.arr()?.mapNotNull { it.obj().str("name") ?: it.str() } ?: listOfNotNull(v.obj().str("name") ?: v.str())
        }
        val nav = links.firstOrNull { it.rel == "subsection" || it.rel == "http://opds-spec.org/subsection" }
        return OpdsEntry(
            id = id,
            title = title,
            summary = htmlToText(meta.str("description")),
            authors = names("author"),
            categories = names("subject"),
            updated = meta.str("modified"),
            cover = (images.firstOrNull { it.rel == "thumbnail" } ?: images.firstOrNull())?.href,
            navUrl = nav?.href,
            navIsAcquisition = nav != null,
            acquisitions = links.filter { it.rel?.startsWith(REL_ACQUISITION) == true },
            stream = links.firstOrNull { it.rel == REL_STREAM },
        )
    }

    // ---- Shared -------------------------------------------------------------------------------

    /** Resolves [href] against [base]; keeps `{templates}` intact (they are not valid in a URI). */
    fun absolute(base: String, href: String): String {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return ""
        val safe = trimmed.replace("{", "%7B").replace("}", "%7D")
        val resolved = runCatching { URI(base).resolve(safe).toString() }.getOrNull() ?: return trimmed
        return resolved.replace("%7B", "{").replace("%7D", "}")
    }
}
