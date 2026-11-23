package com.lume.app.translate

import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.data.story.ChapterNotes
import com.lume.app.data.story.intakeNames
import com.lume.app.data.story.notesInstructions
import com.lume.app.data.story.parseNotes
import com.lume.app.llm.AiFeature
import com.lume.app.llm.LlmClient
import com.lume.app.llm.baseName
import com.lume.app.llm.extractJsonObject
import com.lume.app.llm.isBaseName
import com.lume.app.llm.nameKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jsoup.Jsoup
import java.io.IOException

/** A name or term the model rendered that the glossary doesn't have yet. */
data class SuggestedTerm(val source: String, val target: String, val kind: String?)

/**
 * Translates with a chat model through [client]. Each request carries the glossary items its text
 * contains (so names are written as the user wants, without sending the whole glossary every
 * time), the last translated paragraphs (so tone and pronouns
 * carry on) and the style note. The model also lists new names it met; those that pass
 * [filterSuggestions] go to [onTerms] and into the glossary of the following requests, so a
 * name keeps its first rendering for the rest of the chapter and, once saved, the book.
 *
 * [glossary] must be in the same order as the entries the input HTML was marked with, because the
 * `data-g` numbers point into it. [knownSources] are every glossary source of the title, rejected
 * ones included, so those aren't proposed again.
 *
 * With [notes] on, each answer also brings story notes on its paragraphs (a summary, the names in
 * them with a fact each, relations), in the target language: the chapter's digest and codex
 * knowledge come with the translation, without a request of their own. [takeNotes] hands them over.
 */
