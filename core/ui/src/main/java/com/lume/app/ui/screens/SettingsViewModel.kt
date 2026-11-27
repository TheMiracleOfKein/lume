package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.domain.AutoDownloadMode
import com.lume.app.domain.UpdateRules
import com.lume.app.domain.isContentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    val prefs = app.prefs
    var downloadPath: String by mutableStateOf(currentDownloadPath())
        private set
    val storageChoices = app.downloads.storageChoices()

    /** Name of the folder picked for downloads; null while none is picked or its name can't be read. */
    var downloadsFolderName by mutableStateOf<String?>(null)
        private set

    /** True when a picked downloads folder lost its permission (new downloads use internal storage). */
    var downloadsFolderLost by mutableStateOf(false)
        private set

    init {
        refreshDownloadsFolder()
    }

    /** A folder path for file storages; empty when the downloads go to a picked folder. */
    private fun currentDownloadPath(): String =
        if (isContentUri(prefs.downloadsRoot.value)) "" else app.downloads.downloadsDir().absolutePath

    /** Reads the picked folder's name and permission again (a content query, so off the main thread). */
    fun refreshDownloadsFolder() {
        val root = prefs.downloadsRoot.value
        if (!isContentUri(root)) {
            downloadsFolderName = null
            downloadsFolderLost = false
            return
        }
        viewModelScope.launch {
            val tree = root.toUri()
            val (name, lost) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                (app.safStorage.displayName(tree) ?: folderName(root)) to !app.safStorage.hasAccess(tree)
            }
            downloadsFolderName = name
            downloadsFolderLost = lost
        }
    }

    /** Result of the last action (backup, restore, logout …), shown under the settings. */
    var statusMessage by mutableStateOf<String?>(null)

    private fun string(id: Int, vararg args: Any): String = app.app.getString(id, *args)

    private fun reschedule() {
        LibraryUpdateWorker.schedule(
            app.app,
            enabled = prefs.libraryUpdatesEnabled.value,
            intervalHours = prefs.libraryUpdateIntervalHours.value,
            wifiOnly = prefs.libraryUpdateWifiOnly.value,
            chargingOnly = prefs.libraryUpdateRules.value.chargingOnly,
        )
    }

    fun setLibraryUpdateRules(transform: (UpdateRules) -> UpdateRules) {
        viewModelScope.launch {
            val before = prefs.libraryUpdateRules.value
            val next = transform(before)
            prefs.setLibraryUpdateRules(next)
            if (next.chargingOnly != before.chargingOnly) reschedule()
        }
    }

    val categories: StateFlow<List<com.lume.app.data.db.CategoryEntity>> = app.dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setNotifyMutedCategories(ids: Set<Long>) {
        viewModelScope.launch { prefs.setNotifyMutedCategories(ids) }
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

    fun setRemoteHosts(value: Boolean) {
        viewModelScope.launch { prefs.setRemoteHosts(value) }
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

    var localBusy by mutableStateOf(false)
        private set

    /** Copies a folder picked in the system file picker into the local library. */
    fun importLocalFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            localBusy = true
            runCatching { app.localImporter.import(uri, app.dao) }
                .onSuccess { r ->
                    statusMessage = if (r.titles == 0) string(R.string.local_import_empty)
                    else string(R.string.local_import_done, r.titles, r.chapters)
                }
                .onFailure { statusMessage = string(R.string.local_import_failed, it.message ?: it.toString()) }
            localBusy = false
        }
    }

    /** Whether an installed module reads book files (the full build): shows "Import books…". */
    val canImportBooks: Boolean = app.installedModules.any { it.localBookExtensions.isNotEmpty() }

    /** Copies book files picked in the system file picker into the local novel library. */
    fun importBookFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            localBusy = true
            runCatching { app.localImporter.importBooks(uris) }
                .onSuccess { r ->
                    statusMessage = if (r.titles == 0) string(R.string.local_import_books_empty)
                    else app.app.resources.getQuantityString(R.plurals.local_import_books_done, r.titles, r.titles)
                }
                .onFailure { statusMessage = string(R.string.local_import_failed, it.message ?: it.toString()) }
            localBusy = false
        }
    }

    val watchedFolders: StateFlow<List<com.lume.app.data.db.WatchedFolderEntity>> = app.dao.observeWatchedFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val readGrant = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

    /** Starts watching [uri]: keeps the read permission across restarts and scans it once. */
    fun addWatchedFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { app.app.contentResolver.takePersistableUriPermission(uri, readGrant) }
            app.dao.upsertWatchedFolder(
                com.lume.app.data.db.WatchedFolderEntity(treeUri = uri.toString(), label = folderName(uri.toString())),
            )
            com.lume.app.data.local.FolderScanWorker.runNow(app.app)
        }
    }

    fun removeWatchedFolder(treeUri: String) {
        viewModelScope.launch {
            runCatching { app.app.contentResolver.releasePersistableUriPermission(treeUri.toUri(), readGrant) }
            app.dao.deleteWatchedFolder(treeUri)
            app.dao.deleteImportedFilesFor(treeUri)
        }
    }

    /** Picks [old] again after its permission was lost; the folder keeps its history. */
    fun reconnectWatchedFolder(old: com.lume.app.data.db.WatchedFolderEntity, uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { app.app.contentResolver.takePersistableUriPermission(uri, readGrant) }
            if (uri.toString() == old.treeUri) {
                app.dao.setWatchedFolderAvailable(old.treeUri, true)
            } else {
                // A different tree: document ids differ, so the old ledger no longer applies.
                runCatching { app.app.contentResolver.releasePersistableUriPermission(old.treeUri.toUri(), readGrant) }
                app.dao.deleteWatchedFolder(old.treeUri)
                app.dao.deleteImportedFilesFor(old.treeUri)
                app.dao.upsertWatchedFolder(old.copy(treeUri = uri.toString(), label = folderName(uri.toString()), available = true))
            }
            com.lume.app.data.local.FolderScanWorker.runNow(app.app)
        }
    }

    fun scanWatchedFoldersNow() {
        com.lume.app.data.local.FolderScanWorker.runNow(app.app)
        statusMessage = string(R.string.watched_scan_started)
    }

    fun setFolderScanHours(hours: Int) {
        viewModelScope.launch {
            prefs.setFolderScanHours(hours)
            com.lume.app.data.local.FolderScanWorker.schedule(app.app, hours)
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
            val old = prefs.downloadsRoot.value
            prefs.setDownloadsRoot(path)
            downloadPath = currentDownloadPath()
            refreshDownloadsFolder()
            releaseDownloadsFolder(old, keep = path)
        }
    }

    /**
     * Downloads go to the picked folder [uri] (also used to reconnect one that lost its permission).
     * Needs read and write access, kept across restarts.
     */
    fun setDownloadsFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { app.app.contentResolver.takePersistableUriPermission(uri, flags) }
            val old = prefs.downloadsRoot.value
            prefs.setDownloadsRoot(uri.toString())
            downloadPath = currentDownloadPath()
            refreshDownloadsFolder()
            releaseDownloadsFolder(old, keep = uri.toString())
        }
    }

    /**
     * Gives back the permission of the folder downloads used to go to, unless something still needs
     * it: a stored download inside it, or another feature (backup, sync, watched folders) using it.
     */
    private suspend fun releaseDownloadsFolder(old: String, keep: String) {
        if (!isContentUri(old) || old == keep) return
        val inUse = old == prefs.autoBackupFolder.value ||
            old == prefs.syncSettings.value.folder ||
            app.dao.watchedFolders().any { it.treeUri == old } ||
            app.downloads.referencesTree(old)
        if (inUse) return
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.app.contentResolver.releasePersistableUriPermission(old.toUri(), flags) }
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

    /** The running or last finished list import; it runs app-wide, so it outlives this screen. */
    val trackerImportStatus = app.trackerListImport.status

    fun importTrackerList(trackerId: String) {
        // The import follows the reading mode: Comics imports comics, Ranobe imports novels.
        if (!app.trackerListImport.start(trackerId, app.prefs.activeMode.value)) statusMessage = string(R.string.tracker_import_busy)
    }

    fun cancelTrackerImport() = app.trackerListImport.cancel()

    fun dismissTrackerImport() = app.trackerListImport.dismiss()

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

    /** Restores a picked file: a Lume backup or a Mihon / Tachiyomi / Kotatsu one. */
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
                            com.lume.app.data.local.FolderScanWorker.schedule(app.app, prefs.folderScanHours.value)
                            string(R.string.backup_restored, folderName(uri.toString()))
                        }
                        is com.lume.app.data.backup.BackupRepository.RestoreResult.Kotatsu -> {
                            val r = result.result
                            buildString {
                                append(string(R.string.backup_kotatsu_done, r.added, r.alreadyInLibrary))
                                if (r.unmatched.isNotEmpty()) {
                                    append('\n')
                                    append(string(R.string.backup_mihon_unmatched, r.unmatched.size, r.unmatched.take(10).joinToString()))
                                }
                            }
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
