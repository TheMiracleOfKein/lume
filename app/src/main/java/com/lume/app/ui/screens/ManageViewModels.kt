package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.data.source.LibAuthToken
import com.lume.app.data.source.LibWebSession
import com.lume.app.domain.AutoDownloadMode
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

    val paused: StateFlow<Boolean> = app.downloadQueue.paused

    /** Bytes on disk per title ("sourceId|titleId") and in total; refreshed when downloads change. */
    var usage by mutableStateOf<Pair<Map<String, Long>, Long>>(emptyMap<String, Long>() to 0L)
        private set

    init {
        viewModelScope.launch {
            app.dao.observeDownloads().collect { usage = app.downloads.usage() }
        }
    }

    fun pause() = app.downloadQueue.pause()

    fun resume() = app.downloadQueue.resume()

    fun move(id: Long, delta: Int) = app.downloadQueue.move(id, delta)

    fun moveToTop(id: Long) = app.downloadQueue.moveToTop(id)

    fun retryAll() = app.downloadQueue.retryAllFailed()

    fun cancelAll() = app.downloadQueue.cancelAll()

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
    var downloadPath: String by mutableStateOf(app.downloads.downloadsDir().absolutePath)
        private set
    val storageChoices = app.downloads.storageChoices()

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

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setPureBlack(value: Boolean) {
        viewModelScope.launch { prefs.setPureBlack(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(value) }
    }

    fun setTranslateLabels(value: Boolean) {
        viewModelScope.launch { prefs.setTranslateChapterLabels(value) }
    }

    fun setIncognito(value: Boolean) {
        viewModelScope.launch { prefs.setIncognito(value) }
    }

    fun setAppLock(value: Boolean) {
        viewModelScope.launch {
            val manager = androidx.biometric.BiometricManager.from(app.app)
            val ok = manager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            if (value && !ok) {
                statusMessage = string(R.string.app_lock_no_screen_lock)
                return@launch
            }
            prefs.setAppLock(value)
        }
    }

    fun setSecureScreen(value: Boolean) {
        viewModelScope.launch { prefs.setSecureScreen(value) }
    }

    fun setDoh(id: String) {
        viewModelScope.launch {
            prefs.setDnsOverHttps(id)
            app.http.bumpNetworkEpoch()
        }
    }

    /** Cover and page images, unpacked archives and other rebuildable files. */
    fun clearCache() {
        viewModelScope.launch {
            val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = app.app.cacheDir
                val before = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                app.imageLoader.memoryCache?.clear()
                app.imageLoader.diskCache?.clear()
                dir.listFiles()?.forEach { it.deleteRecursively() }
                before - dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }

            statusMessage = string(R.string.cache_cleared, android.text.format.Formatter.formatShortFileSize(app.app, freed))
        }
    }

    /** Site cookies (and with them Cloudflare checks and site logins in the WebView). */
    fun clearCookies() {
        val manager = android.webkit.CookieManager.getInstance()
        manager.removeAllCookies { manager.flush() }
        app.http.bumpNetworkEpoch()
        statusMessage = string(R.string.cookies_cleared)
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

    val localPath: String = app.localImporter.localRoot().absolutePath
    var localBusy by mutableStateOf(false)
        private set

    /** Copies a folder picked in the system file picker into the local library. */
    fun importLocalFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            localBusy = true
            runCatching { app.localImporter.import(uri) }
                .onSuccess { r ->
                    statusMessage = if (r.titles == 0) string(R.string.local_import_empty)
                    else string(R.string.local_import_done, r.titles, r.chapters)
                }
                .onFailure { statusMessage = string(R.string.local_import_failed, it.message ?: it.toString()) }
            localBusy = false
        }
    }

    fun cleanTemporaryFiles() {
        viewModelScope.launch {
            val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.localImporter.cleanTemporaryFiles() }
            statusMessage = string(R.string.local_clean_done, android.text.format.Formatter.formatShortFileSize(app.app, freed))
        }
    }

    fun setAutoDownloadMode(value: AutoDownloadMode) {
        viewModelScope.launch { prefs.setAutoDownloadMode(value) }
    }

    fun setDeleteAfterRead(value: Boolean) {
        viewModelScope.launch { prefs.setDeleteAfterRead(value) }
    }

    fun setDownloadsCbz(value: Boolean) {
        viewModelScope.launch { prefs.setDownloadsAsCbz(value) }
    }

    fun setParallelPages(value: Int) {
        viewModelScope.launch { prefs.setDownloadParallelPages(value) }
    }

    /** New downloads go to [path]; existing ones stay where they are and keep working. */
    fun setDownloadsRoot(path: String) {
        viewModelScope.launch {
            prefs.setDownloadsRoot(path)
            downloadPath = app.downloads.downloadsDir().absolutePath
        }
    }

    fun setSearchNsfw(value: Boolean) {
        viewModelScope.launch { prefs.setGlobalSearchNsfw(value) }
    }

    fun setSimilarTitles(value: Boolean) {
        viewModelScope.launch { prefs.setSimilarTitles(value) }
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
            val tracker = app.trackers.byId(trackerId) ?: return@launch
            tracker.logout()
            statusMessage = string(R.string.tracker_logged_out, tracker.displayName)
        }
    }

    fun passwordLogin(tracker: com.lume.app.data.tracker.Tracker, user: String, password: String) {
        viewModelScope.launch {
            val ok = runCatching { tracker.login(user, password) }.getOrDefault(false)
            statusMessage = if (ok) string(R.string.tracker_logged_in, tracker.displayName) else string(R.string.auth_failed)
        }
    }

    fun setTrackerAutoLink(value: Boolean) {
        viewModelScope.launch { prefs.setTrackerAutoLink(value) }
    }

    fun setFindOtherSources(value: Boolean) {
        viewModelScope.launch { prefs.setFindOtherSources(value) }
    }

    fun createBackup() {
        viewModelScope.launch {
            runCatching { app.backup.createBackup() }
                .onSuccess { statusMessage = string(R.string.backup_created, it.name) }
                .onFailure { statusMessage = it.message ?: string(R.string.backup_error) }
        }
    }

    /** Titles done / total while a Mihon backup is imported; null when idle. */
    var importProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    fun suggestedBackupName(): String = app.backup.suggestedFileName()

    fun saveBackupTo(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { app.backup.writeBackup(uri) }
                .onSuccess { statusMessage = string(R.string.backup_saved_to, folderName(uri.toString())) }
                .onFailure { statusMessage = it.message ?: string(R.string.backup_error) }
        }
    }

    /** Opens a reading list file; [onOpened] shows it, a file that isn't one says so below. */
    fun openReadingList(uri: android.net.Uri, onOpened: () -> Unit) {
        viewModelScope.launch {
            runCatching { app.readingLists.open(uri) }
                .onSuccess { onOpened() }
                .onFailure { statusMessage = string(R.string.reading_list_not_a_list) }
        }
    }

    /** Restores a picked file: a Lume backup or a Mihon / Tachiyomi one. */
    fun restoreFrom(uri: android.net.Uri) {
        if (importProgress != null) return
        viewModelScope.launch {
            importProgress = 0 to 0
            runCatching { app.backup.restoreFromUri(uri) { done, total -> importProgress = done to total } }
                .onSuccess { result ->
                    statusMessage = when (result) {
                        is com.lume.app.data.backup.BackupRepository.RestoreResult.Lume -> {
                            reschedule()
                            com.lume.app.data.backup.BackupWorker.schedule(app.app, prefs.autoBackupDays.value)
                            string(R.string.backup_restored, folderName(uri.toString()))
                        }
                        is com.lume.app.data.backup.BackupRepository.RestoreResult.Mihon -> {
                            val r = result.result
                            buildString {
                                append(string(R.string.backup_mihon_done, r.added, r.alreadyInLibrary, r.chaptersMarkedRead))
                                if (r.unmatched.isNotEmpty()) {
                                    append('\n')
                                    append(string(R.string.backup_mihon_unmatched, r.unmatched.size, r.unmatched.take(10).joinToString()))
                                }
                                if (r.readMarksFailed > 0) {
                                    append('\n')
                                    append(string(R.string.backup_mihon_read_failed, r.readMarksFailed))
                                }
                            }
                        }
                    }
                }
                .onFailure { statusMessage = string(R.string.backup_restore_failed, it.message ?: it.toString()) }
            importProgress = null
        }
    }

    fun setAutoBackup(days: Int, keep: Int) {
        viewModelScope.launch {
            prefs.setAutoBackup(days, keep)
            com.lume.app.data.backup.BackupWorker.schedule(app.app, days)
        }
    }

    /** Automatic backups are also copied to [uri] (kept across restarts with a persisted permission). */
    fun setAutoBackupFolder(uri: android.net.Uri?) {
        viewModelScope.launch {
            val resolver = app.app.contentResolver
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            prefs.autoBackupFolder.value?.takeIf { it != prefs.syncSettings.value.folder }?.let { old ->
                runCatching { resolver.releasePersistableUriPermission(old.toUri(), flags) }
            }
            if (uri != null) runCatching { resolver.takePersistableUriPermission(uri, flags) }
            prefs.setAutoBackupFolder(uri?.toString())
        }
    }

    val syncRunning = app.sync.running

    /** Saves the sync settings. A new place only clears the old error; the saved merge state stays. */
    fun saveSync(next: com.lume.app.data.prefs.SyncSettings, syncNow: Boolean = false) {
        viewModelScope.launch {
            val old = prefs.syncSettings.value
            val placeChanged = old.backend != next.backend || old.folder != next.folder || old.webDavUrl != next.webDavUrl
            if (placeChanged) prefs.setSyncStatus(prefs.syncStatus.value.copy(error = null))
            prefs.setSyncSettings(next)
            com.lume.app.data.sync.SyncWorker.schedule(app.app, next)
            if (syncNow && next.ready) runSync()
        }
    }

    fun setSyncFolder(uri: android.net.Uri) {
        val resolver = app.app.contentResolver
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val current = prefs.syncSettings.value
        current.folder?.takeIf { it != uri.toString() && it != prefs.autoBackupFolder.value }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old.toUri(), flags) }
        }
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        saveSync(current.copy(backend = com.lume.app.data.prefs.SyncSettings.FOLDER, folder = uri.toString()), syncNow = true)
    }

    fun syncNow() {
        viewModelScope.launch { runSync() }
    }

    private suspend fun runSync() {
        val status = app.sync.sync() ?: return
        statusMessage = status.error?.let { string(R.string.sync_failed, it) }
            ?: if (status.changes == 0) string(R.string.sync_done_nothing) else app.app.resources.getQuantityString(R.plurals.sync_done, status.changes, status.changes)
    }

    /** A readable name for a picked file or folder ("Download/Lume" rather than a content URI). */
    fun folderName(uri: String): String {
        val decoded = android.net.Uri.decode(uri)
        return decoded.substringAfterLast(':').ifBlank { decoded.substringAfterLast('/') }
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
        if (busy) return
        if (url.isEmpty()) {
            viewModelScope.launch { _messages.send(app.app.getString(R.string.sources_import_empty)) }
            return
        }
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

    /** The running or last finished bookmark import; it runs app-wide, so it outlives this screen. */
    val importStatus = app.libBookmarkImport.status

    /** Adds the signed-in account's bookmarks on a Lib site to the library, folders as categories. */
    fun importBookmarks(sourceId: String) {
        val name = app.sources.sourceById(sourceId)?.manifest?.name ?: sourceId
        if (!app.libBookmarkImport.start(sourceId, name)) {
            viewModelScope.launch { _messages.send(app.app.getString(R.string.source_bookmarks_busy)) }
        }
    }

    fun dismissImportStatus() = app.libBookmarkImport.dismiss()

    fun logout(sourceId: String) {
        viewModelScope.launch {
            app.libAuth.clear(sourceId)
            app.sources.sourceById(sourceId)?.manifest?.baseUrl?.let { LibWebSession.clear(it) }
            _messages.send(app.app.getString(R.string.source_logout_ok))
        }
    }
}
