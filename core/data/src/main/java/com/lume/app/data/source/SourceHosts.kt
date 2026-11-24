package com.lume.app.data.source

import android.content.Context
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.domain.HostsFile
import com.lume.app.domain.resolvePackUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

/**
 * The addresses of sources as the hosts file last said (`docs/packs/hosts.json` on `main`), so a
 * moved API does not need an app update. The file is cached; without it, or with the setting off,
 * the addresses inside the app are used.
 */
class SourceHosts(
    context: Context,
    private val http: AppHttp,
    private val prefs: AppPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cacheFile = File(context.filesDir, "hosts.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Mutex()
    private val _file = MutableStateFlow(HostsFile())

    /** The last good file, as downloaded (not yet checked for the sources it applies to). */
    val file: StateFlow<HostsFile> = _file.asStateFlow()

    @Volatile
    private var lastAttempt = 0L

    /** Reads the cached file. Call before the sources load. */
    suspend fun loadCached() = withContext(Dispatchers.IO) {
        if (!prefs.remoteHosts.value) return@withContext
        runCatching {
            if (cacheFile.isFile) _file.value = json.decodeFromString(HostsFile.serializer(), cacheFile.readText())
        }
    }

    /**
     * Downloads the file when the last attempt is older than [minAgeMillis]. Returns true when a
     * new file replaced the one in use.
     */
    suspend fun refresh(minAgeMillis: Long = REFRESH_EVERY): Boolean = lock.withLock {
        if (!prefs.remoteHosts.value) return false
        val now = clock()
        if (now - maxOf(lastAttempt, prefs.hostsFetchedAt.value) < minAgeMillis) return false
        lastAttempt = now
        withContext(Dispatchers.IO) {
            for (url in urls()) {
                val body = runCatching { http.get(URI(url), checkCloudflare = false).body }.getOrNull() ?: continue
                val parsed = runCatching { json.decodeFromString(HostsFile.serializer(), body) }.getOrNull() ?: continue
                runCatching { cacheFile.writeText(body) }
                prefs.setHostsFetchedAt(now)
                val changed = parsed != _file.value
                _file.value = parsed
                return@withContext changed
            }
            false
        }
    }

    /** Forgets the cached file (the setting was turned off). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { cacheFile.delete() }
        _file.value = HostsFile()
    }

    private fun urls(): List<String> = listOf(
        resolvePackUrl(prefs.packIndexUrl.value, "hosts.json"),
        MIRROR,
    ).distinct()

    companion object {
        const val REFRESH_EVERY = 6L * 60 * 60 * 1000

        /** A second copy for places where raw.githubusercontent.com does not open. */
        const val MIRROR = "https://cdn.jsdelivr.net/gh/TheMiracleOfKein/lume@main/docs/packs/hosts.json"
    }
}
