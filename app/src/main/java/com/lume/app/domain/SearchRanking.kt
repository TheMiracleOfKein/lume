package com.lume.app.domain

/**
 * How well a result's name matches the search text, best (0) first. Sources also return titles
 * whose description, tags or characters mention the words; those land in [RANK_ELSEWHERE].
 */
fun nameMatchRank(query: String, names: List<String>): Int {
    val q = normalName(query)
    if (q.isEmpty()) return RANK_ELSEWHERE
    val queryWords = searchWords(query)
    var best = RANK_ELSEWHERE
    for (name in names) {
        val n = normalName(name)
        if (n.isEmpty()) continue
        val words = searchWords(name)
        val rank = when {
            n == q -> 0
            n.startsWith(q) -> 1
            queryWords.isNotEmpty() && containsRun(words, queryWords) -> 2
            q in n -> 3
            queryWords.isNotEmpty() && queryWords.all { w -> words.any { it.startsWith(w) } } -> 4
            queryWords.any { w -> w.length >= 3 && words.any { it.startsWith(w) } } -> 5
            else -> RANK_ELSEWHERE
        }
        if (rank < best) best = rank
        if (best == 0) break
    }
    return best
}

/**
 * [items] with the ones whose name matches [query] first, best match first; the source's own
 * order is kept within each rank (it is often popularity or relevance).
 */
fun <T> rankByName(query: String, items: List<T>, names: (T) -> List<String>): List<T> {
    if (normalName(query).isEmpty() || items.size < 2) return items
    return items.withIndex()
        .sortedWith(compareBy({ nameMatchRank(query, names(it.value)) }, { it.index }))
        .map { it.value }
}

/** Search results ranked by their title and alternative name. */
fun rankSearchResults(query: String, items: List<TitleSummary>): List<TitleSummary> =
    rankByName(query, items) { listOfNotNull(it.title, it.subtitle) }

private const val RANK_ELSEWHERE = 6

private fun searchWords(text: String): List<String> =
    text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

/** [run] appears in [words] as consecutive whole words. */
private fun containsRun(words: List<String>, run: List<String>): Boolean {
    if (run.size > words.size) return false
    for (start in 0..words.size - run.size) {
        if (run.indices.all { words[start + it] == run[it] }) return true
    }
    return false
}
