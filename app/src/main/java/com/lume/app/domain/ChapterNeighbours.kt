package com.lume.app.domain

/** The chapters right before and after the one being read, in reading order (oldest to newest). */
data class ChapterNeighbours(val previous: Chapter?, val next: Chapter?)

/**
 * Finds the neighbours of [currentId] by reading order, not by the order [chapters] arrived in
 * (sources often list newest first). Chapter numbers that repeat keep their listed order, so the
 * result is stable. An id that is not in the list has no neighbours.
 */
fun chapterNeighbours(chapters: List<Chapter>, currentId: String): ChapterNeighbours {
    val ordered = chapters.inReadingOrder()
    val index = ordered.indexOfFirst { sameChapterId(it.id, currentId) }
    if (index < 0) return ChapterNeighbours(null, null)
    return ChapterNeighbours(ordered.getOrNull(index - 1), ordered.getOrNull(index + 1))
}

private fun sameChapterId(a: String, b: String): Boolean =
    a == b || a.trimEnd('/') == b.trimEnd('/')

/** Whether the list has a chapter after ([forward]) or before [edgeChapterId] in reading order. */
fun hasChapterBeyond(chapters: List<Chapter>, edgeChapterId: String, forward: Boolean): Boolean {
    val n = chapterNeighbours(chapters, edgeChapterId)
    return (if (forward) n.next else n.previous) != null
}
