package com.lume.app.domain

/** How the library lays out its titles. */
enum class LibraryDisplay { Grid, Compact, List }

enum class LibrarySort { Title, LastRead, DateAdded, Unread, LatestChapter }

/** A library filter is off, keeps only matching titles, or hides them. */
enum class TriState {
    Off, Include, Exclude;

    fun next(): TriState = entries[(ordinal + 1) % entries.size]
}

enum class LibraryFilter { Unread, Downloaded, Started, Tracking, Completed }

/** Everything the library sheet controls, stored in preferences. */
data class LibrarySettings(
    val display: LibraryDisplay = LibraryDisplay.Grid,
    val sort: LibrarySort = LibrarySort.Title,
    val ascending: Boolean = true,
    val filters: Map<LibraryFilter, TriState> = emptyMap(),
    val unreadBadge: Boolean = true,
    val downloadedBadge: Boolean = true,
) {
    fun filter(key: LibraryFilter): TriState = filters[key] ?: TriState.Off

    val activeFilterCount: Int get() = filters.count { it.value != TriState.Off }
}

/** One title as the library shows it. */
data class LibraryItem(
    val sourceId: String,
    val titleId: String,
    val title: String,
    val altTitle: String? = null,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val addedAt: Long = 0L,
    val categoryIds: Set<Long> = emptySet(),
    val totalChapters: Int = 0,
    val unread: Int = 0,
    val downloaded: Int = 0,
    val lastReadAt: Long? = null,
    val latestChapterAt: Long? = null,
    val tracking: Boolean = false,
    val completed: Boolean = false,
    /** Chapters found since the title was last opened. */
    val hasNew: Boolean = false,
) {
    val key: String get() = "$sourceId\u0000$titleId"
    val started: Boolean get() = lastReadAt != null || (totalChapters > 0 && unread < totalChapters)

    fun matches(filter: LibraryFilter): Boolean = when (filter) {
        LibraryFilter.Unread -> unread > 0
        LibraryFilter.Downloaded -> downloaded > 0
        LibraryFilter.Started -> started
        LibraryFilter.Tracking -> tracking
        LibraryFilter.Completed -> completed
    }
}

/**
 * The titles to show: in [categoryId] (null = all), matching [search] (title or alternative
 * title), passing every filter, sorted. Ties sort by title so the order never jumps.
 */
fun queryLibrary(
    items: List<LibraryItem>,
    settings: LibrarySettings,
    categoryId: Long? = null,
    search: String = "",
): List<LibraryItem> {
    val needle = search.trim().lowercase()
    val filtered = items.filter { item ->
        (categoryId == null || categoryId in item.categoryIds) &&
            (needle.isEmpty() || item.title.lowercase().contains(needle) ||
                item.altTitle?.lowercase()?.contains(needle) == true) &&
            settings.filters.all { (filter, state) ->
                when (state) {
                    TriState.Off -> true
                    TriState.Include -> item.matches(filter)
                    TriState.Exclude -> !item.matches(filter)
                }
            }
    }
    val byTitle = compareBy<LibraryItem> { it.title.lowercase() }
    val primary: Comparator<LibraryItem> = when (settings.sort) {
        LibrarySort.Title -> byTitle
        LibrarySort.LastRead -> compareBy { it.lastReadAt ?: 0L }
        LibrarySort.DateAdded -> compareBy { it.addedAt }
        LibrarySort.Unread -> compareBy { it.unread }
        LibrarySort.LatestChapter -> compareBy { it.latestChapterAt ?: 0L }
    }
    val ordered = if (settings.ascending) primary else primary.reversed()
    return filtered.sortedWith(ordered.then(byTitle))
}

/** A finished or completed status, whatever language the source uses. */
fun isCompletedStatus(status: String?): Boolean {
    val s = status?.lowercase() ?: return false
    return COMPLETED_WORDS.any { it in s }
}

// Not "ended": it would match "suspended".
private val COMPLETED_WORDS = listOf("complet", "finished", "заверш", "законч", "окончен")

/**
 * Library filters are stored as `"Unread:Include,Tracking:Exclude"`; unknown parts are skipped,
 * so a newer or older app version never breaks the library.
 */
fun encodeLibraryFilters(filters: Map<LibraryFilter, TriState>): String =
    filters.filterValues { it != TriState.Off }.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }

fun decodeLibraryFilters(raw: String?): Map<LibraryFilter, TriState> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(',').mapNotNull { part ->
        val (k, v) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        val filter = LibraryFilter.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
        val state = TriState.entries.firstOrNull { it.name == v } ?: return@mapNotNull null
        filter to state
    }.toMap()
}
