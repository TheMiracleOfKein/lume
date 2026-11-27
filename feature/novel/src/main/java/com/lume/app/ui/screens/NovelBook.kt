package com.lume.app.ui.screens

import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.continuity.ReadingRecorder
import com.lume.app.data.locale.appString
import com.lume.app.data.novel.NovelBlocks
import com.lume.app.data.story.StoryIndex
import com.lume.app.data.title.TitleLoad
import com.lume.app.domain.Chapter
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.domain.dedupeChapters
import com.lume.app.domain.inReadingOrder
import com.lume.app.domain.requireNovel
import kotlinx.coroutines.Dispatchers
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

    /** The text index that name lookup searches; [loadChapter] feeds it. */
    val storyIndex = StoryIndex(app)

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
        val lc = LoadedNovelChapter(ch, blocks)
        // Off the reading path: a failed or slow write never holds the chapter back.
        app.appScope.launch { runCatching { storyIndex.index(sourceId, titleId, lc) } }
        return lc
    }

    /**
     * Applied to chapters read aloud: the reader sets it while the title is shown translated, so
     * read aloud's next chapters are translated too. Null reads the original.
     */
    var listeningTransform: (suspend (LoadedNovelChapter) -> LoadedNovelChapter)? = null

    /** [loadChapter], then [listeningTransform]; a failed translation falls back to the original. */
    suspend fun loadChapterForListening(ch: Chapter): LoadedNovelChapter {
        val lc = loadChapter(ch)
        val transform = listeningTransform ?: return lc
        return runCatching { transform(lc) }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else lc }
    }

    /** The chapter's own saved spot, or null. */
    suspend fun chapterProgress(chapterId: String) = dao.getChapterProgress(sourceId, titleId, chapterId)

    /** Reading statistics, spots, history and read marks; text counts time, not pages. */
    val recorder = ReadingRecorder(app, sourceId, titleId, { titleName }, countsPages = false)

    /** Stores a stretch of reading (or listening) for the statistics. Nothing in incognito. */
    fun saveSession(session: ReadingSessionTracker.Session) = recorder.saveSession(session)

    /** Saves [a] as the chapter's spot and as the title's Continue reading entry. Nothing in incognito. */
    suspend fun saveProgress(a: NovelAnchor, lc: LoadedNovelChapter, completed: Boolean) {
        if (recorder.incognito) return
        val count = lc.blocks.size
        recorder.saveChapterProgress(a.chapterId, a.block, a.fraction, count, completed)
        recorder.saveHistory(
            chapter = lc.chapter,
            coverUrl = coverUrl,
            typeLabel = typeLabel,
            position = a.block,
            count = count,
            offset = a.fraction,
            pageImageUrl = null,
            mediaKind = "novel",
        )
    }

    /**
     * Marks [chapterId] read and tells the trackers, in a scope that outlives the caller. When
     * [leftBehind] is given (a finished chapter the reader has moved past), its spot is set to its
     * end, so reopening it from the list starts from the top. The caller checks incognito.
     */
    fun markRead(chapterId: String, chapters: List<Chapter>, leftBehind: LoadedNovelChapter?) {
        recorder.markRead(
            chapterId,
            chapters,
            leftBehind?.let { lc ->
                { recorder.saveChapterProgress(lc.chapter.id, lc.blocks.size - 1, 1f, lc.blocks.size, completed = true) }
            },
        )
    }
}
