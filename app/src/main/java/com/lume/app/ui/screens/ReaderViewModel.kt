package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.locale.appString
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.data.title.TitleLoad
import com.lume.app.domain.Chapter
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.ComicPage
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import com.lume.app.domain.dedupeChapters
import com.lume.app.domain.inReadingOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * Everything the reader knows lives here; `ReaderScreen` only draws it.
 *
 * Position is a chapter-relative [ReaderAnchor]. It is kept in [SavedStateHandle] (so after
 * process death the reader reopens the chapter you were actually in, not the route's chapter),
 * saved to Room 400 ms after scrolling stops, and flushed on stop / when the reader closes in a
 * scope that outlives the screen.
 */
@OptIn(FlowPreview::class)
class ReaderViewModel(
    private val app: AppContainer,
    val sourceId: String,
    val titleId: String,
    routeChapterId: String,
    /** Explicit target (bookmark, Continue); null = resume this chapter's own progress. */
    routePage: Int?,
    routeOffset: Float?,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val dao = app.dao

    // ---- Title ----------------------------------------------------------------------------
    var titleName by mutableStateOf("")
        private set
    var coverUrl by mutableStateOf<String?>(null)
        private set
    private var typeLabel: String? = null

    /** Reading order (oldest → newest). */
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set

    /** Every copy of every chapter (all teams and linked sources), for falling back. */
    private var allCopies: List<Chapter> = emptyList()

    /**
     * Set when a chapter came from another source than its own because its own failed
     * ("Loaded from MangaDex"); the screen shows it once and clears it.
     */
    var fallbackNotice by mutableStateOf<String?>(null)
        private set

    fun clearFallbackNotice() {
        fallbackNotice = null
    }

    // ---- Load state -----------------------------------------------------------------------
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var cloudflareUri by mutableStateOf<URI?>(null)
        private set

    var loaded by mutableStateOf<List<LoadedChapter>>(emptyList())
        private set
    var strip by mutableStateOf<List<ReaderStripItem>>(emptyList())
        private set

    /**
     * Changes whenever the strip is rebuilt from scratch (first load, retry, another chapter).
     * The screen keys its list / pager state on it and restores to [anchor] once per session.
     */
    var session by mutableIntStateOf(0)
        private set

    // ---- Position ------------------------------------------------------------------------
    /** Latest position. Not snapshot state on purpose: it changes every scroll frame. */
    var anchor: ReaderAnchor = ReaderAnchor(routeChapterId, routePage ?: 0, routeOffset ?: 0f)
        private set
    var currentChapterId by mutableStateOf(routeChapterId)
        private set
    var currentPageIndex by mutableIntStateOf(0)
        private set
    val currentChapter: Chapter?
        get() = chapters.firstOrNull { chapterIdsMatch(it.id, currentChapterId) }
    val currentChapterPageCount: Int
        get() = loaded.firstOrNull { chapterIdsMatch(it.chapter.id, currentChapterId) }?.pages?.size ?: 0

    /** Known width / height per page, keyed by [aspectKey]. */
    private val aspects = mutableStateMapOf<String, Float>()

    // ---- Settings ------------------------------------------------------------------------
    var settings by mutableStateOf(ReaderSettings())
        private set

    /** The screen waits for this so it never lays out pages in the default mode first. */
    var settingsReady by mutableStateOf(false)
        private set

    // ---- Library + page bookmarks --------------------------------------------------------
    val inLibrary: StateFlow<Boolean> =
        dao.observeIsBookmarked(sourceId, titleId).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val pageBookmarks: StateFlow<List<PageBookmarkEntity>> =
        dao.observePageBookmarks(sourceId, titleId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Saving --------------------------------------------------------------------------
    private val pendingSave = MutableStateFlow<ReaderAnchor?>(null)
    private var dirty = false
    private val markedRead = HashSet<String>()
    private val visited = LinkedHashSet<String>()
    private val prefetchMutex = Mutex()
    private var loadJob: Job? = null

    private val routeHasTarget = routePage != null

    init {
        // Back from process death: the chapter and spot you were really in.
        val savedChapter = savedState.get<String>(KEY_CHAPTER)
        if (savedChapter != null) {
            anchor = ReaderAnchor(
                savedChapter,
                savedState.get<Int>(KEY_PAGE) ?: 0,
                savedState.get<Float>(KEY_OFFSET) ?: 0f,
            )
        }
        currentChapterId = anchor.chapterId
        viewModelScope.launch { initSettings() }
        viewModelScope.launch {
            pendingSave.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { persist(it) }
        }
        // A solved Cloudflare check / reconnect while the error screen is up: try again.
        viewModelScope.launch {
            app.http.networkEpoch.drop(1).collect { if (error != null) reload() }
        }
        load(anchor, explicit = savedChapter != null || routeHasTarget)
    }

    // ---- Loading ---------------------------------------------------------------------------

    fun retry() = reload()

    private fun reload() = load(anchor, explicit = true)

    /**
     * Opens [target]'s chapter as a fresh strip. When [explicit] is false the chapter's own saved
     * position is used, unless that chapter was finished (then it starts from the top).
     */
    private fun load(target: ReaderAnchor, explicit: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = loaded.isEmpty()
            error = null
            cloudflareUri = null
            runCatching {
                if (chapters.isEmpty()) loadTitle()
                val ch = chapters.firstOrNull { chapterIdsMatch(it.id, target.chapterId) }
                    ?: error(appString(R.string.error_chapter_not_found))
                val pages = loadPagesFor(ch)
                if (pages.isEmpty()) error(appString(R.string.error_no_pages_offline))
                val saved = dao.getChapterProgress(sourceId, titleId, ch.id)
                saved?.let { loadAspects(ch.id, it.pageAspectsJson) }
                val start = when {
                    explicit -> target.copy(chapterId = ch.id)
                    saved != null && !saved.completed -> ReaderAnchor(ch.id, saved.pageIndex, saved.pageOffset)
                    else -> ReaderAnchor(ch.id, 0, 0f)
                }
                LoadedChapter(ch, pages) to start.copy(pageIndex = start.pageIndex.coerceIn(0, pages.size - 1))
            }.onSuccess { (first, start) ->
                replaceLoaded(listOf(first))
                // Stopped with the next chapter's divider at the top: that divider has to exist
                // before the list is built, or the spot can't be restored exactly.
                if (isAtChapterEnd(start, first.pages.size)) prefetchNextChapter()
                anchor = start
                currentChapterId = start.chapterId
                currentPageIndex = start.pageIndex
                visited.clear()
                visited += start.chapterId
                session++
                loading = false
                warmPageImages(first.pages)
                prefetchNextChapter()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                loading = false
                if (e is CloudflareException) {
                    cloudflareUri = e.uri
                    app.http.requestChallenge(e.uri)
                    error = appString(R.string.error_cloudflare_needed)
                } else {
                    error = e.message ?: e.toString()
                }
            }
        }
    }

    private suspend fun loadTitle() {
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
        // Next / previous follow the chapter list: one copy per chapter, continuing with the team
        // of the chapter that was opened.
        val prefs = dao.getTitlePrefs(sourceId, titleId)
        val opened = known.chapters.firstOrNull { chapterIdsMatch(it.id, currentChapterId) }
        chapters = if (prefs?.showDuplicates == true) {
            known.chapters.inReadingOrder()
        } else {
            val deduped = dedupeChapters(known.chapters, opened?.branchId ?: prefs?.branch)
            (if (opened != null && deduped.none { it.id == opened.id }) deduped + opened else deduped).inReadingOrder()
        }
    }

    /**
     * Downloaded pages first. Otherwise the chapter's own source (a linked source for its
     * chapters); when that fails, another source's or team's copy of the same chapter. The
     * chapter keeps its id either way, so the position and read mark stay where they are.
     */
    private suspend fun loadPagesFor(ch: Chapter, notice: Boolean = true): List<ComicPage> {
        withContext(Dispatchers.IO) { app.downloads.localPages(sourceId, titleId, ch.id) }?.let { return it }
        val served = app.titles.loadPages(sourceId, titleId, ch, allCopies)
        if (served.fallback && notice) {
            fallbackNotice = appString(R.string.reader_loaded_from, served.source.manifest.name)
        }
        return served.pages
    }

    private fun replaceLoaded(list: List<LoadedChapter>) {
        loaded = list
        strip = buildReaderStrip(list)
    }

    /** Seamless reading: keep the chapter after the last loaded one ready, images warmed. */
    private suspend fun prefetchNextChapter() {
        if (!settingsReady || !settings.seamlessReading) return
        prefetchMutex.withLock {
            val last = loaded.lastOrNull() ?: return
            val idx = chapters.indexOfFirst { chapterIdsMatch(it.id, last.chapter.id) }
            val next = chapters.getOrNull(idx + 1) ?: return
            if (loaded.any { chapterIdsMatch(it.chapter.id, next.id) }) return
            val pages = runCatching { loadPagesFor(next, notice = false) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
            dao.getChapterProgress(sourceId, titleId, next.id)?.let { loadAspects(next.id, it.pageAspectsJson) }
            // The strip may have been replaced (another chapter opened) while this loaded.
            if (loaded.lastOrNull()?.chapter?.id != last.chapter.id) return
            replaceLoaded(loaded + LoadedChapter(next, pages))
            warmPageImages(pages)
        }
    }

    private fun warmPageImages(pages: List<ComicPage>) {
        val loader = app.imageLoader
        pages.forEach { page ->
            loader.enqueue(
                ImageRequest.Builder(app.app)
                    .data(if (page.imageUrl.startsWith("http")) page.imageUrl else File(page.imageUrl))
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build(),
            )
        }
    }

    // ---- Position ---------------------------------------------------------------------------

    /**
     * Called by the screen when the visible position changes (never while it is restoring),
     * tagged with the [session] its list was built for.
     * [finished] are chapters whose ending is on screen; [lastVisibleIndex] drives prefetch.
     */
    fun onPosition(forSession: Int, next: ReaderAnchor, finished: Set<String>, lastVisibleIndex: Int) {
        // A list from a previous session (being torn down) must not move the new position.
        if (forSession != session) return
        val chapterChanged = !chapterIdsMatch(next.chapterId, anchor.chapterId)
        anchor = next
        dirty = true
        if (chapterChanged) currentChapterId = next.chapterId
        if (currentPageIndex != next.pageIndex) currentPageIndex = next.pageIndex
        pendingSave.value = next

        // Scrolling into a later chapter means the earlier ones you read were finished.
        val order = loaded.indexOfFirst { chapterIdsMatch(it.chapter.id, next.chapterId) }
        val passed = visited.filter { id ->
            val i = loaded.indexOfFirst { chapterIdsMatch(it.chapter.id, id) }
            i in 0 until order
        }
        visited += next.chapterId
        val count = loaded.firstOrNull { chapterIdsMatch(it.chapter.id, next.chapterId) }?.pages?.size ?: 0
        val done = buildSet {
            addAll(finished.filter { id -> visited.any { chapterIdsMatch(it, id) } })
            addAll(passed)
            if (isAtChapterEnd(next, count)) add(next.chapterId)
        }
        done.forEach(::markFinished)

        if (settings.seamlessReading && strip.size - 1 - lastVisibleIndex <= PREFETCH_WHEN_REMAINING) {
            viewModelScope.launch { prefetchNextChapter() }
        }
    }

    /** A chapter counts as read (and trackers move) only once you reach its end. */
    private fun markFinished(chapterId: String) {
        // Incognito: nothing about this reading is kept (read marks, trackers, downloads).
        if (app.prefs.incognito.value) return
        if (!markedRead.add(chapterId)) return
        val lc = loaded.firstOrNull { chapterIdsMatch(it.chapter.id, chapterId) }
        app.appScope.launch {
            withContext(NonCancellable) {
                dao.markChapterRead(ChapterReadEntity(sourceId = sourceId, titleId = titleId, chapterId = chapterId))
                if (lc != null && !chapterIdsMatch(chapterId, anchor.chapterId)) {
                    // Left behind at its end: reopening it from the list starts from the top.
                    writeChapterProgress(ReaderAnchor(lc.chapter.id, lc.pages.size - 1, 1f), lc.pages.size, completed = true)
                }
                runCatching { app.trackerSync.pushProgress(sourceId, titleId, chapters, chapterId) }
            }
        }
    }

    fun aspectOf(chapterId: String, pageIndex: Int): Float? = aspects[aspectKey(chapterId, pageIndex)]

    fun reportAspect(chapterId: String, pageIndex: Int, aspect: Float) {
        if (aspect <= 0f || aspect.isNaN() || aspect.isInfinite()) return
        val key = aspectKey(chapterId, pageIndex)
        val old = aspects[key]
        if (old == null || kotlin.math.abs(old - aspect) / old > 0.005f) aspects[key] = aspect
    }

    private fun loadAspects(chapterId: String, json: String) {
        decodeAspects(json).forEach { (i, a) -> aspects.putIfAbsent(aspectKey(chapterId, i), a) }
    }

    private fun aspectsFor(chapterId: String, pageCount: Int): String {
        val prefix = aspectKey(chapterId, 0).dropLast(1)
        val map = aspects.entries
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { e -> e.key.removePrefix(prefix).toIntOrNull()?.let { it to e.value } }
            .toMap()
        return encodeAspects(map, pageCount)
    }

    /** Save now, in a scope that survives this ViewModel (Back, stop, process going away). */
    fun flush() {
        if (!dirty) return
        val a = anchor
        app.appScope.launch { withContext(NonCancellable) { persist(a) } }
    }

    private suspend fun persist(a: ReaderAnchor) {
        dirty = false
        savedState[KEY_CHAPTER] = a.chapterId
        savedState[KEY_PAGE] = a.pageIndex
        savedState[KEY_OFFSET] = a.pageOffset
        // Incognito: the spot survives rotation (saved state) but never reaches history.
        if (app.prefs.incognito.value) return
        val lc = loaded.firstOrNull { chapterIdsMatch(it.chapter.id, a.chapterId) } ?: return
        val count = lc.pages.size
        writeChapterProgress(a, count, completed = isAtChapterEnd(a, count))
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
                pageIndex = a.pageIndex,
                pageCount = count,
                pageOffset = a.pageOffset,
            ),
        )
    }

    private suspend fun writeChapterProgress(a: ReaderAnchor, count: Int, completed: Boolean) {
        dao.upsertChapterProgress(
            ChapterProgressEntity(
                sourceId = sourceId,
                titleId = titleId,
                chapterId = a.chapterId,
                pageIndex = a.pageIndex,
                pageOffset = a.pageOffset,
                pageCount = count,
                completed = completed,
                pageAspectsJson = aspectsFor(a.chapterId, count),
            ),
        )
    }

    // ---- Navigation inside the reader ------------------------------------------------------

    /** Previous / next chapter without leaving the reader (one reader in the back stack). */
    fun goChapter(delta: Int) {
        val idx = chapters.indexOfFirst { chapterIdsMatch(it.id, currentChapterId) }
        val next = chapters.getOrNull(idx + delta) ?: return
        flush()
        load(ReaderAnchor(next.id, 0, 0f), explicit = false)
    }

    /** Jump to a page bookmark (a fresh strip starting in its chapter). */
    fun jumpTo(bookmark: PageBookmarkEntity) {
        flush()
        load(ReaderAnchor(bookmark.chapterId, bookmark.pageIndex, bookmark.pageOffset), explicit = true)
    }

    fun solveChallenge() {
        val uri = cloudflareUri ?: return
        viewModelScope.launch { app.http.requestChallenge(uri) }
    }

    // ---- Library + bookmarks ---------------------------------------------------------------

    fun toggleLibrary() {
        viewModelScope.launch {
            if (inLibrary.value) {
                app.library.remove(sourceId, titleId)
            } else {
                app.library.add(sourceId, titleId, titleName, coverUrl, typeLabel)
            }
        }
    }

    /** Pins the current spot; returns the new bookmark's id through [onSaved]. */
    fun addPageBookmark(onSaved: (Long) -> Unit) {
        val a = anchor
        val lc = loaded.firstOrNull { chapterIdsMatch(it.chapter.id, a.chapterId) } ?: return
        viewModelScope.launch {
            val id = dao.insertPageBookmark(
                PageBookmarkEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    chapterId = lc.chapter.id,
                    chapterName = lc.chapter.title,
                    pageIndex = a.pageIndex,
                    pageOffset = a.pageOffset,
                    pageImageUrl = lc.pages.getOrNull(a.pageIndex)?.imageUrl,
                ),
            )
            onSaved(id)
        }
    }

    fun setBookmarkNote(id: Long, note: String) {
        viewModelScope.launch { dao.updatePageBookmarkNote(id, note.trim().ifEmpty { null }) }
    }

    fun deletePageBookmark(id: Long) {
        viewModelScope.launch { dao.deletePageBookmark(id) }
    }

    // ---- Settings ---------------------------------------------------------------------------

    /**
     * Global prefs combined with this title's overrides, read once. After that only the sheet
     * changes [settings], so a DataStore round trip can no longer snap a slider back.
     */
    private suspend fun initSettings() {
        val global = app.prefs.readerSettings.first()
        val titlePrefs = dao.getTitlePrefs(sourceId, titleId)
        settings = global.copy(
            mode = titlePrefs?.readerMode?.let { ReaderMode.fromPrefs(it) } ?: global.mode,
            theme = titlePrefs?.readerTheme?.let { ReaderTheme.entries.getOrElse(it) { global.theme } } ?: global.theme,
            gap = titlePrefs?.gap ?: global.gap,
        )
        settingsReady = true
        if (settings.seamlessReading && loaded.isNotEmpty()) prefetchNextChapter()
    }

    /** Live edit from the sheet (slider drag); [commit] also writes it as the new default. */
    fun updateSettings(next: ReaderSettings, commit: Boolean) {
        val turnedSeamlessOn = next.seamlessReading && !settings.seamlessReading
        settings = next
        if (commit) viewModelScope.launch { app.prefs.setReaderSettings(next) }
        if (turnedSeamlessOn) viewModelScope.launch { prefetchNextChapter() }
    }

    fun saveSettingsForTitle(next: ReaderSettings) {
        settings = next
        viewModelScope.launch {
            // Keep the chapter list settings stored in the same row.
            val base = dao.getTitlePrefs(sourceId, titleId) ?: TitlePrefsEntity(sourceId = sourceId, titleId = titleId)
            dao.upsertTitlePrefs(
                base.copy(
                    readerMode = next.mode.prefsIndex,
                    readerTheme = next.theme.ordinal,
                    gap = next.gap,
                ),
            )
        }
    }

    override fun onCleared() {
        flush()
        // "Delete after reading": chapters finished in this session lose their download.
        if (app.prefs.deleteAfterRead.value && markedRead.isNotEmpty()) {
            val finished = markedRead.toList()
            app.appScope.launch(Dispatchers.IO) {
                finished.forEach { id -> runCatching { app.downloads.delete(sourceId, titleId, id) } }
            }
        }
    }

    private companion object {
        const val KEY_CHAPTER = "reader.chapterId"
        const val KEY_PAGE = "reader.pageIndex"
        const val KEY_OFFSET = "reader.pageOffset"
        const val SAVE_DEBOUNCE_MS = 400L

        /** Start loading the next chapter this many strip items before the end. */
        const val PREFETCH_WHEN_REMAINING = 12

        fun aspectKey(chapterId: String, pageIndex: Int) = "$chapterId $pageIndex"
    }
}
