package com.lume.app.domain

/**
 * Is a title on another source the same title? Used to find a library title on the user's other
 * sources ("Also on …"). Names and alternative names are compared after normalising; authors
 * confirm a close but not exact name, and contradict an exact one.
 */
enum class TitleMatch { Same, Maybe, Different }

/** The title and every alternative name ("A / B; C" lists split into names). */
fun titleNames(title: String, altTitle: String?): List<String> =
    (listOf(title) + altTitle.orEmpty().split('/', ';', '|', '\n'))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** Letters and digits only, lower case: "Solo Leveling!" == "solo leveling". */
fun normalName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

private fun words(name: String): Set<String> =
    name.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()

/** 1.0 when two names are equal after [normalName], else the share of words they have in common. */
fun nameSimilarity(a: String, b: String): Double {
    val na = normalName(a)
    val nb = normalName(b)
    if (na.isEmpty() || nb.isEmpty()) return 0.0
    if (na == nb) return 1.0
    val wa = words(a)
    val wb = words(b)
    val union = (wa + wb).size
    // Same words in another spelling ("The X" / "X, The") is close, but never "equal".
    return if (union == 0) 0.0 else minOf(0.99, (wa intersect wb).size.toDouble() / union)
}

/** The best similarity between any of [a] and any of [b]. */
fun bestNameSimilarity(a: List<String>, b: List<String>): Double =
    a.maxOfOrNull { x -> b.maxOfOrNull { y -> nameSimilarity(x, y) } ?: 0.0 } ?: 0.0

/**
 * True when some author is on both lists. Word order is ignored, so "Oh Seong-dae" and
 * "Seong-Dae Oh" are one person. Null when either list is empty (nothing to compare).
 */
fun authorsOverlap(a: List<String>, b: List<String>): Boolean? {
    if (a.isEmpty() || b.isEmpty()) return null
    fun key(name: String) = words(name).sorted().joinToString(" ")
    val keys = a.map(::key).filter { it.isNotEmpty() }.toSet()
    return b.any { key(it) in keys }
}

/**
 * How sure we are that [candidate] is [wanted]:
 * - [TitleMatch.Same]: a name is equal and the authors don't contradict it, or names are close
 *   (most words shared) and an author matches;
 * - [TitleMatch.Maybe]: names are close, or equal but with different authors;
 * - [TitleMatch.Different]: otherwise.
 */
fun matchTitle(wanted: TitleDetails, candidate: TitleDetails): TitleMatch {
    val similarity = bestNameSimilarity(
        titleNames(wanted.title, wanted.altTitle),
        titleNames(candidate.title, candidate.altTitle),
    )
    val authors = authorsOverlap(wanted.authors + wanted.artists, candidate.authors + candidate.artists)
    return when {
        similarity >= 1.0 && authors != false -> TitleMatch.Same
        similarity >= CLOSE_NAMES && authors == true -> TitleMatch.Same
        similarity >= MAYBE_NAMES -> TitleMatch.Maybe
        else -> TitleMatch.Different
    }
}

/** A search result worth opening to compare details: its name is at least close. */
fun worthComparing(wantedNames: List<String>, result: TitleSummary): Boolean =
    bestNameSimilarity(wantedNames, listOfNotNull(result.title, result.subtitle)) >= MAYBE_NAMES

private const val CLOSE_NAMES = 0.6
private const val MAYBE_NAMES = 0.5