class LlmProvider(
    private val client: LlmClient,
    glossary: List<GlossaryTermEntity>,
    knownSources: Collection<String>,
    private val style: String?,
    private val notes: Boolean = false,
    private val onTerms: suspend (List<SuggestedTerm>) -> Unit = {},
) : TranslationProvider {
    // The style note shapes the whole text, so another note is another cache entry.
    override val cacheId: String = "llm:${client.config.cacheId}:p$PROMPT_VERSION" +
        (style?.trim()?.takeIf { it.isNotEmpty() }?.let { ":s" + it.hashCode().toUInt().toString(16) } ?: "")
    override val glossaryInCacheKey: Boolean = false
    // Fewer, larger parts: each request repeats the instructions and the previous paragraphs.
    override val firstRunChars: Int = 1_500
    override val runChars: Int = 10_000

    private val terms = glossary.map { SuggestedTerm(it.source, it.target, it.kind) }.toMutableList()

    /** How many of [terms] the input HTML was marked with; later ones were found during this session. */
    private val marked = glossary.size
    private val known = (knownSources + glossary.map { it.source }).map(::nameKey).toMutableSet()
    private val previous = ArrayDeque<String>()
    private val lock = Mutex()

    /** The service and model, for what the notes were written by. */
    val model: String get() = client.config.cacheId

    /** Where in the chapter the next [translate] call's paragraphs start; set by [ChapterTranslator], orders the notes. */
    internal var position: Int = 0

    /** Notes of the parts translated so far: their position in the chapter, how many paragraphs, the notes. */
    private val partNotes = mutableListOf<Triple<Int, Int, ChapterNotes>>()

    /**
     * The notes of everything translated since the last call, as one in chapter order, and how many
     * paragraphs they cover; null when there are none. Names get their glossary translation.
     */
    fun takeNotes(): Pair<ChapterNotes, Int>? {
        if (partNotes.isEmpty()) return null
        val parts = partNotes.sortedBy { it.first }
        partNotes.clear()
        val targets = terms.associate { nameKey(it.source) to it.target }
        val joined = ChapterNotes.join(parts.map { it.third })
        return joined.copy(names = joined.names.map { n -> n.copy(target = n.target ?: targets[nameKey(n.name)]) }) to parts.sumOf { it.second }
    }

    override suspend fun translate(from: String, to: String, html: List<String>): List<String> = lock.withLock {
        val out = arrayOfNulls<String>(html.size)
        request(from, to, html.indices.toList(), html, out, retried = false)
        out.map { it ?: throw IOException("The AI answer missed a paragraph") }.also { remember(it) }
    }

    /**
     * The glossary items this request needs: those marked in its HTML, and the terms found earlier
     * in this session (not marked, as the chapter was marked before) that occur in its text.
     */
    private fun glossaryFor(html: List<String>, plainText: String): Map<Int, SuggestedTerm> {
        val ids = sortedSetOf<Int>()
        for (h in html) GLOSSARY_MARK.findAll(h).forEach { m -> m.groupValues[1].toIntOrNull()?.let(ids::add) }
        for (i in marked until terms.size) if (containsWord(plainText, terms[i].source)) ids += i
        return ids.filter { it in terms.indices }.associateWith { terms[it] }
    }

    /** Translates the paragraphs [ids] of [html] into [out]; splits a part that was cut off. */
    private suspend fun request(from: String, to: String, ids: List<Int>, html: List<String>, out: Array<String?>, retried: Boolean) {
        val parts = ids.map { html[it] }
        val plainText = parts.joinToString("\n") { plain(it) }
        val answer = client.chat(
            systemPrompt(from, to, style, notes),
            userPrompt(ids.map { it to html[it] }, glossaryFor(parts, plainText), previous.toList()),
            maxTokens = answerTokens(parts.sumOf { it.length }) + if (notes) NOTES_TOKENS else 0,
            feature = AiFeature.Translation,
        )
        if (answer.truncated) {
            if (ids.size == 1) throw IOException("The paragraph is too long for this model's answer limit")
            request(from, to, ids.take(ids.size / 2), html, out, retried)
            request(from, to, ids.drop(ids.size / 2), html, out, retried)
            return
        }
        val parsed = parseLlmTranslation(answer.content)
        for ((id, text) in parsed.paragraphs) if (id in ids && text.isNotBlank()) out[id] = text
        intake(parsed.terms, plainText)
        // A second try for missed paragraphs would only repeat notes the first answer gave.
        if (notes && !retried && !parsed.notes.isEmpty) {
            partNotes += Triple(position + ids.min(), ids.size, parsed.notes.copy(names = intakeNames(parsed.notes.names, plainText)))
        }
        val missing = ids.filter { out[it] == null }
        if (missing.isEmpty()) return
        if (retried) throw IOException("The AI answered ${ids.size - missing.size} of ${ids.size} paragraphs")
        request(from, to, missing, html, out, retried = true)
    }

    private suspend fun intake(found: List<SuggestedTerm>, original: String) {
        val accepted = filterSuggestions(found, original, known)
        if (accepted.isEmpty()) return
        accepted.forEach { known += nameKey(it.source) }
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
        /**
         * Raise when the prompt changes what a translation looks like, so older cached answers
         * aren't reused. Not for changes that only save tokens (v1.11.0's per-request glossary):
         * raising it makes every cached chapter cost a new request.
         */
        const val PROMPT_VERSION = 1
        private const val PREVIOUS_PARAGRAPHS = 3
        private const val PREVIOUS_CHARS = 400
        private val GLOSSARY_MARK = Regex("""data-g="(\d+)"""")

        /** Answer room for a part's notes. */
        private const val NOTES_TOKENS = 800
    }
}

/**
 * The answer cap for [inputChars] of HTML: room for a translation somewhat longer than the
 * original, in a script that may take more tokens (about a token per character at most), plus the
 * term list. It only stops a runaway answer; a cut-off answer is split and asked again.
 */
internal fun answerTokens(inputChars: Int): Int = inputChars + 1_000

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

internal fun systemPrompt(from: String, to: String, style: String?, notes: Boolean = false): String = buildString {
    append("You translate web novels from ${promptLanguageName(from)} into ${promptLanguageName(to)}. ")
    append("Write natural, fluent ${promptLanguageName(to)} prose that keeps the meaning, tone and voice of the original. ")
    append("Keep names and terms consistent with the glossary and with the previous paragraphs.\n")
    append("Rules:\n")
    append("- Each input paragraph has an id. Return exactly one translated paragraph per id. Never merge, split, skip or add paragraphs, and add no comments of your own to them.\n")
    append("- Keep the HTML tags <b> and <i> around the same words. Keep every <span data-g=\"N\">…</span> element with the same number N, and write glossary item N's target inside it.\n")
    append("- The glossary maps N to [source, target]. Write every glossary source as its target, with grammatical endings where the language needs them.\n")
    append("- In \"terms\", list the proper names and special terms in these paragraphs that are not in the glossary (characters, places, groups and organizations, items, techniques, titles), each with the translation you used and a kind: character, place, group, item or term. Give each name once, in its base form without honorifics or possessive endings (\"Yu San A\", not \"Yu San A-san\" or \"Yu San A's\"). Leave out ordinary words.\n")
    if (notes) {
        append("- In \"notes\", for the reader's story notes on these paragraphs: ")
        append(notesInstructions(promptLanguageName(to), "what happens in these paragraphs, 1 to 3 sentences"))
        append("\n")
    }
    if (!style.isNullOrBlank()) append("Style notes from the reader: ${style.trim()}\n")
    append("Answer with only this JSON object: {\"paragraphs\":[{\"id\":0,\"html\":\"…\"}],\"terms\":[{\"source\":\"…\",\"target\":\"…\",\"kind\":\"character\"}]")
    if (notes) append(",\"notes\":{\"summary\":\"…\",\"names\":[],\"relations\":[]}")
    append("}")
}

/** The request: only the [glossary] items it needs, by their `data-g` number, as `"N":[source, target]`. */
internal fun userPrompt(paragraphs: List<Pair<Int, String>>, glossary: Map<Int, SuggestedTerm>, previous: List<String>): String = buildJsonObject {
    if (glossary.isNotEmpty()) {
        putJsonObject("glossary") {
            glossary.forEach { (n, t) -> putJsonArray(n.toString()) { add(t.source); add(t.target) } }
        }
    }
    if (previous.isNotEmpty()) putJsonArray("previous_translated") { previous.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("paragraphs") {
        paragraphs.forEach { (id, html) -> addJsonObject { put("id", id); put("html", html) } }
    }
}.toString()

/** What the model answered: translated paragraphs by id, the new terms it listed, and its story notes (if asked). */
internal data class LlmTranslation(val paragraphs: Map<Int, String>, val terms: List<SuggestedTerm>, val notes: ChapterNotes = ChapterNotes())

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
    return LlmTranslation(paragraphs, terms, parseNotes(root["notes"] as? JsonObject))
}

private val SUGGESTION_KINDS = setOf("character", "place", "group", "item", "term")
private const val MAX_TERM_LENGTH = 60
private const val MAX_TERMS_PER_PART = 20

/**
 * The proposals worth keeping: both sides filled and different, not too long, a name (starts
 * with a capital letter or is CJK), found as a whole word in [original], and whose [nameKey] is
 * not one of [known]. Each is taken in its base form ("Yu San A-san" → "Yu San A", the target
 * likewise), so forms of one name become one term; the base still matches inside the longer
 * forms. Kinds outside the codex kinds become null.
 */
internal fun filterSuggestions(found: List<SuggestedTerm>, original: String, known: Set<String>): List<SuggestedTerm> {
    val seen = HashSet<String>()
    return found.asSequence()
        .map { SuggestedTerm(it.source.trim(), it.target.trim(), it.kind?.trim()?.lowercase()?.takeIf { k -> k in SUGGESTION_KINDS }) }
        .map { t -> if (isBaseName(t.source)) t else t.copy(source = baseName(t.source), target = baseName(t.target)) }
        .filter { it.source.isNotEmpty() && it.target.isNotEmpty() && it.source.length <= MAX_TERM_LENGTH && it.target.length <= MAX_TERM_LENGTH }
        .filter { !it.source.equals(it.target, ignoreCase = false) }
        .filter { it.source.first().isUpperCase() || isCjkChar(it.source.first()) }
        .filter { nameKey(it.source) !in known }
        .filter { containsWord(original, it.source) }
        .filter { seen.add(nameKey(it.source)) }
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
