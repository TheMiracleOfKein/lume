package com.lume.app.data.title

import androidx.room.withTransaction
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.db.TitleSourceEntity
import com.lume.app.data.downloads.DownloadStore
import com.lume.app.data.library.LibraryChapters
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.source.SourceEngines
import com.lume.app.data.source.SourceRegistry
import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.Chapter
import com.lume.app.domain.LinkedChapterId
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleMatch
import com.lume.app.domain.chapterIdForNewMain
import com.lume.app.domain.dedupeKey
import com.lume.app.domain.matchTitle
import com.lume.app.domain.normalName
import com.lume.app.domain.allNames
import com.lume.app.domain.bestNameSimilarity
import com.lume.app.domain.searchNames
import com.lume.app.domain.worthComparing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** A title found on another source. */
data class SourceCandidate(
    val sourceId: String,
    val sourceName: String,
    val titleId: String,
    val title: String,
    val coverUrl: String?,
    val match: TitleMatch,
)

/** One source's answer while looking for a title. */
sealed interface SourceLookup {
    val sourceId: String
    val sourceName: String

    data class Searching(override val sourceId: String, override val sourceName: String) : SourceLookup
    data class Found(override val sourceId: String, override val sourceName: String, val candidates: List<SourceCandidate>) : SourceLookup
    data class Failed(override val sourceId: String, override val sourceName: String, val message: String) : SourceLookup
}

/**
 * The other sources of a title: finding them ("Also on …"), linking and unlinking them, and
 * moving a title to another source ("make main source").
 */
