package com.lume.app.data.tracker

import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.library.LibraryRepository
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.title.TitleRepository
import com.lume.app.data.title.TitleSourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/** What importing a tracker list did. */
data class TrackerListImportResult(
    val added: Int,
    /** Already in the library, linked to the same tracker entry. */
    val alreadyInLibrary: Int,
    /** Names no source had (by the "Found for you" matching). */
    val notFound: List<String>,
    /** Added or found, but loading the chapters failed: no read marks were set. */
    val failed: Int,
    val chaptersMarked: Int = 0,
)

/** Where a list import is. */
sealed interface TrackerImportStage {
    data object Fetching : TrackerImportStage
    data class Progress(val done: Int, val total: Int) : TrackerImportStage
    data object Finished : TrackerImportStage
}

/** One list import for the UI: its stage, then its result or error. */
data class TrackerImportStatus(
    val trackerId: String,
    val trackerName: String,
    val stage: TrackerImportStage,
    val result: TrackerListImportResult? = null,
    val error: String? = null,
    val cancelled: Boolean = false,
) {
    val running: Boolean get() = stage != TrackerImportStage.Finished
}

/**
 * Brings the signed-in user's "Reading" list of a tracker into the library: each entry is searched
 * on the user's sources with the "Found for you" matching, added when a clear match is found, linked
 * to the tracker entry and marked read up to the tracker's progress. Runs in the app scope, so it
 * outlives the screen that started it.
 */
