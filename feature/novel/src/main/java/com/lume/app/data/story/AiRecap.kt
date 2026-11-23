package com.lume.app.data.story

import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterDigestEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.inReadingOrder
import com.lume.app.llm.AiFeature
import com.lume.app.llm.AiQueue
import com.lume.app.llm.LlmClient
import com.lume.app.llm.extractJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.security.MessageDigest

/** The AI part of "Previously…": being written, written, or failed with a message. */
sealed interface AiRecapState {
    data object Loading : AiRecapState
    data class Ready(val text: String) : AiRecapState
    data class Failed(val message: String) : AiRecapState
}

/**
 * AI recap of a title. Each read chapter gets a short digest, written once from that chapter's
 * text only and kept in `chapter_digests`. "Previously…" then joins the digests of the last few
 * chapters before the reading spot and the current chapter's text up to the spot into a short
 * summary. Nothing after the spot is ever sent, so the summary can't spoil.
 *
 * To keep requests few: a digest in any language serves (the summary is written in the wanted
 * one anyway), missing digests are written together in one request, the same digest is never
 * asked for twice at once ([AiQueue]), and a summary is kept until the reader moves on.
 */
class AiRecap(app: AppContainer, private val sourceId: String, private val titleId: String) {
    private val dao = app.dao
    private val cacheDir = File(app.app.cacheDir, "ai-recap")

    /**
     * The digest of [chapter]: a stored one in any language, or a new one in [lang] from [text]
     * (the chapter's paragraphs; null when its text can't be had). Null when there is nothing to digest.
     */
    suspend fun digest(client: LlmClient, chapter: Chapter, lang: String, text: suspend () -> List<String>?): String? =
        digests(client, listOf(chapter), lang) { text() }[chapter.id]

    /**
     * Digests of [chapters] by chapter id: the stored ones, and the missing ones written now, several
     * chapters per request (up to [BATCH_CHARS] of text). Chapters whose text can't be had, or that
     * a request failed for, are left out.
     */
    suspend fun digests(
        client: LlmClient,
        chapters: List<Chapter>,
        lang: String,
        text: suspend (Chapter) -> List<String>?,
    ): Map<String, String> {
        if (chapters.isEmpty()) return emptyMap()
        val stored = storedDigests(chapters.map { it.id }, lang)
        val missing = chapters.filter { it.id !in stored }
        if (missing.isEmpty()) return stored
        val made = AiQueue.serial("digest|$sourceId|$titleId") {
            // A job that ran before this one may have written some.
            val again = storedDigests(missing.map { it.id }, lang)
            val inputs = missing.filter { it.id !in again }.mapNotNull { ch ->
                text(ch)?.let(::digestInput)?.takeIf { it.isNotBlank() }?.let { ch to it }
            }
            again + writeDigests(client, inputs, lang)
        }
        return stored + made
    }

    /** Stored digests of [ids], the one in [lang] where there are several. */
    private suspend fun storedDigests(ids: List<String>, lang: String): Map<String, String> =
        dao.digestsAnyLang(sourceId, titleId, ids)
            .groupBy { it.chapterId }
            .mapValues { (_, list) -> (list.firstOrNull { it.lang == lang } ?: list.maxBy { it.createdAt }).text }

    private suspend fun writeDigests(client: LlmClient, inputs: List<Pair<Chapter, String>>, lang: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (batch in digestBatches(inputs.map { it.second.length }, BATCH_CHARS)) {
            val part = batch.map { inputs[it] }
            try {
                val answer = client.chat(
                    digestSystemPrompt(lang),
                    digestUserPrompt(part.mapIndexed { i, (_, t) -> (i + 1).toString() to t }),
                    maxTokens = DIGEST_TOKENS * part.size + 200,
                    feature = AiFeature.Recap,
                )
                val texts = parseDigests(answer.content)
                part.forEachIndexed { i, (ch, _) ->
                    val digest = texts[(i + 1).toString()]?.trim()?.take(MAX_DIGEST_CHARS)?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
                    dao.upsertDigest(ChapterDigestEntity(sourceId, titleId, ch.id, lang, digest, client.config.cacheId, System.currentTimeMillis()))
                    out[ch.id] = digest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One failed batch leaves a gap; a single chapter's failure is the caller's.
                if (inputs.size == 1) throw e
            }
        }
        return out
    }

    /**
     * The "Previously…" summary for a reader in [current] at the paragraphs [currentSoFar]: the
     * digests of up to [RECAP_CHAPTERS] chapters of [chapters] before it (made now when missing)
     * and the end of the text so far. Null when there is nothing to summarize. A summary written
     * for the same spot (within [ANCHOR_BUCKET] paragraphs) and the same digests is reused.
     */
    suspend fun recap(
        client: LlmClient,
        chapters: List<Chapter>,
        current: Chapter,
        currentSoFar: List<String>,
        lang: String,
        text: suspend (Chapter) -> List<String>?,
    ): String? {
        val ordered = chapters.inReadingOrder()
        val at = ordered.indexOfFirst { it.id == current.id }
        val before = if (at <= 0) emptyList() else ordered.subList((at - RECAP_CHAPTERS).coerceAtLeast(0), at)
        val found = try {
            digests(client, before, lang, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A missing digest leaves a gap; the summary is still useful without it.
            emptyMap()
        }
        val digests = before.mapNotNull { ch -> found[ch.id]?.let { chapterLabel(ch) to it } }
        val soFar = currentTail(currentSoFar)
        if (digests.isEmpty() && soFar.isBlank()) return null
        val file = recapFile(current.id, currentSoFar.size / ANCHOR_BUCKET, lang, client.config.cacheId, digests)
        withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }?.takeIf { it.isNotBlank() }?.let { return it }
        val answer = client.chat(
            recapSystemPrompt(lang), recapUserPrompt(digests, chapterLabel(current), soFar),
            json = false, temperature = 0.4, maxTokens = RECAP_TOKENS, feature = AiFeature.Recap,
        )
        val recap = answer.content.trim().takeIf { it.isNotEmpty() } ?: return null
        withContext(Dispatchers.IO) {
            runCatching {
                cacheDir.mkdirs()
                file.writeText(recap)
                // Only the newest few matter: a reader comes back to one spot per title.
                cacheDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_CACHED_RECAPS)?.forEach { it.delete() }
            }
        }
        return recap
    }

