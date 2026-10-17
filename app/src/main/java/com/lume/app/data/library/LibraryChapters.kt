package com.lume.app.data.library

import androidx.room.withTransaction
import com.lume.app.data.db.LibraryChapterEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.domain.Chapter
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
            val firstSync = known.isEmpty()
            val knownById = known.associateBy { it.chapterId }
            val fresh = if (firstSync) emptyList() else findNewChapters(known, chapters)
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

/**
 * Chapters whose id is unknown, minus those that are only another team's copy of a chapter
 * already known (same [dedupeKey]). One per chapter, in the list's order.
 */
fun findNewChapters(known: List<LibraryChapterEntity>, chapters: List<Chapter>): List<Chapter> {
    val knownIds = known.mapTo(HashSet()) { it.chapterId }
    val seenKeys = known.mapTo(HashSet()) { it.dedupeKey }
    return chapters.filter { ch -> ch.id !in knownIds && seenKeys.add(dedupeKey(ch)) }
}

/**
 * Known chapters the source no longer lists. When most of the list vanished at once, it is more
 * likely a partial answer from the site than real removals, so nothing is dropped.
 */
fun removedChapterIds(known: List<LibraryChapterEntity>, chapters: List<Chapter>): List<String> {
    val current = chapters.mapTo(HashSet()) { it.id }
    val gone = known.map { it.chapterId }.filter { it !in current }
    return if (gone.size * 2 > known.size) emptyList() else gone
}
