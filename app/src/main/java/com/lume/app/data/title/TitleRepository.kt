package com.lume.app.data.title

import com.lume.app.R
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.TitleSourceEntity
import com.lume.app.data.library.LibraryChapters
import com.lume.app.data.locale.appString
import com.lume.app.data.offline.CachedTitle
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.source.SourceRegistry
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.LinkedChapterId
import com.lume.app.domain.SourceChapters
import com.lume.app.domain.alternativeCopies
import com.lume.app.domain.mergeSourceChapters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/** Pages of a chapter and where they really came from (another copy when the chapter failed). */
data class ServedPages(
    val source: CatalogSource,
    val chapter: Chapter,
    val pages: List<ComicPage>,
    /** True when [chapter] is another copy than the one asked for. */
    val fallback: Boolean,
)

/**
 * Title details + chapter list, stale-while-revalidate: whatever is cached (memory, then the
 * offline table) appears immediately, then the source is asked again and the result replaces it
 * and is saved. Before this the cache was used forever and new chapters never appeared.
 *
 * A title with linked sources (`title_sources`) gets their chapters merged into its list (see
 * `mergeSourceChapters`). Each source's own copy is cached under its own key; only the merged
 * list leaves this class.
 *
 * Chapters are kept in source order; callers sort with `inReadingOrder()` where needed.
 */