    private fun recapFile(chapterId: String, bucket: Int, lang: String, model: String, digests: List<Pair<String, String>>): File {
        val md = MessageDigest.getInstance("SHA-256")
        listOf(sourceId, titleId, chapterId, bucket.toString(), lang, model).forEach { md.update((it + "\u0000").toByteArray()) }
        digests.forEach { (c, d) -> md.update((c + "\u0001" + d + "\u0000").toByteArray()) }
        return File(cacheDir, md.digest().joinToString("") { "%02x".format(it) } + ".txt")
    }

    companion object {
        /** How many earlier chapters the summary looks back on. */
        const val RECAP_CHAPTERS = 4
        private const val MAX_DIGEST_CHARS = 1_500

        /** Chapter text per digest request; a longer single chapter still goes alone. */
        internal const val BATCH_CHARS = 40_000

        /** Answer room per digest (about 120 words in any script) and for the summary. */
        private const val DIGEST_TOKENS = 350
        private const val RECAP_TOKENS = 500

        /** Paragraphs a reader may move and still get the same summary. */
        private const val ANCHOR_BUCKET = 10
        private const val MAX_CACHED_RECAPS = 50
    }
}

private fun chapterLabel(c: Chapter): String = c.title.ifBlank { c.number?.let { "Chapter $it" } ?: c.id }

/** A chapter's text for its digest: whole when short, else its start and end around a "…". */
internal fun digestInput(paragraphs: List<String>, maxChars: Int = 24_000): String {
    val text = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    if (text.length <= maxChars) return text
    val head = maxChars * 2 / 3
    return text.take(head) + "\n…\n" + text.takeLast(maxChars - head)
}

/** Indices of texts of [lengths] in consecutive groups of at most [maxChars] each; a longer text goes alone. */
internal fun digestBatches(lengths: List<Int>, maxChars: Int): List<List<Int>> {
    val out = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    var size = 0
    lengths.forEachIndexed { i, len ->
        if (current.isNotEmpty() && size + len > maxChars) {
            out += current
            current = mutableListOf()
            size = 0
        }
        current += i
        size += len
    }
    if (current.isNotEmpty()) out += current
    return out
}

/** The end of the current chapter up to the spot, at most [maxChars], starting at a paragraph where it can. */
internal fun currentTail(paragraphs: List<String>, maxChars: Int = 4_000): String {
    val text = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    if (text.length <= maxChars) return text
    val cut = text.takeLast(maxChars)
    val nl = cut.indexOf('\n')
    return if (nl in 0 until maxChars / 2) cut.substring(nl + 1) else cut
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

internal fun digestSystemPrompt(lang: String): String =
    "You summarize chapters of a novel for a reader who has already read them. For each chapter, " +
        "write in ${langName(lang)}, 3 to 6 sentences, under 120 words, plain text without headings or lists. " +
        "Name the characters involved and say what happened, what changed, and any open question the chapter ends on. " +
        "Use only what is in that chapter; don't guess what comes later. Write names as the text has them, translated to ${langName(lang)} where the reader would expect it. " +
        "Answer with only this JSON object: {\"chapters\":[{\"id\":\"1\",\"digest\":\"…\"}]}"

internal fun digestUserPrompt(chapters: List<Pair<String, String>>): String = buildJsonObject {
    putJsonArray("chapters") { chapters.forEach { (id, text) -> addJsonObject { put("id", id); put("text", text) } } }
}.toString()

/** Digest text by chapter id from the model's answer. */
internal fun parseDigests(text: String): Map<String, String> {
    val root = extractJsonObject(text)
    return (root["chapters"] as? JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val digest = (o["digest"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        id to digest
    }?.toMap().orEmpty()
}

internal fun recapSystemPrompt(lang: String): String =
    "You help a reader come back to a novel after a break. From the chapter digests and the text the reader read last, " +
        "write a short \"Previously…\" in ${langName(lang)}: 4 to 7 sentences, under 150 words, plain text. " +
        "Start with the bigger picture and end with exactly where the reader stopped, so they can read on. " +
        "Use only what is given; never guess what happens next."

internal fun recapUserPrompt(digests: List<Pair<String, String>>, currentChapter: String, soFar: String): String = buildJsonObject {
    putJsonArray("earlier_chapters") {
        digests.forEach { (chapter, digest) -> addJsonObject { put("chapter", chapter); put("digest", digest) } }
    }
    put("current_chapter", currentChapter)
    put("read_so_far_in_current_chapter", JsonPrimitive(soFar))
}.toString()
