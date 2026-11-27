package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.ui.shell.belongsToMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    val groups: StateFlow<List<DownloadTitleGroup>> = combine(
        app.dao.observeDownloads(),
        app.sources.sources,
        app.prefs.activeMode,
    ) { downloads, sources, mode ->
        val kinds = sources.associate { it.manifest.id to it.manifest.kind }
        downloads.filter { belongsToMode(it.sourceId, kinds, mode) }
    }
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

    private val modeQueue: Flow<List<DownloadQueueEntity>> = combine(
        app.downloadQueue.items,
        app.sources.sources,
        app.prefs.activeMode,
    ) { queue, sources, mode ->
        val kinds = sources.associate { it.manifest.id to it.manifest.kind }
        queue.filter { belongsToMode(it.sourceId, kinds, mode) }
    }

    val activeQueue: StateFlow<List<DownloadQueueEntity>> = modeQueue
        .map { queue ->
            queue.filter {
                it.status == DownloadQueueEntity.STATUS_PENDING || it.status == DownloadQueueEntity.STATUS_RUNNING
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val failedQueue: StateFlow<List<DownloadQueueEntity>> = modeQueue
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
