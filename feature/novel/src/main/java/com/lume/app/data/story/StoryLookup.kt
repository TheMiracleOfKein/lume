package com.lume.app.data.story

import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterMention
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.domain.Chapter
import com.lume.app.ui.screens.NovelAnchor

/** One block that mentions the term, with the chapter it is in. */
data class StoryMention(val chapter: Chapter, val block: Int, val text: String)

/** How much of the story the search covered: [indexed] of [total] chapters up to the reading position. */
data class StoryCoverage(val indexed: Int, val total: Int)

/**
 * What the reader knows about a term at the current spot. Mentions after the spot are left out,
 * so nothing here spoils. Also the input a later story-insight (summaries, relations) can use.
 */
data class StoryContext(
    val term: String,
    /** The saved codex entry the term belongs to (by name or alias), if any. */
    val codexEntry: CodexEntryWithAliases?,
    /** The translation the glossary gives for the term's original, when it has one. */
    val glossaryTarget: String?,
    /** Every spelling that was searched, for highlighting. */
    val searchTerms: List<String>,
    val firstMention: StoryMention?,
    /** Up to [StoryLookup.RECENT_LIMIT], newest first, all before the reading position. */
    val recentMentions: List<StoryMention>,
    /** Chapters before the position (or the current one) that mention the term. */
    val chapterCount: Int,
    val coverage: StoryCoverage,
)

/** Finds where a term appears in the chapters read so far; see [StoryIndex] for what is searched. */
class StoryLookup(private val app: AppContainer, private val index: StoryIndex) {
    private val dao = app.dao

    /** Chapters of [readingOrder] up to and including [anchor]'s. */
    private fun upTo(readingOrder: List<Chapter>, anchor: NovelAnchor): List<Chapter> {
        val at = readingOrder.indexOfFirst { it.id == anchor.chapterId }
        return if (at < 0) emptyList() else readingOrder.subList(0, at + 1)
    }

    /** Indexes the downloaded chapters up to the position that are not indexed yet; returns how many. */
    suspend fun backfill(sourceId: String, titleId: String, readingOrder: List<Chapter>, anchor: NovelAnchor): Int =
        index.backfillDownloaded(sourceId, titleId, upTo(readingOrder, anchor).map { it.id })

    /**
     * Searches [term] in the indexed chapters of [readingOrder] (oldest first) before [anchor].
     * Chapters that are not in [readingOrder] are skipped.
     */
    suspend fun lookup(
        sourceId: String,
        titleId: String,
        term: String,
        readingOrder: List<Chapter>,
        anchor: NovelAnchor,
        glossary: List<GlossaryTermEntity>,
        codex: List<CodexEntryWithAliases>,
    ): StoryContext {
        val resolved = resolveTerms(term, glossary, codex)
        val read = upTo(readingOrder, anchor)
        val indexed = dao.indexedChapterIds(sourceId, titleId).toSet()
        val coverage = StoryCoverage(read.count { it.id in indexed }, read.size)
        val query = ftsQuery(resolved.searchTerms)
        val empty = StoryContext(term, resolved.codexEntry, resolved.glossaryTarget, resolved.searchTerms, null, emptyList(), 0, coverage)
        if (query == null || read.isEmpty()) return empty

        val visible = visibleMentions(dao.mentionsByChapter(sourceId, titleId, query), readingOrder.map { it.id }, anchor)
        if (visible.isEmpty()) return empty
        val byId = read.associateBy { it.id }

        val first = visible.first().let { m ->
            dao.blockText(sourceId, titleId, m.chapterId, m.firstBlock)?.let { StoryMention(byId.getValue(m.chapterId), m.firstBlock, it) }
        }
        val recent = ArrayList<StoryMention>(RECENT_LIMIT)
        for (m in visible.asReversed()) {
            if (recent.size >= RECENT_LIMIT) break
            val before = if (m.chapterId == anchor.chapterId) anchor.block else Int.MAX_VALUE
            val chapter = byId.getValue(m.chapterId)
            dao.matchingBlocksBefore(sourceId, titleId, m.chapterId, query, before, RECENT_LIMIT - recent.size)
                .mapTo(recent) { StoryMention(chapter, it.block, it.text) }
        }
        return StoryContext(term, resolved.codexEntry, resolved.glossaryTarget, resolved.searchTerms, first, recent, visible.size, coverage)
    }

