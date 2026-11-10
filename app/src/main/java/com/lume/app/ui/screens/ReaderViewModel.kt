package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.ReadingSessionEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.locale.appString
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.data.title.TitleLoad
import com.lume.app.domain.Chapter
import com.lume.app.domain.ChapterNeighbours
import com.lume.app.domain.chapterNeighbours
import com.lume.app.domain.hasChapterBeyond
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.ComicPage
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.domain.ReaderTheme
import com.lume.app.domain.dedupeChapters
import com.lume.app.domain.inReadingOrder
import com.lume.app.domain.ReadProgressSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    /** The chapters before and after the current one in reading order (null at either end). */
    val neighbours: ChapterNeighbours
        get() = chapterNeighbours(chapters, currentChapterId)
    /**
     * Whether a chapter lies beyond the end of what is loaded (the strip's edge), not beyond the
     * chapter currently tracked: the tracked one can lag behind the list while a swipe lands in
     * the next chapter.
     */
    fun hasChapterBeyondStrip(forward: Boolean): Boolean {
        val edge = (if (forward) loaded.lastOrNull() else loaded.firstOrNull())?.chapter?.id ?: currentChapterId
        return hasChapterBeyond(chapters, edge, forward)
    }
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
                warmed.clear()
                warmAhead()
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
        strip = buildReaderStrip(list, stripLayout())
    }

    // ---- Layout of the paged modes ---------------------------------------------------------

    /** Landscape or a wide screen: spreads in "Auto", no splitting. Set by the screen. */
    private var wideScreen by mutableStateOf(false)

    fun onWideScreen(wide: Boolean) {
        if (wide == wideScreen) return
        wideScreen = wide
        rebuildStrip()
    }

    val spreadsActive: Boolean
        get() = settings.mode != ReaderMode.Webtoon && when (settings.spreads) {
            ReaderSettings.SPREADS_ALWAYS -> true
            ReaderSettings.SPREADS_AUTO -> wideScreen
            else -> false
        }

    private val splitActive: Boolean
        get() = settings.mode != ReaderMode.Webtoon && settings.splitWide && !spreadsActive && !wideScreen

    private fun stripLayout() = StripLayout(
        splitWide = splitActive,
        spreads = spreadsActive,
        coverAlone = settings.spreadCoverAlone,
        rightToLeft = settings.mode == ReaderMode.Rtl,
        aspect = ::aspectOf,
    )

    /**
     * Lays the loaded chapters out again (split pages or spreads changed). The screen keeps its
     * pager and moves it to [anchor], so the page on screen stays.
     */
    private fun rebuildStrip() {
        if (loaded.isEmpty()) return
        val next = buildReaderStrip(loaded, stripLayout())
        if (next != strip) strip = next
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
            warmAhead()
        }
    }

    /**
     * Seamless webtoon reading: scrolling near the top puts the chapter before the first loaded
     * one above it. The list keeps its spot by item keys, so nothing on screen moves.
     */
    private suspend fun prefetchPreviousChapter() {
        if (!settingsReady || !settings.seamlessReading || settings.mode != ReaderMode.Webtoon) return
        prefetchMutex.withLock {
            val first = loaded.firstOrNull() ?: return
            val idx = chapters.indexOfFirst { chapterIdsMatch(it.id, first.chapter.id) }
            val previous = chapters.getOrNull(idx - 1) ?: return
            if (idx <= 0 || loaded.any { chapterIdsMatch(it.chapter.id, previous.id) }) return
            val pages = runCatching { loadPagesFor(previous, notice = false) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
            dao.getChapterProgress(sourceId, titleId, previous.id)?.let { loadAspects(previous.id, it.pageAspectsJson) }
            if (loaded.firstOrNull()?.chapter?.id != first.chapter.id) return
            replaceLoaded(listOf(LoadedChapter(previous, pages)) + loaded)
        }
    }

    /** Page URLs already sent to the image cache in this session. */
    private val warmed = HashSet<String>()

    /**
     * Loads the [ReaderSettings.preloadPages] pages after the current one (across chapters) into
     * the image cache, and learns their sizes on the way (for split pages and spreads).
     */
    private fun warmAhead() {
        val count = settings.preloadPages
        if (count <= 0) return
        val strip = strip
        val from = stripIndexOf(strip, anchor).coerceAtLeast(0)
        val loader = app.imageLoader
        val width = app.app.resources.displayMetrics.widthPixels
        var left = count
        var i = from + 1
        while (left > 0 && i < strip.size) {
            val pages = when (val item = strip[i]) {
                is ReaderStripItem.Page -> listOf(item)
                is ReaderStripItem.Spread -> listOfNotNull(item.first, item.second)
                is ReaderStripItem.Divider -> emptyList()
            }
            for (p in pages) {
                left--
                if (!warmed.add(p.page.imageUrl)) continue
                val chapterId = p.chapter.id
                val index = p.indexInChapter
                loader.enqueue(
                    ImageRequest.Builder(app.app)
                        .data(if (p.page.imageUrl.startsWith("http")) p.page.imageUrl else File(p.page.imageUrl))
                        // Screen-wide is enough for the cache; the reader decodes detail itself.
                        .size(width, MAX_DRAW_PX)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .listener(
                            onSuccess = { _, result ->
                                val image = result.image
                                if (image.width > 0 && image.height > 0) {
                                    reportAspect(chapterId, index, image.width.toFloat() / image.height)
                                }
                            },
                            onError = { _, _ -> warmed.remove(p.page.imageUrl) },
                        )
                        .build(),
                )
            }
            i++
        }
    }

    // ---- Position ---------------------------------------------------------------------------

    /**
     * Called by the screen when the visible position changes (never while it is restoring),
     * tagged with the [session] its list was built for.
     * [finished] are chapters whose ending is on screen; [firstVisibleIndex] and
     * [lastVisibleIndex] drive loading the chapters before and after.
     */
    fun onPosition(forSession: Int, next: ReaderAnchor, finished: Set<String>, firstVisibleIndex: Int, lastVisibleIndex: Int) {
        // A list from a previous session (being torn down) must not move the new position.
        if (forSession != session) return
        val chapterChanged = !chapterIdsMatch(next.chapterId, anchor.chapterId)
        anchor = next
        dirty = true
        if (chapterChanged) currentChapterId = next.chapterId
        if (currentPageIndex != next.pageIndex) currentPageIndex = next.pageIndex
        pendingSave.value = next
        trackReading(next)

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
        if (firstVisibleIndex <= PREFETCH_PREVIOUS_WHEN_WITHIN) {
            viewModelScope.launch { prefetchPreviousChapter() }
        }
        if (chapterChanged || next.pageIndex != lastWarmedFrom) {
            lastWarmedFrom = next.pageIndex
            warmAhead()
        }
    }

    private var lastWarmedFrom = -1

    // ---- Reading statistics -----------------------------------------------------------------

    private val sessionTracker = ReadingSessionTracker()

    /** Feeds the statistics; a chapter change closes the previous chapter's session. */
    private fun trackReading(next: ReaderAnchor) {
        if (app.prefs.incognito.value) return
        sessionTracker.onPage(next.chapterId, next.pageIndex, System.currentTimeMillis())?.let(::saveSession)
    }

    /** The reader stopped or closed: the stretch of reading ends here. */
    private fun finishReading() {
        sessionTracker.finish(System.currentTimeMillis())?.let(::saveSession)
    }

    private fun saveSession(session: ReadingSessionTracker.Session) {
        // Incognito: nothing about this reading is kept.
        if (app.prefs.incognito.value) return
        val entity = ReadingSessionEntity(
            sourceId = sourceId,
            titleId = titleId,
            titleName = titleName,
            chapterId = session.chapterId,
            startedAt = session.startedAt,
            durationMs = session.durationMs,
            pagesRead = session.pagesRead,
        )
        app.appScope.launch { withContext(NonCancellable) { dao.insertSession(entity) } }
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
        if (old == null || kotlin.math.abs(old - aspect) / old > 0.005f) {
            aspects[key] = aspect
            // A page turning out to be a double page changes split pages and spreads.
            val wasWide = (old ?: 0f) > WIDE_PAGE_ASPECT
            if ((splitActive || spreadsActive) && wasWide != aspect > WIDE_PAGE_ASPECT) rebuildStrip()
        }
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
        finishReading()
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
        val atEnd = isAtChapterEnd(a, count)
        writeChapterProgress(a, count, completed = atEnd)
        reportToServer(lc.chapter, a.pageIndex, atEnd)
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
                pageImageUrl = lc.pages.getOrNull(a.pageIndex)?.imageUrl,
            ),
        )
    }

    private var serverReport: Job? = null
    private var serverReportFinished = false

    /**
     * Tells a server that keeps reading progress (Komga, Kavita) where you are. Fire and forget:
     * it waits a moment so scrolling sends one update, and a failure is ignored. The scope
     * outlives the reader, so the last position still goes out after Back.
     */
    private fun reportToServer(chapter: Chapter, pageIndex: Int, finished: Boolean) {
        val sink = app.sources.engineById(sourceId) as? ReadProgressSink ?: return
        if (serverReportFinished && serverReport?.isActive == true) return
        serverReport?.cancel()
        serverReportFinished = finished
        serverReport = app.appScope.launch(Dispatchers.IO) {
            if (!finished) delay(SERVER_REPORT_DELAY_MS)
            runCatching { sink.reportProgress(chapter, pageIndex, finished) }
        }
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
        val next = (if (delta < 0) neighbours.previous else neighbours.next) ?: return
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

    /** Puts a just-deleted bookmark back (undo). */
    fun restorePageBookmark(bookmark: PageBookmarkEntity) {
        viewModelScope.launch { dao.insertPageBookmark(bookmark) }
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
        // The first chapter may have arrived before the settings: lay it out for them.
        rebuildStrip()
        if (settings.seamlessReading && loaded.isNotEmpty()) prefetchNextChapter()
    }

    /** Live edit from the sheet (slider drag); [commit] also writes it as the new default. */
    fun updateSettings(next: ReaderSettings, commit: Boolean) {
        val turnedSeamlessOn = next.seamlessReading && !settings.seamlessReading
        val old = settings
        settings = next
        if (layoutChanged(old, next)) rebuildStrip()
        if (commit) viewModelScope.launch { app.prefs.setReaderSettings(next) }
        if (turnedSeamlessOn) viewModelScope.launch { prefetchNextChapter() }
    }

    private fun layoutChanged(a: ReaderSettings, b: ReaderSettings) =
        a.mode != b.mode || a.splitWide != b.splitWide || a.spreads != b.spreads || a.spreadCoverAlone != b.spreadCoverAlone

    fun saveSettingsForTitle(next: ReaderSettings) {
        val old = settings
        settings = next
        if (layoutChanged(old, next)) rebuildStrip()
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

        /** How long the position must stay put before it is sent to a server. */
        const val SERVER_REPORT_DELAY_MS = 2_000L

        /** Seamless webtoon: load the chapter before this close to the start of the strip. */
        const val PREFETCH_PREVIOUS_WHEN_WITHIN = 3

        fun aspectKey(chapterId: String, pageIndex: Int) = "$chapterId $pageIndex"
    }
}
