package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.db.TitleSourceEntity
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.similar.SimilarResult
import com.lume.app.data.title.SourceCandidate
import com.lume.app.data.title.SourceLookup
import com.lume.app.data.locale.appString
import com.lume.app.data.title.TitleLoad
import com.lume.app.domain.Chapter
import com.lume.app.domain.ChapterListOptions
import com.lume.app.domain.ChapterReadState
import com.lume.app.domain.chaptersBefore
import com.lume.app.domain.nextUnread
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Shared loading for screens that show one title. State lives in the ViewModel, so coming back
 * to the screen shows the content at once instead of replaying spinner → content.
 */
abstract class TitleBackedViewModel(
    protected val app: AppContainer,
    val sourceId: String,
    val titleId: String,
) : ViewModel() {
    protected val dao = app.dao

    private val initial = app.titles.peek(sourceId, titleId)

    var details by mutableStateOf(initial?.details)
        private set

    /** Source order (usually newest first). */
    var chapters by mutableStateOf(initial?.chapters.orEmpty())
        private set

    /** True only while there is nothing to show yet. */
    var loading by mutableStateOf(initial == null)
        private set

    /** Fetching a newer copy while cached content is on screen. */
    var refreshing by mutableStateOf(false)
        private set

    /** Set only when there is nothing to show. */
    var error by mutableStateOf<String?>(null)
        private set
    var cloudflareUri by mutableStateOf<URI?>(null)
        private set

    private var loadJob: Job? = null

    init {
        load(force = false)
        // A solved Cloudflare check or a reconnect bumps the epoch: try the network again.
        viewModelScope.launch { app.http.networkEpoch.drop(1).collect { load(force = true) } }
        // A source linked, unlinked or reordered: the merged chapter list changes.
        viewModelScope.launch {
            dao.observeTitleSources(sourceId, titleId)
                .map { rows -> rows.filter { it.state == TitleSourceEntity.LINKED }.map { Triple(it.linkSourceId, it.linkTitleId, it.priority) } }
                .distinctUntilChanged()
                .drop(1)
                .collect { load(force = false) }
        }
    }

    fun refresh() = load(force = true)

    protected fun load(force: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            app.titles.stream(sourceId, titleId, force).collect { result ->
                when (result) {
                    TitleLoad.Loading -> {
                        if (details == null) loading = true
                        error = null
                    }
                    is TitleLoad.Ready -> {
                        details = result.title.details
                        chapters = result.title.chapters
                        loading = false
                        refreshing = result.refreshing
                        error = null
                        cloudflareUri = null
                    }
                    is TitleLoad.Failed -> {
                        loading = false
                        refreshing = false
                        val e = result.error
                        if (e is CloudflareException) {
                            cloudflareUri = e.uri
                            app.http.requestChallenge(e.uri)
                            error = appString(R.string.error_cloudflare_needed)
                        } else {
                            error = e.message ?: appString(R.string.error_no_title_data)
                        }
                    }
                }
            }
        }
    }

    fun solveChallenge() {
        val uri = cloudflareUri ?: return
        viewModelScope.launch { app.http.requestChallenge(uri) }
    }

    protected fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)
}

class TitleViewModel(app: AppContainer, sourceId: String, titleId: String) : TitleBackedViewModel(app, sourceId, titleId) {
    val inLibrary: StateFlow<Boolean> = dao.observeIsBookmarked(sourceId, titleId).state(false)

    /** Linked, suggested and dismissed sources of this title. */
    val titleSources: StateFlow<List<TitleSourceEntity>> = app.titleSources.observe(sourceId, titleId).state(emptyList())

    /** Per source, while "Find on other sources" runs and after. */
    var lookups by mutableStateOf<List<SourceLookup>>(emptyList())
        private set
    var finding by mutableStateOf(false)
        private set

    /** A source change in progress (link, unlink, make main): buttons wait. */
    var busy by mutableStateOf(false)
        private set

    /** Set after the title moved to another main source; the screen opens it. */
    var movedTo by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var findJob: Job? = null

    fun findOtherSources() {
        val d = details ?: return
        findJob?.cancel()
        findJob = viewModelScope.launch {
            finding = true
            lookups = emptyList()
            try {
                app.titleSources.find(sourceId, d) { lookup ->
                    lookups = lookups.filterNot { it.sourceId == lookup.sourceId } + lookup
                }
            } finally {
                finding = false
            }
        }
    }

