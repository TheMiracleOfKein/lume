package com.lume.app.ui.screens

import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.ReadingSessionEntity
import com.lume.app.data.locale.appString
import com.lume.app.data.novel.NovelBlocks
import com.lume.app.data.title.TitleLoad
import com.lume.app.domain.Chapter
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.domain.dedupeChapters
import com.lume.app.domain.inReadingOrder
import com.lume.app.domain.requireNovel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One novel title's chapters, text and saved progress: what the text reader and read aloud both
 * need. Loading the title fills [titleName], [coverUrl], [typeLabel] and the chapter copies; the
 * writes follow the reader's rules (incognito keeps nothing, read marks go to the trackers).
 */
class NovelBook(private val app: AppContainer, val sourceId: String, val titleId: String) {
    private val dao = app.dao

    var titleName: String = ""
        private set
    var coverUrl: String? = null
        private set
    var typeLabel: String? = null
        private set

    /** Every copy of every chapter (all teams), for switching translation. */
    var allCopies: List<Chapter> = emptyList()
        private set

    /** The title's chapter list shows every team's copy ("All"), so the reader does too. */
    private var showAllCopies = false

    /** Loads the title and returns its reading order, continuing with the team of [openedId]. */
    suspend fun loadTitle(openedId: String): List<Chapter> {
        val known = when (val r = app.titles.stream(sourceId, titleId).first { it !is TitleLoad.Loading }) {
            is TitleLoad.Ready -> r.title
            is TitleLoad.Failed -> throw r.error
            TitleLoad.Loading -> error(appString(R.string.error_no_title_offline))
        }
        titleName = known.details.title
        coverUrl = known.details.coverUrl
        typeLabel = known.details.typeLabel
        if (known.chapters.isEmpty()) error(appString(R.string.error_no_chapters))
        allCopies = known.chapters
        // One copy per chapter, continuing with the team of the chapter that was opened.
        val prefs = dao.getTitlePrefs(sourceId, titleId)
        val opened = known.chapters.firstOrNull { it.id == openedId }
        showAllCopies = prefs?.showDuplicates == true
        return readingList(opened?.branchId ?: prefs?.branch, opened)
    }

    /** Reading order (oldest to newest) for one team's copies, keeping [opened] in it. */
    fun readingList(branch: String?, opened: Chapter?): List<Chapter> =
        if (showAllCopies) {
            allCopies.inReadingOrder()
        } else {
            val deduped = dedupeChapters(allCopies, branch)
            (if (opened != null && deduped.none { it.id == opened.id }) deduped + opened else deduped).inReadingOrder()
        }

    /**
     * The chapter's text, from its download when there is one (offline, no request), else from its
     * source; parsed into blocks away from the main thread.
     */
    suspend fun loadChapter(ch: Chapter): LoadedNovelChapter {
        val text = app.downloads.localText(sourceId, titleId, ch.id) ?: withContext(Dispatchers.IO) {
            val source = app.sources.sourceById(sourceId) ?: error(appString(R.string.error_source_not_found))
            source.requireNovel().getChapterText(ch)
        }
        val blocks = withContext(Dispatchers.Default) { NovelBlocks.parse(text.html, null) }
        return LoadedNovelChapter(ch, blocks)
    }

    /** The chapter's own saved spot, or null. */
    suspend fun chapterProgress(chapterId: String) = dao.getChapterProgress(sourceId, titleId, chapterId)

    /** Saves [a] as the chapter's spot and as the title's Continue reading entry. Nothing in incognito. */
    suspend fun saveProgress(a: NovelAnchor, lc: LoadedNovelChapter, completed: Boolean) {
        if (app.prefs.incognito.value) return
        val count = lc.blocks.size
        writeChapterProgress(a, count, completed)
        dao.upsertProgress(
            ProgressEntity(
                sourceId = sourceId,
                titleId = titleId,
                titleName = titleName,
                coverUrl = coverUrl,
                typeLabel = typeLabel,
                chapterId = lc.chapter.id,
                chapterName = lc.chapter.title,
                chapterUrl = lc.chapter.url,
                pageIndex = a.block,
                pageCount = count,
                pageOffset = a.fraction,
                pageImageUrl = null,
                mediaKind = "novel",
            ),
        )
    }

    private suspend fun writeChapterProgress(a: NovelAnchor, count: Int, completed: Boolean) {
        dao.upsertChapterProgress(
            ChapterProgressEntity(
                sourceId = sourceId,
                titleId = titleId,
                chapterId = a.chapterId,
                pageIndex = a.block,
                pageOffset = a.fraction,
                pageCount = count,
                completed = completed,
            ),
        )
    }

    /**
     * Marks [chapterId] read and tells the trackers, in a scope that outlives the caller. When
     * [leftBehind] is given (a finished chapter the reader has moved past), its spot is set to its
     * end, so reopening it from the list starts from the top. The caller checks incognito.
     */
    fun markRead(chapterId: String, chapters: List<Chapter>, leftBehind: LoadedNovelChapter?) {
        app.appScope.launch {
            withContext(NonCancellable) {
                dao.markChapterRead(ChapterReadEntity(sourceId = sourceId, titleId = titleId, chapterId = chapterId))
                if (leftBehind != null) {
                    writeChapterProgress(NovelAnchor(leftBehind.chapter.id, leftBehind.blocks.size - 1, 1f), leftBehind.blocks.size, completed = true)
                }
                runCatching { app.trackerSync.pushProgress(sourceId, titleId, chapters, chapterId) }
            }
        }
    }

    /** Stores a stretch of reading (or listening) for the statistics. Nothing in incognito. */
    fun saveSession(session: ReadingSessionTracker.Session) {
        if (app.prefs.incognito.value) return
        val entity = ReadingSessionEntity(
            sourceId = sourceId,
            titleId = titleId,
            titleName = titleName,
            chapterId = session.chapterId,
            startedAt = session.startedAt,
            durationMs = session.durationMs,
            // Time counts for novels; "pages" don't exist.
            pagesRead = 0,
        )
        app.appScope.launch { withContext(NonCancellable) { dao.insertSession(entity) } }
    }
}