    /** The paragraphs around a mention, for expanding a snippet: [radius] blocks either side, in order. */
    suspend fun context(sourceId: String, titleId: String, mention: StoryMention, radius: Int = 1) =
        dao.blocksInRange(sourceId, titleId, mention.chapter.id, (mention.block - radius).coerceAtLeast(0), mention.block + radius)

    companion object {
        const val RECENT_LIMIT = 5
    }
}

/** The terms to search for [term], and what the saved data says about it. */
internal class ResolvedTerms(
    val searchTerms: List<String>,
    val codexEntry: CodexEntryWithAliases?,
    val glossaryTarget: String?,
)

/**
 * The text is searched in the original, so a glossary target (the translated name) also searches
 * its source, and a codex entry searches its name and every alias.
 */
internal fun resolveTerms(term: String, glossary: List<GlossaryTermEntity>, codex: List<CodexEntryWithAliases>): ResolvedTerms {
    val t = term.trim()
    val entry = codex.firstOrNull { e -> e.entry.name.equals(t, true) || e.aliases.any { it.alias.equals(t, true) } }
    val terms = LinkedHashSet<String>()
    terms += t
    if (entry != null) {
        terms += entry.entry.name
        entry.aliases.forEach { terms += it.alias }
    }
    var target: String? = null
    for (name in terms.toList()) {
        for (g in glossary) {
            if (g.target.equals(name, true)) terms += g.source
            if ((g.source.equals(name, true) || g.target.equals(name, true)) && target == null) target = g.target
        }
    }
    val distinct = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    return ResolvedTerms(distinct, entry, target)
}

/** Every glossary side and codex name or alias: what a long-press prefers over guessing a word. */
internal fun knownTerms(glossary: List<GlossaryTermEntity>, codex: List<CodexEntryWithAliases>): List<String> =
    buildList {
        glossary.forEach { add(it.source); add(it.target) }
        codex.forEach { e -> add(e.entry.name); e.aliases.forEach { add(it.alias) } }
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/**
 * An FTS4 query matching any of [terms] as a phrase, e.g. `"lin feng" OR "lin-feng"`, or null when
 * none has a letter or digit. Quotes are dropped: FTS4 phrases have no escape for them.
 */
internal fun ftsQuery(terms: List<String>): String? =
    terms.map { it.replace("\"", "").trim() }
        .filter { p -> p.any { it.isLetterOrDigit() } }
        .distinctBy { it.lowercase() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" OR ") { "\"$it\"" }

/**
 * The mentions a reader at [anchor] may see, in reading order. Chapters after the anchor's and
 * chapters not in [readingOrder] are dropped; in the anchor's chapter a mention counts only if its
 * first matching block comes before the anchor block.
 */
internal fun visibleMentions(mentions: List<ChapterMention>, readingOrder: List<String>, anchor: NovelAnchor): List<ChapterMention> {
    val position = HashMap<String, Int>(readingOrder.size)
    readingOrder.forEachIndexed { i, id -> position.putIfAbsent(id, i) }
    val limit = position[anchor.chapterId] ?: return emptyList()
    return mentions
        .filter { m ->
            val p = position[m.chapterId] ?: return@filter false
            p < limit || (p == limit && m.firstBlock < anchor.block)
        }
        .sortedBy { position.getValue(it.chapterId) }
}

private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
    -> true
    else -> false
}

private fun isWordChar(c: Char) = (c.isLetterOrDigit() || c == '_') && !isCjk(c)

private fun isJoiner(c: Char) = c == '-' || c == '\'' || c == '’'