class TrackerListImport(
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
    private val trackers: TrackerRegistry,
    private val titleSources: TitleSourceRepository,
    private val titles: TitleRepository,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<TrackerImportStatus?>(null)

    /** The running or last finished import; null when there is nothing to show. */
    val status: StateFlow<TrackerImportStatus?> = _status.asStateFlow()

    private var job: Job? = null

    /** Starts importing [trackerId]'s list. False when an import is already running. */
    fun start(trackerId: String): Boolean {
        val tracker = trackers.byId(trackerId)?.takeIf { it.supportsListImport } ?: return false
        if (_status.value?.running == true) return false
        _status.value = TrackerImportStatus(tracker.id, tracker.displayName, TrackerImportStage.Fetching)
        job = scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { withTimeout(FETCH_TIMEOUT_MS) { tracker.fetchReadingList() } }
                if (entries == null) {
                    finish(error = "")
                    return@launch
                }
                val result = import(tracker.id, entries) { done, total ->
                    _status.update { it?.copy(stage = TrackerImportStage.Progress(done, total)) }
                }
                finish(result = result)
            } catch (e: CancellationException) {
                finish(cancelled = true)
                throw e
            } catch (e: Exception) {
                finish(error = e.message ?: e::class.simpleName.orEmpty())
            }
        }
        return true
    }

    private fun finish(result: TrackerListImportResult? = null, error: String? = null, cancelled: Boolean = false) {
        _status.update { it?.copy(stage = TrackerImportStage.Finished, result = result, error = error, cancelled = cancelled) }
    }

    /** Stops a running import; what was added stays. */
    fun cancel() {
        job?.cancel()
    }

    /** Hides a finished import's summary. */
    fun dismiss() {
        _status.update { if (it?.running == true) it else null }
    }

    suspend fun import(
        trackerId: String,
        entries: List<TrackerListEntry>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): TrackerListImportResult {
        sources.awaitLoaded()
        // Entries already in the library: a title linked to the same remote id.
        val linked = dao.allTrackerLinks()
            .filter { it.tracker == trackerId && dao.getBookmark(it.sourceId, it.titleId) != null }
            .mapTo(HashSet()) { it.remoteId }

        val added = AtomicInteger()
        val already = AtomicInteger()
        val failed = AtomicInteger()
        val marked = AtomicInteger()
        val notFound = java.util.Collections.synchronizedList(mutableListOf<String>())
        val done = AtomicInteger()
        val permits = Semaphore(PARALLEL_ENTRIES)
        onProgress(0, entries.size)
        coroutineScope {
            entries.map { entry ->
                async {
                    permits.withPermit {
                        if (entry.remoteId in linked) {
                            already.incrementAndGet()
                        } else {
                            try {
                                when (val outcome = importEntry(trackerId, entry)) {
                                    is EntryOutcome.NotFound -> notFound += entry.title
                                    is EntryOutcome.Already -> already.incrementAndGet()
                                    is EntryOutcome.Added -> {
                                        added.incrementAndGet()
                                        marked.addAndGet(outcome.marked)
                                        if (!outcome.chaptersLoaded) failed.incrementAndGet()
                                    }
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                // One slow title is a failure of that title, not of the import.
                                failed.incrementAndGet()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                failed.incrementAndGet()
                            }
                        }
                        onProgress(done.incrementAndGet(), entries.size)
                    }
                }
            }.awaitAll()
        }
        return TrackerListImportResult(added.get(), already.get(), notFound.toList(), failed.get(), marked.get())
    }

    private sealed interface EntryOutcome {
        data object NotFound : EntryOutcome
        data object Already : EntryOutcome
        data class Added(val marked: Int, val chaptersLoaded: Boolean) : EntryOutcome
    }

    private suspend fun importEntry(trackerId: String, entry: TrackerListEntry): EntryOutcome {
        // An empty main source id matches no source, so every searchable source is asked.
        val found = withTimeout(ENTRY_TIMEOUT_MS) { titleSources.find("", entry.toTitleDetails()) }
        val best = chooseListCandidate(
            entry,
            found.map { ListImportCandidate(it.sourceId, it.sourceName, it.titleId, it.title, it.coverUrl, it.match) },
        ) ?: return EntryOutcome.NotFound

        val sourceId = best.sourceId
        val titleId = best.titleId
        val existing = dao.getBookmark(sourceId, titleId)
        if (existing == null) {
            // Details and chapters first, so adding knows the chapters like a title opened by hand.
            val chapters = runCatching { withTimeout(CHAPTERS_TIMEOUT_MS) { titles.refreshOwn(sourceId, titleId) } }
                .onFailure { if (it is CancellationException && it !is kotlinx.coroutines.TimeoutCancellationException) throw it }
                .getOrNull()
            library.add(
                sourceId = sourceId,
                titleId = titleId,
                titleName = chapters?.details?.title ?: best.title,
                coverUrl = chapters?.details?.coverUrl ?: best.coverUrl,
                typeLabel = chapters?.details?.typeLabel,
                // One search per source is already done for every entry; the rest waits for a hand-add.
                discover = false,
            )
            link(trackerId, entry, sourceId, titleId)
            val marked = chapters?.let { markRead(sourceId, titleId, it.chapters, entry, it.details.title, it.details.coverUrl, it.details.typeLabel) } ?: 0
            return EntryOutcome.Added(marked, chaptersLoaded = chapters != null)
        }
        // In the library by hand: only the tracker link is new.
        if (dao.getTrackerLinks(sourceId, titleId).none { it.tracker == trackerId }) link(trackerId, entry, sourceId, titleId)
        return EntryOutcome.Already
    }

    private suspend fun link(trackerId: String, entry: TrackerListEntry, sourceId: String, titleId: String) {
        dao.upsertTrackerLink(
            TrackerLinkEntity(
                sourceId = sourceId,
                titleId = titleId,
                tracker = trackerId,
                remoteId = entry.remoteId,
                remoteUrl = entry.url,
                // The same spelling the tracker's own progress reads give.
                status = if (trackerId == trackers.anilist.id) "CURRENT" else TrackStatus.Reading.name,
                score = entry.score,
            ),
        )
    }

    /** Marks the chapters the tracker counts as read and points "Continue" at the last of them. */
    private suspend fun markRead(
        sourceId: String,
        titleId: String,
        chapters: List<com.lume.app.domain.Chapter>,
        entry: TrackerListEntry,
        name: String,
        coverUrl: String?,
        typeLabel: String?,
    ): Int {
        val read = chaptersCoveredBy(chapters, entry.chaptersRead)
        if (read.isEmpty()) return 0
        val now = System.currentTimeMillis()
        dao.markChaptersRead(read.map { ChapterReadEntity(sourceId, titleId, it.id, now) })
        val last = findChapterForProgress(chapters, entry.chaptersRead)
        if (last != null && dao.getProgress(sourceId, titleId) == null) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = name,
                    coverUrl = coverUrl,
                    typeLabel = typeLabel,
                    chapterId = last.id,
                    chapterName = last.title,
                    chapterUrl = last.url,
                    updatedAt = now,
                ),
            )
        }
        return read.size
    }

    private companion object {
        // Each entry already searches several sources at once.
        const val PARALLEL_ENTRIES = 2
        const val FETCH_TIMEOUT_MS = 60_000L
        const val ENTRY_TIMEOUT_MS = 120_000L
        const val CHAPTERS_TIMEOUT_MS = 45_000L
    }
}
