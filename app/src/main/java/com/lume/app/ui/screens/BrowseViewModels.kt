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
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.BrowseSection
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PagedResult
import com.lume.app.domain.TitleSummary
import com.lume.app.data.db.ProgressEntity
import com.lume.app.domain.withGenre
import com.lume.app.domain.FilterList
import com.lume.app.domain.carryOver
import com.lume.app.domain.rankSearchResults
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
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

    /**
     * Reading history for "Continue reading". Held here, started eagerly, so that coming back
     * to Home shows the row in the first frame: collecting it in the screen with an empty
     * initial value inserted the row a frame later and slid the list below it down.
     */
    val history: StateFlow<List<ProgressEntity>> = app.dao.observeHistoryLimit(HISTORY_LIMIT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    private companion object {
        /** Home shows at most this many "Continue reading" cards. */
        const val HISTORY_LIMIT = 20
    }
}

/** A feed that loaded fine but has no titles: show an empty message, not a blank list. */
internal fun catalogFeedIsEmpty(loadedOnce: Boolean, loading: Boolean, error: String?, itemCount: Int): Boolean =
    loadedOnce && !loading && error == null && itemCount == 0

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
    /** The last paging request (not the first) failed; the list stays and offers a retry. */
    var moreError by mutableStateOf(false)
        internal set
    var loadedOnce by mutableStateOf(false)
        internal set
    internal var page = 1
    internal var job: Job? = null

    /** The source [items] came from; a first page for another source never shows them. */
    internal var itemsSourceId: String? = null

    internal suspend fun load(source: CatalogSource, reset: Boolean) {
        if (reset) {
            if (itemsSourceId != source.manifest.id) {
                items = emptyList()
                hasNext = false
                page = 1
            }
            loading = true
            error = null
            cloudflareUri = null
        } else {
            loadingMore = true
            moreError = false
        }
        try {
            val result = source.fetch(if (reset) 1 else page + 1)
            items = if (reset) result.items else items + result.items
            itemsSourceId = source.manifest.id
            page = result.page
            hasNext = result.hasNext
            loadedOnce = true
            moreError = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset || items.isEmpty()) {
                error = describeFailure(http, e) { cloudflareUri = it }
            } else {
                moreError = true
            }
        } finally {
            loading = false
            loadingMore = false
        }
    }
}

/**
 * Popular / latest of a catalogue. [fixedSourceId] browses that source without making it the
 * active one (Sources → Browse); null follows the active source.
 */
