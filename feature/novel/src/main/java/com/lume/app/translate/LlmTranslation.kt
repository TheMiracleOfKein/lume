package com.lume.app.translate

import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.llm.LlmClient
import com.lume.app.llm.extractJsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jsoup.Jsoup
import java.io.IOException

/** A name or term the model rendered that the glossary doesn't have yet. */
data class SuggestedTerm(val source: String, val target: String, val kind: String?)

/**
 * Translates with a chat model through [client]. Each request carries the title's glossary (so
 * names are written as the user wants), the last translated paragraphs (so tone and pronouns
 * carry on) and the style note. The model also lists new names it met; those that pass
 * [filterSuggestions] go to [onTerms] and into the glossary of the following requests, so a
 * name keeps its first rendering for the rest of the chapter and, once saved, the book.
 *
 * [glossary] must be in the same order as the entries the input HTML was marked with, because the
 * `data-g` numbers point into it. [knownSources] are every glossary source of the title, rejected
 * ones included, so those aren't proposed again.
 */
class LlmProvider(
    private val client: LlmClient,
    glossary: List<GlossaryTermEntity>,
    knownSources: Collection<String>,
    private val style: String?,
    private val onTerms: suspend (List<SuggestedTerm>) -> Unit = {},
) : TranslationProvider {
    // The style note shapes the whole text, so another note is another cache entry.
    override val cacheId: String = "llm:${client.config.cacheId}:p$PROMPT_VERSION" +
        (style?.trim()?.takeIf { it.isNotEmpty() }?.let { ":s" + it.hashCode().toUInt().toString(16) } ?: "")
    override val glossaryInCacheKey: Boolean = false
    override val firstRunChars: Int = 1_500
    override val runChars: Int = 6_000

    private val terms = glossary.map { SuggestedTerm(it.source, it.target, it.kind) }.toMutableList()
    private val known = (knownSources + glossary.map { it.source }).map { it.trim().lowercase() }.toMutableSet()
    private val previous = ArrayDeque<String>()
    private val lock = Mutex()

    override suspend fun translate(from: String, to: String, html: List<String>): List<String> = lock.withLock {
        val out = arrayOfNulls<String>(html.size)
        request(from, to, html.indices.toList(), html, out, retried = false)
        out.map { it ?: throw IOException("The AI answer missed a paragraph") }.also { remember(it) }
    }

    /** Translates the paragraphs [ids] of [html] into [out]; splits a part that was cut off. */
    private suspend fun request(from: String, to: String, ids: List<Int>, html: List<String>, out: Array<String?>, retried: Boolean) {
        val answer = client.chat(systemPrompt(from, to, style), userPrompt(ids.map { it to html[it] }, terms, previous.toList()))
        if (answer.truncated) {
            if (ids.size == 1) throw IOException("The paragraph is too long for this model's answer limit")
            request(from, to, ids.take(ids.size / 2), html, out, retried)
            request(from, to, ids.drop(ids.size / 2), html, out, retried)
            return
        }
        val parsed = parseLlmTranslation(answer.content)
        for ((id, text) in parsed.paragraphs) if (id in ids && text.isNotBlank()) out[id] = text
        intake(parsed.terms, ids.joinToString("\n") { plain(html[it]) })
        val missing = ids.filter { out[it] == null }
        if (missing.isEmpty()) return
        if (retried) throw IOException("The AI answered ${ids.size - missing.size} of ${ids.size} paragraphs")
        request(from, to, missing, html, out, retried = true)
    }

    private suspend fun intake(found: List<SuggestedTerm>, original: String) {
        val accepted = filterSuggestions(found, original, known)
        if (accepted.isEmpty()) return
        accepted.forEach { known += it.source.lowercase() }
        terms += accepted
        onTerms(accepted)
    }

    private fun remember(translated: List<String>) {
        for (t in translated.takeLast(PREVIOUS_PARAGRAPHS)) {
            previous.addLast(plain(t).take(PREVIOUS_CHARS))
            while (previous.size > PREVIOUS_PARAGRAPHS) previous.removeFirst()
        }
    }

    companion object {
        /** Raise when the prompt changes, so older cached answers aren't reused. */
        const val PROMPT_VERSION = 1
        private const val PREVIOUS_PARAGRAPHS = 3
        private const val PREVIOUS_CHARS = 400
    }
}

private fun plain(html: String): String = Jsoup.parseBodyFragment(html).text()

