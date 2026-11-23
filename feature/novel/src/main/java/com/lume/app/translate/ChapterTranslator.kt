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
            val stored = runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrNull() ?: return@withContext null
            if (stored.size != blocks.size) return@withContext null
            file.setLastModified(System.currentTimeMillis())
            val raw = if (route.provider.glossaryInCacheKey) stored else stored.map { it?.let { h -> TranslationMarkup.sourcesToMarks(h, glossary) } }
            render(blocks, raw, glossary)
        }

    /** Drops this chapter's cached translation, so the next [translate] asks the engine again. */
    suspend fun forget(blocks: List<NovelBlock>, route: TranslationRoute, glossary: List<GlossaryEntry>) {
        withContext(Dispatchers.IO) {
            val file = cacheFile(blocks, route, glossary)
            file.delete()
            File(cacheDir, file.name.removeSuffix(".json") + PART_SUFFIX).delete()
        }
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
        val file = cacheFile(blocks, route, glossary)
        val partFile = File(cacheDir, file.name.removeSuffix(".json") + PART_SUFFIX)
        val raw = arrayOfNulls<String>(blocks.size)
        val current = blocks.toMutableList()
        // Parts that came back before an error (a timeout, a closed reader) aren't asked for again.
        readStored(partFile, blocks.size, route, glossary)?.forEachIndexed { i, h ->
            if (h != null && inputs[i] != null) {
                raw[i] = h
                current[i] = TranslationMarkup.fromHtml(blocks[i], h, glossary)
            }
        }
        val total = inputs.count { it != null }
        val order = readingOrderFrom(start.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)), blocks.size)
            .filter { inputs[it] != null && raw[it] == null }
        var done = total - order.size
        if (done > 0) onPartial(current.toList(), done, total)
        for (run in runs(order, inputs, route.provider.firstRunChars, route.provider.runChars)) {
            // Notes of the parts are put in chapter order, whichever part was translated first.
            (route.provider as? LlmProvider)?.position = run.min()
            val out = route.provider.translate(route.from, route.to, run.map { inputs[it]!! })
            run.forEachIndexed { k, i ->
                raw[i] = out[k]
                current[i] = TranslationMarkup.fromHtml(blocks[i], out[k], glossary)
            }
            done += run.size
            if (done < total) writeStored(partFile, raw, route, glossary)
            onPartial(current.toList(), done, total)
        }
        withContext(Dispatchers.IO) {
            writeStored(file, raw, route, glossary)
            partFile.delete()
            runCatching { trimCache() }
        }
        return current
    }

    /** Engine output saved in [file] for [size] blocks, with glossary marks for the current [glossary]; null when missing or stale. */
    private suspend fun readStored(file: File, size: Int, route: TranslationRoute, glossary: List<GlossaryEntry>): List<String?>? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            val stored = runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrNull() ?: return@withContext null
            if (stored.size != size) return@withContext null
            if (route.provider.glossaryInCacheKey) stored else stored.map { it?.let { h -> TranslationMarkup.sourcesToMarks(h, glossary) } }
        }

    /** Saves engine output [raw] to [file]; engines that don't key the cache by the glossary store sources, not mark numbers. */
    private suspend fun writeStored(file: File, raw: Array<String?>, route: TranslationRoute, glossary: List<GlossaryEntry>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val stored = if (route.provider.glossaryInCacheKey) raw.toList() else raw.map { it?.let { h -> TranslationMarkup.marksToSources(h, glossary) } }
                file.writeText(json.encodeToString(listSerializer, stored))
            }
        }
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
        // Engines that add terms themselves keep their cache when the glossary grows.
        if (route.provider.glossaryInCacheKey) glossary.forEach { add(it.source.trim().lowercase()) }
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

        /** A chapter translated part way: the parts done so far, the rest null. */
        const val PART_SUFFIX = ".part.json"
    }

    /** Consecutive pieces of [order]: a short first one ([firstChars]), then about [runChars] characters each. */
    private fun runs(order: List<Int>, inputs: List<String?>, firstChars: Int, runChars: Int): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var size = 0
        for (i in order) {
            val len = inputs[i]!!.length
            val limit = if (out.isEmpty()) firstChars else runChars
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