class TitleSourceRepository(
    private val db: LumeDatabase,
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
    private val titles: TitleRepository,
    private val libraryChapters: LibraryChapters,
    private val downloads: DownloadStore,
    private val prefs: AppPreferences,
) {
    fun observe(sourceId: String, titleId: String): Flow<List<TitleSourceEntity>> =
        dao.observeTitleSources(sourceId, titleId)

    // ---- Finding ----------------------------------------------------------------------------

    /** Sources worth searching for a title of [mainSourceId]: enabled, not local, not itself. */
    fun searchableSources(mainSourceId: String): List<CatalogSource> {
        val disabled = prefs.disabledSources.value
        val mainNsfw = sources.sourceById(mainSourceId)?.manifest?.nsfw == true
        val includeNsfw = mainNsfw || prefs.globalSearchNsfw.value
        return sources.sources.value
            .filter { it.manifest.id != mainSourceId && it.manifest.engine != SourceEngines.LOCAL }
            .filter { it.manifest.id !in disabled }
            .filter { includeNsfw || !it.manifest.nsfw }
            .sortedBy { it.manifest.name.lowercase() }
    }

    /**
     * Searches every source in [among] (default: [searchableSources]) for [details], reporting
     * each source's answer through [onLookup] as it arrives. Each source is searched by the
     * title's names in turn; close results are opened and compared (names, authors).
     */
    suspend fun find(
        mainSourceId: String,
        details: TitleDetails,
        among: List<CatalogSource> = searchableSources(mainSourceId),
        onLookup: (SourceLookup) -> Unit = {},
    ): List<SourceCandidate> = coroutineScope {
        val permits = Semaphore(MAX_PARALLEL)
        val found = java.util.Collections.synchronizedList(mutableListOf<SourceCandidate>())
        among.forEach { onLookup(SourceLookup.Searching(it.manifest.id, it.manifest.name)) }
        among.forEach { source ->
            launch {
                val lookup = permits.withPermit { lookUp(source, details) }
                if (lookup is SourceLookup.Found) found += lookup.candidates
                onLookup(lookup)
            }
        }
        found
    }

    private suspend fun lookUp(source: CatalogSource, details: TitleDetails): SourceLookup {
        val id = source.manifest.id
        val name = source.manifest.name
        return try {
            withTimeout(LOOKUP_TIMEOUT_MS) {
                val names = allNames(details)
                for (query in searchNames(details).take(MAX_QUERIES)) {
                    val results = source.search(query, 1).items.take(RESULTS_PER_QUERY)
                    val close = results.filter { worthComparing(names, it) }.take(COMPARE_PER_SOURCE)
                    if (close.isEmpty()) continue
                    val candidates = close.map { result ->
                        val theirs = runCatching { source.getTitle(result.id) }.getOrNull()
                            ?: TitleDetails(id = result.id, title = result.title, coverUrl = result.coverUrl)
                        SourceCandidate(
                            sourceId = id,
                            sourceName = name,
                            titleId = result.id,
                            title = result.title,
                            coverUrl = result.coverUrl ?: theirs.coverUrl,
                            match = matchTitle(details, theirs),
                        )
                    }.sortedWith(
                        compareBy<SourceCandidate> { it.match.ordinal }
                            .thenByDescending { bestNameSimilarity(names, listOf(it.title)) },
                    )
                    return@withTimeout SourceLookup.Found(id, name, candidates)
                }
                SourceLookup.Found(id, name, emptyList())
            }
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                SourceLookup.Failed(id, name, appString(R.string.search_source_timeout))
            } else {
                throw e
            }
        } catch (e: CloudflareException) {
            SourceLookup.Failed(id, name, appString(R.string.error_cloudflare_needed))
        } catch (e: Exception) {
            SourceLookup.Failed(id, name, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /**
     * "Found for you": after a title joins the library, look for it on the sources it has no
     * row for yet (linked, suggested or dismissed) and suggest the clear matches, one per source.
     */
    suspend fun suggest(sourceId: String, titleId: String) {
        if (!prefs.findOtherSources.value) return
        sources.awaitLoaded()
        val details = titles.cached(sourceId, titleId)?.details ?: return
        val known = dao.titleSources(sourceId, titleId).mapTo(HashSet()) { it.linkSourceId }
        val among = searchableSources(sourceId).filter { it.manifest.id !in known }
        if (among.isEmpty()) return
        val found = find(sourceId, details, among)
        val best = found.filter { it.match == TitleMatch.Same }
            .groupBy { it.sourceId }
            .mapNotNull { (_, list) ->
                // The same name, preferably exactly the same.
                list.firstOrNull { normalName(it.title) == normalName(details.title) } ?: list.firstOrNull()
            }
        if (best.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertTitleSources(
            best.map {
                TitleSourceEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    linkSourceId = it.sourceId,
                    linkTitleId = it.titleId,
                    linkTitleName = it.title,
                    coverUrl = it.coverUrl,
                    state = TitleSourceEntity.SUGGESTED,
                    addedAt = now,
                )
            },
        )
    }

    // ---- Linking ----------------------------------------------------------------------------

    /** Links another source's title: its chapters join the title's list after the current ones. */
    suspend fun link(sourceId: String, titleId: String, linkSourceId: String, linkTitleId: String, name: String, coverUrl: String?) {
        if (linkSourceId == sourceId && linkTitleId == titleId) return
        val rows = dao.titleSources(sourceId, titleId)
        val priority = (rows.filter { it.state == TitleSourceEntity.LINKED }.maxOfOrNull { it.priority } ?: -1) + 1
        val existing = rows.firstOrNull { it.linkSourceId == linkSourceId && it.linkTitleId == linkTitleId }
        dao.upsertTitleSource(
            (existing ?: TitleSourceEntity(sourceId, titleId, linkSourceId, linkTitleId, name, coverUrl))
                .copy(state = TitleSourceEntity.LINKED, priority = priority, coverUrl = coverUrl ?: existing?.coverUrl),
        )
        titles.linksChanged(sourceId, titleId)
        // Its chapters are needed right away (list, unread counts); a failure shows up on refresh.
        if (titles.ownCached(linkSourceId, linkTitleId) == null) {
            runCatching { titles.refreshOwn(linkSourceId, linkTitleId) }
        }
        titles.cached(sourceId, titleId)?.chapters?.let { libraryChapters.syncIfInLibrary(sourceId, titleId, it) }
    }

    suspend fun linkSuggestion(row: TitleSourceEntity) =
        link(row.sourceId, row.titleId, row.linkSourceId, row.linkTitleId, row.linkTitleName, row.coverUrl)

    /** Not this one: it is not suggested again. */
    suspend fun dismiss(row: TitleSourceEntity) {
        dao.upsertTitleSource(row.copy(state = TitleSourceEntity.DISMISSED))
    }

    /** Moves a linked source one place earlier ([delta] = -1) or later (+1) in the fill-in order. */
    suspend fun move(row: TitleSourceEntity, delta: Int) {
        db.withTransaction {
            val linked = dao.titleSources(row.sourceId, row.titleId).filter { it.state == TitleSourceEntity.LINKED }.toMutableList()
            val from = linked.indexOfFirst { it.linkSourceId == row.linkSourceId && it.linkTitleId == row.linkTitleId }
            val to = from + delta
            if (from < 0 || to !in linked.indices) return@withTransaction
            linked.add(to, linked.removeAt(from))
            dao.upsertTitleSources(linked.mapIndexed { i, r -> r.copy(priority = i) })
        }
        titles.linksChanged(row.sourceId, row.titleId)
    }

    /**
     * Unlinks a source. Its chapters leave the title; what was attached to them (read marks, the
     * spot inside a chapter, page bookmarks, downloads) moves to another copy of the same chapter
     * when the title has one. Downloads of chapters no other source has are deleted.
     */
    suspend fun unlink(row: TitleSourceEntity) {
        val sourceId = row.sourceId
        val titleId = row.titleId
        val before = titles.cached(sourceId, titleId)?.chapters.orEmpty()
        val (gone, remaining) = before.partition { ch ->
            LinkedChapterId.decode(ch.id)?.let { it.sourceId == row.linkSourceId && it.titleId == row.linkTitleId } == true
        }
        val copyOf = gone.associate { g ->
            val key = dedupeKey(g)
            g.id to (remaining.firstOrNull { !LinkedChapterId.isLinked(it.id) && dedupeKey(it) == key }
                ?: remaining.firstOrNull { dedupeKey(it) == key })?.id
        }
        val goneIds = copyOf.keys
        val orphanDownloads = mutableListOf<String>()
        db.withTransaction {
            val read = dao.chapterReadFor(sourceId, titleId).filter { it.chapterId in goneIds }
            dao.markChaptersRead(read.mapNotNull { r -> copyOf[r.chapterId]?.let { r.copy(chapterId = it) } })
            for (chunk in read.map { it.chapterId }.chunked(500)) dao.markChaptersUnread(sourceId, titleId, chunk)

            val spots = dao.chapterProgressFor(sourceId, titleId)
            val spotIds = spots.mapTo(HashSet()) { it.chapterId }
            for (spot in spots.filter { it.chapterId in goneIds }) {
                val target = copyOf[spot.chapterId]
                if (target != null && target !in spotIds) dao.upsertChapterProgress(spot.copy(chapterId = target))
            }
            for (chunk in spots.map { it.chapterId }.filter { it in goneIds }.chunked(500)) {
                dao.deleteChapterProgress(sourceId, titleId, chunk)
            }

            dao.getProgress(sourceId, titleId)?.takeIf { it.chapterId in goneIds }?.let { p ->
                copyOf[p.chapterId]?.let { target ->
                    val chapter = remaining.firstOrNull { it.id == target }
                    dao.upsertProgress(p.copy(chapterId = target, chapterName = chapter?.title ?: p.chapterName, chapterUrl = chapter?.url ?: p.chapterUrl))
                }
            }

            for (b in dao.pageBookmarksFor(sourceId, titleId).filter { it.chapterId in goneIds }) {
                copyOf[b.chapterId]?.let { dao.movePageBookmark(b.id, sourceId, titleId, it) }
            }

            val downloaded = dao.downloadsForTitle(sourceId, titleId)
            val downloadedIds = downloaded.mapTo(HashSet()) { it.chapterId }
            for (d in downloaded.filter { it.chapterId in goneIds }) {
                val target = copyOf[d.chapterId]
                if (target != null && target !in downloadedIds) {
                    // The pages are a fine copy of the same chapter; the files stay where they are.
                    dao.upsertDownload(d.copy(chapterId = target))
                    dao.deleteDownload(sourceId, titleId, d.chapterId)
                    downloadedIds += target
                } else {
                    orphanDownloads += d.chapterId
                }
            }
            for (q in dao.downloadQueueFor(sourceId, titleId).filter { it.chapterId in goneIds }) {
                dao.deleteDownloadQueueItem(q.id)
            }

            libraryChapters.forgetLinked(sourceId, titleId, row.linkSourceId, row.linkTitleId)
            dao.upsertTitleSource(row.copy(state = TitleSourceEntity.DISMISSED))
        }
        orphanDownloads.forEach { runCatching { downloads.delete(sourceId, titleId, it) } }
        titles.linksChanged(sourceId, titleId)
    }

    // ---- Moving -----------------------------------------------------------------------------

    /**
     * Makes the linked source [row] the title's main source. Everything moves with the title:
     * library entry and categories, the Continue spot, per-chapter spots, read marks, page
     * bookmarks, tracker links, chapter list settings, downloads and the other linked sources.
     * The old main source stays linked, so no chapter or read mark is lost; unlink it afterwards
     * to move away from it completely. Returns the new main title.
     */
    suspend fun makeMain(row: TitleSourceEntity): Pair<String, String> {
        val old = row.sourceId to row.titleId
        val new = row.linkSourceId to row.linkTitleId
        fun id(chapterId: String) = chapterIdForNewMain(chapterId, old, new)
        val oldDetails = titles.ownCached(old.first, old.second)?.details
        val oldName = oldDetails?.title
            ?: dao.getBookmark(old.first, old.second)?.titleName
            ?: dao.getProgress(old.first, old.second)?.titleName
            ?: old.second
        val newDetails = titles.ownCached(new.first, new.second)?.details
            ?: runCatching { titles.refreshOwn(new.first, new.second).details }.getOrNull()
        val newName = newDetails?.title ?: row.linkTitleName
        val newCover = newDetails?.coverUrl ?: row.coverUrl

        db.withTransaction {
            dao.getBookmark(old.first, old.second)?.let { b ->
                val existing = dao.getBookmark(new.first, new.second)
                val categories = if (existing == null) b.categoryIdsJson else mergeCategoryJson(b.categoryIdsJson, existing.categoryIdsJson)
                dao.upsertBookmark(
                    b.copy(
                        sourceId = new.first,
                        titleId = new.second,
                        titleName = newName,
                        coverUrl = newCover,
                        typeLabel = newDetails?.typeLabel ?: b.typeLabel,
                        categoryIdsJson = categories,
                    ),
                )
                dao.deleteBookmark(old.first, old.second)
            }

            dao.getProgress(old.first, old.second)?.let { p ->
                val current = dao.getProgress(new.first, new.second)
                if (current == null || current.updatedAt < p.updatedAt) {
                    dao.upsertProgress(p.copy(sourceId = new.first, titleId = new.second, titleName = newName, coverUrl = newCover, chapterId = id(p.chapterId)))
                }
                dao.clearProgress(old.first, old.second)
            }

            dao.chapterProgressFor(old.first, old.second).forEach { c ->
                dao.upsertChapterProgress(c.copy(sourceId = new.first, titleId = new.second, chapterId = id(c.chapterId)))
            }
            dao.deleteChapterProgressFor(old.first, old.second)

            dao.markChaptersRead(
                dao.chapterReadFor(old.first, old.second).map { it.copy(sourceId = new.first, titleId = new.second, chapterId = id(it.chapterId)) },
            )
            dao.deleteChapterReadFor(old.first, old.second)

            dao.pageBookmarksFor(old.first, old.second).forEach { b ->
                dao.movePageBookmark(b.id, new.first, new.second, id(b.chapterId))
            }

            dao.getTitleMeta(old.first, old.second)?.let { m ->
                dao.upsertTitleMeta(m.copy(sourceId = new.first, titleId = new.second, lastChapterId = null, lastChapterName = null))
                dao.deleteTitleMeta(old.first, old.second)
            }
            dao.getTitlePrefs(old.first, old.second)?.let { p ->
                // Teams belong to a source; the new main source has its own.
                dao.upsertTitlePrefs(p.copy(sourceId = new.first, titleId = new.second, branch = null))
                dao.deleteTitlePrefs(old.first, old.second)
            }

            dao.getTrackerLinks(old.first, old.second).forEach { l ->
                dao.upsertTrackerLink(l.copy(sourceId = new.first, titleId = new.second))
            }
            dao.deleteTrackerLinksFor(old.first, old.second)

            dao.upsertLibraryChapters(
                dao.libraryChapters(old.first, old.second).map { it.copy(sourceId = new.first, titleId = new.second, chapterId = id(it.chapterId)) },
            )
            dao.deleteLibraryChaptersForTitle(old.first, old.second)

            dao.downloadsForTitle(old.first, old.second).forEach { d ->
                // Page paths are absolute: the files are read where they are.
                dao.upsertDownload(d.copy(sourceId = new.first, titleId = new.second, chapterId = id(d.chapterId)))
            }
            dao.deleteDownloadsForTitle(old.first, old.second)
            dao.downloadQueueFor(old.first, old.second).forEach { q ->
                dao.upsertDownloadQueue(q.copy(sourceId = new.first, titleId = new.second, chapterId = id(q.chapterId)))
            }

            // The other sources follow; the old main source becomes the first linked one.
            val others = dao.titleSources(old.first, old.second)
                .filterNot { it.linkSourceId == new.first && it.linkTitleId == new.second }
            val linked = others.filter { it.state == TitleSourceEntity.LINKED }.sortedBy { it.priority }
            val oldAsLink = TitleSourceEntity(
                sourceId = new.first,
                titleId = new.second,
                linkSourceId = old.first,
                linkTitleId = old.second,
                linkTitleName = oldName,
                coverUrl = oldDetails?.coverUrl,
                state = TitleSourceEntity.LINKED,
                priority = 0,
            )
            dao.upsertTitleSources(
                listOf(oldAsLink) +
                    linked.mapIndexed { i, r -> r.copy(sourceId = new.first, titleId = new.second, priority = i + 1) } +
                    others.filter { it.state != TitleSourceEntity.LINKED }.map { it.copy(sourceId = new.first, titleId = new.second) },
            )
            dao.deleteTitleSources(old.first, old.second)
        }
        titles.linksChanged(old.first, old.second)
        titles.linksChanged(new.first, new.second)
        return new
    }

    /** Moves the title to another source's copy and drops the old source completely. */
    suspend fun moveTo(sourceId: String, titleId: String, target: SourceCandidate): Pair<String, String> {
        link(sourceId, titleId, target.sourceId, target.titleId, target.title, target.coverUrl)
        val row = dao.titleSources(sourceId, titleId)
            .first { it.linkSourceId == target.sourceId && it.linkTitleId == target.titleId }
        val newMain = makeMain(row)
        dao.titleSources(newMain.first, newMain.second)
            .firstOrNull { it.linkSourceId == sourceId && it.linkTitleId == titleId }
            ?.let { unlink(it) }
        return newMain
    }

    /** The name of the source a chapter of the title's merged list comes from. */
    fun sourceNameOf(chapter: Chapter, mainSourceId: String): String {
        val id = LinkedChapterId.sourceOf(chapter.id, mainSourceId)
        return sources.sourceById(id)?.manifest?.name ?: id
    }

    private fun mergeCategoryJson(a: String, b: String): String =
        com.lume.app.data.library.encodeCategoryIds(
            com.lume.app.data.library.parseCategoryIds(a) + com.lume.app.data.library.parseCategoryIds(b),
        )

    private companion object {
        const val MAX_PARALLEL = 4
        const val LOOKUP_TIMEOUT_MS = 25_000L
        const val MAX_QUERIES = 4
        const val RESULTS_PER_QUERY = 8
        const val COMPARE_PER_SOURCE = 3
    }
}
