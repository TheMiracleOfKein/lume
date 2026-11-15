package com.lume.app.domain

import java.text.Normalizer

/** One setting as the search sees it: texts already in the app language. */
data class SearchableSetting(
    val id: String,
    val title: String,
    val section: String,
    val hint: String = "",
    /** Related words, separated by commas ("dark, night, OLED"). */
    val keywords: String = "",
)

/**
 * Settings that match every word of [query], best first: words found in the title beat words
 * found in the section, the explanation or the keywords; a title that starts with the query
 * comes first. Case, accents and ё / е don't matter. A blank query finds nothing.
 */
fun searchSettings(query: String, settings: List<SearchableSetting>): List<SearchableSetting> {
    val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val phrase = words.joinToString(" ")
    return settings.mapNotNull { setting ->
        val title = normalizeForSearch(setting.title)
        val rest = normalizeForSearch("${setting.section} ${setting.hint} ${setting.keywords}")
        var score = 0
        for (word in words) {
            score += when {
                title.containsWordStart(word) -> 4
                word in title -> 3
                rest.containsWordStart(word) -> 2
                word in rest -> 1
                else -> return@mapNotNull null
            }
        }
        if (title.startsWith(phrase)) score += 10
        setting to score
    }
        .sortedByDescending { it.second }
        .map { it.first }
}

/** Lower case, no accents, ё → е, every run of punctuation or spaces turned into one space. */
fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    var space = true
    for (c in decomposed) {
        when {
            Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
            c.isLetterOrDigit() -> {
                builder.append(c)
                space = false
            }
            !space -> {
                builder.append(' ')
                space = true
            }
        }
    }
    // NFD splits "ё" into "е" plus a mark, so it is already "е" here.
    return builder.toString().trimEnd()
}

private fun String.containsWordStart(word: String): Boolean =
    startsWith(word) || contains(" $word")
