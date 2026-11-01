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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Download queue backed by Room + [DownloadStore]. The work itself runs in [DownloadWorker]
 * (a foreground job with a notification), so it goes on when the app is in the background and
 * starts again after Android closes the app.
 *
 * Chapters of different sources download at the same time ([MAX_SOURCES_AT_ONCE]); one source
 * gets one chapter at a time, with several pages at once inside it.
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
    private val runMutex = Mutex()
    private var strandedReset = false

    /** Running chapter jobs by queue row id. */
    private val active = ConcurrentHashMap<Long, Job>()

    /** Wakes the running loop: something was queued, finished, retried or resumed. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val _items = MutableStateFlow<List<DownloadQueueEntity>>(emptyList())
    val items: StateFlow<List<DownloadQueueEntity>> = _items.asStateFlow()

    val paused: StateFlow<Boolean> = prefs.downloadsPaused

    init {
        scope.launch {
            dao.observeDownloadQueue().collect { _items.value = it }
        }
        scope.launch {
            // Resuming (here or from the notification) starts the job again.
            prefs.downloadsPaused.drop(1).collect { isPaused ->
                if (isPaused) stopActive() else start()
            }
        }
        // Anything left in the queue from last time.
        start()
    }

    fun enqueue(
        sourceId: String,
        titleId: String,
        chapter: Chapter,
        titleName: String,
        coverUrl: String? = null,
    ) {
        scope.launch {
            if (insert(sourceId, titleId, chapter, titleName, coverUrl)) start()
        }
    }

    fun enqueueAll(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        titleName: String,
        coverUrl: String? = null,
    ) {
        scope.launch {
            var added = false
            chapters.forEach { if (insert(sourceId, titleId, it, titleName, coverUrl)) added = true }
            if (added) start()
        }
    }

    private suspend fun insert(sourceId: String, titleId: String, chapter: Chapter, titleName: String, coverUrl: String?): Boolean {
        val existing = dao.findDownloadQueueItem(sourceId, titleId, chapter.id)
        if (existing != null &&
            (existing.status == DownloadQueueEntity.STATUS_PENDING || existing.status == DownloadQueueEntity.STATUS_RUNNING)
        ) {
            return false
        }
        if (dao.getDownload(sourceId, titleId, chapter.id) != null) return false
        dao.upsertDownloadQueue(
            DownloadQueueEntity(
                id = existing?.id ?: 0,
                sourceId = sourceId,
                titleId = titleId,
                chapterId = chapter.id,
                titleName = titleName,
                chapterName = chapter.title,
                coverUrl = coverUrl,
                status = DownloadQueueEntity.STATUS_PENDING,
            ),
        )
        return true
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

    /** Clears the whole queue (running, waiting and failed). */
    fun cancelAll() {
        scope.launch {
            _items.value.forEach { cancelItem(it) }
        }
    }

    /** Put a failed download back in the queue. */
    fun retry(id: Long) {
        scope.launch {
            val item = dao.getDownloadQueueItem(id) ?: return@launch
            if (item.status != DownloadQueueEntity.STATUS_ERROR) return@launch
            dao.updateDownloadQueueStatus(id, DownloadQueueEntity.STATUS_PENDING, 0f)
            start()
        }
    }

    fun retryAllFailed() {
        scope.launch {
            _items.value.filter { it.status == DownloadQueueEntity.STATUS_ERROR }
                .forEach { dao.updateDownloadQueueStatus(it.id, DownloadQueueEntity.STATUS_PENDING, 0f) }
            start()
        }
    }

    fun pause() {
        scope.launch { prefs.setDownloadsPaused(true) }
    }

    fun resume() {
        scope.launch { prefs.setDownloadsPaused(false) }
    }

    /**
     * Moves a waiting download [delta] places up (negative) or down in the queue. The queue is
     * ordered by `createdAt`, so moving swaps that value with the neighbour.
     */
    fun move(id: Long, delta: Int) {
        scope.launch {
            val waiting = dao.pendingDownloadQueue().filter { it.status == DownloadQueueEntity.STATUS_PENDING }
            val from = waiting.indexOfFirst { it.id == id }
            if (from < 0) return@launch
            val to = (from + delta).coerceIn(0, waiting.lastIndex)
            if (to == from) return@launch
            val reordered = waiting.toMutableList().apply { add(to, removeAt(from)) }
            val times = waiting.map { it.createdAt }.sorted()
            reordered.forEachIndexed { i, item ->
                if (item.createdAt != times[i]) dao.upsertDownloadQueue(item.copy(createdAt = times[i]))
            }
        }
    }

    /** Puts a waiting download first. */
    fun moveToTop(id: Long) = move(id, -Int.MAX_VALUE / 2)

    private suspend fun cancelItem(item: DownloadQueueEntity) {
        active.remove(item.id)?.cancel()
        store.abortPartial(item.sourceId, item.titleId, item.chapterId)
        dao.deleteDownloadQueueItem(item.id)
        wake.trySend(Unit)
    }

    /** Starts the background job when there is something to download. */
    private fun start() {
        scope.launch {
            if (prefs.downloadsPaused.value) return@launch
            val hasWork = dao.pendingDownloadQueue().isNotEmpty()
            if (hasWork) {
                wake.trySend(Unit)
                DownloadWorker.start(context)
            }
        }
    }

    /** Pausing: running chapters go back to the queue and start over on resume. */
    private fun stopActive() {
        active.values.forEach { it.cancel() }
        wake.trySend(Unit)
    }

    /**
     * Downloads until the queue is empty or paused. Called by [DownloadWorker]; a second call
     * while one is running returns at once.
     */
    suspend fun runAll() {
        if (!runMutex.tryLock()) return
        try {
            if (!strandedReset) {
                // A download killed with the process stays "running" forever unless re-queued.
                dao.resetRunningDownloads()
                strandedReset = true
            }
            sources.awaitLoaded()
            coroutineScope {
                val bySource = HashMap<String, Long>()
                while (true) {
                    if (prefs.downloadsPaused.value) break
                    awaitAllowedNetwork()
                    // Drop finished jobs.
                    bySource.entries.removeAll { (_, id) -> active[id]?.isActive != true }
                    val waiting = dao.pendingDownloadQueue().filter { it.status == DownloadQueueEntity.STATUS_PENDING }
                    if (waiting.isEmpty() && bySource.isEmpty()) break
                    val next = waiting
                        .filter { it.sourceId !in bySource }
                        .distinctBy { it.sourceId }
                        .take((MAX_SOURCES_AT_ONCE - bySource.size).coerceAtLeast(0))
                    for (item in next) {
                        bySource[item.sourceId] = item.id
                        val job = launch { processOne(item) }
                        active[item.id] = job
                        job.invokeOnCompletion {
                            active.remove(item.id, job)
                            wake.trySend(Unit)
                        }
                    }
                    wake.receive()
                }
                // Paused: let cancelled jobs put their rows back before returning.
                active.values.forEach { it.join() }
            }
        } finally {
            runMutex.unlock()
        }
    }

    suspend fun hasWaiting(): Boolean =
        dao.pendingDownloadQueue().any { it.status == DownloadQueueEntity.STATUS_PENDING }

    /** "Wi-Fi only": park the queue on metered networks until Wi-Fi returns or the option is turned off. */
    private suspend fun awaitAllowedNetwork() {
        combine(prefs.downloadsWifiOnly, network.unmetered) { wifiOnly, unmetered -> !wifiOnly || unmetered }
            .first { it }
    }

    private suspend fun processOne(item: DownloadQueueEntity) {
        dao.updateDownloadQueueStatus(item.id, DownloadQueueEntity.STATUS_RUNNING, 0f)
        try {
            val source = sources.sourceById(item.sourceId)
                ?: error(context.getString(R.string.error_source_not_found))
            val known = runCatching { titles.refresh(item.sourceId, item.titleId) }.getOrNull()
                ?: titles.cached(item.sourceId, item.titleId)
            val chapter = known?.chapters.orEmpty().firstOrNull { it.id == item.chapterId }
                ?: Chapter(id = item.chapterId, title = item.chapterName, url = item.chapterId)
            var lastWrite = 0L
            store.downloadChapter(
                source = source,
                titleId = item.titleId,
                title = known?.details,
                chapter = chapter,
                // A linked source's chapter comes from that source; a failing one from another copy.
                fetchPages = {
                    titles.loadPages(item.sourceId, item.titleId, chapter, known?.chapters.orEmpty())
                        .let { it.source to it.pages }
                },
                onProgress = { p ->
                    // Progress rows are for the list and the notification; a few per second is plenty.
                    val now = System.currentTimeMillis()
                    if (now - lastWrite > 300 || p >= 1f) {
                        lastWrite = now
                        scope.launch { dao.updateRunningDownloadProgress(item.id, p) }
                    }
                },
            )
            dao.deleteDownloadQueueItem(item.id)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                store.abortPartial(item.sourceId, item.titleId, item.chapterId)
                // Paused: back to the queue. Cancelled by hand: the row is already gone.
                if (dao.getDownloadQueueItem(item.id) != null) {
                    dao.updateDownloadQueueStatus(item.id, DownloadQueueEntity.STATUS_PENDING, 0f)
                }
            }
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DownloadQueue", "${item.titleName} / ${item.chapterName}: ${e.message}")
            dao.updateDownloadQueueStatus(item.id, DownloadQueueEntity.STATUS_ERROR, 0f)
        }
    }

    companion object {
        const val MAX_SOURCES_AT_ONCE = 3
    }
}
