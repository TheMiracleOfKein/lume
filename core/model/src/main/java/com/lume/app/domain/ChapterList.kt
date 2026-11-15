package com.lume.app.domain

/** Chapter list filters, stored per title as a bitmask (`title_prefs.chapterFilter`). */
object ChapterFilter {
    const val UNREAD = 1
    const val READ = 2
    const val DOWNLOADED = 4
    const val BOOKMARKED = 8

    val all = listOf(UNREAD, READ, DOWNLOADED, BOOKMARKED)
}

/** How one title's chapter list is shown. */
data class ChapterListOptions(
    val filter: Int = 0,
    /** Oldest first; otherwise newest first. */
    val ascending: Boolean = false,
    /** Preferred translation team; null = the team with the most chapters. */
    val branch: String? = null,
    val showDuplicates: Boolean = false,
)

/**
 * Read state per chapter, not per copy: reading one team's chapter 12 makes every team's
 * chapter 12 count as read.
 */
class ChapterReadState(chapters: List<Chapter>, readIds: Set<String>) {
    private val readKeys: Set<String> = chapters.filter { it.id in readIds }.mapTo(HashSet()) { dedupeKey(it) }

    fun isRead(chapter: Chapter): Boolean = dedupeKey(chapter) in readKeys
}

/**
 * The chapters to show: one per chapter unless [ChapterListOptions.showDuplicates], then the
 * filters (all chosen filters must match), then sorted. [chapters] is in source order.
 */
fun chapterListView(
    chapters: List<Chapter>,
    options: ChapterListOptions,
    read: ChapterReadState,
    downloadedIds: Set<String>,
    bookmarkedIds: Set<String>,
): List<Chapter> {
    val base = if (options.showDuplicates) chapters else dedupeChapters(chapters, options.branch)
    val f = options.filter
    val filtered = base.filter { ch ->
        val isRead = read.isRead(ch)
        ((f and ChapterFilter.UNREAD) == 0 || !isRead) &&
            ((f and ChapterFilter.READ) == 0 || isRead) &&
            ((f and ChapterFilter.DOWNLOADED) == 0 || ch.id in downloadedIds) &&
            ((f and ChapterFilter.BOOKMARKED) == 0 || ch.id in bookmarkedIds)
    }
    val oldestFirst = filtered.inReadingOrder()
    return if (options.ascending) oldestFirst else oldestFirst.asReversed()
}

/** Every chapter before [chapter] in reading order (for "mark previous as read"). */
fun chaptersBefore(chapters: List<Chapter>, chapter: Chapter): List<Chapter> {
    val ordered = chapters.inReadingOrder()
    val index = ordered.indexOfFirst { it.id == chapter.id }
    return if (index <= 0) emptyList() else ordered.subList(0, index)
}

/** The next [count] unread chapters after the furthest read one, oldest first (null = all). */
fun nextUnread(chapters: List<Chapter>, read: ChapterReadState, count: Int?): List<Chapter> {
    val ordered = chapters.inReadingOrder()
    val lastRead = ordered.indexOfLast { read.isRead(it) }
    val unread = ordered.drop(lastRead + 1).filterNot { read.isRead(it) }
    return if (count == null) unread else unread.take(count)
}
