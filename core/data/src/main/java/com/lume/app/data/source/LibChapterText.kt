package com.lume.app.data.source

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup

/**
 * The text of a RanobeLib chapter (`/manga/<slug>/chapter` with `Site-Id: 3`) as HTML for the
 * text reader. `data.content` comes in two shapes:
 * - a rich-text document (`{"type":"doc","content":[…]}`, paragraphs of text nodes with
 *   bold/italic marks), the editor format of newer chapters;
 * - an HTML string, in older chapters.
 *
 * Images point into `data.attachments` (by name, file name or id); relative URLs are resolved
 * against the site. Returns null when the answer has no text (a locked or removed chapter).
 */
object LibChapterText {
    fun toHtml(data: JsonObject, siteUrl: String): String? {
        val attachments = attachmentUrls(data["attachments"].arr(), siteUrl)
        val html = when (val content = data["content"]) {
            is JsonObject -> buildString { node(content, attachments, siteUrl) }
            is JsonPrimitive -> content.str()?.let { htmlString(it, siteUrl) }
            is JsonArray -> buildString { content.forEach { node(it, attachments, siteUrl) } }
            else -> null
        }
        return html?.takeIf { Jsoup.parse(it).text().isNotBlank() || "<img" in it }
    }

    /** Attachment key (name, file name, id) → absolute URL. */
    private fun attachmentUrls(list: JsonArray?, siteUrl: String): Map<String, String> {
        val map = HashMap<String, String>()
        list.orEmpty().forEach { el ->
            val item = el.obj() ?: return@forEach
            val url = item.str("url")?.let { absolute(it, siteUrl) } ?: return@forEach
            listOf("name", "filename", "id").mapNotNull { item.str(it) }.forEach { key ->
                map.putIfAbsent(key, url)
                map.putIfAbsent(key.substringBeforeLast('.'), url)
            }
        }
        return map
    }

    private fun StringBuilder.node(el: JsonElement, attachments: Map<String, String>, siteUrl: String) {
        val node = el.obj() ?: return
        val children = node["content"].arr().orEmpty()
        fun inner() = children.forEach { node(it, attachments, siteUrl) }
        fun wrap(tag: String) {
            append('<').append(tag).append('>')
            inner()
            append("</").append(tag).append('>')
        }
        when (node.str("type")) {
            "doc" -> inner()
            "paragraph" -> wrap("p")
            "heading" -> wrap("h" + (node["attrs"].obj().str("level")?.toIntOrNull()?.coerceIn(1, 6) ?: 3))
            "text" -> text(node)
            "hardBreak" -> append("<br>")
            "horizontalRule" -> append("<hr>")
            "blockquote" -> wrap("blockquote")
            "bulletList" -> wrap("ul")
            "orderedList" -> wrap("ol")
            "listItem" -> wrap("li")
            "image" -> images(node, attachments, siteUrl)
            // Unknown blocks keep their text.
            else -> inner()
        }
    }

    private fun StringBuilder.text(node: JsonObject) {
        // Not str(): a text node of a single space between two marked runs must stay.
        val text = (node["text"] as? JsonPrimitive)?.content ?: return
        val tags = node["marks"].arr().orEmpty().mapNotNull { mark ->
            when (mark.obj().str("type")) {
                "bold" -> "b"
                "italic" -> "i"
                "underline" -> "u"
                "strike" -> "s"
                else -> null
            }
        }
        tags.forEach { append('<').append(it).append('>') }
        append(escape(text))
        tags.asReversed().forEach { append("</").append(it).append('>') }
    }

    private fun StringBuilder.images(node: JsonObject, attachments: Map<String, String>, siteUrl: String) {
        val attrs = node["attrs"].obj()
        val keys = attrs?.get("images").arr().orEmpty().mapNotNull { it.obj().str("image") ?: it.str() } +
            listOfNotNull(attrs.str("image"))
        val urls = keys.mapNotNull { attachments[it] ?: attachments[it.substringBeforeLast('.')] } +
            listOfNotNull(attrs.str("src")?.let { absolute(it, siteUrl) })
        urls.distinct().forEach { append("<p><img src=\"").append(escape(it)).append("\"></p>") }
    }

    /** An HTML chapter with its image links made absolute. */
    private fun htmlString(html: String, siteUrl: String): String {
        val doc = Jsoup.parseBodyFragment(html, siteUrl.trimEnd('/') + "/")
        doc.select("img[src]").forEach { it.attr("src", it.absUrl("src").ifEmpty { it.attr("src") }) }
        doc.select("script, style").remove()
        return doc.body().html()
    }

    private fun absolute(url: String, siteUrl: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        else -> siteUrl.trimEnd('/') + "/" + url.trimStart('/')
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}
