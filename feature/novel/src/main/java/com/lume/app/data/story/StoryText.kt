package com.lume.app.data.story

import com.lume.app.data.db.CODEX_AI
import com.lume.app.data.db.CODEX_IGNORED
import com.lume.app.data.db.CODEX_USER
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.llm.nameKey

/** The kinds a codex entry can have, as stored in [CodexEntryEntity.kind]. */
val CODEX_KINDS = listOf("character", "place", "group", "item", "term", "other")

/**
 * Where any of [terms] occurs in [text], ignoring case, as ranges of indices in order. Overlapping
 * and touching matches are merged, so "Lin Feng" and "Lin" give one range.
 */
fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
    val found = ArrayList<IntRange>()
    for (term in terms.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }) {
        var from = 0
        while (from <= text.length - term.length) {
            val at = text.indexOf(term, from, ignoreCase = true)
            if (at < 0) break
            found += at until at + term.length
            from = at + term.length
        }
    }
    found.sortBy { it.first }
    val merged = ArrayList<IntRange>()
    for (r in found) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
        } else {
            merged += r
        }
    }
    return merged
}

/**
 * A window of at most about [max] characters of [text] around its first highlight, with "…" where
 * it was cut, and [ranges] moved to match (ranges outside the window are dropped, ones cut by it
 * are shortened).
 */
fun snippetAround(text: String, ranges: List<IntRange>, max: Int = 240): Pair<String, List<IntRange>> {
    if (text.length <= max) return text to ranges
    val hit = ranges.firstOrNull()?.first ?: 0
    val end = (hit - max / 3).coerceAtLeast(0).plus(max).coerceAtMost(text.length)
    val start = (end - max).coerceAtLeast(0)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    val shift = prefix.length - start
    val moved = ranges.mapNotNull { r ->
        val s = maxOf(r.first, start)
        val e = minOf(r.last, end - 1)
        if (s <= e) (s + shift)..(e + shift) else null
    }
    return (prefix + text.substring(start, end) + suffix) to moved
}

/** Aliases typed as `a, b; c` (commas, semicolons or new lines): trimmed, without blanks, repeats or the [name] itself. */
fun parseAliases(text: String, name: String): List<String> =
    text.split(',', ';', '\n').map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(name.trim(), ignoreCase = true) }
        .distinctBy { it.lowercase() }

/**
 * Removes a codex entry. One the AI found is kept as ignored instead, so the name isn't added
 * again; the reader's own entries are deleted.
 */
suspend fun removeCodexEntry(dao: LibraryDao, entry: CodexEntryEntity) {
    if (entry.origin == CODEX_AI) dao.updateCodexEntry(entry.copy(origin = CODEX_IGNORED, updatedAt = System.currentTimeMillis()))
    else dao.deleteCodexEntry(entry.id)
}

/**
 * Saves a codex entry of the title: a new one when [existing] is null, else a change of it. Naming
 * an entry like another one of the title replaces that one (the unique name would otherwise fail).
 * An entry the reader saves is theirs, also one the AI found or one they had removed. A renamed
 * entry keeps its AI facts.
 */
suspend fun saveCodexEntry(
    dao: LibraryDao,
    sourceId: String,
    titleId: String,
    existing: CodexEntryEntity?,
    name: String,
    kind: String,
    note: String,
    aliases: List<String>,
) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    val now = System.currentTimeMillis()
    val clash = dao.codexWithIgnored(sourceId, titleId).firstOrNull { it.entry.name == clean && it.entry.id != existing?.id }
    if (clash != null && existing != null) dao.deleteCodexEntry(existing.id)
    val base = clash?.entry ?: existing
    if (existing != null && nameKey(existing.name) != nameKey(clean)) {
        dao.moveFacts(sourceId, titleId, nameKey(existing.name), nameKey(clean))
    }
    val entry = CodexEntryEntity(
        id = base?.id ?: 0L,
        sourceId = sourceId,
        titleId = titleId,
        name = clean,
        kind = kind.takeIf { it in CODEX_KINDS } ?: "other",
        note = note.trim().ifEmpty { null },
        summary = base?.summary,
        summaryOrigin = base?.summaryOrigin,
        summaryUpToChapterId = base?.summaryUpToChapterId,
        createdAt = base?.createdAt?.takeIf { it > 0 } ?: now,
        updatedAt = now,
        origin = CODEX_USER,
        firstChapterId = base?.firstChapterId,
    )
    dao.saveCodexEntry(entry, aliases.filter { !it.trim().equals(clean, ignoreCase = true) })
}