class TitleRepository(
    private val sources: SourceRegistry,
    private val offline: OfflineTitleCache,
    private val libraryChapters: LibraryChapters,
    private val dao: LibraryDao,
) {
    /** Each source's own copy. */
    private val memory = LruMap<CachedTitle>(CACHE_ENTRIES)

    /** The last merged copy of titles with linked sources. */
    private val merged = LruMap<CachedTitle>(CACHE_ENTRIES)
    private val refreshedAt = LruMap<Long>(CACHE_ENTRIES)
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun key(sourceId: String, titleId: String) = "$sourceId\u0000$titleId"

    /** Already-decoded copy, if any screen loaded this title in this process. */
    fun peek(sourceId: String, titleId: String): CachedTitle? =
        key(sourceId, titleId).let { merged[it] ?: memory[it] }

    /** Cached copy without touching the network, linked sources merged in. */
    suspend fun cached(sourceId: String, titleId: String): CachedTitle? =
        ownCached(sourceId, titleId)?.let { withLinks(sourceId, titleId, it) }

    /** One source's own cached copy (memory first, then the offline table). */
    suspend fun ownCached(sourceId: String, titleId: String): CachedTitle? {
        val k = key(sourceId, titleId)
        memory[k]?.let { return it }
        return offline.get(sourceId, titleId)?.also { memory[k] = it }
    }

    /** Sources linked to the title, in fill-in order. */
    suspend fun linkedSources(sourceId: String, titleId: String): List<TitleSourceEntity> =
        dao.titleSources(sourceId, titleId).filter { it.state == TitleSourceEntity.LINKED }

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
            emit(TitleLoad.Ready(refresh(sourceId, titleId, forceLinks = force)))
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

    /**
     * Fetches from the source (and the linked sources not fetched in the last [FRESH_FOR_MS],
     * or all of them with [forceLinks]), saves and returns the merged copy. A linked source that
     * fails keeps its cached chapters.
     */
    suspend fun refresh(sourceId: String, titleId: String, forceLinks: Boolean = false): CachedTitle {
        val fresh = refreshOwn(sourceId, titleId)
        val links = linkedSources(sourceId, titleId)
        if (links.isEmpty()) {
            merged.remove(key(sourceId, titleId))
            if (fresh.chapters.isNotEmpty()) {
                // A refresh on the title screen finds new chapters too; they belong in Updates.
                runCatching { libraryChapters.syncIfInLibrary(sourceId, titleId, fresh.chapters) }
            }
            return fresh
        }
        coroutineScope {
            links.map { link ->
                async {
                    val k = key(link.linkSourceId, link.linkTitleId)
                    val recent = (System.currentTimeMillis() - (refreshedAt[k] ?: 0L)) < FRESH_FOR_MS
                    if (forceLinks || !recent || ownCached(link.linkSourceId, link.linkTitleId) == null) {
                        runCatching { refreshOwn(link.linkSourceId, link.linkTitleId) }
                    }
                }
            }.awaitAll()
        }
        val result = withLinks(sourceId, titleId, fresh, links)
        if (result.chapters.isNotEmpty()) {
            runCatching { libraryChapters.syncIfInLibrary(sourceId, titleId, result.chapters) }
        }
        return result
    }

    /** Fetches one source's own copy, saves and returns it. Concurrent calls for one title share a fetch. */
    suspend fun refreshOwn(sourceId: String, titleId: String): CachedTitle {
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
                fresh.copy(chapters = ownCached(sourceId, titleId)?.chapters.orEmpty())
            } else {
                fresh
            }
            if (result.chapters.isNotEmpty()) offline.save(sourceId, titleId, result.details, result.chapters)
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
        val details = ownCached(sourceId, titleId)?.details ?: return
        offline.save(sourceId, titleId, details, chapters)
        memory[key(sourceId, titleId)] = CachedTitle(details, chapters)
    }

    /**
     * A library check: the main source's chapter list and every linked source's, fetched now and
     * merged. Returns the main source's own list (for "last chapter") and the merged one, or
     * throws when the main source fails. A linked source that fails keeps its cached chapters.
     */
    suspend fun fetchChapterLists(sourceId: String, titleId: String): Pair<List<Chapter>, List<Chapter>> {
        val source = sources.sourceById(sourceId) ?: error(appString(R.string.error_source_not_found))
        val links = linkedSources(sourceId, titleId)
        return coroutineScope {
            val linked = links.map { link ->
                async {
                    val fetched = sources.sourceById(link.linkSourceId)?.let { s ->
                        runCatching { s.getChapters(link.linkTitleId) }.getOrNull()?.takeIf { it.isNotEmpty() }
                    }
                    if (fetched != null) {
                        updateChapters(link.linkSourceId, link.linkTitleId, fetched)
                        fetched
                    } else {
                        ownCached(link.linkSourceId, link.linkTitleId)?.chapters.orEmpty()
                    }
                }
            }
            val own = source.getChapters(titleId)
            updateChapters(sourceId, titleId, own)
            val lists = linked.awaitAll()
            val all = mergeSourceChapters(
                own,
                links.zip(lists) { link, chapters -> SourceChapters(link.linkSourceId, link.linkTitleId, chapters) },
            )
            if (links.isNotEmpty()) ownCached(sourceId, titleId)?.let { merged[key(sourceId, titleId)] = it.copy(chapters = all) }
            own to all
        }
    }

    /** Forget the merged copy after links changed; the next read merges again. */
    fun linksChanged(sourceId: String, titleId: String) {
        merged.remove(key(sourceId, titleId))
    }

    /**
     * The source that serves [chapter] of the title and the chapter as that source knows it (a
     * linked chapter's own id, number and volume), or null when the source is not installed.
     */
    suspend fun resolve(sourceId: String, titleId: String, chapter: Chapter): Pair<CatalogSource, Chapter>? {
        val ref = LinkedChapterId.decode(chapter.id)
        val (sid, tid, cid) = if (ref != null) Triple(ref.sourceId, ref.titleId, ref.chapterId) else Triple(sourceId, titleId, chapter.id)
        val source = sources.sourceById(sid) ?: return null
        val original = ownCached(sid, tid)?.chapters?.firstOrNull { it.id == cid } ?: chapter.copy(id = cid)
        return source to original
    }

    /**
     * Pages of [chapter]. When its source fails (site check, region block, removed chapter) the
     * other copies in [copies] (the title's merged list) are tried in order. The first error is
     * thrown when every copy fails, so a site check still asks to be solved.
     */
    suspend fun loadPages(sourceId: String, titleId: String, chapter: Chapter, copies: List<Chapter>): ServedPages {
        val candidates = listOf(chapter) + alternativeCopies(copies, chapter)
        var firstError: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            val (source, original) = resolve(sourceId, titleId, candidate) ?: continue
            try {
                val pages = withContext(Dispatchers.IO) { source.getPages(original) }
                if (pages.isEmpty()) error(appString(R.string.error_no_pages_offline))
                return ServedPages(source, candidate, pages, fallback = index > 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        throw firstError ?: IllegalStateException(appString(R.string.error_source_not_found))
    }

    private suspend fun withLinks(
        sourceId: String,
        titleId: String,
        own: CachedTitle,
        links: List<TitleSourceEntity>? = null,
    ): CachedTitle {
        val linked = links ?: linkedSources(sourceId, titleId)
        val k = key(sourceId, titleId)
        if (linked.isEmpty()) {
            merged.remove(k)
            return own
        }
        val lists = linked.map { link ->
            SourceChapters(link.linkSourceId, link.linkTitleId, ownCached(link.linkSourceId, link.linkTitleId)?.chapters.orEmpty())
        }
        val result = own.copy(chapters = withContext(Dispatchers.Default) { mergeSourceChapters(own.chapters, lists) })
        merged[k] = result
        return result
    }

    private suspend fun fromDownloads(sourceId: String, titleId: String): CachedTitle? {
        val details = offline.detailsFromDownloads(sourceId, titleId) ?: return null
        return CachedTitle(details, offline.chaptersFromDownloads(sourceId, titleId))
    }

    private companion object {
        const val CACHE_ENTRIES = 200
        const val FRESH_FOR_MS = 2 * 60_000L
    }
}
