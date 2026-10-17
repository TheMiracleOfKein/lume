package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.data.source.LibAuthToken
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.PackEntry
import com.lume.app.domain.PackState
import com.lume.app.domain.packState
import com.lume.app.data.source.SourceEngines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadTitleGroup(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String?,
    val chapters: List<DownloadEntity>,
)

class DownloadsViewModel(private val app: AppContainer) : ViewModel() {
    val groups: StateFlow<List<DownloadTitleGroup>> = app.dao.observeDownloads()
        .map { downloads ->
            downloads
                .groupBy { it.sourceId to it.titleId }
                .map { (_, items) ->
                    val first = items.first()
                    DownloadTitleGroup(
                        sourceId = first.sourceId,
                        titleId = first.titleId,
                        titleName = first.titleName,
                        coverUrl = first.coverUrl,
                        chapters = items.sortedBy { it.chapterName },
                    )
                }
                .sortedBy { it.titleName.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeQueue: StateFlow<List<DownloadQueueEntity>> = app.downloadQueue.items
        .map { queue ->
            queue.filter {
                it.status == DownloadQueueEntity.STATUS_PENDING || it.status == DownloadQueueEntity.STATUS_RUNNING
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val failedQueue: StateFlow<List<DownloadQueueEntity>> = app.downloadQueue.items
        .map { queue -> queue.filter { it.status == DownloadQueueEntity.STATUS_ERROR } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Titles whose chapter list is open, kept here so it survives opening the reader. */
    var expanded by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggle(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    fun cancel(id: Long) = app.downloadQueue.cancel(id)

    fun retry(id: Long) = app.downloadQueue.retry(id)

    fun deleteTitle(sourceId: String, titleId: String) {
        viewModelScope.launch { app.downloads.deleteTitle(sourceId, titleId) }
    }

    fun deleteChapter(sourceId: String, titleId: String, chapterId: String) {
        viewModelScope.launch { app.downloads.delete(sourceId, titleId, chapterId) }
    }
}

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    val prefs = app.prefs
    val downloadPath: String = app.app.filesDir.resolve("downloads").absolutePath

    /** Result of the last action (backup, restore, logout …), shown under the settings. */
    var statusMessage by mutableStateOf<String?>(null)

    private fun string(id: Int, vararg args: Any): String = app.app.getString(id, *args)

    private fun reschedule() {
        LibraryUpdateWorker.schedule(
            app.app,
            enabled = prefs.libraryUpdatesEnabled.value,
            intervalHours = prefs.libraryUpdateIntervalHours.value,
            wifiOnly = prefs.libraryUpdateWifiOnly.value,
        )
    }

    fun setDarkTheme(value: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(value) }
    }

    fun setAppLanguage(tag: String, apply: () -> Unit) {
        viewModelScope.launch {
            prefs.setAppLanguage(tag)
            apply()
        }
    }

    fun updateReader(next: ReaderSettings) {
        viewModelScope.launch { prefs.setReaderSettings(next) }
    }

    fun setLibraryUpdates(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setLibraryUpdatesEnabled(enabled)
            reschedule()
        }
    }

    fun setLibraryInterval(hours: Int) {
        viewModelScope.launch {
            prefs.setLibraryUpdateIntervalHours(hours)
            reschedule()
        }
    }

    fun setLibraryWifiOnly(value: Boolean) {
        viewModelScope.launch {
            prefs.setLibraryUpdateWifiOnly(value)
            reschedule()
        }
    }

    fun setDownloadsWifiOnly(value: Boolean) {
        viewModelScope.launch { prefs.setDownloadsWifiOnly(value) }
    }

    fun setSearchNsfw(value: Boolean) {
        viewModelScope.launch { prefs.setGlobalSearchNsfw(value) }
    }

    /** Finds downloaded chapters on disk again; [onDone] gets the message (shown as a toast too). */
    fun recoverDownloads(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val found = app.downloads.recoverFromDisk()
            val enriched = runCatching { app.downloads.enrichMetadata(app.sources, app.offlineCache) }.getOrDefault(0)
            val message = when {
                found > 0 || enriched > 0 -> string(R.string.restore_downloads_done, found, enriched)
                else -> string(R.string.restore_downloads_empty)
            }
            statusMessage = message
            onDone(message)
        }
    }

    fun logout(trackerId: String) {
        viewModelScope.launch {
            when (trackerId) {
                "shikimori" -> {
                    app.trackers.shikimori.logout()
                    statusMessage = string(R.string.tracker_logout_shikimori)
                }
                "anilist" -> {
                    app.trackers.anilist.logout()
                    statusMessage = string(R.string.tracker_logout_anilist)
                }
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            runCatching { app.backup.createBackup() }
                .onSuccess { statusMessage = string(R.string.backup_created, it.name) }
                .onFailure { statusMessage = it.message ?: string(R.string.backup_error) }
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            runCatching { app.backup.restoreLatest() }
                .onSuccess { file ->
                    statusMessage = if (file != null) {
                        string(R.string.backup_restored, file.name)
                    } else {
                        string(R.string.backup_none)
                    }
                }
                .onFailure { statusMessage = it.message ?: string(R.string.restore_error) }
        }
    }
}

class SourcesViewModel(private val app: AppContainer) : ViewModel() {
    val sources: StateFlow<List<CatalogSource>> = app.sources.sources
    val activeId: StateFlow<String?> = app.sources.activeSourceId
    val disabled: StateFlow<Set<String>> = app.prefs.disabledSources
    val pinned: StateFlow<Set<String>> = app.prefs.pinnedSources

    var importUrl by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set

    /** One row of the pack catalogue. */
    data class PackRow(val entry: PackEntry, val state: PackState)

    var packs by mutableStateOf<List<PackRow>?>(null)
        private set
    var packsLoading by mutableStateOf(false)
        private set
    var packsError by mutableStateOf<String?>(null)
        private set
    private var packIndexUrl = app.prefs.packIndexUrl.value

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off texts for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val tokens = HashMap<String, Flow<LibAuthToken?>>()

    fun isRemovable(id: String): Boolean = !app.sources.isBundled(id)

    fun loginToken(sourceId: String): Flow<LibAuthToken?> =
        tokens.getOrPut(sourceId) { app.libAuth.tokenFlow(sourceId) }

    fun import() {
        val url = importUrl.trim()
        if (url.isEmpty() || busy) return
        // A catalogue link fills the pack list instead of installing one pack.
        if (url.substringBefore('?').endsWith("index.json")) {
            importUrl = ""
            checkPacks(url)
            return
        }
        viewModelScope.launch {
            busy = true
            runCatching {
                val pack = app.sources.importUrl(url)
                app.sources.setActive(pack.id)
                pack
            }.onSuccess {
                importUrl = ""
                _messages.send(app.app.getString(R.string.sources_pack_added, it.name, it.engine))
                refreshPackStates()
            }.onFailure {
                _messages.send(it.message ?: it.toString())
            }
            busy = false
        }
    }

    fun checkPacks(url: String = packIndexUrl) {
        if (packsLoading) return
        packIndexUrl = url
        viewModelScope.launch {
            packsLoading = true
            packsError = null
            runCatching { app.sources.fetchPackIndex(url) }
                .onSuccess { index ->
                    packs = index.packs
                        .filter { SourceEngines.isPackImportable(it.engine) }
                        .map { PackRow(it, packState(it, app.sources.installedVersion(it.id))) }
                }
                .onFailure { packsError = app.app.getString(R.string.sources_packs_error, it.message ?: it.toString()) }
            packsLoading = false
        }
    }

    fun installPack(entry: PackEntry) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val wasInstalled = app.sources.installedVersion(entry.id) != null
            runCatching { app.sources.installPack(packIndexUrl, entry) }
                .onSuccess { manifest ->
                    _messages.send(
                        if (wasInstalled) app.app.getString(R.string.sources_pack_updated, manifest.name, manifest.version)
                        else app.app.getString(R.string.sources_pack_added, manifest.name, manifest.engine),
                    )
                }
                .onFailure { _messages.send(it.message ?: it.toString()) }
            refreshPackStates()
            busy = false
        }
    }

    fun updateAllPacks() {
        if (busy) return
        val outdated = packs.orEmpty().filter { it.state == PackState.UpdateAvailable }.map { it.entry }
        viewModelScope.launch {
            busy = true
            for (entry in outdated) {
                runCatching { app.sources.installPack(packIndexUrl, entry) }
                    .onFailure { _messages.send("${entry.name}: ${it.message ?: it}") }
            }
            refreshPackStates()
            busy = false
        }
    }

    private fun refreshPackStates() {
        packs = packs?.map { it.copy(state = packState(it.entry, app.sources.installedVersion(it.entry.id))) }
    }

    fun setActive(id: String) {
        viewModelScope.launch {
            // Using a disabled source turns it back on.
            app.prefs.setSourceDisabled(id, false)
            app.sources.setActive(id)
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { app.prefs.setSourcePinned(id, pinned) }
    }

    fun setDisabled(id: String, disabled: Boolean) {
        viewModelScope.launch { app.prefs.setSourceDisabled(id, disabled) }
    }

    fun settingsOf(id: String): Map<String, String> = app.sources.settingsOf(id)

    fun saveSettings(id: String, values: Map<String, String>) {
        viewModelScope.launch {
            app.sources.saveSettings(id, values)
            _messages.send(app.app.getString(R.string.source_settings_saved))
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            app.sources.remove(id)
            refreshPackStates()
        }
    }

    fun logout(sourceId: String) {
        viewModelScope.launch {
            app.libAuth.clear(sourceId)
            _messages.send(app.app.getString(R.string.source_logout_ok))
        }
    }
}
