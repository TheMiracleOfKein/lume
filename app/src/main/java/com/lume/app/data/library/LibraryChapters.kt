package com.lume.app.data.library

import androidx.room.withTransaction
import com.lume.app.data.db.LibraryChapterEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.domain.Chapter
import com.lume.app.domain.LinkedChapterId
import com.lume.app.domain.dedupeKey

/**
 * The known chapters of every library title (`library_chapters`). The one place that decides
 * which chapters are new: by chapter id, not by how many chapters there are, so a site that
 * removes one chapter and adds another still reports the new one.
 */
class LibraryChapters(
    private val db: LumeDatabase,
    private val dao: LibraryDao,
) {
    /**
     * Stores [chapters] (source order) and returns the ones that are new. The first sync of a
     * title only records what is there and returns nothing.
     */
    suspend fun sync(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        now: Long = System.currentTimeMillis(),
    ): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        return db.withTransaction {
            val known = dao.libraryChapters(sourceId, titleId)
            val knownById = known.associateBy { it.chapterId }
            val fresh = findNewChapters(known, chapters)
            val freshIds = fresh.mapTo(HashSet()) { it.id }
            dao.upsertLibraryChapters(
                chapters.distinctBy { it.id }.mapIndexed { index, ch ->
                    LibraryChapterEntity(
                        sourceId = sourceId,
                        titleId = titleId,
                        chapterId = ch.id,
                        dedupeKey = dedupeKey(ch),
                        name = ch.title,
                        branchId = ch.branchId,
                        branchName = ch.branchName,
                        sourceOrder = index,
                        discoveredAt = if (ch.id in freshIds) now else knownById[ch.id]?.discoveredAt,
                    )
                },
            )
            val gone = removedChapterIds(known, chapters)
            if (gone.isNotEmpty()) dao.deleteLibraryChapters(sourceId, titleId, gone)
            fresh
        }
    }

    /** [sync] for titles in the library; a title opened from the catalogue is not tracked. */
    suspend fun syncIfInLibrary(sourceId: String, titleId: String, chapters: List<Chapter>) {
        if (dao.getBookmark(sourceId, titleId) != null) sync(sourceId, titleId, chapters)
    }

    /** The title left the library. */
    suspend fun forget(sourceId: String, titleId: String) {
        dao.deleteLibraryChaptersForTitle(sourceId, titleId)
    }

    /** A linked source was unlinked: its chapters leave the title. */
    suspend fun forgetLinked(sourceId: String, titleId: String, linkSourceId: String, linkTitleId: String) {
        val ids = dao.libraryChapters(sourceId, titleId)
            .map { it.chapterId }
            .filter { id -> LinkedChapterId.decode(id)?.let { it.sourceId == linkSourceId && it.titleId == linkTitleId } == true }
        ids.chunked(500).forEach { dao.deleteLibraryChapters(sourceId, titleId, it) }
    }

    /**
     * Library titles with no known chapters yet (for example right after the v8 upgrade) take
     * them from the offline cache, so the next check does not report the whole list as new.
     */
    suspend fun seedFromCache(cache: OfflineTitleCache) {
        for (bookmark in dao.allBookmarks()) {
            if (dao.libraryChapters(bookmark.sourceId, bookmark.titleId).isNotEmpty()) continue
            val chapters = cache.get(bookmark.sourceId, bookmark.titleId)?.chapters.orEmpty()
            sync(bookmark.sourceId, bookmark.titleId, chapters)
        }
    }
}

/** Which source a library chapter comes from: "" for the main source, else the linked title. */
private fun originOf(chapterId: String): String =
    LinkedChapterId.decode(chapterId)?.let { "${it.sourceId}\u0000${it.titleId}" }.orEmpty()

/**
 * Chapters whose id is unknown, minus those that are only another team's (or source's) copy of a
 * chapter already known (same [dedupeKey]). One per chapter, in the list's order, so with linked
 * sources the copy listed first (the main source's) counts as the one that came first.
 *
 * Nothing is new on a title's first sync, nor from a source seen for the first time (a source
 * just linked brings its whole list at once).
 */
fun findNewChapters(known: List<LibraryChapterEntity>, chapters: List<Chapter>): List<Chapter> {
    if (known.isEmpty()) return emptyList()
    val knownIds = known.mapTo(HashSet()) { it.chapterId }
    val knownOrigins = known.mapTo(HashSet()) { originOf(it.chapterId) }
    val seenKeys = known.mapTo(HashSet()) { it.dedupeKey }
    return chapters.filter { ch ->
        if (originOf(ch.id) !in knownOrigins) {
            seenKeys.add(dedupeKey(ch))
            false
        } else {
            ch.id !in knownIds && seenKeys.add(dedupeKey(ch))
        }
    }
}

/**
 * Known chapters their source no longer lists, per source: a source missing from [chapters]
 * (it failed this time) loses nothing. When most of a source's list vanished at once, it is
 * more likely a partial answer from the site than real removals, so nothing is dropped.
 */
fun removedChapterIds(known: List<LibraryChapterEntity>, chapters: List<Chapter>): List<String> {
    val current = chapters.mapTo(HashSet()) { it.id }
    val origins = chapters.mapTo(HashSet()) { originOf(it.id) }
    return known.groupBy { originOf(it.chapterId) }
        .filterKeys { it in origins }
        .flatMap { (_, rows) ->
            val gone = rows.map { it.chapterId }.filter { it !in current }
            if (gone.size * 2 > rows.size) emptyList() else gone
        }
}
