package com.lume.app.data.tracker

import com.lume.app.domain.Chapter

data class TrackerSearchResult(
    val remoteId: String,
    val title: String,
    val url: String? = null,
    val coverUrl: String? = null,
    /** Other names (romaji, English, native, Russian …), used for automatic linking. */
    val altTitles: List<String> = emptyList(),
)

data class TrackerProgress(
    val chaptersRead: Int,
    val status: String? = null,
    val score: Float? = null,
    /** Shikimori user_rate id / AniList mediaList entry id when known. */
    val libraryId: String? = null,
)

data class TitleSyncResult(
    val summary: String,
    val details: List<String> = emptyList(),
) {
    fun displayText(): String = buildString {
        append(summary)
        if (details.isNotEmpty()) {
            append('\n')
            append(details.joinToString("\n"))
        }
    }
}

interface Tracker {
    val id: String
    val displayName: String
    suspend fun isLoggedIn(): Boolean
    suspend fun search(query: String): List<TrackerSearchResult>
    suspend fun link(
        sourceId: String,
        titleId: String,
        remoteId: String,
        remoteUrl: String? = null,
    )
    suspend fun unlink(sourceId: String, titleId: String)
    suspend fun fetchProgress(remoteId: String): TrackerProgress?
    suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean
    fun authorizeUrl(): String?
    suspend fun exchangeCode(code: String): Boolean
    suspend fun logout()

    val login: TrackerLogin get() = TrackerLogin.OAuth

    /** Username / password sign-in, for trackers with [TrackerLogin.Password]. */
    suspend fun login(username: String, password: String): Boolean = false

    /** Statuses this tracker knows, in the order the editor shows them. */
    val statuses: List<TrackStatus> get() = TrackStatus.entries

    /** The tracker keeps start / finish dates. */
    val supportsDates: Boolean get() = false

    /** The whole list entry; null when signed out or on errors, an empty entry when not in the list. */
    suspend fun fetchEntry(remoteId: String): TrackerEntry? =
        fetchProgress(remoteId)?.let { TrackerEntry(chaptersRead = it.chaptersRead, score = it.score ?: 0f) }

    /** Writes status, score, chapters and dates (adds the title to the list when needed). */
    suspend fun updateEntry(remoteId: String, entry: TrackerEntry): Boolean = updateProgress(remoteId, entry.chaptersRead)
}

fun chapterProgressNumber(chapters: List<Chapter>, chapterId: String): Int {
    val ch = chapters.firstOrNull { it.id == chapterId }
    ch?.number?.replace(',', '.')?.toFloatOrNull()?.toInt()?.takeIf { it > 0 }?.let { return it }
    val match = Regex("""(\d+(?:[.,]\d+)?)""").find(ch?.title.orEmpty())
    match?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()?.toInt()?.takeIf { it > 0 }
        ?.let { return it }
    val idx = chapters.indexOfFirst { it.id == chapterId }
    return if (idx >= 0) idx + 1 else 0
}

fun findChapterForProgress(chapters: List<Chapter>, chaptersRead: Int): Chapter? {
    if (chaptersRead <= 0 || chapters.isEmpty()) return null
    chapters.firstOrNull { chapterProgressNumber(chapters, it.id) == chaptersRead }?.let { return it }
    return chapters
        .map { it to chapterProgressNumber(chapters, it.id) }
        .filter { it.second in 1..chaptersRead }
        .maxByOrNull { it.second }
        ?.first
}

fun resolveLocalChapterProgress(
    chapters: List<Chapter>,
    progressChapterId: String?,
    readChapterIds: Set<String>,
): Pair<Int, Chapter?> {
    var best = 0
    var bestChapter: Chapter? = null
    fun consider(chapterId: String?) {
        if (chapterId.isNullOrBlank()) return
        val ch = chapters.firstOrNull { it.id == chapterId } ?: return
        val n = chapterProgressNumber(chapters, ch.id)
        if (n > best) {
            best = n
            bestChapter = ch
        }
    }
    consider(progressChapterId)
    for (id in readChapterIds) consider(id)
    if (bestChapter == null && best > 0) {
        bestChapter = findChapterForProgress(chapters, best)
    }
    return best to bestChapter
}
