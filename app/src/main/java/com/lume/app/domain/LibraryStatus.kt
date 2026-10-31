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

/** The status the first matching category names, in the order reading, dropped, completed, planned; [LibraryStatus.Other] when none does. */
fun libraryStatusOf(categoryNames: Collection<String>): LibraryStatus {
    val names = categoryNames.mapTo(HashSet()) { it.trim().lowercase() }
    return STATUS_NAMES.firstOrNull { (_, aliases) -> aliases.any { it in names } }?.first ?: LibraryStatus.Other
}
