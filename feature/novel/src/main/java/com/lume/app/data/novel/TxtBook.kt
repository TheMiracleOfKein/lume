package com.lume.app.data.novel

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** One chapter of a text file: a name and its paragraphs. */
class TxtChapter(val title: String, val paragraphs: List<String>) {
    /** The paragraphs as HTML, one `<p>` each, with the text escaped. */
    fun html(): String = paragraphs.joinToString("\n") { "<p>${escape(it)}</p>" }
}

/**
 * A plain text file split into chapters. Chapters start at heading lines ("Chapter 12",
 * "Глава 12", "Prologue", "第12章", ...); a file without headings is cut into parts of about
 * [PART_SIZE] characters at paragraph breaks.
 */
class TxtBook(val title: String, val chapters: List<TxtChapter>) {
    companion object {
        /** Target size of a part when the file has no chapter headings. */
        const val PART_SIZE = 20_000

        fun open(file: File): TxtBook = parse(decode(file.readBytes()), file.nameWithoutExtension)

        /** UTF-8 (with or without a BOM), UTF-16 with a BOM, else windows-1251 for Cyrillic text, else ISO-8859-1. */
        fun decode(bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            strict(bytes, Charsets.UTF_8)?.let { return it }
            strict(bytes, Charset.forName("windows-1251"))?.let { cyrillic ->
                val letters = cyrillic.count { it.isLetter() }
                if (letters > 0 && cyrillic.count { it in 'Ѐ'..'ӿ' } * 2 >= letters) return cyrillic
            }
            return String(bytes, Charsets.ISO_8859_1)
        }

        private fun strict(bytes: ByteArray, charset: Charset): String? = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }

        fun parse(text: String, fallbackTitle: String): TxtBook {
            val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            val headings = lines.indices.filter { isHeading(lines[it]) }
            // Paragraphs follow blank lines, or single newlines when the file has no blank lines.
            val blankSeparated = lines.any { it.isBlank() }

            if (headings.isEmpty()) {
                return TxtBook(fallbackTitle, parts(paragraphs(lines, blankSeparated)))
            }
            val chapters = mutableListOf<TxtChapter>()
            val front = paragraphs(lines.subList(0, headings.first()), blankSeparated)
            if (front.isNotEmpty()) chapters += TxtChapter(fallbackTitle, front)
            for ((i, start) in headings.withIndex()) {
                val end = headings.getOrNull(i + 1) ?: lines.size
                chapters += TxtChapter(lines[start].trim(), paragraphs(lines.subList(start + 1, end), blankSeparated))
            }
            return TxtBook(fallbackTitle, chapters)
        }

        private fun paragraphs(lines: List<String>, blankSeparated: Boolean): List<String> {
            val out = mutableListOf<String>()
            if (!blankSeparated) {
                lines.mapTo(out) { it.trim() }.removeAll { it.isEmpty() }
                return out
            }
            val current = StringBuilder()
            fun end() {
                if (current.isNotEmpty()) out += current.toString()
                current.setLength(0)
            }
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    end()
                } else {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(trimmed)
                }
            }
            end()
            return out
        }

        /** Chapters of about [PART_SIZE] characters named "Part N". */
        private fun parts(paragraphs: List<String>): List<TxtChapter> {
            if (paragraphs.isEmpty()) return emptyList()
            val out = mutableListOf<TxtChapter>()
            var current = mutableListOf<String>()
            var size = 0
            for (paragraph in paragraphs) {
                current += paragraph
                size += paragraph.length
                if (size >= PART_SIZE) {
                    out += TxtChapter("Part ${out.size + 1}", current)
                    current = mutableListOf()
                    size = 0
                }
            }
            if (current.isNotEmpty()) out += TxtChapter("Part ${out.size + 1}", current)
            return out
        }

        private const val KEYWORD = """(?:chapter|ch\.|глава|гл\.|part|часть|book|книга|volume|vol\.|том)"""
        private const val DIGITS = """(?:\d+|(?-i:[IVXLCDM]+))"""
        private const val WORDS = """(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|первая|вторая|третья|четвёртая|четвертая|пятая|шестая|седьмая|восьмая|девятая|десятая|первый|второй|третий|четвёртый|четвертый|пятый|шестой|седьмой|восьмой|девятый|десятый)"""
        private const val TAIL = """\s*[:.\-–—)]\s*.{0,60}"""

        // A numeral may be followed by a title ("Chapter 12 The Return"); a number word only
        // with punctuation ("Chapter One: The Return"), so "Part one of the plan" is not a heading.
        private val numbered = Regex("""^$KEYWORD\s+$DIGITS(?:$TAIL|\s+.{0,60})?$""", RegexOption.IGNORE_CASE)
        private val numberWord = Regex("""^$KEYWORD\s+$WORDS(?:$TAIL)?$""", RegexOption.IGNORE_CASE)
        private val named = Regex(
            """^(?:prologue|prolog|epilogue|interlude|afterword|foreword|preface|introduction|пролог|эпилог|интерлюдия|предисловие|послесловие|введение|заключение)(?:\s*[:.\-–—]\s*.{0,50})?$""",
            RegexOption.IGNORE_CASE,
        )
        private val chinese = Regex("""^第\s*[0-9零一二三四五六七八九十百千万〇两]+\s*[章节節回卷部集篇话話].{0,40}$""")

        /** A whole short line only, so "Chapter" inside a sentence is not a heading. */
        internal fun isHeading(line: String): Boolean {
            val text = line.trim().trimEnd('.', ':')
            if (text.isEmpty() || text.length > 80) return false
            return chinese.matches(text) || named.matches(text) || numbered.matches(text) || numberWord.matches(text)
        }
    }
}

internal fun escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