    private fun sourceChange(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun link(candidate: SourceCandidate) = sourceChange {
        app.titleSources.link(sourceId, titleId, candidate.sourceId, candidate.titleId, candidate.title, candidate.coverUrl)
    }

    fun linkSuggestion(row: TitleSourceEntity) = sourceChange { app.titleSources.linkSuggestion(row) }

    fun linkAllSuggestions() = sourceChange {
        titleSources.value.filter { it.state == TitleSourceEntity.SUGGESTED }.forEach { app.titleSources.linkSuggestion(it) }
    }

    fun dismiss(row: TitleSourceEntity) = sourceChange { app.titleSources.dismiss(row) }

    fun dismissAllSuggestions() = sourceChange {
        titleSources.value.filter { it.state == TitleSourceEntity.SUGGESTED }.forEach { app.titleSources.dismiss(it) }
    }

    fun unlink(row: TitleSourceEntity) = sourceChange { app.titleSources.unlink(row) }

    fun moveSource(row: TitleSourceEntity, delta: Int) = sourceChange { app.titleSources.move(row, delta) }

    fun makeMain(row: TitleSourceEntity) = sourceChange { movedTo = app.titleSources.makeMain(row) }

    fun moveTo(candidate: SourceCandidate) = sourceChange { movedTo = app.titleSources.moveTo(sourceId, titleId, candidate) }
    val progress: StateFlow<ProgressEntity?> = dao.observeProgress(sourceId, titleId).state(null)
    val trackerLinks: StateFlow<List<TrackerLinkEntity>> = dao.observeTrackerLinks(sourceId, titleId).state(emptyList())
    val pageBookmarks: StateFlow<List<PageBookmarkEntity>> = dao.observePageBookmarks(sourceId, titleId).state(emptyList())

    fun deletePageBookmark(id: Long) {
        viewModelScope.launch { dao.deletePageBookmark(id) }
    }

    init {
        viewModelScope.launch {
            val meta = dao.getTitleMeta(sourceId, titleId)
            if (meta != null && meta.unreadNewCount > 0) {
                dao.upsertTitleMeta(meta.copy(unreadNewCount = 0))
            }
        }
    }

    /** "Similar titles": null while loading, switched off or after a failed request. */
    var similar by mutableStateOf<SimilarResult?>(null)
        private set

    init {
        // Once the title has a name: ask again whenever the setting or the 18+ choice changes.
        viewModelScope.launch {
            val d = snapshotFlow { details }.filterNotNull().first()
            combine(app.prefs.similarTitles, app.prefs.globalSearchNsfw) { on, adult -> on to adult }
                .distinctUntilChanged()
                .collectLatest { (on, adult) ->
                    similar = if (on) app.similarTitles.find(sourceId, titleId, d, adult) else null
                }
        }
    }

    fun toggleLibrary() {
        val d = details ?: return
        viewModelScope.launch {
            if (inLibrary.value) {
                app.library.remove(sourceId, titleId)
            } else {
                app.library.add(sourceId, titleId, d.title, d.coverUrl, d.typeLabel)
            }
        }
    }
}

class ChaptersViewModel(app: AppContainer, sourceId: String, titleId: String) : TitleBackedViewModel(app, sourceId, titleId) {
    val readIds: StateFlow<Set<String>> =
        dao.observeReadChapterIds(sourceId, titleId).map { it.toSet() }.state(emptySet())
    val currentChapterId: StateFlow<String?> =
        dao.observeProgress(sourceId, titleId).map { it?.chapterId }.state(null)

    /** Chapters started but not finished, for the "p. 12 / 40" hint. */
    val partialProgress: StateFlow<Map<String, ChapterProgressEntity>> =
        dao.observeChapterProgress(sourceId, titleId)
            .map { rows ->
                rows.filter { !it.completed && it.pageCount > 0 && (it.pageIndex > 0 || it.pageOffset > 0.02f) }
                    .associateBy { it.chapterId }
            }
            .state(emptyMap())

    /** Re-read whenever the queue or the downloads change, so ticks come and go at once. */
    val downloadedIds: StateFlow<Set<String>> =
        combine(app.downloadQueue.items, dao.observeDownloads()) { _, _ -> }
            .map { app.downloads.downloadedChapterIds(sourceId, titleId) }
            .state(emptySet())

    val bookmarkedIds: StateFlow<Set<String>> =
        dao.observePageBookmarkChapterIds(sourceId, titleId).map { it.toSet() }.state(emptySet())

