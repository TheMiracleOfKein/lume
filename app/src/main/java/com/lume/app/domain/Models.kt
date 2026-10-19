package com.lume.app.domain

import kotlinx.serialization.Serializable

data class TitleSummary(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val latestChapter: String? = null,
    val updatedLabel: String? = null,
    val subtitle: String? = null,
)

@Serializable
data class TitleDetails(
    val id: String,
    val title: String,
    val altTitle: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val typeLabel: String? = null,
    val status: String? = null,
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    /** Every other name the source knows (translations, romanizations); for matching, not shown. */
    val otherNames: List<String> = emptyList(),
)

@Serializable
data class Chapter(
    val id: String,
    val title: String,
    val url: String,
    val number: String? = null,
    val volume: String? = null,
    val dateLabel: String? = null,
    val branchId: String? = null,
    val branchName: String? = null,
)

/** Numeric key for sorting chapters oldest → newest. */
fun chapterSortKey(chapter: Chapter): Double? {
    chapter.number?.replace(',', '.')?.toDoubleOrNull()?.let { return it }
    val match = Regex("""(\d+(?:[.,]\d+)?)""").find(chapter.title)
    return match?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
}

/**
 * Sources often return newest-first. Reader next/prev and seamless prefetch
 * need oldest → newest (reading order).
 */
fun List<Chapter>.inReadingOrder(): List<Chapter> {
    if (size <= 1) return this
    val keyed = mapIndexed { index, ch -> Triple(ch, chapterSortKey(ch), index) }
    val keyedCount = keyed.count { it.second != null }
    if (keyedCount >= (size + 1) / 2) {
        return keyed.sortedWith(
            compareBy<Triple<Chapter, Double?, Int>> { it.second ?: Double.MAX_VALUE }
                .thenBy { it.third },
        ).map { it.first }
    }
    val first = chapterSortKey(first())
    val last = chapterSortKey(last())
    if (first != null && last != null && first > last) return asReversed()
    return this
}

data class ComicPage(
    val index: Int,
    val imageUrl: String,
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean = false,
)

data class HomeFeed(
    val spotlight: List<TitleSummary> = emptyList(),
    val latest: List<TitleSummary> = emptyList(),
)

/** Order matches Flutter: rtl=0, webtoon=1, ltr=2 */
enum class ReaderMode(val prefsIndex: Int, val label: String) {
    Rtl(0, "RTL"),
    Webtoon(1, "Webtoon"),
    Ltr(2, "LTR");

    companion object {
        fun fromPrefs(index: Int): ReaderMode =
            entries.firstOrNull { it.prefsIndex == index } ?: Webtoon
    }
}

enum class ReaderTheme(val labelRes: Int) {
    Light(com.lume.app.R.string.reader_theme_light),
    Dark(com.lume.app.R.string.reader_theme_dark),
    System(com.lume.app.R.string.reader_theme_system),
}
