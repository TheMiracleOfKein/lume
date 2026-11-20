package com.lume.app.data.novel

import java.text.BreakIterator
import java.util.Locale

/**
 * One piece of a chapter that read aloud speaks at a time, usually a sentence.
 *
 * [block] is the block's index in the chapter ([SpeechScript.TITLE] for the chapter's title row),
 * and [start] until [end] are character offsets in that block's [plainText], the text the reader
 * draws, so the reader can highlight it. A [pause] is a scene break: silence, nothing to show.
 */
data class Utterance(
    val block: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val pause: Boolean = false,
)

/** Turns a chapter's blocks into the utterances read aloud speaks, in order. */
object SpeechScript {
    /** [Utterance.block] of the chapter's title row (the reader's header). */
    const val TITLE = -1

    /** Shorter sentences are joined to the next one, so the voice doesn't stop after every "Yes.". */
    const val MIN_LENGTH = 20

    /** Longer sentences are cut at a comma or a space; engines refuse very long input. */
    const val MAX_LENGTH = 1000

    /**
     * The utterances of a chapter: [title] first (null when the text opens with its own title),
     * then every heading and paragraph sentence by sentence. Scene breaks are pauses; pictures
     * are skipped.
     */
    fun build(
        title: String?,
        blocks: List<NovelBlock>,
        locale: Locale = Locale.getDefault(),
        maxLength: Int = MAX_LENGTH,
    ): List<Utterance> {
        val out = ArrayList<Utterance>()
        if (!title.isNullOrBlank()) {
            sentences(title, locale, maxLength).forEach { r -> out += utterance(TITLE, title, r) }
        }
        blocks.forEachIndexed { index, block ->
            when (block) {
                is NovelBlock.Paragraph -> addText(out, index, block.spans.plainText, locale, maxLength)
                is NovelBlock.Heading -> addText(out, index, block.spans.plainText, locale, maxLength)
                NovelBlock.Divider -> out += Utterance(index, 0, 0, "", pause = true)
                is NovelBlock.Image -> Unit
            }
        }
        return out
    }

    private fun addText(out: MutableList<Utterance>, index: Int, text: String, locale: Locale, maxLength: Int) {
        sentences(text, locale, maxLength).forEach { r -> out += utterance(index, text, r) }
    }

    private fun utterance(block: Int, text: String, r: IntRange) =
        Utterance(block, r.first, r.last + 1, text.substring(r.first, r.last + 1).replace('\n', ' '))

    /**
     * The sentences of [text] as ranges (inclusive), without surrounding whitespace. Pieces with no
     * letter or digit are joined to their neighbour, short ones to the next, long ones are cut.
     */
    fun sentences(text: String, locale: Locale = Locale.getDefault(), maxLength: Int = MAX_LENGTH): List<IntRange> {
        val raw = ArrayList<IntRange>()
        val it = BreakIterator.getSentenceInstance(locale)
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            trim(text, start, end)?.let { raw += it }
            start = end
            end = it.next()
        }

        // Join pieces that can't stand alone ("…", "—", short replies) to the next one.
        val joined = ArrayList<IntRange>()
        var pending: IntRange? = null
        for (r in raw) {
            val cur = pending?.let { it.first..r.last } ?: r
            val tooShort = cur.last - cur.first + 1 < MIN_LENGTH || !hasWord(text, cur)
            if (tooShort && cur.last - cur.first + 1 < maxLength) {
                pending = cur
            } else {
                joined += cur
                pending = null
            }
        }
        if (pending != null) {
            // Nothing after it: a lone mark goes onto the sentence before; a short sentence stays.
            val last = joined.lastOrNull()
            if (!hasWord(text, pending) && last != null) {
                joined[joined.lastIndex] = last.first..pending.last
            } else if (hasWord(text, pending)) {
                joined += pending
            }
        }
        return joined.flatMap { split(text, it, maxLength) }
    }

    private fun trim(text: String, start: Int, end: Int): IntRange? {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return if (s < e) s until e else null
    }

    private fun hasWord(text: String, r: IntRange): Boolean = (r.first..r.last).any { text[it].isLetterOrDigit() }

    /** [r] cut into pieces of at most [maxLength] chars: after a comma-like mark, else at a space. */
    private fun split(text: String, r: IntRange, maxLength: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = r.first
        while (r.last - start + 1 > maxLength) {
            val limit = start + maxLength // exclusive
            var cut = (limit - 1 downTo start + 1).firstOrNull { text[it - 1] in ",;:—–" && text[it].isWhitespace() }
            if (cut == null) cut = (limit - 1 downTo start + 1).firstOrNull { text[it].isWhitespace() }
            if (cut == null) cut = limit
            trim(text, start, cut)?.let { out += it }
            start = cut
            while (start <= r.last && text[start].isWhitespace()) start++
        }
        trim(text, start, r.last + 1)?.let { out += it }
        return out
    }
}
