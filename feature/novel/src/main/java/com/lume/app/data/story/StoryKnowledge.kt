package com.lume.app.data.story

import com.lume.app.AppContainer
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.db.CodexRelationEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.inReadingOrder
import com.lume.app.data.db.CODEX_AI
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexFactEntity
import com.lume.app.llm.AiFeature
import com.lume.app.llm.LlmClient
import com.lume.app.llm.extractJsonObject
import com.lume.app.llm.nameKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException

/** What the AI said about a codex entry: a description and its relations to other names. */
internal data class EntryKnowledge(val summary: String, val relations: List<Pair<String, String>>)

/**
 * AI descriptions of codex entries. A summary is written from what the reader has seen up to the
 * spot only: digests of earlier chapters (missing recent ones are made first, see [AiRecap]) and
 * the entry's mentions before the spot. The entry keeps the chapter it was written up to, so a
 * reader further back never sees it.
 */
class StoryKnowledge(app: AppContainer, private val sourceId: String, private val titleId: String) {
    private val dao = app.dao
    private val recap = AiRecap(app, sourceId, titleId)

    /**
     * Writes [entry]'s summary for a reader in [current] (of [chapters]) and saves it: from the
     * name's story-note facts when it has any, else from digests and [mentions] (the entry's
     * mentions before the spot), which also give relations. The last [fill] chapters before the
     * spot get their story notes first if they have none; [text] loads a chapter's paragraphs.
     */
    suspend fun summarize(
        client: LlmClient,
        entry: CodexEntryWithAliases,
        codex: List<CodexEntryWithAliases>,
        chapters: List<Chapter>,
        current: Chapter,
        mentions: List<StoryMention>,
        lang: String,
        fill: Int = AiRecap.RECAP_CHAPTERS,
        text: suspend (Chapter) -> List<String>?,
    ) {
        val ordered = chapters.inReadingOrder()
        val order = ordered.withIndex().associate { (i, c) -> c.id to i }
        val at = ordered.indexOfFirst { it.id == current.id }
        val before = if (at <= 0) emptyList() else ordered.subList(0, at)
        // The last [fill] chapters get story notes now if they have none (several per request); older ones count only when they have them.
        try {
            recap.digests(client, before.takeLast(fill), lang, order, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A gap in the notes still leaves the rest.
        }
        // Story notes already say what each chapter showed about the name: a summary of those is a small request.
        val facts = factsBefore(dao.factsFor(sourceId, titleId, entryKeys(entry)), order, current.id)
        if (facts.isNotEmpty()) {
            summarizeFacts(client, entry, facts, ordered, lang)
            return
        }
        val byId = dao.titleDigestsAnyLang(sourceId, titleId).groupBy { it.chapterId }
            .mapValues { (_, list) -> (list.firstOrNull { it.lang == lang } ?: list.maxBy { it.createdAt }).text }
        val all = before.mapNotNull { ch -> byId[ch.id]?.let { ch.title.ifBlank { ch.id } to it } }
        val digests = relevantDigests(all, listOf(entry.entry.name) + entry.aliases.map { it.alias })
        if (digests.isEmpty() && mentions.isEmpty()) throw IOException("Nothing read about this name yet")
        val others = codex.filter { it.entry.id != entry.entry.id }.map { it.entry.name }
        val answer = client.chat(
            knowledgeSystemPrompt(lang),
            knowledgeUserPrompt(entry, others, digests, mentions.map { it.chapter.title.ifBlank { it.chapter.id } to it.text }),
            maxTokens = SUMMARY_TOKENS,
            feature = AiFeature.Summary,
        )
        val knowledge = parseKnowledge(answer.content, entry)
        val now = System.currentTimeMillis()
        val origin = "llm:${client.config.cacheId}"
        dao.updateCodexEntry(
            entry.entry.copy(summary = knowledge.summary, summaryOrigin = origin, summaryUpToChapterId = current.id, updatedAt = now),
        )
        dao.replaceRelationsFrom(
            sourceId, titleId, entry.entry.name,
            knowledge.relations.map { (name, label) ->
                CodexRelationEntity(
                    sourceId = sourceId, titleId = titleId, fromName = entry.entry.name, toName = name,
                    label = label, upToChapterId = current.id, origin = origin, createdAt = now,
                )
            },
        )
    }

    /**
     * The summary of [entry] from its [facts] (in reading order, all before the spot). Kept up to the
     * chapter of the last fact, so a reader anywhere after it sees it; asking again with no newer
     * facts costs nothing. Relations come from the story notes themselves.
     */
    private suspend fun summarizeFacts(
        client: LlmClient,
        entry: CodexEntryWithAliases,
        facts: List<CodexFactEntity>,
        ordered: List<Chapter>,
        lang: String,
    ) {
        val upTo = facts.last().chapterId
        val e = entry.entry
        if (e.summary != null && e.summaryUpToChapterId == upTo && e.summaryOrigin?.startsWith(FACTS_ORIGIN) == true) return
        val labels = ordered.associate { it.id to it.title.ifBlank { it.id } }
        val answer = client.chat(
            factsSystemPrompt(lang),
            factsUserPrompt(entry, facts.map { (labels[it.chapterId] ?: it.chapterId) to it.text }),
            maxTokens = FACTS_SUMMARY_TOKENS,
            feature = AiFeature.Summary,
        )
        val summary = parseKnowledgeSummary(answer.content)
        dao.updateCodexEntry(
            e.copy(summary = summary, summaryOrigin = "$FACTS_ORIGIN${client.config.cacheId}", summaryUpToChapterId = upTo, updatedAt = System.currentTimeMillis()),
        )
    }

    private companion object {
        /** Room for the summary and up to 8 relations. */
        const val SUMMARY_TOKENS = 700
        const val FACTS_SUMMARY_TOKENS = 350

        /** Origin prefix of a summary written from story-note facts. */
        const val FACTS_ORIGIN = "facts:"
    }
}

/** Keys of every spelling of [entry], for its facts. */
internal fun entryKeys(entry: CodexEntryWithAliases): List<String> =
    (listOf(entry.entry.name) + entry.aliases.map { it.alias }).map(::nameKey).distinct()

/**
 * [facts] a reader in [currentChapterId] may see, in reading order ([order]): only from chapters
 * before the current one, as a chapter's notes cover all of it and the reader may be part way.
 * Several spellings with a fact in one chapter give one fact each.
 */
internal fun factsBefore(facts: List<CodexFactEntity>, order: Map<String, Int>, currentChapterId: String): List<CodexFactEntity> {
    val at = order[currentChapterId] ?: return emptyList()
    return facts.filter { (order[it.chapterId] ?: Int.MAX_VALUE) < at }
        .distinctBy { it.chapterId to it.text }
        .sortedBy { order[it.chapterId] }
}

internal fun factsSystemPrompt(lang: String): String =
    "You keep a reader's notes on a novel. From what each chapter showed about a name, describe it in ${langName(lang)} for a reader " +
        "at this point of the story: who or what it is, its role, and what has happened with it so far, the latest last. " +
        "2 to 5 sentences, under 100 words. Use only what is given; never guess what comes later. " +
        "Answer with only this JSON object: {\"summary\":\"…\"}"

internal fun factsUserPrompt(entry: CodexEntryWithAliases, facts: List<Pair<String, String>>): String = buildJsonObject {
    put("name", entry.entry.name)
    put("kind", entry.entry.kind)
    if (entry.aliases.isNotEmpty()) putJsonArray("other_spellings") { entry.aliases.forEach { add(JsonPrimitive(it.alias)) } }
    entry.entry.note?.let { put("reader_note", it) }
    putJsonArray("facts") { facts.forEach { (c, t) -> addJsonObject { put("chapter", c); put("fact", t) } } }
}.toString()

/** The summary of a facts answer; required. */
internal fun parseKnowledgeSummary(text: String): String =
    (extractJsonObject(text)["summary"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.take(1_200)
        ?: throw IOException("The AI answer had no summary")

/** Older chapters than this many digests back are left out, so the request stays small. */
private const val MAX_DIGESTS = 30

/** The last digests, kept for context even when they don't name the entry. */
private const val CONTEXT_DIGESTS = 4

/**
 * The digests a summary of a name needs, from [all] (chapter label to digest, in reading order):
 * those that mention one of [names], plus the last [CONTEXT_DIGESTS] for where the story is now,
 * at most [MAX_DIGESTS]. A minor character then costs a few digests, not the whole book.
 */
internal fun relevantDigests(all: List<Pair<String, String>>, names: List<String>): List<Pair<String, String>> {
    val needles = names.map { it.trim() }.filter { it.length >= 2 }
    val lastStart = (all.size - CONTEXT_DIGESTS).coerceAtLeast(0)
    return all.filterIndexed { i, (_, digest) -> i >= lastStart || needles.any { digest.contains(it, ignoreCase = true) } }
        .takeLast(MAX_DIGESTS)
}

/**
 * Whether something written up to [upToChapterId] may be shown to a reader in [currentChapterId]:
 * only when that chapter is at or before the reader's in [chapters]' reading order. Unknown
 * chapters count as further on.
 */
fun visibleAt(upToChapterId: String?, chapters: List<Chapter>, currentChapterId: String): Boolean {
    if (upToChapterId == null) return false
    val ordered = chapters.inReadingOrder()
    val upTo = ordered.indexOfFirst { it.id == upToChapterId }
    val current = ordered.indexOfFirst { it.id == currentChapterId }
    return upTo >= 0 && current >= 0 && upTo <= current
}

/**
 * Like [visibleAt], but only for chapters before the reader's: for what story notes wrote about a
 * whole chapter, which a reader part way through it may not have reached.
 */
fun visibleBefore(chapterId: String?, chapters: List<Chapter>, currentChapterId: String): Boolean {
    if (chapterId == null) return false
    val ordered = chapters.inReadingOrder()
    val at = ordered.indexOfFirst { it.id == chapterId }
    val current = ordered.indexOfFirst { it.id == currentChapterId }
    return at >= 0 && current >= 0 && at < current
}

/** Whether a reader in [currentChapterId] may see [entry]: an entry the AI found only from the chapter it was first seen in. */
fun entryVisible(entry: CodexEntryEntity, chapters: List<Chapter>, currentChapterId: String): Boolean =
    entry.origin != CODEX_AI || entry.firstChapterId == null || visibleAt(entry.firstChapterId, chapters, currentChapterId)

/** Whether a reader in [currentChapterId] may see [entry]'s summary: one from story-note facts only after its last chapter. */
fun summaryVisible(entry: CodexEntryEntity, chapters: List<Chapter>, currentChapterId: String): Boolean =
    entry.summary != null && if (entry.summaryOrigin?.startsWith("facts:") == true) {
        visibleBefore(entry.summaryUpToChapterId, chapters, currentChapterId)
    } else {
        visibleAt(entry.summaryUpToChapterId, chapters, currentChapterId)
    }

/** Whether a reader in [currentChapterId] may see [relation]; one from story notes only after its chapter. */
fun relationVisible(relation: CodexRelationEntity, chapters: List<Chapter>, currentChapterId: String): Boolean =
    if (relation.origin.startsWith(StoryNotes.NOTES_ORIGIN)) visibleBefore(relation.upToChapterId, chapters, currentChapterId)
    else visibleAt(relation.upToChapterId, chapters, currentChapterId)

private fun langName(code: String): String = when (code.lowercase()) {
    "ru" -> "Russian"
    "en" -> "English"
    "uk" -> "Ukrainian"
    "es" -> "Spanish"
    "pt" -> "Portuguese"
    "de" -> "German"
    "fr" -> "French"
    else -> code
}

internal fun knowledgeSystemPrompt(lang: String): String =
    "You keep a reader's notes on a novel: characters, places, organizations and things. " +
        "From the chapter digests and the passages that mention the name, describe it in ${langName(lang)} for a reader at this point of the story: " +
        "who or what it is, its role, and what has happened with it so far. 2 to 5 sentences, under 100 words. " +
        "Also list its relations to other names (family, allies, rivals, membership, ownership, places it belongs to), " +
        "preferring names from known_names, each with a short label in ${langName(lang)} saying what that other name is to this one, " +
        "such as \"sister\", \"mentor\", \"organization it belongs to\" or \"home town\". At most 8. " +
        "Use only what is given; never guess what comes later. " +
        "Answer with only this JSON object: {\"summary\":\"…\",\"relations\":[{\"name\":\"…\",\"relation\":\"…\"}]}"

internal fun knowledgeUserPrompt(
    entry: CodexEntryWithAliases,
    knownNames: List<String>,
    digests: List<Pair<String, String>>,
    mentions: List<Pair<String, String>>,
): String = buildJsonObject {
    put("name", entry.entry.name)
    put("kind", entry.entry.kind)
    if (entry.aliases.isNotEmpty()) putJsonArray("other_spellings") { entry.aliases.forEach { add(JsonPrimitive(it.alias)) } }
    entry.entry.note?.let { put("reader_note", it) }
    putJsonArray("known_names") { knownNames.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("chapter_digests") { digests.forEach { (c, d) -> addJsonObject { put("chapter", c); put("digest", d) } } }
    putJsonArray("passages") { mentions.forEach { (c, t) -> addJsonObject { put("chapter", c); put("text", t.take(800)) } } }
}.toString()

private const val MAX_RELATIONS = 8
private const val MAX_NAME = 60
private const val MAX_LABEL = 80

/** The model's answer for [entry]: a summary (required) and relations to names other than the entry's own. */
internal fun parseKnowledge(text: String, entry: CodexEntryWithAliases): EntryKnowledge {
    val root: JsonObject = extractJsonObject(text)
    val summary = (root["summary"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IOException("The AI answer had no summary")
    val own = (listOf(entry.entry.name) + entry.aliases.map { it.alias }).map { it.lowercase() }.toSet()
    val seen = HashSet<String>()
    val relations = (root["relations"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val label = ((o["relation"] ?: o["label"]) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (name.isEmpty() || label.isEmpty() || name.length > MAX_NAME || label.length > MAX_LABEL) return@mapNotNull null
        if (name.lowercase() in own || !seen.add(name.lowercase())) return@mapNotNull null
        name to label
    }.take(MAX_RELATIONS)
    return EntryKnowledge(summary.take(1_200), relations)
}
