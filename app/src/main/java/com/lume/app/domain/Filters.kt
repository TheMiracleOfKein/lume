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
