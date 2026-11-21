package com.lume.translate.addon

import com.lume.translate.ModelPair
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Runs every native call on one thread. Keeps the last used model loaded and frees it after
 * [IDLE_MS] without work, so the add-on doesn't hold ~150 MB while Lume reads in the original.
 */
internal class Engine(private val store: ModelStore) {
    private val thread = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "lume-translate").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private var loadedId: String? = null
    private var handle = 0L
    private var idle: ScheduledFuture<*>? = null

    fun translate(pair: ModelPair, html: List<String>, onResult: (List<String>) -> Unit, onError: (String) -> Unit) {
        thread.execute {
            idle?.cancel(false)
            try {
                val h = ensureLoaded(pair)
                val out = BergamotNative.translate(h, Array(html.size) { html[it].toByteArray() }, true)
                onResult(out.map { String(it) })
            } catch (e: Throwable) {
                onError(e.message ?: e.toString())
            } finally {
                idle = thread.schedule(::unload, IDLE_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Frees the model if [pair] is loaded (before its files are deleted). */
    fun release(pair: ModelPair) {
        thread.execute { if (loadedId == pair.id) unload() }
    }

    fun shutdown() {
        thread.execute(::unload)
        thread.shutdown()
    }

    private fun ensureLoaded(pair: ModelPair): Long {
        if (loadedId == pair.id && handle != 0L) return handle
        unload()
        check(store.isInstalled(pair)) { "The ${pair.id} language pack isn't downloaded" }
        val config = EngineConfig.yaml(store.dir(pair), pair.model.name, pair.vocab.name, pair.shortlist.name)
        handle = BergamotNative.load(config.toByteArray())
        loadedId = pair.id
        return handle
    }

    private fun unload() {
        if (handle != 0L) BergamotNative.free(handle)
        handle = 0L
        loadedId = null
    }

    private companion object {
        const val IDLE_MS = 2 * 60 * 1000L
    }
}
