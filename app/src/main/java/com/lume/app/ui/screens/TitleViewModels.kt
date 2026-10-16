package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.locale.appString
import com.lume.app.data.title.TitleLoad
import com.lume.app.domain.Chapter
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val sourceId: String,
    val titleId: String,
) : ViewModel() {
    protected val app: LumeApp = LumeApp.instance
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

class TitleViewModel(sourceId: String, titleId: String) : TitleBackedViewModel(sourceId, titleId) {
    val inLibrary: StateFlow<Boolean> = dao.observeIsBookmarked(sourceId, titleId).state(false)
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

    fun toggleLibrary() {
        val d = details ?: return
        viewModelScope.launch {
            if (inLibrary.value) {
                dao.deleteBookmark(sourceId, titleId)
            } else {
                dao.upsertBookmark(
                    BookmarkEntity(
                        sourceId = sourceId,
                        titleId = titleId,
                        titleName = d.title,
                        coverUrl = d.coverUrl,
                        typeLabel = d.typeLabel,
                    ),
                )
            }
        }
    }
}

class ChaptersViewModel(sourceId: String, titleId: String) : TitleBackedViewModel(sourceId, titleId) {
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

    /** Re-read whenever the queue changes, so finished downloads get their tick. */
    val downloadedIds: StateFlow<Set<String>> =
        app.downloadQueue.items.map { app.downloads.downloadedChapterIds(sourceId, titleId) }.state(emptySet())

    init {
        // Back online after a failed first load: try again without a tap.
        viewModelScope.launch {
            app.network.online.drop(1).filter { it && error != null }.collect { load(force = true) }
        }
    }

    fun enqueue(chapters: List<Chapter>) {
        val d: TitleDetails? = details
        app.downloadQueue.enqueueAll(
            sourceId = sourceId,
            titleId = titleId,
            chapters = chapters,
            titleName = d?.title ?: titleId,
            coverUrl = d?.coverUrl,
        )
    }
}
