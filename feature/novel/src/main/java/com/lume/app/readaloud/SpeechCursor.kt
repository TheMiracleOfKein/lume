package com.lume.app.readaloud

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.SpeechScript
import com.lume.app.data.novel.Utterance
import com.lume.app.data.novel.plainText
import com.lume.app.ui.screens.NovelAnchor

/** The length of a block's drawn text; 0 for pictures and scene breaks. */
internal fun NovelBlock.textLength(): Int = when (this) {
    is NovelBlock.Paragraph -> spans.plainText.length
    is NovelBlock.Heading -> spans.plainText.length
    else -> 0
}

/**
 * Where to start speaking for a reader position: the sentence at [fraction] of the anchor's
 * block, so reading starts at the text on screen. The top of a chapter starts at its title.
 */
internal fun utteranceAt(script: List<Utterance>, blocks: List<NovelBlock>, block: Int, fraction: Float): Int {
    if (script.isEmpty()) return 0
    if (block <= 0 && fraction <= 0f) return 0
    val length = blocks.getOrNull(block)?.textLength() ?: 0
    val target = (fraction.coerceIn(0f, 1f) * length).toInt()
    val inBlock = script.indexOfFirst { it.block == block && !it.pause && it.end > target }
    if (inBlock >= 0) return inBlock
    val after = script.indexOfFirst { it.block > block && !it.pause }
    return if (after >= 0) after else script.lastIndex
}

/** The reader position of an utterance: its block, and how far into the block's text it starts. */
internal fun anchorOf(chapterId: String, u: Utterance, blocks: List<NovelBlock>): NovelAnchor {
    if (u.block == SpeechScript.TITLE) return NovelAnchor(chapterId, 0, 0f)
    val length = blocks.getOrNull(u.block)?.textLength() ?: 0
    val fraction = if (length <= 0) 0f else (u.start.toFloat() / length).coerceIn(0f, 1f)
    return NovelAnchor(chapterId, u.block, fraction)
}

/** The first sentence of the next paragraph after [index], or -1 at the end of the chapter. */
internal fun nextParagraph(script: List<Utterance>, index: Int): Int {
    val block = script.getOrNull(index)?.block ?: return -1
    return ((index + 1) until script.size).firstOrNull { script[it].block != block && !script[it].pause } ?: -1
}

/**
 * Back one paragraph: to the start of this one when the voice is past its first sentence, else
 * to the start of the one before. Stays at 0 at the top of the chapter.
 */
internal fun previousParagraph(script: List<Utterance>, index: Int): Int {
    if (index <= 0 || index >= script.size) return 0
    val start = paragraphStart(script, index)
    if (start < index) return start
    val before = (index - 1 downTo 0).firstOrNull { !script[it].pause } ?: return 0
    return paragraphStart(script, before)
}

private fun paragraphStart(script: List<Utterance>, index: Int): Int {
    var i = index
    while (i > 0 && script[i - 1].block == script[index].block) i--
    return i
}
