package com.lume.app.data.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A run of text with one style. Plain data, so the reader maps it to its own text type. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/** One block of a chapter, in reading order. */
sealed interface NovelBlock {
    /** [level] is 1 (largest) to 6. */
    data class Heading(val level: Int, val spans: List<Span>) : NovelBlock

    data class Paragraph(val spans: List<Span>) : NovelBlock

    /** [src] is an absolute URL or a `file://` URI when the chapter had a base, else as written. */
    data class Image(val src: String) : NovelBlock

    data object Divider : NovelBlock
}

/** The plain text of a block's spans. */
val List<Span>.plainText: String get() = joinToString("") { it.text }

/**
 * Turns a chapter's HTML into blocks. Scripts, styles and empty paragraphs are dropped, links
 * keep their text only, whitespace is collapsed, and `<br><br>` starts a new paragraph.
 */
object NovelBlocks {
    fun parse(html: String, baseHref: String? = null): List<NovelBlock> {
        val doc = Jsoup.parse(html, baseHref.orEmpty())
        val body = doc.body()
        val reader = BlockReader(baseHref)
        reader.children(body, bold = false, italic = false)
        reader.flush()
        return reader.blocks
    }

    private val skipped = setOf("script", "style", "head", "noscript", "template", "title", "rt", "rp", "audio", "video", "iframe", "button", "form", "select")
    private val blocks = setOf(
        "p", "div", "li", "ul", "ol", "blockquote", "section", "article", "aside", "header", "footer",
        "nav", "main", "figure", "figcaption", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
        "pre", "dl", "dt", "dd", "center", "address", "details", "summary", "body",
    )
    private val bold = setOf("b", "strong")
    private val italic = setOf("i", "em", "cite", "dfn", "var")
    private val WHITESPACE = Regex("""[\s ]+""")
    private val dividerChars = "*-–—_~#=•·  "

    private class BlockReader(private val baseHref: String?) {
        val blocks = mutableListOf<NovelBlock>()
        private val spans = mutableListOf<Span>()
        private var heading = 0
        private var breaks = 0

        fun children(parent: Element, bold: Boolean, italic: Boolean) {
            for (child in parent.childNodes()) node(child, bold, italic)
        }

        private fun node(node: Node, bold: Boolean, italic: Boolean) {
            when (node) {
                is TextNode -> {
                    val text = node.wholeText
                    if (text.isNotBlank()) breaks = 0
                    append(text, bold, italic)
                }
                is Element -> element(node, bold, italic)
                else -> Unit
            }
        }

        private fun element(el: Element, bold: Boolean, italic: Boolean) {
            val name = el.normalName()
            when {
                name in skipped -> Unit
                name == "br" -> {
                    breaks++
                    if (breaks >= 2) {
                        flush()
                        breaks = 0
                    } else {
                        add("\n", bold, italic)
                    }
                }
                name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' -> {
                    flush()
                    heading = name[1] - '0'
                    children(el, bold = true, italic = italic)
                    flush()
                }
                name == "hr" -> {
                    flush()
                    blocks += NovelBlock.Divider
                }
                name == "img" || name == "image" -> {
                    flush()
                    imageSrc(el)?.let { blocks += NovelBlock.Image(it) }
                }
                name in NovelBlocks.blocks -> {
                    flush()
                    children(el, bold, italic)
                    flush()
                }
                else -> children(
                    el,
                    bold = bold || name in NovelBlocks.bold,
                    italic = italic || name in NovelBlocks.italic,
                )
            }
        }

        private fun imageSrc(el: Element): String? {
            val key = listOf("src", "xlink:href", "href").firstOrNull { el.attr(it).isNotBlank() } ?: return null
            val raw = el.attr(key).trim()
            if (baseHref.isNullOrEmpty()) return raw
            return el.absUrl(key).ifEmpty { raw }
        }

        private fun append(raw: String, bold: Boolean, italic: Boolean) = add(raw.replace(WHITESPACE, " "), bold, italic)

        private fun add(normalized: String, bold: Boolean, italic: Boolean) {
            var text = normalized
            if (text.isEmpty()) return
            val last = spans.lastOrNull()?.text
            // No leading space at the start of a line or after another space.
            if (text.startsWith(' ') && (last == null || last.endsWith(' ') || last.endsWith('\n'))) text = text.trimStart(' ')
            if (text.isEmpty()) return
            if (text == "\n" && last != null && last.endsWith(' ')) {
                spans[spans.lastIndex] = spans.last().copy(text = last.trimEnd(' '))
            }
            val tail = spans.lastOrNull()
            if (tail != null && tail.bold == bold && tail.italic == italic) {
                spans[spans.lastIndex] = tail.copy(text = tail.text + text)
            } else {
                spans += Span(text, bold, italic)
            }
        }

        fun flush() {
            breaks = 0
            val level = heading
            heading = 0
            val clean = trimmed(spans)
            spans.clear()
            if (clean.isEmpty()) return
            if (level > 0) {
                blocks += NovelBlock.Heading(level, clean)
            } else if (isDivider(clean.plainText)) {
                blocks += NovelBlock.Divider
            } else {
                blocks += NovelBlock.Paragraph(clean)
            }
        }

        private fun trimmed(list: List<Span>): List<Span> {
            val out = list.toMutableList()
            while (out.isNotEmpty() && out.first().text.isBlank()) out.removeAt(0)
            while (out.isNotEmpty() && out.last().text.isBlank()) out.removeAt(out.lastIndex)
            if (out.isEmpty()) return out
            out[0] = out[0].copy(text = out[0].text.trimStart())
            out[out.lastIndex] = out.last().copy(text = out.last().text.trimEnd())
            return out.filter { it.text.isNotEmpty() }
        }

        /** A line of asterisks or dashes ("* * *", "---") is a scene break. */
        private fun isDivider(text: String): Boolean =
            text.all { it in NovelBlocks.dividerChars } && text.count { !it.isWhitespace() && it != ' ' } >= 3
    }
}