    /** Filters, sort and team, saved per title. */
    val options: StateFlow<ChapterListOptions> = dao.observeTitlePrefs(sourceId, titleId)
        .map { p ->
            if (p == null) ChapterListOptions()
            else ChapterListOptions(p.chapterFilter, p.chapterSortAsc, p.branch, p.showDuplicates)
        }
        .state(ChapterListOptions())

    init {
        // Back online after a failed first load: try again without a tap.
        viewModelScope.launch {
            app.network.online.drop(1).filter { it && error != null }.collect { load(force = true) }
        }
    }

    private fun updateOptions(transform: (ChapterListOptions) -> ChapterListOptions) {
        viewModelScope.launch {
            val next = transform(options.value)
            val base = dao.getTitlePrefs(sourceId, titleId) ?: TitlePrefsEntity(sourceId = sourceId, titleId = titleId)
            dao.upsertTitlePrefs(
                base.copy(
                    chapterFilter = next.filter,
                    chapterSortAsc = next.ascending,
                    branch = next.branch,
                    showDuplicates = next.showDuplicates,
                ),
            )
        }
    }

    fun toggleFilter(bit: Int) = updateOptions { it.copy(filter = it.filter xor bit) }

    fun toggleSort() = updateOptions { it.copy(ascending = !it.ascending) }

    /** A team (null = the one with the most chapters), shown one copy per chapter. */
    fun preferBranch(branch: String?) = updateOptions { it.copy(branch = branch, showDuplicates = false) }

    fun showDuplicates() = updateOptions { it.copy(showDuplicates = true) }

    // ---- Selection ----------------------------------------------------------------------------

    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    private var anchorId: String? = null

    val selecting: Boolean get() = selected.isNotEmpty()

    fun toggle(chapter: Chapter) {
        selected = if (chapter.id in selected) selected - chapter.id else selected + chapter.id
        anchorId = chapter.id
    }

    /** Long-press while selecting: everything between the last tapped chapter and this one. */
    fun selectRange(chapter: Chapter, shown: List<Chapter>) {
        val from = shown.indexOfFirst { it.id == anchorId }
        val to = shown.indexOfFirst { it.id == chapter.id }
        if (from < 0 || to < 0) return toggle(chapter)
        selected = selected + shown.subList(minOf(from, to), maxOf(from, to) + 1).map { it.id }
        anchorId = chapter.id
    }

    fun selectAll(shown: List<Chapter>) {
        selected = selected + shown.map { it.id }
    }

    fun clearSelection() {
        selected = emptySet()
        anchorId = null
    }

    private fun takeSelection(): List<String> = selected.toList().also { clearSelection() }

    // ---- Actions ------------------------------------------------------------------------------

    fun markSelected(read: Boolean) {
        val ids = takeSelection()
        viewModelScope.launch {
            if (read) app.library.markRead(sourceId, titleId, ids) else app.library.markUnread(sourceId, titleId, ids)
        }
    }

    /** Marks every chapter before the selected one read (every team's copy). */
    fun markPreviousRead() {
        val target = chapters.firstOrNull { it.id in selected } ?: return
        clearSelection()
        val before = chaptersBefore(chapters, target).map { it.id }
        viewModelScope.launch { app.library.markRead(sourceId, titleId, before) }
    }

    fun downloadSelected() {
        val ids = takeSelection().toSet()
        enqueue(chapters.filter { it.id in ids })
    }

    fun deleteSelectedDownloads() = deleteDownloads(takeSelection())

    /** The next [count] unread chapters (null = all unread), as the list shows them. */
    fun downloadNext(count: Int?, shown: List<Chapter>) {
        val read = ChapterReadState(chapters, readIds.value)
        enqueue(nextUnread(shown, read, count))
    }

    fun deleteAllDownloads() = deleteDownloads(downloadedIds.value.toList())

    private fun deleteDownloads(ids: List<String>) {
        val downloaded = downloadedIds.value
        viewModelScope.launch {
            ids.filter { it in downloaded }.forEach { app.downloads.delete(sourceId, titleId, it) }
        }
    }

    fun enqueue(chapters: List<Chapter>) {
        val d: TitleDetails? = details
        app.downloadQueue.enqueueAll(
            sourceId = sourceId,
            titleId = titleId,
            chapters = chapters.filter { it.id !in downloadedIds.value },
            titleName = d?.title ?: titleId,
            coverUrl = d?.coverUrl,
        )
    }

    fun cancelDownload(chapterId: String) = app.downloadQueue.cancel(sourceId, titleId, chapterId)
}
