@file:Suppress("unused") // Filter variants are part of the catalogue API surface (Madara uses Select).

package com.lume.app.domain

/**
 * Mihon-inspired filter model for catalogue browse/search.
 * Engines return a list of filters; UI binds and passes selected values back into search().
 */
sealed class SourceFilter {
    abstract val id: String
    abstract val name: String

    data class Header(
        override val id: String,
        override val name: String,
    ) : SourceFilter()

    data class Check(
        override val id: String,
        override val name: String,
        var state: Boolean = false,
    ) : SourceFilter()

    data class TriState(
        override val id: String,
        override val name: String,
        /** 0 ignore, 1 include, 2 exclude */
        var state: Int = 0,
    ) : SourceFilter()

    /** [options] are the values sent to the site; [labels] are what the filter panel shows. */
    data class Select(
        override val id: String,
        override val name: String,
        val options: List<String>,
        var selected: Int = 0,
        val labels: List<String> = options,
    ) : SourceFilter() {
        val selectedValue: String get() = options.getOrElse(selected) { options.firstOrNull().orEmpty() }
        fun labelAt(index: Int): String = labels.getOrElse(index) { options.getOrElse(index) { "" } }
    }

    data class Sort(
        override val id: String,
        override val name: String,
        val options: List<String>,
        var index: Int = 0,
        var ascending: Boolean = false,
        val labels: List<String> = options,
    ) : SourceFilter() {
        val selectedValue: String get() = options.getOrElse(index) { options.firstOrNull().orEmpty() }
    }

    data class Text(
        override val id: String,
        override val name: String,
        var state: String = "",
    ) : SourceFilter()

    data class Group(
        override val id: String,
        override val name: String,
        val filters: List<SourceFilter>,
    ) : SourceFilter()
}

data class FilterList(val filters: List<SourceFilter> = emptyList()) {
    fun isEmpty(): Boolean = filters.isEmpty()

    /** Every filter, with the children of groups, in panel order. */
    fun flat(): List<SourceFilter> = filters.flatMap { if (it is SourceFilter.Group) it.filters else listOf(it) }

    fun selected(id: String): String? = flat().filterIsInstance<SourceFilter.Select>().firstOrNull { it.id == id }?.selectedValue

    fun sort(id: String): SourceFilter.Sort? = flat().filterIsInstance<SourceFilter.Sort>().firstOrNull { it.id == id }

    fun text(id: String): String? = flat().filterIsInstance<SourceFilter.Text>().firstOrNull { it.id == id }?.state?.trim()?.ifEmpty { null }

    /** Ids of the checked boxes inside group [groupId] (or anywhere when it is null). */
    fun checked(groupId: String? = null): List<String> = scope(groupId).filterIsInstance<SourceFilter.Check>().filter { it.state }.map { it.id }

    /** Ids of tri-state filters in [groupId] that include (state 1) or exclude (state 2). */
    fun included(groupId: String? = null): List<String> = scope(groupId).filterIsInstance<SourceFilter.TriState>().filter { it.state == 1 }.map { it.id }

    fun excluded(groupId: String? = null): List<String> = scope(groupId).filterIsInstance<SourceFilter.TriState>().filter { it.state == 2 }.map { it.id }

    private fun scope(groupId: String?): List<SourceFilter> =
        if (groupId == null) flat()
        else filters.filterIsInstance<SourceFilter.Group>().firstOrNull { it.id == groupId }?.filters.orEmpty()

    /** True when nothing differs from [defaults] (the source's own [CatalogSource.getFilters]). */
    fun sameAs(defaults: FilterList): Boolean = filterStates(this) == filterStates(defaults)
}

/** Comparable state of each filter, for "is anything set?" checks. */
private fun filterStates(list: FilterList): List<Any?> = list.flat().map { f ->
    when (f) {
        is SourceFilter.Check -> f.state
        is SourceFilter.TriState -> f.state
        is SourceFilter.Select -> f.selected
        is SourceFilter.Sort -> f.index to f.ascending
        is SourceFilter.Text -> f.state.trim()
        is SourceFilter.Header, is SourceFilter.Group -> null
    }
}

/**
 * Returns a copy of this list with the filter [id] replaced by [change] applied to it. Filters are
 * data classes, so the UI never mutates the list a search is running with.
 */
fun FilterList.updated(id: String, change: (SourceFilter) -> SourceFilter): FilterList =
    FilterList(
        filters.map { f ->
            when {
                f.id == id -> change(f)
                f is SourceFilter.Group -> f.copy(filters = f.filters.map { c -> if (c.id == id) change(c) else c })
                else -> f
            }
        },
    )

/**
 * This list with the states of [previous] wherever a filter of the same id and kind exists in
 * both (a Select keeps its choice only when its options are the same).
 */
fun FilterList.carryOver(previous: FilterList): FilterList {
    val old = previous.flat().associateBy { it.id }
    fun carry(f: SourceFilter): SourceFilter {
        val p = old[f.id]
        return when {
            f is SourceFilter.Group -> f.copy(filters = f.filters.map(::carry))
            f is SourceFilter.Check && p is SourceFilter.Check -> f.copy(state = p.state)
            f is SourceFilter.TriState && p is SourceFilter.TriState -> f.copy(state = p.state)
            f is SourceFilter.Text && p is SourceFilter.Text -> f.copy(state = p.state)
            f is SourceFilter.Select && p is SourceFilter.Select && p.options == f.options -> f.copy(selected = p.selected)
            f is SourceFilter.Sort && p is SourceFilter.Sort && p.options == f.options -> f.copy(index = p.index, ascending = p.ascending)
            else -> f
        }
    }
    return FilterList(filters.map(::carry))
}

/**
 * Sets the [SourceFilter.Select] or [SourceFilter.Sort] [id] to the option whose site value is
 * [value] (unknown values leave it as it is). Engines build their browse sections with this.
 */
fun FilterList.choose(id: String, value: String, ascending: Boolean = false): FilterList =
    updated(id) { f ->
        when (f) {
            is SourceFilter.Select -> f.options.indexOf(value).takeIf { it >= 0 }?.let { f.copy(selected = it) } ?: f
            is SourceFilter.Sort -> f.options.indexOf(value).takeIf { it >= 0 }?.let { f.copy(index = it, ascending = ascending) } ?: f
            else -> f
        }
    }

/** Ticks the [SourceFilter.Check] or includes the [SourceFilter.TriState] [id]. */
fun FilterList.include(id: String): FilterList =
    updated(id) { f ->
        when (f) {
            is SourceFilter.Check -> f.copy(state = true)
            is SourceFilter.TriState -> f.copy(state = 1)
            else -> f
        }
    }

/**
 * An extra catalogue feed of a source besides popular and latest ("New titles", "Top rated").
 * It is a search without text, with [filters] set the way the feed needs them.
 */
data class BrowseSection(
    val id: String,
    val name: String,
    val filters: FilterList,
)

data class GlobalSearchHit(
    val sourceId: String,
    val sourceName: String,
    val title: TitleSummary,
)

data class LibraryUpdateResult(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val newChapterCount: Int,
    /** For titles with linked sources: the sources that had the new chapters first. */
    val firstOn: List<String> = emptyList(),
)
