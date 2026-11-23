package com.lume.app.translate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.lume.app.AppContainer
import com.lume.app.data.update.AppUpdater
import com.lume.translate.IDownloadCallback
import com.lume.translate.ILumeTranslator
import com.lume.translate.ITranslateCallback
import com.lume.translate.ModelPair
import com.lume.translate.TranslateContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** What Lume knows about the Lume Translate add-on. */
sealed interface AddonState {
    data object Unknown : AddonState
    data object NotInstalled : AddonState

    /** Installed, but too old (or too new) for this Lume: an update of one of them is needed. */
    data class Incompatible(val version: String) : AddonState

    /** Installed but signed with another key (a debug build next to a release, for example). */
    data object WrongSignature : AddonState

    data class Ready(val version: String, val installedPairs: Set<String>) : AddonState
}

sealed interface PackDownload {
    data class Progress(val done: Long, val total: Long) : PackDownload
    data object Done : PackDownload
}

/**
 * Talks to the optional Lume Translate add-on: finds it, installs or updates it through the same
 * release download as Lume's own updates, and binds to its service to download language packs
 * and translate. One per process, like [com.lume.app.readaloud.ReadAloud]; Lume itself never
 * contacts Mozilla, the add-on does.
 */
object TranslateAddon {
    private val _state = MutableStateFlow<AddonState>(AddonState.Unknown)
    val state: StateFlow<AddonState> = _state.asStateFlow()

    private var context: Context? = null
    private var updater: AppUpdater? = null
    private val bindLock = Mutex()
    private var service: ILumeTranslator? = null
    private var connection: ServiceConnection? = null

    /** Installs or updates the add-on from its newest own release (`translate-v*`). */
    fun updater(app: AppContainer): AppUpdater {
        updater?.let { return it }
        val appContext = app.app
        context = appContext
        return AppUpdater(
            app = app.app,
            http = app.http,
            scope = app.appScope,
            flavor = TranslateContract.RELEASE_FLAVOR,
            currentVersion = { installedVersion(appContext) },
            prefsName = "translate_addon_updates",
            downloadDir = "translate-addon",
            tagPrefix = TranslateContract.RELEASE_TAG_PREFIX,
        ).also { updater = it }
    }

    fun installedVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(TranslateContract.ADDON_PACKAGE, 0).versionName ?: "0"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Looks at the add-on again (after an install, or when the reader opens). */
    suspend fun refresh(context: Context): AddonState {
        this.context = context.applicationContext
        val version = installedVersion(context)
        if (version == null) {
            unbind()
            return AddonState.NotInstalled.also { _state.value = it }
        }
        val next = try {
            val s = bound(context)
            if (s.apiVersion() != TranslateContract.API_VERSION) AddonState.Incompatible(version)
            else AddonState.Ready(version, s.installedPairs().toSet())
        } catch (_: SecurityException) {
            AddonState.WrongSignature
        } catch (_: Exception) {
            unbind()
            AddonState.Incompatible(version)
        }
        _state.value = next
        return next
    }

    fun isReady(pair: ModelPair): Boolean = (_state.value as? AddonState.Ready)?.installedPairs?.contains(pair.id) == true

    /** Downloads a language pack inside the add-on. Cancel by cancelling the collector. */
    fun download(context: Context, pair: ModelPair): Flow<PackDownload> = callbackFlow {
        val s = bound(context)
        s.download(
            pair.id,
            object : IDownloadCallback.Stub() {
                override fun onProgress(done: Long, total: Long) {
                    trySend(PackDownload.Progress(done, total))
                }

                override fun onDone() {
                    trySend(PackDownload.Done)
                    close()
                }

                override fun onError(message: String?) {
                    close(IOException(message ?: "Download failed"))
                }
            },
        )
        awaitClose {
            runCatching { s.cancelDownload(pair.id) }
        }
    }

    suspend fun delete(context: Context, pair: ModelPair): Boolean = bound(context).deletePair(pair.id).also { refresh(context) }

    /** Translates HTML paragraphs in order, in batches the binder can carry. */
    suspend fun translate(context: Context, pair: ModelPair, html: List<String>): List<String> {
        if (html.isEmpty()) return emptyList()
        val s = bound(context)
        val out = ArrayList<String>(html.size)
        for (batch in batches(html, TranslateContract.MAX_BATCH_CHARS)) {
            val result = CompletableDeferred<List<String>>()
            s.translate(
                pair.id,
                batch.toTypedArray(),
                object : ITranslateCallback.Stub() {
                    override fun onResult(html: Array<out String>?) {
                        result.complete(html?.toList().orEmpty())
                    }

                    override fun onError(message: String?) {
                        result.completeExceptionally(IOException(message ?: "Translation failed"))
                    }
                },
            )
            val translated = result.await()
            if (translated.size != batch.size) throw IOException("The add-on answered ${translated.size} of ${batch.size} paragraphs")
            out += translated
        }
        return out
    }

    private suspend fun bound(context: Context): ILumeTranslator = bindLock.withLock {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        unbindLocked()
        val ready = CompletableDeferred<ILumeTranslator>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                val s = ILumeTranslator.Stub.asInterface(binder)
                service = s
                ready.complete(s)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }

            override fun onBindingDied(name: ComponentName?) {
                service = null
                ready.completeExceptionally(IOException("The translation add-on stopped"))
            }

            override fun onNullBinding(name: ComponentName?) {
                ready.completeExceptionally(IOException("The translation add-on refused the connection"))
            }
        }
        val intent = Intent(TranslateContract.SERVICE_ACTION).setPackage(TranslateContract.ADDON_PACKAGE)
        // Throws SecurityException when the add-on is signed with another key.
        val ok = context.applicationContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!ok) {
            runCatching { context.applicationContext.unbindService(conn) }
            throw IOException("The translation add-on isn't installed")
        }
        connection = conn
        withTimeout(BIND_TIMEOUT_MS) { ready.await() }
    }

    /** Lets the add-on's process go (the reader closed). */
    suspend fun unbind() = bindLock.withLock { unbindLocked() }

    private fun unbindLocked() {
        connection?.let { conn -> runCatching { context?.unbindService(conn) } }
        connection = null
        service = null
    }

    private const val BIND_TIMEOUT_MS = 10_000L
}

/** Splits [items] into runs whose total length stays under [maxChars]; a longer item goes alone. */
internal fun batches(items: List<String>, maxChars: Int): List<List<String>> {
    val out = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    var size = 0
    for (item in items) {
        if (current.isNotEmpty() && size + item.length > maxChars) {
            out += current
            current = mutableListOf()
            size = 0
        }
        current += item
        size += item.length
    }
    if (current.isNotEmpty()) out += current
    return out
}
