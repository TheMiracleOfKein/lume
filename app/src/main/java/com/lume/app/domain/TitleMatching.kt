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

/** Every name of [details]: its title, alternative names and the source's other names. */
fun allNames(details: TitleDetails): List<String> =
    (titleNames(details.title, details.altTitle) + details.otherNames.map { it.trim() })
        .filter { it.isNotEmpty() }
        .distinctBy { normalName(it).ifEmpty { it } }

/**
 * Names to search other sources with, best first: the title, then the other names (sources put
 * English and Russian ones first), then the alternative title (often in the original script).
 */
fun searchNames(details: TitleDetails): List<String> =
    (listOf(details.title) + details.otherNames + titleNames(details.title, details.altTitle))
        .map { it.trim() }
        .filter { normalName(it).isNotEmpty() }
        .distinctBy { normalName(it) }

/** Letters and digits only, lower case: "Solo Leveling!" == "solo leveling". */
fun normalName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

private fun words(name: String): Set<String> =
    name.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()

private val ARTICLES = setOf("the", "a", "an")

/** The name without punctuation, case and articles: "The Beginning After the End" = "Beginning after end". */
private fun coreName(name: String): String =
    name.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() && it !in ARTICLES }.joinToString("")

/**
 * 1.0 when two names are equal after [normalName] (or equal but for articles), else the share
 * of words they have in common, below 1.0.
 */
fun nameSimilarity(a: String, b: String): Double {
    val na = normalName(a)
    val nb = normalName(b)
    if (na.isEmpty() || nb.isEmpty()) return 0.0
    if (na == nb || coreName(a).let { it.isNotEmpty() && it == coreName(b) }) return 1.0
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
 * True when some author is on both lists. Spelling differs between sites, so word order, hyphens
 * and spaces are ignored ("Oh Seong-dae" = "Seong-Dae Oh", "Chu-Gong" = "Chugong"), and a shared
 * name part of 4+ letters is enough ("DUBU (Redice Studio)" = "Dubu"). Null when either list is
 * empty (nothing to compare).
 */
fun authorsOverlap(a: List<String>, b: List<String>): Boolean? {
    val left = a.filter { normalName(it).isNotEmpty() }
    val right = b.filter { normalName(it).isNotEmpty() }
    if (left.isEmpty() || right.isEmpty()) return null
    fun joined(name: String) = words(name).sorted().joinToString("")
    fun parts(name: String) = words(name).filter { it.length >= 4 }.toSet() + normalName(name)
    return left.any { x ->
        right.any { y ->
            joined(x) == joined(y) || normalName(x) == normalName(y) || (parts(x) intersect parts(y)).isNotEmpty()
        }
    }
}

/**
 * How sure we are that [candidate] is [wanted]:
 * - [TitleMatch.Same]: a name is equal and the authors don't contradict it;
 * - [TitleMatch.Maybe]: names are close, or equal but with different authors. Close is not enough
 *   even with the same author: spin-offs ("Solo Leveling: Ragnarok") share both;
 * - [TitleMatch.Different]: otherwise.
 */
fun matchTitle(wanted: TitleDetails, candidate: TitleDetails): TitleMatch {
    val similarity = bestNameSimilarity(allNames(wanted), allNames(candidate))
    val authors = authorsOverlap(wanted.authors + wanted.artists, candidate.authors + candidate.artists)
    return when {
        similarity >= 1.0 && authors != false -> TitleMatch.Same
        similarity >= MAYBE_NAMES -> TitleMatch.Maybe
        else -> TitleMatch.Different
    }
}

/** A search result worth opening to compare details: its name is at least close. */
fun worthComparing(wantedNames: List<String>, result: TitleSummary): Boolean =
    bestNameSimilarity(wantedNames, listOfNotNull(result.title, result.subtitle)) >= MAYBE_NAMES

private const val MAYBE_NAMES = 0.5