/** The known term that covers [offset] and is longest, matched ignoring case on word edges. */
private fun knownAt(text: String, offset: Int, known: List<String>): IntRange? {
    var best: IntRange? = null
    for (term in known) {
        if (term.isEmpty() || term.length > text.length) continue
        val from = (offset - term.length + 1).coerceAtLeast(0)
        val to = offset.coerceAtMost(text.length - term.length)
        for (start in from..to) {
            if (!text.regionMatches(start, term, 0, term.length, ignoreCase = true)) continue
            val end = start + term.length - 1
            if (start > 0 && isWordChar(text[start - 1]) && isWordChar(term.first())) continue
            if (end + 1 < text.length && isWordChar(text[end + 1]) && isWordChar(term.last())) continue
            if (best == null || term.length > best.last - best.first + 1) best = start..end
        }
    }
    return best
}

/**
 * The term the character at [offset] of [text] belongs to, as a range of indices, or null for
 * whitespace and punctuation. In order: the longest of [known] covering it; a run of capitalized
 * words ("Lin Feng", "Ли Фэн") when the word at [offset] is capitalized; the single word. A CJK
 * character, which has no word breaks, is a term of its own unless a known term covers it.
 */
internal fun termAt(text: String, offset: Int, known: List<String>): IntRange? {
    if (offset !in text.indices) return null
    knownAt(text, offset, known)?.let { return it }
    val c = text[offset]
    if (isCjk(c)) return offset..offset
    // A joiner counts as part of the word only between letters ("Lin-Feng", "O'Neil").
    fun inWord(i: Int): Boolean =
        i in text.indices && (isWordChar(text[i]) || (isJoiner(text[i]) && i > 0 && i + 1 < text.length && isWordChar(text[i - 1]) && isWordChar(text[i + 1])))
    if (!inWord(offset)) return null
    var start = offset
    while (inWord(start - 1)) start--
    var end = offset
    while (inWord(end + 1)) end++
    if (!text[start].isUpperCase()) return start..end

    // Extend over neighbouring capitalized words separated by one space.
    while (start >= 2 && text[start - 1] == ' ' && inWord(start - 2)) {
        var s = start - 2
        while (inWord(s - 1)) s--
        if (!text[s].isUpperCase()) break
        start = s
    }
    while (end + 2 < text.length && text[end + 1] == ' ' && inWord(end + 2) && text[end + 2].isUpperCase()) {
        end += 2
        while (inWord(end + 1)) end++
    }
    return start..end
}

/** A paragraph of the recap; [fromPreviousChapter] marks the ones taken from the chapter before. */
data class RecapParagraph(val text: String, val fromPreviousChapter: Boolean)

/**
 * The paragraphs to show on coming back to a spot: the last [maxParagraphs] before it, within
 * [maxChars] in total, in reading order. [current] is the chapter's plain text per block (blank
 * for images and dividers); the spot is [startBlock] at [startFraction], whose block counts only
 * when the reader is already into it. [previousTail] (the previous chapter's last blocks) fills
 * what the current chapter cannot, and comes first.
 */
fun recapBlocks(
    current: List<String>,
    startBlock: Int,
    startFraction: Float,
    previousTail: List<String>,
    maxParagraphs: Int = 4,
    maxChars: Int = 900,
): List<RecapParagraph> {
    val upto = if (startFraction > 0f) startBlock + 1 else startBlock
    val candidates = previousTail.map { it.trim() to true }.filter { it.first.isNotEmpty() } +
        current.take(upto.coerceIn(0, current.size)).map { it.trim() to false }.filter { it.first.isNotEmpty() }
    // Newest first until the budget is used up; the first paragraph is kept (shortened) even if it alone is too long.
    val picked = ArrayList<RecapParagraph>()
    var chars = 0
    for ((text, previous) in candidates.asReversed()) {
        if (picked.size >= maxParagraphs) break
        if (picked.isEmpty() && text.length > maxChars) {
            picked += RecapParagraph("…" + text.takeLast(maxChars).trimStart(), previous)
            break
        }
        if (chars + text.length > maxChars) break
        picked += RecapParagraph(text, previous)
        chars += text.length
    }
    return picked.asReversed()
}
