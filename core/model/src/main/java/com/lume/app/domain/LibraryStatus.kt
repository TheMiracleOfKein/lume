package com.lume.app.domain

/**
 * Where a library title stands, read from its category names: the reading lists that a
 * MangaLib import (or the user) creates. Search results show it as a colour bar.
 */
enum class LibraryStatus { Reading, Planned, Dropped, Completed, Other }

private val STATUS_NAMES: List<Pair<LibraryStatus, Set<String>>> = listOf(
    LibraryStatus.Reading to setOf("читаю", "reading", "читаемое", "in progress"),
    LibraryStatus.Dropped to setOf("брошено", "заброшено", "dropped", "abandoned"),
    LibraryStatus.Completed to setOf("прочитано", "completed", "read", "finished"),
    LibraryStatus.Planned to setOf("в планах", "planned", "plan to read", "planning", "plans"),
)

/**
 * Finds the library title behind a search or catalogue result, so the result gets its status
 * mark even when the title was saved from another source:
 * 1. the same source and id;
 * 2. a title on another source linked to a library title (title screen → Sources);
 * 3. the same name after [normalName] (names of 3+ letters or digits, to skip "A" or "01").
 */
class LibraryMarkIndex(
    entries: List<Entry>,
    links: List<Link> = emptyList(),
) {
    data class Entry(val sourceId: String, val titleId: String, val titleName: String, val status: LibraryStatus)

    /** [sourceId] / [titleId] is the library title; [linkSourceId] / [linkTitleId] the same title elsewhere. */
    data class Link(val sourceId: String, val titleId: String, val linkSourceId: String, val linkTitleId: String, val linkTitleName: String = "")

    private val byKey: Map<Pair<String, String>, LibraryStatus> = entries.associate { (it.sourceId to it.titleId) to it.status }
    private val byLink: Map<Pair<String, String>, LibraryStatus> = links.mapNotNull { l ->
        byKey[l.sourceId to l.titleId]?.let { (l.linkSourceId to l.linkTitleId) to it }
    }.toMap()
    private val byName: Map<String, LibraryStatus> = buildMap {
        // Library names first, so they win over the name a linked source uses.
        entries.forEach { e -> nameKey(e.titleName)?.let { putIfAbsent(it, e.status) } }
        links.forEach { l -> byKey[l.sourceId to l.titleId]?.let { s -> nameKey(l.linkTitleName)?.let { putIfAbsent(it, s) } } }
    }

    fun statusOf(sourceId: String, titleId: String, title: String? = null): LibraryStatus? =
        byKey[sourceId to titleId]
            ?: byLink[sourceId to titleId]
            ?: title?.let(::nameKey)?.let(byName::get)

    private fun nameKey(name: String): String? = normalName(name).takeIf { it.length >= MIN_NAME_LENGTH }

    private companion object {
        const val MIN_NAME_LENGTH = 3
    }
}

/** The status the first matching category names, in the order reading, dropped, completed, planned; [LibraryStatus.Other] when none does. */
fun libraryStatusOf(categoryNames: Collection<String>): LibraryStatus {
    val names = categoryNames.mapTo(HashSet()) { it.trim().lowercase() }
    return STATUS_NAMES.firstOrNull { (_, aliases) -> aliases.any { it in names } }?.first ?: LibraryStatus.Other
}
