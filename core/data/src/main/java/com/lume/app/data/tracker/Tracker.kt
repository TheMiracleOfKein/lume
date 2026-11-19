package com.lume.app.data.tracker

import com.lume.app.domain.Chapter
import com.lume.app.domain.MediaKind

data class TrackerSearchResult(
    val remoteId: String,
    val title: String,
    val url: String? = null,
    val coverUrl: String? = null,
    /** Other names (romaji, English, native, Russian …), used for automatic linking. */
    val altTitles: List<String> = emptyList(),
    /** What the entry is (manhwa, light novel …), so a manga and its novel aren't confused. */
    val format: TitleFormat? = null,
    /** The year it started, when the tracker says. */
    val year: Int? = null,
)

/** False when the entry is known to be the other kind: a manga offered for a novel, or the reverse. */
fun TrackerSearchResult.matchesKind(kind: MediaKind): Boolean =
    format == null || format.isNovel == (kind == MediaKind.Novel)

/** The kind of work a tracker entry describes. Novels and comics of the same name are different entries. */
enum class TitleFormat(val isNovel: Boolean = false) {
    Manga, Manhwa, Manhua, OneShot, Doujinshi, Comic, LightNovel(true), Novel(true), WebNovel(true);

    companion object {
        /**
         * Reads the format names trackers use: AniList (`MANGA`, `NOVEL`, `ONE_SHOT`), MyAnimeList
         * and Shikimori (`manhwa`, `light_novel`, `one_shot` …), Kitsu (`oneshot`, `oel`) and
         * MangaUpdates (`Manhwa`, `Novel`, `OEL`). [country] is AniList's country of origin, which
         * tells a manhwa (KR) or manhua (CN, TW) from a manga.
         */
        fun parse(raw: String?, country: String? = null): TitleFormat? {
            val key = raw?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            return when (key) {
                "manga" -> when (country?.uppercase()) {
                    "KR" -> Manhwa
                    "CN", "TW", "HK" -> Manhua
                    else -> Manga
                }
                "manhwa" -> Manhwa
                "manhua" -> Manhua
                "one_shot", "oneshot" -> OneShot
                "doujinshi", "doujin" -> Doujinshi
                "oel", "comic", "artbook", "filipino", "indonesian", "thai", "vietnamese", "malaysian", "nordic", "french", "spanish" -> Comic
                "light_novel" -> LightNovel
                "novel" -> Novel
                "web_novel" -> WebNovel
                else -> null
            }
        }

        /** The year from a date such as `2018-01-12` or a bare `2018`. */
        fun year(raw: String?): Int? = raw?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2200 }
    }
}

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

    /** The kinds of titles this tracker keeps lists of; the others never see it. */
    val kinds: Set<MediaKind> get() = setOf(MediaKind.Comics)

    suspend fun isLoggedIn(): Boolean
    /** Titles matching [query] among the tracker's [kind] entries; empty for kinds it does not serve yet. */
    suspend fun search(query: String, kind: MediaKind = MediaKind.Comics): List<TrackerSearchResult>
    suspend fun link(
        sourceId: String,
        titleId: String,
        remoteId: String,
        remoteUrl: String? = null,
        kind: MediaKind = MediaKind.Comics,
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

    /** The tracker can hand over the signed-in user's "Reading" list ([fetchReadingList]). */
    val supportsListImport: Boolean get() = false

    /** The user's "Reading" list; null when unsupported, signed out or on errors. */
    suspend fun fetchReadingList(kind: MediaKind = MediaKind.Comics): List<TrackerListEntry>? = null

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
