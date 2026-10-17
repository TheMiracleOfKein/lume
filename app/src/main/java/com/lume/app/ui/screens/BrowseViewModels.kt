package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PagedResult
import com.lume.app.domain.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.net.URI

/** Error text + optional Cloudflare URI for a failed load (and asks for the challenge). */
private suspend fun describeFailure(http: AppHttp, e: Throwable, onChallenge: (URI) -> Unit): String {
    if (e is CloudflareException) {
        onChallenge(e.uri)
        http.requestChallenge(e.uri)
        return appString(R.string.error_cloudflare_needed)
    }
    return e.message ?: e.toString()
}

/**
 * Home feed. Kept in a ViewModel so switching tabs or coming back shows the feed at once
 * instead of reloading it behind a spinner.
 */
class HomeViewModel(private val app: AppContainer) : ViewModel() {

    var feed by mutableStateOf<HomeFeed?>(null)
        private set
    var sourceId by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var cloudflareUri by mutableStateOf<URI?>(null)
        private set

    private var job: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            // A solved check, a reconnect or another active source: load again.
            merge(
                app.http.networkEpoch.drop(1),
                app.network.online.drop(1).distinctUntilChanged(),
                app.prefs.activeSourceId.drop(1),
            ).collect { refresh() }
        }
    }

    fun refresh() {
        job?.cancel()
        if (!app.network.online.value) {
            loading = false
            return
        }
        job = viewModelScope.launch {
            loading = true
            error = null
            cloudflareUri = null
            try {
                val source = app.sources.activeSource() ?: error(appString(R.string.error_no_active_sources))
                sourceId = source.manifest.id
                feed = source.getHome()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = describeFailure(app.http, e) { cloudflareUri = it }
            } finally {
                loading = false
            }
        }
    }

    fun solveChallenge() {
        val uri = cloudflareUri ?: return
        viewModelScope.launch { app.http.requestChallenge(uri) }
    }
}

/** One catalog list (popular or latest), with its own paging and its own in-flight request. */
class CatalogFeed(
    private val http: AppHttp,
    private val fetch: suspend CatalogSource.(page: Int) -> PagedResult<TitleSummary>,
) {
    var items by mutableStateOf<List<TitleSummary>>(emptyList())
        internal set
    var hasNext by mutableStateOf(false)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var loadingMore by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var cloudflareUri by mutableStateOf<URI?>(null)
        internal set
    var loadedOnce = false
        internal set
    internal var page = 1
    internal var job: Job? = null

    internal suspend fun load(source: CatalogSource, reset: Boolean) {
        if (reset) {
            loading = true
            error = null
            cloudflareUri = null
        } else {
            loadingMore = true
        }
        try {
            val result = source.fetch(if (reset) 1 else page + 1)
            items = if (reset) result.items else items + result.items
            page = result.page
            hasNext = result.hasNext
            loadedOnce = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = describeFailure(http, e) { cloudflareUri = it }
        } finally {
            loading = false
            loadingMore = false
        }
    }
}

class CatalogViewModel(private val app: AppContainer) : ViewModel() {

    val popular = CatalogFeed(app.http) { getPopular(it) }
    val latest = CatalogFeed(app.http) { getLatest(it) }

    var sourceId by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            merge(app.http.networkEpoch.drop(1), app.prefs.activeSourceId.drop(1)).collect {
                listOf(popular, latest).forEach { it.loadedOnce = false }
                ensureLoaded(popular, force = true)
                ensureLoaded(latest, force = latest.items.isNotEmpty())
            }
        }
    }

    /** First load of a tab, or a forced reload; cancels that tab's earlier request. */
    fun ensureLoaded(feed: CatalogFeed, force: Boolean = false) {
        if (feed.loadedOnce && !force) return
        start(feed, reset = true)
    }

    fun loadMore(feed: CatalogFeed) {
        if (!feed.hasNext || feed.loading || feed.loadingMore) return
        start(feed, reset = false)
    }

    private fun start(feed: CatalogFeed, reset: Boolean) {
        feed.job?.cancel()
        feed.job = viewModelScope.launch {
            val source = app.sources.activeSource()
            if (source == null) {
                feed.error = appString(R.string.error_no_sources)
                return@launch
            }
            sourceId = source.manifest.id
            feed.load(source, reset)
        }
    }

    fun solveChallenge(feed: CatalogFeed) {
        val uri = feed.cloudflareUri ?: return
        viewModelScope.launch { app.http.requestChallenge(uri) }
    }
}
