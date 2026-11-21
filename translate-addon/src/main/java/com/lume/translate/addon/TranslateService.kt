package com.lume.translate.addon

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.lume.translate.IDownloadCallback
import com.lume.translate.ILumeTranslator
import com.lume.translate.ITranslateCallback
import com.lume.translate.ModelCatalog
import com.lume.translate.ModelPair
import com.lume.translate.TranslateContract
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The add-on's only entry point. Bound by Lume; the manifest and every call require the
 * signature permission, so no other app can use the engine or the downloads.
 */
class TranslateService : Service() {
    private lateinit var store: ModelStore
    private lateinit var engine: Engine
    private val downloads = Executors.newSingleThreadExecutor { r -> Thread(r, "lume-translate-download") }
    private val running = ConcurrentHashMap<String, AtomicBoolean>()

    override fun onCreate() {
        super.onCreate()
        store = ModelStore(File(filesDir, "models"))
        engine = Engine(store)
    }

    override fun onDestroy() {
        running.values.forEach { it.set(true) }
        downloads.shutdownNow()
        engine.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun pair(id: String): ModelPair = requireNotNull(ModelCatalog.find(id)) { "Unknown language pair $id" }

    private fun enforce() = enforceCallingPermission(TranslateContract.PERMISSION, "Lume only")

    private val binder = object : ILumeTranslator.Stub() {
        override fun apiVersion(): Int = TranslateContract.API_VERSION

        override fun installedPairs(): Array<String> {
            enforce()
            return store.installedPairs().map { it.id }.toTypedArray()
        }

        override fun download(pairId: String, callback: IDownloadCallback) {
            enforce()
            val pair = pair(pairId)
            val cancel = AtomicBoolean(false)
            if (running.putIfAbsent(pair.id, cancel) != null) return
            downloads.execute {
                try {
                    var lastPercent = -1L
                    store.download(pair, cancelled = cancel::get) { done, total ->
                        val percent = done * 100 / total
                        if (percent != lastPercent) {
                            lastPercent = percent
                            runCatching { callback.onProgress(done, total) }
                        }
                    }
                    runCatching { callback.onDone() }
                } catch (e: Exception) {
                    runCatching { callback.onError(if (e is ModelStore.CancelledException) CANCELLED else e.message ?: e.toString()) }
                } finally {
                    running.remove(pair.id)
                }
            }
        }

        override fun cancelDownload(pairId: String) {
            enforce()
            running[pairId]?.set(true)
        }

        override fun deletePair(pairId: String): Boolean {
            enforce()
            val pair = pair(pairId)
            engine.release(pair)
            return store.delete(pair)
        }

        override fun translate(pairId: String, html: Array<String>, callback: ITranslateCallback) {
            enforce()
            val pair = pair(pairId)
            engine.translate(
                pair,
                html.toList(),
                onResult = { runCatching { callback.onResult(it.toTypedArray()) } },
                onError = { runCatching { callback.onError(it) } },
            )
        }
    }

    private companion object {
        const val CANCELLED = "cancelled"
    }
}
