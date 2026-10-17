package com.lume.app.domain

/**
 * Some sources (MangaLib, YaoiLib, HentaiLib) list one chapter once per translation team.
 * These helpers show one row per chapter and let the reader pick a team.
 */

/** One translation team (branch) of a title. */
data class ChapterBranch(
    val id: String,
    val name: String,
    val chapterCount: Int,
)

/**
 * Equal for every team's copy of one chapter: volume + number when the source gives a number,
 * the chapter id otherwise (so chapters without numbers are never merged).
 */
fun dedupeKey(chapter: Chapter): String {
    val number = chapter.number?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() }
        ?: return "id:${chapter.id}"
    val normalized = number.toDoubleOrNull()?.let { d ->
        if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    } ?: number.lowercase()
    val volume = chapter.volume?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
    return "v$volume#c$normalized"
}

/** The title's teams, most chapters first; empty when there is at most one team. */
fun chapterBranches(chapters: List<Chapter>): List<ChapterBranch> {
    val counts = LinkedHashMap<String, Int>()
    val names = HashMap<String, String>()
    for (ch in chapters) {
        val id = ch.branchId ?: continue
        counts[id] = (counts[id] ?: 0) + 1
        ch.branchName?.takeIf { it.isNotBlank() }?.let { names.putIfAbsent(id, it) }
    }
    if (counts.size <= 1) return emptyList()
    return counts.entries
        .map { (id, count) -> ChapterBranch(id, names[id] ?: id, count) }
        .sortedByDescending { it.chapterCount }
}

/**
 * One chapter per [dedupeKey], in the list's order. Each chapter comes from [preferredBranch]
 * when that team has it, else from the team with the most chapters, else the first copy.
 */
fun dedupeChapters(chapters: List<Chapter>, preferredBranch: String? = null): List<Chapter> {
    if (chapters.size <= 1) return chapters
    val branchRank = chapterBranches(chapters).withIndex().associate { (i, b) -> b.id to i }
    if (branchRank.isEmpty()) {
        // One team (or none): only exact duplicates can exist.
        return chapters.distinctBy { it.id }
    }
    fun rank(ch: Chapter): Int = when {
        preferredBranch != null && ch.branchId == preferredBranch -> -1
        else -> branchRank[ch.branchId] ?: Int.MAX_VALUE
    }
    val best = LinkedHashMap<String, Chapter>()
    for (ch in chapters) {
        val key = dedupeKey(ch)
        val current = best[key]
        if (current == null || rank(ch) < rank(current)) best[key] = ch
    }
    // Keep the position of the key's first copy, so the list order stays the source's order.
    return best.values.toList()
}
