package com.lume.app.translate

import android.content.Context
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.plainText

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Translates a chapter's blocks one for one (same count and order, so positions, progress and
 * read aloud keep working) through the add-on, and caches the engine's output per chapter.
 *
 * The cache keeps the engine HTML with its glossary marks, keyed by the original text, the provider, the languages
 * and the glossary's source terms. Changing only a term's target re-renders from the cache; a
 * new term or an edited chapter translates again.
 */
class ChapterTranslator(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "translate").apply { mkdirs() }
    private val json = Json
    private val listSerializer = ListSerializer(String.serializer().nullable)

    /** The translated blocks, from the cache only; null when this chapter wasn't translated yet. */
    suspend fun cached(blocks: List<NovelBlock>, route: TranslationRoute, glossary: List<GlossaryEntry>): List<NovelBlock>? =
        withContext(Dispatchers.IO) {
            val file = cacheFile(blocks, route, glossary)
            val raw = runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrNull() ?: return@withContext null
            if (raw.size != blocks.size) return@withContext null
            file.setLastModified(System.currentTimeMillis())
            render(blocks, raw, glossary)
        }

    /**
     * Translates [blocks], nearest [start] first so the screen fills before the rest. [onPartial]
     * gets the blocks so far (untranslated ones still in the original) after every batch.
     */
    suspend fun translate(
        blocks: List<NovelBlock>,
        route: TranslationRoute,
        glossary: List<GlossaryEntry>,
        start: Int = 0,
        onPartial: suspend (blocks: List<NovelBlock>, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<NovelBlock> {
        cached(blocks, route, glossary)?.let { return it }
        val inputs = withContext(Dispatchers.Default) { blocks.map { TranslationMarkup.toHtml(it, glossary) } }
        val raw = arrayOfNulls<String>(blocks.size)
        val order = readingOrderFrom(start.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)), blocks.size).filter { inputs[it] != null }
        val current = blocks.toMutableList()
        var done = 0
        for (run in runs(order, inputs)) {
            val out = route.provider.translate(route.from, route.to, run.map { inputs[it]!! })
            run.forEachIndexed { k, i ->
                raw[i] = out[k]
                current[i] = TranslationMarkup.fromHtml(blocks[i], out[k], glossary)
            }
            done += run.size
            onPartial(current.toList(), done, order.size)
        }
        withContext(Dispatchers.IO) {
            runCatching {
                cacheFile(blocks, route, glossary).writeText(json.encodeToString(listSerializer, raw.toList()))
                trimCache()
            }
        }
        return current
    }

    /** Re-renders cached engine output with the current glossary targets. */
    private fun render(blocks: List<NovelBlock>, raw: List<String?>, glossary: List<GlossaryEntry>): List<NovelBlock> =
        blocks.mapIndexed { i, block -> raw[i]?.let { TranslationMarkup.fromHtml(block, it, glossary) } ?: block }

    private fun cacheFile(blocks: List<NovelBlock>, route: TranslationRoute, glossary: List<GlossaryEntry>): File {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(s: String) = digest.update((s + "\u0000").toByteArray())
        add("v1")
        add(route.provider.cacheId)
        add("${route.from}>${route.to}")
        glossary.forEach { add(it.source.trim().lowercase()) }
        add("|")
        blocks.forEach { b ->
            when (b) {
                is NovelBlock.Paragraph -> b.spans.forEach { add("${it.bold}${it.italic}${it.text}") }
                is NovelBlock.Heading -> add("h" + b.spans.plainText)
                else -> add("-")
            }
            add("/")
        }
        val name = digest.digest().joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$name.json")
    }

    /** Keeps the newest [MAX_CACHED] chapters. */
    private fun trimCache() {
        val files = cacheDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_CACHED).forEach { it.delete() }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_CACHED = 300

        /** The first engine call: about a screen of text, so reading can start soon. */
        const val FIRST_RUN_CHARS = 1_000

        /** Later calls: larger, fewer round trips. */
        const val RUN_CHARS = 3_000
    }

    /** Consecutive pieces of [order]: a short first one, then about [RUN_CHARS] characters each. */
    private fun runs(order: List<Int>, inputs: List<String?>): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var size = 0
        for (i in order) {
            val len = inputs[i]!!.length
            val limit = if (out.isEmpty()) FIRST_RUN_CHARS else RUN_CHARS
            if (current.isNotEmpty() && size + len > limit) {
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
}

/** A provider and the languages it translates between, for one title. */
data class TranslationRoute(val provider: TranslationProvider, val from: String, val to: String)

/** Indices [start] to the end, then the ones before [start] nearest first. */
internal fun readingOrderFrom(start: Int, count: Int): List<Int> =
    if (count <= 0) emptyList() else (start until count).toList() + (start - 1 downTo 0).toList()
