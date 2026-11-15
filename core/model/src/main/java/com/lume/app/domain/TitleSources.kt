package com.lume.app.domain

/**
 * One title, many sources. A library entry keeps its main source; other sources linked to it
 * add their chapters to its list. A linked chapter gets an id inside the main title
 * ([LinkedChapterId]), so read marks, positions, page bookmarks and downloads keep working the
 * way they do for the main source's own chapters.
 */

/** Where a linked chapter really lives. */
data class LinkedChapterRef(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
)

object LinkedChapterId {
    const val PREFIX = "lume-link:"

    fun encode(sourceId: String, titleId: String, chapterId: String): String =
        PREFIX + listOf(sourceId, titleId, chapterId).joinToString("|") { escape(it) }

    /** Null for the main source's own chapters. */
    fun decode(id: String): LinkedChapterRef? {
        if (!id.startsWith(PREFIX)) return null
        val parts = id.removePrefix(PREFIX).split('|')
        if (parts.size != 3) return null
        return LinkedChapterRef(unescape(parts[0]), unescape(parts[1]), unescape(parts[2]))
    }

    fun isLinked(id: String): Boolean = id.startsWith(PREFIX)

    /** The source a chapter id belongs to: its linked source, or [mainSourceId]. */
    fun sourceOf(id: String, mainSourceId: String): String = decode(id)?.sourceId ?: mainSourceId

    private fun escape(value: String) = value.replace("%", "%25").replace("|", "%7C")
    private fun unescape(value: String) = value.replace("%7C", "|").replace("%25", "%")
}

/** The chapters of one linked source title, in that source's order. */
data class SourceChapters(
    val sourceId: String,
    val titleId: String,
    val chapters: List<Chapter>,
)

/**
 * The chapter number as text: the source's own number, else one read from the title
 * ("Chapter 12", "Глава 12.5", "#12", or the only number in it). Null when there is none.
 */
fun chapterNumber(chapter: Chapter): String? {
    chapter.number?.trim()?.takeIf { it.isNotEmpty() }?.let { return normalizeNumber(it) }
    val title = chapter.title
    CHAPTER_WORD.find(title)?.let { return normalizeNumber(it.groupValues[1]) }
    val numbers = ANY_NUMBER.findAll(title).map { it.value }.toList()
    return numbers.singleOrNull()?.let(::normalizeNumber)
}

private val CHAPTER_WORD = Regex(
    """(?iu)(?:chapter|chap|ch|episode|ep|глава|гл|эпизод|#)\.?\s*(\d+(?:[.,]\d+)?)""",
)
private val ANY_NUMBER = Regex("""\d+(?:[.,]\d+)?""")

/** "12.0" → "12", "12,5" → "12.5". */
fun normalizeNumber(raw: String): String {
    val text = raw.trim().replace(',', '.')
    val d = text.toDoubleOrNull() ?: return text.lowercase()
    return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
}

/**
 * The main source's chapters followed by every linked source's chapters, as one list.
 *
 * Linked chapters get ids from [LinkedChapterId] and lose their team (teams belong to the main
 * source). A linked chapter takes the volume of the main source's chapter with the same number,
 * so both copies share one [dedupeKey]: a chapter read on one source counts as read on all.
 * Linked chapters without a number can't be placed and are left out.
 *
 * Every copy stays in the list; [dedupeChapters] picks one per chapter (the main source's when
 * it has the chapter, else the first linked source that does), and the reader can fall back to
 * the other copies when one fails to load.
 */
fun mergeSourceChapters(own: List<Chapter>, linked: List<SourceChapters>): List<Chapter> {
    if (linked.all { it.chapters.isEmpty() }) return own
    val main = own.map { ch ->
        if (ch.number.isNullOrBlank()) chapterNumber(ch)?.let { ch.copy(number = it) } ?: ch else ch
    }
    // number → the volumes the main source files it under
    val volumes = HashMap<String, MutableSet<String>>()
    for (ch in main) {
        val n = ch.number?.let(::normalizeNumber) ?: continue
        volumes.getOrPut(n) { LinkedHashSet() } += ch.volume?.trim().orEmpty()
    }
    val out = ArrayList<Chapter>(main.size + linked.sumOf { it.chapters.size })
    out += main
    for (source in linked) {
        for (ch in source.chapters) {
            val number = chapterNumber(ch) ?: continue
            val known = volumes[number]
            // With the number in several volumes of the main source, only an equal volume matches.
            val volume = if (known?.size == 1) known.first().ifEmpty { null } else ch.volume
            out += ch.copy(
                id = LinkedChapterId.encode(source.sourceId, source.titleId, ch.id),
                number = number,
                volume = volume,
                branchId = null,
                branchName = null,
            )
        }
    }
    return out
}

/**
 * The other copies of [chapter] (same [dedupeKey], another source or team), in list order:
 * the main source's first, then linked sources in their order. Used when [chapter] fails to load.
 */
fun alternativeCopies(chapters: List<Chapter>, chapter: Chapter): List<Chapter> {
    val key = dedupeKey(chapter)
    val origin = LinkedChapterId.decode(chapter.id)?.sourceId
    return chapters.filter { it.id != chapter.id && dedupeKey(it) == key }
        .sortedBy { if (LinkedChapterId.decode(it.id)?.sourceId == origin) 1 else 0 }
}

/**
 * Turns a chapter id of the old main title into the id it gets when [newMain] becomes the main
 * source and the old main becomes a linked source: the old main's own chapters become linked
 * ids, the new main's linked chapters become its own ids, other linked chapters stay as they are.
 */
fun chapterIdForNewMain(id: String, oldMain: Pair<String, String>, newMain: Pair<String, String>): String {
    val ref = LinkedChapterId.decode(id)
        ?: return LinkedChapterId.encode(oldMain.first, oldMain.second, id)
    return if (ref.sourceId == newMain.first && ref.titleId == newMain.second) ref.chapterId else id
}
