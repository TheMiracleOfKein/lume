package com.lume.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A list of titles to share with someone else: what to read, not how far you got. It carries no
 * progress, read marks or sign-ins, only enough to find each title again: its source and id,
 * its name, and the title's web page for sources the other phone doesn't have.
 */
@Serializable
data class ReadingList(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val name: String,
    val createdAt: Long,
    val titles: List<ReadingListEntry>,
) {
    companion object {
        const val FORMAT = "lume-reading-list"
        const val VERSION = 1
    }
}

@Serializable
data class ReadingListEntry(
    val sourceId: String,
    val titleId: String,
    val title: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    /** The title's page on the source's website. */
    val url: String? = null,
    val sourceName: String? = null,
)

class NotAReadingListException : IllegalArgumentException("Not a Lume reading list")

private val listJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

fun encodeReadingList(list: ReadingList): String = listJson.encodeToString(ReadingList.serializer(), list)

/**
 * Reads a list written by [encodeReadingList]. Newer versions are read as far as this one
 * understands them; anything else (a backup, some other JSON) is a [NotAReadingListException].
 * Entries without a source, id or name are dropped, and so are repeats.
 */
fun parseReadingList(raw: String): ReadingList {
    val list = runCatching { listJson.decodeFromString(ReadingList.serializer(), raw) }.getOrNull()
        ?: throw NotAReadingListException()
    if (list.format != ReadingList.FORMAT) throw NotAReadingListException()
    val titles = list.titles
        .filter { it.sourceId.isNotBlank() && it.titleId.isNotBlank() && it.title.isNotBlank() }
        .distinctBy { it.sourceId to it.titleId }
    return list.copy(titles = titles)
}

/** Where each entry of a list stands on this phone. */
enum class ReadingListState { New, InLibrary, NoSource }

data class ReadingListRow(
    val entry: ReadingListEntry,
    val state: ReadingListState,
    /** Source and id to add; differs from the entry's when the title was found through its link. */
    val sourceId: String = entry.sourceId,
    val titleId: String = entry.titleId,
)

/**
 * Sorts the entries of [list]: titles already in the library, titles that can be added (their
 * source exists here, or [resolveLink] finds their web page on a source that does), and titles
 * whose source Lume doesn't have.
 */
fun planReadingList(
    list: ReadingList,
    inLibrary: (sourceId: String, titleId: String) -> Boolean,
    hasSource: (sourceId: String) -> Boolean,
    resolveLink: (url: String) -> Pair<String, String>? = { null },
): List<ReadingListRow> = list.titles.map { entry ->
    val target = when {
        hasSource(entry.sourceId) -> entry.sourceId to entry.titleId
        else -> entry.url?.let(resolveLink)
    }
    when {
        target == null -> ReadingListRow(entry, ReadingListState.NoSource)
        inLibrary(target.first, target.second) ->
            ReadingListRow(entry, ReadingListState.InLibrary, target.first, target.second)
        else -> ReadingListRow(entry, ReadingListState.New, target.first, target.second)
    }
}

/** "lume-list-on-hold-2026-10-20.json": the list name (letters and digits only) and the day. */
fun readingListFileName(name: String, day: String): String {
    val plain = name.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(40)
        .trim('-')
    return if (plain.isEmpty()) "lume-list-$day.json" else "lume-list-$plain-$day.json"
}
