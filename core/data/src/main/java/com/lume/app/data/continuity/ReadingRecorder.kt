package com.lume.app.data.continuity

import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.ReadingSessionEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.ReadingSessionTracker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What both readers write while a title is read: statistics sessions, per-chapter spots, the
 * Continue reading entry, read marks and the tracker push. A position is a plain number (a page,
 * or a text block), so the helper is the same for both. Incognito keeps nothing: [track],
 * [finish], [saveChapterProgress]'s callers and [saveHistory] all stop there; callers of [markRead]
 * check [incognito] themselves, before they note the chapter as read.
 *
 * [countsPages] is true where pages exist (the comic reader); a text reader counts time only.
 * [titleName] is read when a session is saved, because the title loads after the reader opens.
 */
class ReadingRecorder(
    private val app: AppContainer,
    private val sourceId: String,
    private val titleId: String,
    private val titleName: () -> String,
    private val countsPages: Boolean,
) {
    private val dao = app.dao
    private val sessionTracker = ReadingSessionTracker()

    val incognito: Boolean get() = app.prefs.incognito.value

    /** The reader is at [position] of [chapterId]; a chapter change closes the previous session. */
    fun track(chapterId: String, position: Int) {
        if (incognito) return
        sessionTracker.onPage(chapterId, position, System.currentTimeMillis())?.let(::saveSession)
    }

    /** The reader stopped or closed: the stretch of reading ends here. */
    fun finish() {
        sessionTracker.finish(System.currentTimeMillis())?.let(::saveSession)
    }

    /** Stores a finished stretch of reading (or listening) for the statistics. Nothing in incognito. */
    fun saveSession(session: ReadingSessionTracker.Session) {
        // Incognito: nothing about this reading is kept.
        if (incognito) return
        val entity = sessionEntity(sourceId, titleId, titleName(), session, countsPages)
        app.appScope.launch { withContext(NonCancellable) { dao.insertSession(entity) } }
    }

    /**
     * Marks [chapterId] read and tells the trackers, in a scope that outlives the caller.
     * [leftBehind] runs between the two, for a finished chapter the reader has moved past (its
     * spot is set to its end, so reopening it from the list starts from the top). The caller
     * checks [incognito].
     */
    fun markRead(chapterId: String, chapters: List<Chapter>, leftBehind: (suspend () -> Unit)? = null) {
        app.appScope.launch {
            withContext(NonCancellable) {
                dao.markChapterRead(ChapterReadEntity(sourceId = sourceId, titleId = titleId, chapterId = chapterId))
                leftBehind?.invoke()
                runCatching { app.trackerSync.pushProgress(sourceId, titleId, chapters, chapterId) }
            }
        }
    }

    /** The chapter's own saved spot. The caller checks [incognito]. */
    suspend fun saveChapterProgress(
        chapterId: String,
        position: Int,
        offset: Float,
        count: Int,
        completed: Boolean,
        pageAspectsJson: String = "[]",
    ) {
        dao.upsertChapterProgress(
            ChapterProgressEntity(
                sourceId = sourceId,
                titleId = titleId,
                chapterId = chapterId,
                pageIndex = position,
                pageOffset = offset,
                pageCount = count,
                completed = completed,
                pageAspectsJson = pageAspectsJson,
            ),
        )
    }

    /** The title's Continue reading entry. The caller checks [incognito]. */
    suspend fun saveHistory(
        chapter: Chapter,
        coverUrl: String?,
        typeLabel: String?,
        position: Int,
        count: Int,
        offset: Float,
        pageImageUrl: String?,
        mediaKind: String,
    ) {
        dao.upsertProgress(
            ProgressEntity(
                sourceId = sourceId,
                titleId = titleId,
                titleName = titleName(),
                coverUrl = coverUrl,
                typeLabel = typeLabel,
                chapterId = chapter.id,
                chapterName = chapter.title,
                chapterUrl = chapter.url,
                pageIndex = position,
                pageCount = count,
                pageOffset = offset,
                pageImageUrl = pageImageUrl,
                mediaKind = mediaKind,
            ),
        )
    }

    companion object {
        /** The statistics row for [session]; text readers count time, so their pages are 0. */
        fun sessionEntity(
            sourceId: String,
            titleId: String,
            titleName: String,
            session: ReadingSessionTracker.Session,
            countsPages: Boolean,
        ) = ReadingSessionEntity(
            sourceId = sourceId,
            titleId = titleId,
            titleName = titleName,
            chapterId = session.chapterId,
            startedAt = session.startedAt,
            durationMs = session.durationMs,
            pagesRead = if (countsPages) session.pagesRead else 0,
        )
    }
}
