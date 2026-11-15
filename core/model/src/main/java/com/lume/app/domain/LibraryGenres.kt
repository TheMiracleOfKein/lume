package com.lume.app.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A genre the library filter can offer: [key] is the merged form, [label] the spelling to show. */
data class GenreOption(val key: String, val label: String)

/** Case- and spacing-insensitive form of a genre name, used to merge "Action", "action " and "ACTION". */
fun normalizeGenre(raw: String): String = raw.trim().split(Regex("\\s+")).joinToString(" ").lowercase()

/** Trimmed genre names without blanks and without case-insensitive repeats, first spelling kept. */
fun cleanGenres(raw: List<String>): List<String> {
    val seen = HashSet<String>()
    return raw.map { it.trim().split(Regex("\\s+")).joinToString(" ") }
        .filter { it.isNotEmpty() && seen.add(normalizeGenre(it)) }
}

private val genresJson = Json { ignoreUnknownKeys = true }

fun encodeGenres(genres: List<String>): String =
    genresJson.encodeToString(ListSerializer(String.serializer()), cleanGenres(genres))

/** Genres stored by [encodeGenres]; anything unreadable counts as none. */
fun decodeGenres(raw: String?): List<String> {
    if (raw.isNullOrBlank() || raw == "[]") return emptyList()
    return runCatching { genresJson.decodeFromString(ListSerializer(String.serializer()), raw) }
        .getOrDefault(emptyList())
}

/**
 * The genres present in [items], merged case-insensitively and sorted by label. A merged genre
 * is shown in its most common spelling (ties: the alphabetically first).
 */
fun collectLibraryGenres(items: List<LibraryItem>): List<GenreOption> {
    val spellings = HashMap<String, HashMap<String, Int>>()
    for (item in items) {
        for (genre in cleanGenres(item.genres)) {
            spellings.getOrPut(normalizeGenre(genre)) { HashMap() }.merge(genre, 1, Int::plus)
        }
    }
    return spellings.map { (key, counts) ->
        val label = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key
        GenreOption(key, label)
    }.sortedWith(compareBy({ it.label.lowercase() }, { it.key }))
}

/** True when [item] has every genre in [selected] (normalized keys); an empty selection matches all. */
fun hasAllGenres(item: LibraryItem, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return true
    val own = item.genres.mapTo(HashSet(), ::normalizeGenre)
    return selected.all { it in own }
}