private fun promptLanguageName(code: String): String = when (code.lowercase()) {
    "en" -> "English"
    "ru" -> "Russian"
    "uk" -> "Ukrainian"
    "zh" -> "Chinese"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "es" -> "Spanish"
    "pt" -> "Portuguese"
    "de" -> "German"
    "fr" -> "French"
    else -> code
}

internal fun systemPrompt(from: String, to: String, style: String?): String = buildString {
    append("You translate web novels from ${promptLanguageName(from)} into ${promptLanguageName(to)}. ")
    append("Write natural, fluent ${promptLanguageName(to)} prose that keeps the meaning, tone and voice of the original. ")
    append("Keep names and terms consistent with the glossary and with the previous paragraphs.\n")
    append("Rules:\n")
    append("- Each input paragraph has an id. Return exactly one translated paragraph per id. Never merge, split, skip or add paragraphs, and add no notes.\n")
    append("- Keep the HTML tags <b> and <i> around the same words. Keep every <span data-g=\"N\">…</span> element with the same number N, and write glossary item N's target inside it.\n")
    append("- Write every glossary source as its target, with grammatical endings where the language needs them.\n")
    append("- In \"terms\", list the proper names and special terms in these paragraphs that are not in the glossary (characters, places, groups and organizations, items, techniques, titles), each with the translation you used and a kind: character, place, group, item or term. Leave out ordinary words.\n")
    if (!style.isNullOrBlank()) append("Style notes from the reader: ${style.trim()}\n")
    append("Answer with only this JSON object: {\"paragraphs\":[{\"id\":0,\"html\":\"…\"}],\"terms\":[{\"source\":\"…\",\"target\":\"…\",\"kind\":\"character\"}]}")
}

internal fun userPrompt(paragraphs: List<Pair<Int, String>>, glossary: List<SuggestedTerm>, previous: List<String>): String = buildJsonObject {
    putJsonArray("glossary") {
        glossary.forEachIndexed { i, t ->
            addJsonObject {
                put("id", i)
                put("source", t.source)
                put("target", t.target)
                t.kind?.let { put("kind", it) }
            }
        }
    }
    if (previous.isNotEmpty()) putJsonArray("previous_translated") { previous.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("paragraphs") {
        paragraphs.forEach { (id, html) -> addJsonObject { put("id", id); put("html", html) } }
    }
}.toString()

/** What the model answered: translated paragraphs by id, and the new terms it listed. */
internal data class LlmTranslation(val paragraphs: Map<Int, String>, val terms: List<SuggestedTerm>)

internal fun parseLlmTranslation(text: String): LlmTranslation {
    val root = extractJsonObject(text)
    val paragraphs = (root["paragraphs"] as? JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: return@mapNotNull null
        val html = (o["html"] as? JsonPrimitive)?.contentOrNull ?: (o["text"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        id to html
    }?.toMap() ?: throw IOException("The AI answer had no paragraphs")
    val terms = (root["terms"] as? JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val source = (o["source"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val target = (o["target"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        SuggestedTerm(source, target, (o["kind"] as? JsonPrimitive)?.contentOrNull)
    }.orEmpty()
    return LlmTranslation(paragraphs, terms)
}

private val SUGGESTION_KINDS = setOf("character", "place", "group", "item", "term")
private const val MAX_TERM_LENGTH = 60
private const val MAX_TERMS_PER_PART = 20

/**
 * The proposals worth keeping: both sides filled and different, not too long, a name (starts
 * with a capital letter or is CJK), found as a whole word in [original], and not one of [known]
 * (lowercase sources). Kinds outside the codex kinds become null. One per source.
 */
internal fun filterSuggestions(found: List<SuggestedTerm>, original: String, known: Set<String>): List<SuggestedTerm> {
    val seen = HashSet<String>()
    return found.asSequence()
        .map { SuggestedTerm(it.source.trim(), it.target.trim(), it.kind?.trim()?.lowercase()?.takeIf { k -> k in SUGGESTION_KINDS }) }
        .filter { it.source.isNotEmpty() && it.target.isNotEmpty() && it.source.length <= MAX_TERM_LENGTH && it.target.length <= MAX_TERM_LENGTH }
        .filter { !it.source.equals(it.target, ignoreCase = false) }
        .filter { it.source.first().isUpperCase() || isCjkChar(it.source.first()) }
        .filter { it.source.lowercase() !in known }
        .filter { containsWord(original, it.source) }
        .filter { seen.add(it.source.lowercase()) }
        .take(MAX_TERMS_PER_PART)
        .toList()
}

private fun isCjkChar(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
    else -> false
}

private fun containsWord(text: String, term: String): Boolean =
    if (isCjkChar(term.first())) text.contains(term)
    else Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
