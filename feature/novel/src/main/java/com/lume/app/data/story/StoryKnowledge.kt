package com.lume.app.data.story

import com.lume.app.AppContainer
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.db.CodexRelationEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.inReadingOrder
import com.lume.app.llm.LlmClient
import com.lume.app.llm.extractJsonObject
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
     * Writes [entry]'s summary and relations for a reader in [current] (of [chapters]) and saves
     * them. [mentions] are the entry's mentions before the spot; [text] loads a chapter's paragraphs.
     */
    suspend fun summarize(
        client: LlmClient,
        entry: CodexEntryWithAliases,
        codex: List<CodexEntryWithAliases>,
        chapters: List<Chapter>,
        current: Chapter,
        mentions: List<StoryMention>,
        lang: String,
        text: suspend (Chapter) -> List<String>?,
    ) {
        val ordered = chapters.inReadingOrder()
        val at = ordered.indexOfFirst { it.id == current.id }
        val before = if (at <= 0) emptyList() else ordered.subList(0, at)
        // The last few chapters get a digest now if they have none; older ones count only when they have one.
        for (ch in before.takeLast(AiRecap.RECAP_CHAPTERS)) {
            try {
                recap.digest(client, ch, lang) { text(ch) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A gap in the digests still leaves the mentions.
            }
        }
        val byId = dao.titleDigests(sourceId, titleId, lang).associateBy { it.chapterId }
        val digests = before.mapNotNull { ch -> byId[ch.id]?.let { ch.title.ifBlank { ch.id } to it.text } }.takeLast(MAX_DIGESTS)
        if (digests.isEmpty() && mentions.isEmpty()) throw IOException("Nothing read about this name yet")
        val others = codex.filter { it.entry.id != entry.entry.id }.map { it.entry.name }
        val answer = client.chat(
            knowledgeSystemPrompt(lang),
            knowledgeUserPrompt(entry, others, digests, mentions.map { it.chapter.title.ifBlank { it.chapter.id } to it.text }),
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

    private companion object {
        /** Older chapters than this many digests back are left out, so the request stays small. */
        const val MAX_DIGESTS = 30
    }
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
