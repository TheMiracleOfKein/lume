package com.lume.app.data.source

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.net.URI

/* Small helpers shared by the catalogue engines. */

/** Non-blank primitive content; JSON `null` and the literal string "null" become Kotlin null. */
internal fun JsonElement?.str(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

internal fun JsonObject?.str(key: String): String? = this?.get(key).str()

internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

/**
 * Converts an HTML fragment (API descriptions are often `<p>…</p>` with entities) into plain text,
 * keeping paragraph and line breaks.
 */
internal fun htmlToText(html: String?): String? {
    if (html.isNullOrBlank()) return null
    if ('<' !in html && '&' !in html) return html.trim()
    val body = Jsoup.parseBodyFragment(html).body()
    // wholeText() already renders <br> as a newline; only paragraph breaks need adding.
    body.select("p, div, li, h1, h2, h3, h4").forEach { it.after(TextNode("\n\n")) }
    return body.wholeText()
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .ifEmpty { null }
}

/** Percent-encode one URL path segment (slugs may contain `<`, `>`, spaces, Cyrillic, …). */
internal fun pathSegment(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** Resolve [pathOrUrl] against [base]; absolute URLs pass through unchanged. */
internal fun resolveUrl(base: String, pathOrUrl: String): URI {
    val trimmed = pathOrUrl.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return URI(trimmed)
    if (trimmed.startsWith("//")) return URI("https:$trimmed")
    return URI(base.trimEnd('/') + "/").resolve(trimmed.trimStart('/'))
}

/** Append a query fragment to a URL that may or may not already have one. */
internal fun withQuery(url: String, query: String): String {
    if (query.isBlank()) return url
    val q = query.removePrefix("?").removePrefix("&")
    return if ('?' in url) "$url&$q" else "$url?$q"
}

internal fun chapterNumberFrom(text: String): String? =
    Regex("""(?:chapter|ch\.?|episode|ep\.?|глава|гл\.?)\s*([0-9]+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.getOrNull(1)?.replace(',', '.')
