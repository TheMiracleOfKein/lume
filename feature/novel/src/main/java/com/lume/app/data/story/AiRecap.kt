package com.lume.app.data.story

import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterDigestEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.inReadingOrder
import com.lume.app.llm.LlmClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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
 */
class AiRecap(app: AppContainer, private val sourceId: String, private val titleId: String) {
    private val dao = app.dao

    /**
     * The digest of [chapter] in [lang]: the stored one, or a new one from [text] (the chapter's
     * paragraphs; null when its text can't be had). Null when there is nothing to digest.
     */
    suspend fun digest(client: LlmClient, chapter: Chapter, lang: String, text: suspend () -> List<String>?): String? {
        dao.digests(sourceId, titleId, lang, listOf(chapter.id)).firstOrNull()?.let { return it.text }
        val input = text()?.let(::digestInput)?.takeIf { it.isNotBlank() } ?: return null
        val answer = client.chat(digestSystemPrompt(lang), input, json = false).content.trim().take(MAX_DIGEST_CHARS)
        if (answer.isBlank()) return null
        dao.upsertDigest(ChapterDigestEntity(sourceId, titleId, chapter.id, lang, answer, client.config.cacheId, System.currentTimeMillis()))
        return answer
    }

    /**
     * The "Previously…" summary for a reader in [current] at the paragraphs [currentSoFar]: the
     * digests of up to [RECAP_CHAPTERS] chapters of [chapters] before it (made now when missing)
     * and the end of the text so far. Null when there is nothing to summarize.
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
        val digests = before.mapNotNull { ch ->
            try {
                digest(client, ch, lang) { text(ch) }?.let { chapterLabel(ch) to it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One missing digest leaves a gap; the summary is still useful without it.
                null
            }
        }
        val soFar = currentTail(currentSoFar)
        if (digests.isEmpty() && soFar.isBlank()) return null
        val answer = client.chat(recapSystemPrompt(lang), recapUserPrompt(digests, chapterLabel(current), soFar), json = false, temperature = 0.4)
        return answer.content.trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        /** How many earlier chapters the summary looks back on. */
        const val RECAP_CHAPTERS = 4
        private const val MAX_DIGEST_CHARS = 1_500
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

/** The end of the current chapter up to the spot, at most [maxChars], starting at a paragraph where it can. */
internal fun currentTail(paragraphs: List<String>, maxChars: Int = 6_000): String {
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
    "You summarize one chapter of a novel for a reader who has already read it. " +
        "Write in ${langName(lang)}, 3 to 6 sentences, under 120 words, plain text without headings or lists. " +
        "Name the characters involved and say what happened, what changed, and any open question the chapter ends on. " +
        "Use only what is in the chapter; don't guess what comes later. Write names as the text has them, translated to ${langName(lang)} where the reader would expect it."

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
