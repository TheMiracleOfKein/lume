package com.lume.app.data.title

import com.lume.app.R
import com.lume.app.data.library.LibraryChapters
import com.lume.app.data.locale.appString
import com.lume.app.data.offline.CachedTitle
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.source.SourceRegistry
import com.lume.app.domain.Chapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** What a title screen shows while [TitleRepository.stream] runs. */
sealed interface TitleLoad {
    data object Loading : TitleLoad

    /**
     * [title] is shown right away. [refreshing] is true while the network copy is fetched in the
     * background; [refreshError] is set when that refresh failed but older data is still shown.
     */
    data class Ready(
        val title: CachedTitle,
        val refreshing: Boolean = false,
        val refreshError: Throwable? = null,
    ) : TitleLoad

    data class Failed(val error: Throwable) : TitleLoad
}

/**
 * Title details + chapter list, stale-while-revalidate: whatever is cached (memory, then the
 * offline table) appears immediately, then the source is asked again and the result replaces it
 * and is saved. Before this the cache was used forever and new chapters never appeared.
 *
 * Chapters are kept in source order; callers sort with `inReadingOrder()` where needed.
 */
class TitleRepository(
    private val sources: SourceRegistry,
    private val offline: OfflineTitleCache,
    private val libraryChapters: LibraryChapters,
) {
    private val memory = ConcurrentHashMap<String, CachedTitle>()
    private val refreshedAt = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun key(sourceId: String, titleId: String) = "$sourceId\u0000$titleId"

    /** Already-decoded copy, if any screen loaded this title in this process. */
    fun peek(sourceId: String, titleId: String): CachedTitle? = memory[key(sourceId, titleId)]

    /** Cached copy without touching the network (memory first, then the offline table). */
    suspend fun cached(sourceId: String, titleId: String): CachedTitle? {
        val k = key(sourceId, titleId)
        memory[k]?.let { return it }
        return offline.get(sourceId, titleId)?.also { memory[k] = it }
    }

    /**
     * Emits the cached copy (if any), then the refreshed one. A refresh is skipped when this
     * title was fetched less than [FRESH_FOR_MS] ago, unless [force] is set, so Title → Chapters →
     * Reader costs one network round trip, not three.
     */
    fun stream(sourceId: String, titleId: String, force: Boolean = false): Flow<TitleLoad> = flow {
        val k = key(sourceId, titleId)
        val cached = cached(sourceId, titleId)
        val recent = (System.currentTimeMillis() - (refreshedAt[k] ?: 0L)) < FRESH_FOR_MS
        val canRefresh = sources.sourceById(sourceId) != null
        if (cached != null) {
            val willRefresh = canRefresh && (force || !recent)
            emit(TitleLoad.Ready(cached, refreshing = willRefresh))
            if (!willRefresh) return@flow
        } else {
            emit(TitleLoad.Loading)
        }
        try {
            emit(TitleLoad.Ready(refresh(sourceId, titleId)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                cached != null -> emit(TitleLoad.Ready(cached, refreshError = e))
                else -> {
                    val fallback = fromDownloads(sourceId, titleId)
                    emit(if (fallback != null) TitleLoad.Ready(fallback, refreshError = e) else TitleLoad.Failed(e))
                }
            }
        }
    }

    /** Fetches from the source, saves and returns it. Concurrent calls for one title share a fetch. */
    suspend fun refresh(sourceId: String, titleId: String): CachedTitle {
        val k = key(sourceId, titleId)
        val startedAt = System.currentTimeMillis()
        return locks.getOrPut(k) { Mutex() }.withLock {
            // Someone else refreshed while we waited for the lock: reuse their result.
            if ((refreshedAt[k] ?: 0L) >= startedAt) memory[k]?.let { return@withLock it }
            val source = sources.sourceById(sourceId) ?: error(appString(R.string.error_source_not_found))
            val fresh = withContext(Dispatchers.IO) {
                val details = source.getTitle(titleId)
                val chapters = source.getChapters(titleId)
                CachedTitle(details, chapters)
            }
            // An empty list from the site is more often a hiccup than a wiped title: keep what we had.
            val result = if (fresh.chapters.isEmpty()) {
                fresh.copy(chapters = cached(sourceId, titleId)?.chapters.orEmpty())
            } else {
                fresh
            }
            if (result.chapters.isNotEmpty()) {
                offline.save(sourceId, titleId, result.details, result.chapters)
                // A refresh on the title screen finds new chapters too; they belong in Updates.
                runCatching { libraryChapters.syncIfInLibrary(sourceId, titleId, fresh.chapters) }
            }
            memory[k] = result
            refreshedAt[k] = System.currentTimeMillis()
            result
        }
    }

    /**
     * Background library checks fetch only the chapter list; store it so the title screen shows
     * the new chapters immediately. Without cached details there is nothing to pair it with yet.
     */
    suspend fun updateChapters(sourceId: String, titleId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val details = cached(sourceId, titleId)?.details ?: return
        offline.save(sourceId, titleId, details, chapters)
        memory[key(sourceId, titleId)] = CachedTitle(details, chapters)
    }

    private suspend fun fromDownloads(sourceId: String, titleId: String): CachedTitle? {
        val details = offline.detailsFromDownloads(sourceId, titleId) ?: return null
        return CachedTitle(details, offline.chaptersFromDownloads(sourceId, titleId))
    }

    private companion object {
        const val FRESH_FOR_MS = 2 * 60_000L
    }
}
