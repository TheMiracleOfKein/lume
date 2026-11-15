package com.lume.app.util

/**
 * The small part of Markdown that release notes use: headings, bullet lists (nested by indent),
 * paragraphs, **bold**, *italic*, `code` and [links](url). Anything else stays as plain text.
 */
sealed class MdBlock {
    abstract val spans: List<MdSpan>

    data class Heading(val level: Int, override val spans: List<MdSpan>) : MdBlock()

    /** [depth] 0 for a top-level item, 1 for an item under it, and so on. */
    data class Bullet(val depth: Int, override val spans: List<MdSpan>) : MdBlock()

    data class Paragraph(override val spans: List<MdSpan>) : MdBlock()
}

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)(?:[-*+]|\d+[.)])\s+(.*)$""")

fun parseMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(parseInline(paragraph.toString().trim()))
        paragraph.clear()
    }
    for (raw in text.replace("\r\n", "\n").lines()) {
        val line = raw.trimEnd()
        val heading = HEADING.matchEntire(line.trimStart())
        val bullet = BULLET.matchEntire(line)
        when {
            line.isBlank() -> flush()
            heading != null -> {
                flush()
                blocks += MdBlock.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2].trim()))
            }
            bullet != null -> {
                flush()
                val indent = bullet.groupValues[1].replace("\t", "  ").length
                blocks += MdBlock.Bullet(indent / 2, parseInline(bullet.groupValues[2].trim()))
            }
            // A wrapped line of a list item continues that item.
            paragraph.isEmpty() && raw.startsWith(" ") && blocks.lastOrNull() is MdBlock.Bullet -> {
                val last = blocks.removeAt(blocks.lastIndex) as MdBlock.Bullet
                blocks += last.copy(spans = last.spans + MdSpan(" ") + parseInline(line.trim()))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flush()
    return blocks
}

/** Inline marks of one line or paragraph. Unclosed marks are kept as the characters they are. */
fun parseInline(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    var bold = false
    var italic = false
    var i = 0
    fun emit() {
        if (plain.isNotEmpty()) spans += MdSpan(plain.toString(), bold = bold, italic = italic)
        plain.clear()
    }
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length -> {
                plain.append(text[i + 1])
                i += 2
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { plain.append(c); i++; continue }
                emit()
                spans += MdSpan(text.substring(i + 1, end), bold = bold, italic = italic, code = true)
                i = end + 1
            }
            c == '[' -> {
                val close = text.indexOf("](", i + 1)
                val end = if (close < 0) -1 else text.indexOf(')', close + 2)
                if (close < 0 || end < 0) { plain.append(c); i++; continue }
                emit()
                spans += MdSpan(text.substring(i + 1, close), bold = bold, italic = italic, url = text.substring(close + 2, end))
                i = end + 1
            }
            (c == '*' || c == '_') && text.startsWith("$c$c", i) -> {
                if (!bold && text.indexOf("$c$c", i + 2) < 0) { plain.append("$c$c"); i += 2; continue }
                emit()
                bold = !bold
                i += 2
            }
            (c == '*' || c == '_') && (italic || isItalicOpen(text, i)) -> {
                emit()
                italic = !italic
                i++
            }
            else -> {
                plain.append(c)
                i++
            }
        }
    }
    emit()
    return spans
}

/** A single `*` or `_` opens italics only before a non-space with a closing mark later on, and never inside a word for `_`. */
private fun isItalicOpen(text: String, i: Int): Boolean {
    val c = text[i]
    val next = text.getOrNull(i + 1) ?: return false
    if (next.isWhitespace()) return false
    if (c == '_' && text.getOrNull(i - 1)?.isLetterOrDigit() == true) return false
    return text.indexOf(c, i + 1) > i + 1
}
