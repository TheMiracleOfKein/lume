package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.server.ServerAuth
import com.lume.app.data.server.ServerCredentials
import com.lume.app.data.source.LibAuthToken
import com.lume.app.data.source.LibWebSession
import com.lume.app.data.source.SourceEngines
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.PackEntry
import com.lume.app.domain.PackState
import com.lume.app.domain.packState
import com.lume.app.ui.shell.sourcesOfMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(private val app: AppContainer) : ViewModel() {
    /** The sources of the active reading mode. */
    val sources: StateFlow<List<CatalogSource>> = combine(app.sources.sources, app.prefs.activeMode) { all, mode ->
        sourcesOfMode(all, mode) { it.manifest.kind }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeId: StateFlow<String?> = app.sources.activeSourceId
    val disabled: StateFlow<Set<String>> = app.prefs.disabledSources
    val pinned: StateFlow<Set<String>> = app.prefs.pinnedSources

    /** The last failure of each source in this session. */
    val problems: StateFlow<Map<String, com.lume.app.data.source.SourceProblem>> = app.sources.health.problems

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

    // ---- Self-hosted servers --------------------------------------------------------------------

    /** The server being added or edited (null: no dialog). */
    var serverDraft by mutableStateOf<ServerDraft?>(null)
        private set

    class ServerDraft(
        /** Id of the server being edited; null when adding. */
        val editId: String?,
        engine: String,
        name: String = "",
        url: String = "",
        username: String = "",
        useApiKey: Boolean = false,
    ) {
        var engine by mutableStateOf(engine)
        var name by mutableStateOf(name)
        var url by mutableStateOf(url)
        var username by mutableStateOf(username)
        var password by mutableStateOf("")
        var apiKey by mutableStateOf("")
        var useApiKey by mutableStateOf(useApiKey)
        var error by mutableStateOf<String?>(null)
        var saving by mutableStateOf(false)

        val isPlainHttp: Boolean get() = ServerAuth.isPlainHttp(url)

        /** Null when nothing was typed (edit: keep the stored sign-in). */
        fun credentials(): ServerCredentials? {
            val c = if (useApiKey) ServerCredentials(apiKey = apiKey.trim()) else ServerCredentials(username.trim(), password)
            return if (c.isEmpty) null else c
        }
    }

    fun isServer(id: String): Boolean = app.sources.serverManifest(id) != null

    fun startAddServer() {
        serverDraft = ServerDraft(editId = null, engine = SourceEngines.OPDS)
    }

    fun startEditServer(id: String) {
        val manifest = app.sources.serverManifest(id) ?: return
        val stored = app.sources.serverCredentials(id)
        serverDraft = ServerDraft(
            editId = id,
            engine = manifest.engine,
            name = manifest.name,
            url = manifest.baseUrl,
            username = stored?.username.orEmpty(),
            useApiKey = stored?.hasApiKey == true,
        )
    }

    fun dismissServerDialog() {
        serverDraft = null
    }

    fun saveServer() {
        val draft = serverDraft ?: return
        if (draft.saving) return
        if (ServerAuth.normalizeUrl(draft.url) == null) {
            draft.error = app.app.getString(R.string.server_error_url)
            return
        }
        val sign = draft.credentials()
        // Kavita has no anonymous access.
        if (draft.engine == SourceEngines.KAVITA && sign == null && draft.editId == null) {
            draft.error = app.app.getString(R.string.server_error_kavita_signin)
            return
        }
        viewModelScope.launch {
            draft.saving = true
            draft.error = null
            runCatching {
                if (draft.editId == null) {
                    app.sources.addServer(draft.engine, draft.name, draft.url, sign ?: ServerCredentials())
                } else {
                    app.sources.updateServer(draft.editId, draft.name, draft.url, sign)
                    app.sources.serverManifest(draft.editId)!!
                }
            }.onSuccess { manifest ->
                serverDraft = null
                _messages.send(app.app.getString(R.string.server_saved, manifest.name))
            }.onFailure {
                draft.error = it.message ?: it.toString()
            }
            draft.saving = false
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
