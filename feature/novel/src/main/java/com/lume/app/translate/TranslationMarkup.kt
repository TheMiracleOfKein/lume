package com.lume.app.translate

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A glossary line: [source] as written in the original, [target] as it must read in the translation. */
data class GlossaryEntry(val source: String, val target: String)

/**
 * Turns text blocks into the HTML the engine translates and back. Bold and italic travel as
 * `<b>`/`<i>`; each glossary match travels as `<span data-g="N">…</span>`, which the engine moves
 * onto the translated words. Afterwards the span's content is replaced by the glossary target,
 * so a name reads the same in every chapter however the model would have spelled it.
 */
object TranslationMarkup {
    /** The block as engine input, or null for blocks that aren't translated (images, dividers, blank text). */
    fun toHtml(block: NovelBlock, glossary: List<GlossaryEntry>): String? {
        val spans = when (block) {
            is NovelBlock.Paragraph -> block.spans
            is NovelBlock.Heading -> block.spans
            else -> return null
        }
        if (spans.all { it.text.isBlank() }) return null
        val matcher = GlossaryMatcher(glossary)
        return buildString {
            for (span in spans) {
                if (span.bold) append("<b>")
                if (span.italic) append("<i>")
                matcher.mark(span.text, this)
                if (span.italic) append("</i>")
                if (span.bold) append("</b>")
            }
        }
    }

    /** The translated [html] as a block of the same kind as [original]. */
    fun fromHtml(original: NovelBlock, html: String, glossary: List<GlossaryEntry>): NovelBlock {
        val spans = spans(html, glossary)
        return when (original) {
            is NovelBlock.Heading -> original.copy(spans = spans)
            is NovelBlock.Paragraph -> original.copy(spans = spans)
            else -> original
        }
    }

    internal fun spans(html: String, glossary: List<GlossaryEntry>): List<Span> {
        val body = Jsoup.parseBodyFragment(html).body()
        val out = mutableListOf<Span>()
        fun add(text: String, bold: Boolean, italic: Boolean) {
            if (text.isEmpty()) return
            val last = out.lastOrNull()
            if (last != null && last.bold == bold && last.italic == italic) out[out.size - 1] = last.copy(text = last.text + text)
            else out += Span(text, bold, italic)
        }
        fun walk(node: Node, bold: Boolean, italic: Boolean) {
            when (node) {
                is TextNode -> add(node.wholeText, bold, italic)
                is Element -> {
                    val index = node.attr("data-g").toIntOrNull()
                    val term = index?.let(glossary::getOrNull)
                    if (term != null) {
                        add(term.target, bold, italic)
                        return
                    }
                    val b = bold || node.normalName() in BOLD
                    val i = italic || node.normalName() in ITALIC
                    for (child in node.childNodes()) walk(child, b, i)
                }
                else -> Unit
            }
        }
        for (child in body.childNodes()) walk(child, bold = false, italic = false)
        return out.ifEmpty { listOf(Span("")) }
    }

    /**
     * Engine output with each `data-g` number replaced by its term's source (`data-gs`), for a cache
     * that outlives glossary changes: new terms shift the numbers, the sources stay.
     */
    fun marksToSources(html: String, glossary: List<GlossaryEntry>): String {
        val body = fragment(html)
        for (span in body.select("span[data-g]")) {
            val term = span.attr("data-g").toIntOrNull()?.let(glossary::getOrNull)
            span.removeAttr("data-g")
            if (term != null) span.attr("data-gs", term.source.trim()) else span.unwrap()
        }
        return body.html()
    }

    /** The reverse of [marksToSources] for the current [glossary]; a term no longer there keeps the engine's words. */
    fun sourcesToMarks(html: String, glossary: List<GlossaryEntry>): String {
        val index = HashMap<String, Int>()
        glossary.forEachIndexed { i, e -> index.putIfAbsent(e.source.trim().lowercase(), i) }
        val body = fragment(html)
        for (span in body.select("span[data-gs]")) {
            val i = index[span.attr("data-gs").lowercase()]
            span.removeAttr("data-gs")
            if (i != null) span.attr("data-g", i.toString()) else span.unwrap()
        }
        return body.html()
    }

    private fun fragment(html: String): Element =
        Jsoup.parseBodyFragment(html).also { it.outputSettings().prettyPrint(false) }.body()

    private val BOLD = setOf("b", "strong")
    private val ITALIC = setOf("i", "em")
}

/**
 * Finds glossary terms in text: whole words only, case-insensitive, longest term first, never
 * overlapping. Appends the HTML-escaped text with each match wrapped in its numbered span.
 */
internal class GlossaryMatcher(glossary: List<GlossaryEntry>) {
    private val terms = glossary.withIndex()
        .filter { it.value.source.isNotBlank() }
        .sortedByDescending { it.value.source.trim().length }
    private val regex: Regex? = terms.takeIf { it.isNotEmpty() }?.let { list ->
        Regex(
            list.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(?:", postfix = ")(?![\\p{L}\\p{N}])") { Regex.escape(it.value.source.trim()) },
            setOf(RegexOption.IGNORE_CASE),
        )
    }
    private val bySource = terms.associate { it.value.source.trim().lowercase() to it.index }

    fun mark(text: String, out: StringBuilder) {
        val r = regex
        if (r == null) {
            out.escape(text)
            return
        }
        var at = 0
        for (m in r.findAll(text)) {
            out.escape(text.substring(at, m.range.first))
            val index = bySource[m.value.lowercase()]
            if (index == null) out.escape(m.value)
            else out.append("<span data-g=\"").append(index).append("\">").escape(m.value).append("</span>")
            at = m.range.last + 1
        }
        out.escape(text.substring(at))
    }

    private fun StringBuilder.escape(s: String): StringBuilder {
        for (c in s) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                else -> append(c)
            }
        }
        return this
    }
}
