package com.lume.app.data.downloads

import android.content.Context
import com.lume.app.R
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.network.NetworkMonitor
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.title.TitleRepository
import com.lume.app.domain.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sequential download queue backed by Room + [DownloadStore].
 */
class DownloadQueue(
    private val context: Context,
    private val dao: LibraryDao,
    private val store: DownloadStore,
    private val sources: SourceRegistry,
    private val titles: TitleRepository,
    private val prefs: AppPreferences,
    private val network: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var processing = false
    private var activeJob: Job? = null
    private var activeQueueId: Long? = null
    private var strandedReset = false

    private val _items = MutableStateFlow<List<DownloadQueueEntity>>(emptyList())
    val items: StateFlow<List<DownloadQueueEntity>> = _items.asStateFlow()

    init {
        scope.launch {
            dao.observeDownloadQueue().collect { _items.value = it }
        }
        scope.launch { pump() }
    }

    fun enqueue(
        sourceId: String,
        titleId: String,
        chapter: Chapter,
        titleName: String,
        coverUrl: String? = null,
    ) {
        scope.launch {
            val existing = dao.findDownloadQueueItem(sourceId, titleId, chapter.id)
            if (existing != null &&
                (existing.status == DownloadQueueEntity.STATUS_PENDING ||
                    existing.status == DownloadQueueEntity.STATUS_RUNNING)
            ) {
                return@launch
            }
            if (dao.getDownload(sourceId, titleId, chapter.id) != null) return@launch
            dao.upsertDownloadQueue(
                DownloadQueueEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    chapterId = chapter.id,
                    titleName = titleName,
                    chapterName = chapter.title,
                    coverUrl = coverUrl,
                    status = DownloadQueueEntity.STATUS_PENDING,
                ),
            )
            pump()
        }
    }

    fun enqueueAll(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        titleName: String,
        coverUrl: String? = null,
    ) {
        chapters.forEach { enqueue(sourceId, titleId, it, titleName, coverUrl) }
    }

    fun cancel(sourceId: String, titleId: String, chapterId: String) {
        scope.launch {
            val item = dao.findDownloadQueueItem(sourceId, titleId, chapterId) ?: return@launch
            cancelItem(item)
        }
    }

    fun cancel(id: Long) {
        scope.launch {
            val item = dao.getDownloadQueueItem(id) ?: return@launch
            cancelItem(item)
        }
    }

    /** Put a failed download back in the queue. */
    fun retry(id: Long) {
        scope.launch {
            val item = dao.getDownloadQueueItem(id) ?: return@launch
            if (item.status != DownloadQueueEntity.STATUS_ERROR) return@launch
            dao.updateDownloadQueueStatus(id, DownloadQueueEntity.STATUS_PENDING, 0f)
            pump()
        }
    }

    private suspend fun cancelItem(item: DownloadQueueEntity) {
        if (item.id == activeQueueId) {
            activeJob?.cancel()
            activeJob = null
            activeQueueId = null
        }
        store.abortPartial(item.sourceId, item.titleId, item.chapterId)
        dao.deleteDownloadQueueItem(item.id)
        pump()
    }

    private fun pump() {
        scope.launch {
            // Only one pump at a time (a bare `return@withLock` used to let a second one through).
            val claimed = mutex.withLock {
                if (processing) false else true.also { processing = true }
            }
            if (!claimed) return@launch
            try {
                if (!strandedReset) {
                    // A download killed with the process stays "running" forever unless re-queued.
                    // Done here, inside the single active pump, so it never races a live download.
                    dao.resetRunningDownloads()
                    strandedReset = true
                }
                sources.awaitLoaded()
                while (coroutineContext.isActive) {
                    val next = dao.pendingDownloadQueue()
                        .firstOrNull { it.status == DownloadQueueEntity.STATUS_PENDING }
                        ?: break
                    awaitAllowedNetwork()
                    processOne(next)
                }
            } finally {
                mutex.withLock { processing = false }
            }
            // Something enqueued while this pump was finishing was turned away above; pick it up.
            if (dao.pendingDownloadQueue().any { it.status == DownloadQueueEntity.STATUS_PENDING }) pump()
        }
    }

    /** "Wi-Fi only": park the queue on metered networks until Wi-Fi returns or the option is turned off. */
    private suspend fun awaitAllowedNetwork() {
        combine(prefs.downloadsWifiOnly, network.unmetered) { wifiOnly, unmetered -> !wifiOnly || unmetered }
            .first { it }
    }

    private suspend fun processOne(item: DownloadQueueEntity) {
        dao.updateDownloadQueueStatus(item.id, DownloadQueueEntity.STATUS_RUNNING, 0f)
        val job = scope.launch {
            runCatching {
                ensureActive()
                val source = sources.sourceById(item.sourceId)
                    ?: error(context.getString(R.string.error_source_not_found))
                val known = runCatching { titles.refresh(item.sourceId, item.titleId) }.getOrNull()
                    ?: titles.cached(item.sourceId, item.titleId)
                val title = known?.details
                val chapters = known?.chapters.orEmpty()
                val chapter = chapters.firstOrNull { it.id == item.chapterId }
                    ?: Chapter(
                        id = item.chapterId,
                        title = item.chapterName,
                        url = item.chapterId,
                    )
                store.downloadChapter(
                    source = source,
                    titleId = item.titleId,
                    title = title,
                    chapter = chapter,
                    onProgress = { p ->
                        scope.launch {
                            dao.updateDownloadQueueStatus(
                                item.id,
                                DownloadQueueEntity.STATUS_RUNNING,
                                p,
                            )
                        }
                    },
                )
                ensureActive()
                dao.updateDownloadQueueStatus(item.id, DownloadQueueEntity.STATUS_DONE, 1f)
                dao.deleteDownloadQueueItem(item.id)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) {
                    store.abortPartial(item.sourceId, item.titleId, item.chapterId)
                    runCatching { dao.deleteDownloadQueueItem(item.id) }
                } else {
                    dao.updateDownloadQueueStatus(item.id, DownloadQueueEntity.STATUS_ERROR, 0f)
                }
            }
        }
        activeJob = job
        activeQueueId = item.id
        job.join()
        activeJob = null
        activeQueueId = null
    }
}
