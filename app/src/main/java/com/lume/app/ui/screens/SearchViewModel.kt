package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.source.SourceEngines
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

/**
 * Search state lives here (not in the composable) so results survive opening a title and
 * coming back, and so a running search is cancelled when a new one starts.
 *
 * Global search streams: every source gets its own row that fills in as soon as *that* source
 * answers. A slow or blocked site can no longer hold the whole screen on a spinner — each
 * source has a hard timeout and only a few run at once.
 */
class SearchViewModel(private val app: AppContainer) : ViewModel() {
    enum class Mode { Current, Global }

    sealed interface SourceState {
        data object Loading : SourceState
        data class Done(val items: List<TitleSummary>) : SourceState
        data class Failed(val message: String, val challenge: URI? = null) : SourceState
    }

    data class SourceRow(
        val sourceId: String,
        val sourceName: String,
        val state: SourceState,
    )

    var query by mutableStateOf("")
    var mode by mutableStateOf(Mode.Current)
    var searched by mutableStateOf(false)
        private set

    // Current-source mode
    var currentSourceId by mutableStateOf("")
        private set
    var currentState by mutableStateOf<SourceState?>(null)
        private set

    // Global mode, in display order
    var globalRows by mutableStateOf<List<SourceRow>>(emptyList())
        private set
    var globalPending by mutableIntStateOf(0)
        private set

    private var job: Job? = null

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        job?.cancel()
        searched = true
        job = when (mode) {
            Mode.Current -> viewModelScope.launch { searchCurrent(q) }
            Mode.Global -> viewModelScope.launch { searchGlobal(q) }
        }
    }

    /** Retry a single source in global mode (e.g. after solving its Cloudflare check). */
    fun retrySource(sourceId: String) {
        val q = query.trim().ifEmpty { return }
        val source = app.sources.sourceById(sourceId) ?: return
        viewModelScope.launch {
            update(sourceId, SourceState.Loading)
            globalPending++
            update(sourceId, runSource(source, q))
            globalPending--
        }
    }

    /** After a Cloudflare check is solved, re-run only the sources that were blocked by one. */
    fun retryChallenged() {
        if (mode == Mode.Current) {
            if ((currentState as? SourceState.Failed)?.challenge != null) search()
            return
        }
        globalRows.filter { (it.state as? SourceState.Failed)?.challenge != null }
            .forEach { retrySource(it.sourceId) }
    }

    private suspend fun searchCurrent(q: String) {
        currentState = SourceState.Loading
        val source = app.sources.activeSource()
        if (source == null) {
            currentState = SourceState.Failed(appString(R.string.error_no_sources))
            return
        }
        currentSourceId = source.manifest.id
        currentState = runSource(source, q, timeoutMs = CURRENT_TIMEOUT_MS)
    }

    private suspend fun searchGlobal(q: String) {
        val registry = app.sources
        val includeNsfw = app.prefs.globalSearchNsfw.first()
        val activeId = registry.activeSource()?.manifest?.id
        val sources = registry.sources.value
            .filter { includeNsfw || !it.manifest.nsfw }
            // Active source first, local files last, the rest alphabetically.
            .sortedWith(
                compareBy<CatalogSource>(
                    { it.manifest.id != activeId },
                    { it.manifest.engine == SourceEngines.LOCAL },
                    { it.manifest.name.lowercase() },
                ),
            )
        globalRows = sources.map { SourceRow(it.manifest.id, it.manifest.name, SourceState.Loading) }
        globalPending = sources.size
        val permits = Semaphore(MAX_PARALLEL_SOURCES)
        // Children of this search's job: cancelling the search cancels every source request.
        coroutineScope {
            sources.forEach { source ->
                launch {
                    val result = permits.withPermit { runSource(source, q) }
                    update(source.manifest.id, result)
                    globalPending--
                }
            }
        }
    }

    private suspend fun runSource(
        source: CatalogSource,
        q: String,
        timeoutMs: Long = GLOBAL_TIMEOUT_MS,
    ): SourceState = try {
        withTimeout(timeoutMs.milliseconds) { SourceState.Done(source.search(q).items) }
    } catch (e: TimeoutCancellationException) {
        SourceState.Failed(appString(R.string.search_source_timeout))
    } catch (e: CancellationException) {
        throw e
    } catch (e: CloudflareException) {
        SourceState.Failed(appString(R.string.error_cloudflare_needed), challenge = e.uri)
    } catch (e: Exception) {
        SourceState.Failed(e.message ?: e::class.simpleName.orEmpty())
    }

    private fun update(sourceId: String, state: SourceState) {
        globalRows = globalRows.map { if (it.sourceId == sourceId) it.copy(state = state) else it }
    }

    private companion object {
        /** Per source in global search; anything slower is reported, not waited for. */
        const val GLOBAL_TIMEOUT_MS = 15_000L
        const val CURRENT_TIMEOUT_MS = 45_000L
        const val MAX_PARALLEL_SOURCES = 5
    }
}