class CatalogViewModel(
    private val app: AppContainer,
    private val fixedSourceId: String? = null,
    /** A genre tapped on a title screen: [genreFeed] lists the source's titles with it. */
    val genre: String? = null,
) : ViewModel() {

    val popular = CatalogFeed(app.http) { getPopular(it) }
    val latest = CatalogFeed(app.http) { getLatest(it) }

    /**
     * The catalogue with [genre] included in the site's own filters; a text search for the genre
     * when the source has no filter by that name. Null without a genre.
     */
    val genreFeed: CatalogFeed? = genre?.let { name ->
        CatalogFeed(app.http) { page ->
            val filters = loadFilters().withGenre(name)
            if (filters != null) search("", page, filters) else search(name, page)
        }
    }

    var sourceId by mutableStateOf(fixedSourceId.orEmpty())
        private set

    /** The source's extra feeds ("New titles", "Top rated"…), shown as chips above the popular list. */
    var sections by mutableStateOf<List<BrowseSection>>(emptyList())
        private set

    private val sectionFeeds = mutableMapOf<String, CatalogFeed>()

    /** Bumped when the source or the network changes: the open section loads again. */
    var sectionsEpoch by mutableIntStateOf(0)
        private set

    /** The feed of section [id]; it asks whichever source is current for that section's filters. */
    fun sectionFeed(id: String): CatalogFeed = sectionFeeds.getOrPut(id) {
        CatalogFeed(app.http) { page ->
            val section = browseSections().firstOrNull { it.id == id }
            if (section != null) search("", page, section.filters) else getPopular(page)
        }
    }

    private suspend fun source(): CatalogSource? =
        if (fixedSourceId != null) app.sources.sourceById(fixedSourceId) else app.sources.activeSource()

    // ---- Search and filters inside this source's catalogue ---------------------------------

    /** The text in the catalogue's search field (not yet submitted). */
    var query by mutableStateOf("")

    /** The text and filters the shown results were asked for. */
    private var searchedQuery = ""
    private var searchedFilters = FilterList()

    /** True while the catalogue shows search results instead of its tabs. */
    var searchActive by mutableStateOf(false)
        private set

    /** This source's results for [searchedQuery] and [searchedFilters], name matches first. */
    val searchFeed = CatalogFeed(app.http) { page ->
        val result = search(searchedQuery, page, searchedFilters)
        result.copy(items = rankSearchResults(searchedQuery, result.items))
    }

    var filters by mutableStateOf(FilterList())
        private set
    var defaultFilters by mutableStateOf(FilterList())
        private set
    var filtersSourceId by mutableStateOf<String?>(null)
        private set
    var filtersLoading by mutableStateOf(false)
        private set
    private var filtersComplete = false

    val filtersActive: Boolean get() = !filters.sameAs(defaultFilters)

    /** The source can't combine search text with filters (see [CatalogSource.filtersWithQuery]). */
    val filtersNeedEmptyQuery: Boolean
        get() = filtersSourceId?.let { app.sources.sourceById(it) }?.filtersWithQuery == false

    /** Runs the search; empty text and default filters go back to the catalogue. */
    fun submitSearch() {
        val q = query.trim()
        if (q.isEmpty() && !filtersActive) {
            clearSearch()
            return
        }
        searchedQuery = q
        searchedFilters = filters
        searchActive = true
        ensureLoaded(searchFeed, force = true)
    }

    fun clearSearch() {
        query = ""
        filters = defaultFilters
        searchActive = false
        searchFeed.job?.cancel()
        searchFeed.items = emptyList()
        searchFeed.loading = false
        searchFeed.loadingMore = false
        searchFeed.hasNext = false
        searchFeed.loadedOnce = false
    }

    fun applyFilters(next: FilterList) {
        filters = next
        submitSearch()
    }

    /** Loads the source's filters into the panel (kept while the source stays the same). */
    fun prepareFilters() {
        viewModelScope.launch {
            app.sources.awaitLoaded()
            val source = source() ?: return@launch
            filtersLoading = true
            try {
                ensureFilters(source)
            } finally {
                filtersLoading = false
            }
        }
    }

    private suspend fun ensureFilters(source: CatalogSource) {
        val id = source.manifest.id
        if (filtersSourceId == id && filtersComplete) return
        if (filtersSourceId != id) {
            val static = runCatching { source.getFilters() }.getOrDefault(FilterList())
            filtersSourceId = id
            filtersComplete = false
            defaultFilters = static
            filters = static
        }
        val loaded = try {
            withTimeoutOrNull(FILTERS_TIMEOUT_MS.milliseconds) { source.loadFilters() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        if (filtersSourceId != id) return
        filters = loaded.carryOver(filters)
        defaultFilters = loaded
        filtersComplete = true
    }

    init {
        viewModelScope.launch {
            app.sources.awaitLoaded()
            sections = source()?.browseSections().orEmpty()
        }
        viewModelScope.launch {
            val activeChanges = if (fixedSourceId == null) app.prefs.activeSourceId.drop(1) else emptyFlow()
            merge(app.http.networkEpoch.drop(1), activeChanges).collect {
                sections = source()?.browseSections().orEmpty()
                // Another source has other filters; a solved check or reconnect repeats the search.
                if (source()?.manifest?.id != filtersSourceId && filtersSourceId != null) {
                    clearSearch()
                    filtersSourceId = null
                    filtersComplete = false
                    filters = FilterList()
                    defaultFilters = FilterList()
                } else if (searchActive) {
                    ensureLoaded(searchFeed, force = true)
                }
                val stale = sectionFeeds.values.toList() + listOfNotNull(genreFeed)
                (listOf(popular, latest) + stale).forEach { it.loadedOnce = false }
                ensureLoaded(popular, force = true)
                ensureLoaded(latest, force = latest.items.isNotEmpty())
                // The list of another source must not linger; the open section reloads on the bump.
                // A request still running for the old source must not write its titles back.
                stale.forEach {
                    it.job?.cancel()
                    it.loading = false
                    it.loadingMore = false
                    it.hasNext = false
                    it.items = emptyList()
                }
                sectionsEpoch++
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
            app.sources.awaitLoaded()
            val source = source()
            if (source == null) {
                feed.error = appString(R.string.error_no_sources)
                return@launch
            }
            sourceId = source.manifest.id
            feed.load(source, reset)
            // Some sources (OPDS) only know their sections once the first feed has been read.
            if (source.manifest.id == sourceId) {
                val fresh = source.browseSections()
                if (fresh.map { it.id } != sections.map { it.id }) sections = fresh
            }
        }
    }

    fun solveChallenge(feed: CatalogFeed) {
        val uri = feed.cloudflareUri ?: return
        viewModelScope.launch { app.http.requestChallenge(uri) }
    }
}

/** How long the catalogue's filter panel waits for the site's own lists (genres, tags). */
private const val FILTERS_TIMEOUT_MS = 10_000L
