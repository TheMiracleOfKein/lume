package com.lume.app.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.R
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.story.AiRecap
import com.lume.app.data.story.AiRecapState
import com.lume.app.data.story.RecapParagraph
import com.lume.app.llm.LlmClient
import com.lume.app.translate.llmConfig
import java.util.Locale
import com.lume.app.data.story.StoryLookup
import com.lume.app.data.story.StoryMention
import com.lume.app.data.story.knownTerms
import com.lume.app.data.story.recapBlocks
import com.lume.app.data.story.saveCodexEntry
import com.lume.app.data.story.termAt
import com.lume.app.translate.originalText
import com.lume.app.data.locale.appString
import com.lume.app.data.userMessage
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.data.prefs.NovelTheme
import com.lume.app.domain.Chapter
import com.lume.app.domain.ChapterNeighbours
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.domain.chapterNeighbours
import com.lume.app.domain.chapterTranslations
import com.lume.app.readaloud.ReadAloud
import com.lume.app.readaloud.ReadAloudState
import com.lume.app.readaloud.ReadAloudStatus
import com.lume.app.data.novel.NovelBlock
import com.lume.app.translate.ReaderTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the text reader knows lives here; `NovelReaderScreen` only draws it.
 *
 * Position is a [NovelAnchor] (chapter, block, fraction of the block). It is kept in
 * [SavedStateHandle] (so after process death the reader reopens where you were, not at the route's
 * chapter), saved to Room 400 ms after scrolling stops, and flushed on stop and when the reader
 * closes, in a scope that outlives the screen. Same shape as the comics reader.
 */
@OptIn(FlowPreview::class)
class NovelReaderViewModel(
    private val app: AppContainer,
    val sourceId: String,
    val titleId: String,
    routeChapterId: String,
    /** Explicit target block (Continue, bookmark); null = resume this chapter's own progress. */
    routeBlock: Int?,
    routeOffset: Float?,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val dao = app.dao

    /** Chapters, text and saved progress, shared with read aloud. */
    private val book = NovelBook(app, sourceId, titleId)

    // ---- Title ----------------------------------------------------------------------------
    var titleName by mutableStateOf("")
        private set

    /** Reading order (oldest to newest). */
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set

    /** Every copy of every chapter (all teams), for switching translation. */
    private val allCopies: List<Chapter> get() = book.allCopies

    /** The current chapter's copies by each team (empty when there is only one). */
    var translations by mutableStateOf<List<Chapter>>(emptyList())
        private set

    // ---- Load state -----------------------------------------------------------------------
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loaded: List<LoadedNovelChapter> = emptyList()

    /** The rows of the list: every loaded chapter's title and blocks. */
    var items by mutableStateOf<List<NovelItem>>(emptyList())
        private set

    /**
     * Changes whenever the list is rebuilt from scratch (first load, retry, another chapter). The
     * screen keys its list state on it and restores to [anchor] once per session. Appending the
     * next chapter keeps the session.
     */
    var session by mutableIntStateOf(0)
        private set

    // ---- Position -------------------------------------------------------------------------
    /** Latest position. Not snapshot state on purpose: it changes every scroll frame. */
    var anchor: NovelAnchor = NovelAnchor(routeChapterId, routeBlock ?: 0, routeOffset ?: 0f)
        private set
    var currentChapterId by mutableStateOf(routeChapterId)
        private set
    var currentBlock by mutableIntStateOf(0)
        private set

    /** The end of the current chapter is on screen. */
    var atChapterEnd by mutableStateOf(false)
        private set

    val currentChapter: Chapter?
        get() = chapters.firstOrNull { it.id == currentChapterId }
    val currentBlockCount: Int
        get() = loaded.firstOrNull { it.chapter.id == currentChapterId }?.blocks?.size ?: 0
    val neighbours: ChapterNeighbours
        get() = chapterNeighbours(chapters, currentChapterId)

    /** Progress through the current chapter, 0..100. */
    val percent: Int
        get() = if (atChapterEnd) 100 else chapterPercent(currentBlock, anchor.fraction, currentBlockCount)

    // ---- Settings -------------------------------------------------------------------------
    /** Read once; after that only the sheet changes it, so a DataStore round trip can't snap a slider back. */
    private var globalSettings = app.prefs.novelReaderSettings.value

    /** The global settings with this title's text size, theme and page mode overrides applied. */
    var settings by mutableStateOf(globalSettings)
        private set

    /** "Save for this title" is on: size, theme and page mode edits go to this title, not the defaults. */
    var saveForTitle by mutableStateOf(false)
        private set

    /** The sheet changed something; the title's saved overrides, read late, must not undo it. */
    private var settingsTouched = false

    /** Title override writes, one at a time in the order they were made. */
    private val titleWrites = Mutex()

    private suspend fun initSettings() {
        val prefs = dao.getTitlePrefs(sourceId, titleId) ?: return
        if (settingsTouched) return
        if (prefs.novelFontSize == null && prefs.novelTheme == null && prefs.novelPaged == null) return
        saveForTitle = true
        settings = settings.withTitleOverrides(prefs).clamped()
    }

    private fun NovelReaderSettings.withTitleOverrides(prefs: TitlePrefsEntity) = copy(
        fontSize = prefs.novelFontSize ?: fontSize,
        theme = prefs.novelTheme?.let { NovelTheme.entries.getOrNull(it) } ?: theme,
        paged = prefs.novelPaged ?: paged,
    )

    /** These settings with the global size, theme and page mode back in: what a title override leaves untouched globally. */
    private fun NovelReaderSettings.keepingGlobalTitleFields() = copy(
        fontSize = globalSettings.fontSize,
        theme = globalSettings.theme,
        paged = globalSettings.paged,
    )

    private fun saveTitleOverrides(s: NovelReaderSettings?) {
        viewModelScope.launch {
            titleWrites.withLock {
                val base = dao.getTitlePrefs(sourceId, titleId)
                if (base == null && s == null) return@withLock
                val row = base ?: TitlePrefsEntity(sourceId = sourceId, titleId = titleId)
                dao.upsertTitlePrefs(
                    row.copy(novelFontSize = s?.fontSize, novelTheme = s?.theme?.ordinal, novelPaged = s?.paged),
                )
            }
        }
    }

    /** Live edit from the sheet (slider drag); [commit] also writes it as the new default (or title override). */
    fun updateSettings(next: NovelReaderSettings, commit: Boolean) {
        val clean = next.clamped()
        settingsTouched = true
        settings = clean
        if (!commit) return
        if (saveForTitle) {
            globalSettings = clean.keepingGlobalTitleFields()
            saveTitleOverrides(clean)
        } else {
            globalSettings = clean
        }
        val global = globalSettings
        viewModelScope.launch { app.prefs.setNovelReaderSettings(global) }
    }

    /** Switch "Save for this title": keeps the current size, theme and page mode for it, or drops them. */
    fun changeSaveForTitle(on: Boolean) {
        if (on == saveForTitle) return
        settingsTouched = true
        saveForTitle = on
        if (on) {
            saveTitleOverrides(settings)
        } else {
            settings = settings.keepingGlobalTitleFields()
            saveTitleOverrides(null)
        }
    }

    /** Every text reader setting back to its default, saved; this title's overrides are dropped too. */
    fun resetSettings() {
        settingsTouched = true
        saveForTitle = false
        saveTitleOverrides(null)
        globalSettings = NovelReaderSettings()
        settings = globalSettings
        viewModelScope.launch { app.prefs.setNovelReaderSettings(NovelReaderSettings()) }
    }

    // ---- Read aloud state (declared before init, whose collector sets it at once) --------

    /** Read aloud of this title, or null when it is off or reading another title. */
    var readAloud by mutableStateOf<ReadAloudState?>(null)
        private set

    /** The list keeps the spoken sentence in view; off once the reader scrolls away by hand. */
    var followVoice by mutableStateOf(true)
        private set

    // ---- Translation (declared before init, which starts it) -----------------------------

    /** On-device translation through the Lume Translate add-on; inert when no model fits the title. */
    val translation = ReaderTranslation(app, viewModelScope, sourceId, titleId) { chapterId, blocks, original ->
        replaceBlocks(chapterId, blocks, original)
    }

    /** A chapter's text changed in place (translated, or back to the original): same blocks, new words. */
    private fun replaceBlocks(chapterId: String, blocks: List<NovelBlock>, original: List<NovelBlock>?) {
        val i = loaded.indexOfFirst { it.chapter.id == chapterId }
        if (i < 0 || loaded[i].let { (it.original ?: it.blocks).size } != blocks.size) return
        loaded = loaded.toMutableList().also { it[i] = LoadedNovelChapter(it[i].chapter, blocks, original) }
        items = buildNovelItems(loaded)
        textVersion++
    }

    /** Bumped when text changes in place (a translation batch): the paged reader re-finds its page. */
    var textVersion by mutableIntStateOf(0)
        private set

    /** The source text of a translated block, for the long press; null when it isn't translated. */
    fun originalOf(chapterId: String, index: Int): NovelBlock? =
        loaded.firstOrNull { it.chapter.id == chapterId }?.original?.getOrNull(index)

    // ---- Saving ---------------------------------------------------------------------------
    private val pendingSave = MutableStateFlow<NovelAnchor?>(null)
    private var dirty = false
    private val markedRead = HashSet<String>()
    private val appendMutex = Mutex()
    private var appendFailedFor: String? = null
    private var appendFailedAt = 0L
    private var loadJob: Job? = null
    private val routeHasTarget = routeBlock != null

    init {
        // Back from process death: the chapter and spot you were really in.
        val savedChapter = savedState.get<String>(KEY_CHAPTER)
        if (savedChapter != null) {
            anchor = NovelAnchor(
                savedChapter,
                savedState.get<Int>(KEY_BLOCK) ?: 0,
                savedState.get<Float>(KEY_FRACTION) ?: 0f,
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
        // Read aloud of this title: its state for the screen, and its chapter in the list.
        viewModelScope.launch {
            ReadAloud.state.collect { s ->
                val mine = s?.takeIf { it.isFor(sourceId, titleId) }
                readAloud = mine
                if (mine != null && followVoice) followChapter(mine)
            }
        }
        translation.currentChapterId = anchor.chapterId
        translation.start(loaded = { loaded }, anchorBlock = { anchor.block })
        book.listeningTransform = translation::forListening
        load(anchor, explicit = savedChapter != null || routeHasTarget, opening = true)
    }

    // ---- Read aloud -----------------------------------------------------------------------

    /** The sentence being read, for the highlight; null on the chapter title or a pause. */
    internal val spoken: SpokenRange?
        get() = readAloud?.takeIf { it.block >= 0 && it.end > it.start }?.let { SpokenRange(it.chapterId, it.block, it.start, it.end) }

    /** Starts reading aloud from the text at the top of the screen. */
    fun startReadAloud(context: Context) {
        val lc = loaded.firstOrNull { it.chapter.id == anchor.chapterId } ?: return
        flush()
        followVoice = true
        ReadAloud.start(context, app, book, chapters, lc, anchor)
    }

    fun changeFollowVoice(on: Boolean) {
        if (on == followVoice) return
        followVoice = on
        readAloud?.let { if (on) followChapter(it) }
    }

    /** The voice went on into a chapter the list doesn't have: open it there. */
    private fun followChapter(s: ReadAloudState) {
        if (loading || loadJob?.isActive == true) return
        if (loaded.any { it.chapter.id == s.chapterId }) return
        if (chapters.none { it.id == s.chapterId }) return
        load(NovelAnchor(s.chapterId, s.block.coerceAtLeast(0), 0f), explicit = true)
    }

    /** Read aloud is playing this title, so it keeps the progress, not the scrolling. */
    private fun readAloudKeepsProgress(): Boolean =
        ReadAloud.state.value?.let { it.isFor(sourceId, titleId) && it.status != ReadAloudStatus.Paused } == true

    // ---- Loading --------------------------------------------------------------------------

    fun retry() = reload()

    private fun reload() = load(anchor, explicit = true)

    /**
     * Opens [target]'s chapter as a fresh list. When [explicit] is false the chapter's own saved
     * position is used, unless that chapter was finished (then it starts from the top). [opening] is
     * the reader's first load (from Continue reading, the title or the chapter list): only then can
     * a "Previously…" recap show.
     */
    private fun load(target: NovelAnchor, explicit: Boolean, opening: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = loaded.isEmpty()
            error = null
            runCatching {
                // Before anything is saved for this visit: when the title was last read, for the recap.
                val lastRead = if (opening) dao.getProgress(sourceId, titleId)?.updatedAt else null
                if (chapters.isEmpty()) loadTitle()
                val ch = chapters.firstOrNull { it.id == target.chapterId }
                    ?: error(appString(R.string.error_chapter_not_found))
                val chapter = book.loadChapter(ch)
                if (chapter.blocks.isEmpty()) error(appString(R.string.novel_no_text))
                val saved = book.chapterProgress(ch.id)
                val start = when {
                    explicit -> target.copy(chapterId = ch.id)
                    saved != null && !saved.completed -> NovelAnchor(ch.id, saved.pageIndex, saved.pageOffset)
                    else -> NovelAnchor(ch.id, 0, 0f)
                }
                Triple(chapter, start.copy(block = start.block.coerceIn(0, chapter.blocks.size - 1)), lastRead)
            }.onSuccess { (first, start, lastRead) ->
                dismissRecap()
                translation.onListReset()
                loaded = listOf(first)
                items = buildNovelItems(loaded)
                anchor = start
                currentChapterId = start.chapterId
                translation.currentChapterId = start.chapterId
                translation.onChapterLoaded(first)
                translations = chapterTranslations(allCopies, first.chapter)
                currentBlock = start.block
                atChapterEnd = false
                appendFailedFor = null
                session++
                loading = false
                if (items.size - 1 - novelItemIndex(items, start) <= APPEND_WHEN_REMAINING) appendNextChapter()
                if (lastRead != null) viewModelScope.launch { runCatching { buildRecap(first, start, lastRead) } }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                loading = false
                if (e is CloudflareException) {
                    app.http.requestChallenge(e.uri)
                    error = appString(R.string.error_cloudflare_needed)
                } else {
                    app.errorLog.record(sourceId, e)
                    error = e.userMessage()
                }
            }
        }
    }

    private suspend fun loadTitle() {
        val list = book.loadTitle(currentChapterId)
        titleName = book.titleName
        chapters = list
    }

    /** The title's cover, for read aloud's notification. */
    val coverUrl: String? get() = book.coverUrl

    /** Adds the chapter after the last loaded one to the list (no-op at the end or while one loads). */
    private fun appendNextChapter() {
        val last = loaded.lastOrNull()?.chapter ?: return
        val next = chapterNeighbours(chapters, last.id).next ?: return
        if (appendFailedFor == next.id && System.currentTimeMillis() - appendFailedAt < APPEND_RETRY_MS) return
        val forSession = session
        viewModelScope.launch {
            appendMutex.withLock {
                if (forSession != session || loaded.lastOrNull()?.chapter?.id != last.id) return@launch
                runCatching { book.loadChapter(next) }
                    .onSuccess { chapter ->
                        if (forSession != session) return@onSuccess
                        loaded = loaded + chapter
                        items = buildNovelItems(loaded)
                        translation.onChapterLoaded(chapter)
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        // Not shown: the reader asks again after the next scroll step; the chapter
                        // buttons still work.
                        appendFailedFor = next.id
                        appendFailedAt = System.currentTimeMillis()
                    }
            }
        }
    }

    // ---- Position -------------------------------------------------------------------------

    /**
     * Called by the screen when the visible position changes (never while it is restoring), tagged
     * with the [session] its list was built for. [lastVisibleIndex] is the last row on screen: it
     * says which chapter ends are in view and when the next chapter is due.
     */
    fun onPosition(forSession: Int, next: NovelAnchor, lastVisibleIndex: Int) {
        // A list from a previous session (being torn down) must not move the new position.
        if (forSession != session) return
        val chapterChanged = next.chapterId != anchor.chapterId
        anchor = next
        dirty = true
        if (chapterChanged) {
            currentChapterId = next.chapterId
            translation.currentChapterId = next.chapterId
            translations = currentChapter?.let { chapterTranslations(allCopies, it) }.orEmpty()
        }
        if (currentBlock != next.block) currentBlock = next.block
        pendingSave.value = next
        trackReading(next)

        // A chapter counts as read once its last row is on screen; every chapter before the one
        // you are in has been scrolled through.
        val order = loaded.indexOfFirst { it.chapter.id == next.chapterId }
        val done = buildSet {
            loaded.forEachIndexed { i, lc ->
                if (i < order || chapterEndIndex(items, lc.chapter.id) <= lastVisibleIndex) add(lc.chapter.id)
            }
        }
        atChapterEnd = next.chapterId in done && chapterEndIndex(items, next.chapterId) <= lastVisibleIndex
        done.forEach(::markFinished)

        if (items.size - 1 - lastVisibleIndex <= APPEND_WHEN_REMAINING) appendNextChapter()
    }

    // ---- Story lookup and recap -----------------------------------------------------------

    private val storyLookup = StoryLookup(app, book.storyIndex)

    /** The lookup sheet: null while closed. */
    var lookupState by mutableStateOf<LookupState?>(null)
        private set

    /** Where the pressed paragraph is: mentions after it are left out of the lookup. */
    private var lookupAnchor: NovelAnchor = anchor
    private var lookupJob: Job? = null

    /**
     * Opens the lookup for the word at [offset] of the pressed block's shown text. When no word
     * can be made out, [onNoTerm] gets the paragraph's original (only for a translated block), so
     * the long press still shows what it used to.
     */
    fun lookup(chapterId: String, block: Int, offset: Int?, onNoTerm: (String) -> Unit) {
        val lc = loaded.firstOrNull { it.chapter.id == chapterId }
        val shown = lc?.blocks?.getOrNull(block)?.originalText()
        val original = lc?.original?.getOrNull(block)?.originalText()
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val term = if (shown != null && offset != null) {
                val known = knownTerms(dao.glossaryOnce(sourceId, titleId), dao.codexOnce(sourceId, titleId))
                termAt(shown, offset, known)?.let { shown.substring(it.first, it.last + 1) }
            } else {
                null
            }
            if (term == null) {
                original?.let(onNoTerm)
                return@launch
            }
            lookupAnchor = NovelAnchor(chapterId, block, 0f)
            search(term, original)
        }
    }

    /** Searches [term] typed into the sheet's field. */
    fun lookupTerm(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { search(t, lookupState?.original) }
    }

    private suspend fun search(term: String, original: String?) {
        lookupState = LookupState(term, busy = true, context = null, original = original)
        val result = runCatching {
            // Downloaded chapters that were never opened are indexed first.
            storyLookup.backfill(sourceId, titleId, chapters, lookupAnchor)
            storyLookup.lookup(
                sourceId, titleId, term, chapters, lookupAnchor,
                dao.glossaryOnce(sourceId, titleId), dao.codexOnce(sourceId, titleId),
            )
        }.getOrElse { if (it is CancellationException) throw it else null }
        lookupState = LookupState(term, busy = false, context = result, original = original)
    }

    /** The paragraphs around a mention, for expanding its snippet. */
    suspend fun mentionContext(mention: StoryMention) = storyLookup.context(sourceId, titleId, mention)

    /** Saves (or changes) a codex entry of this title, then looks the term up again so the sheet shows it. */
    fun saveCodex(existing: CodexEntryEntity?, name: String, kind: String, note: String, aliases: List<String>) {
        val term = lookupState?.term
        viewModelScope.launch {
            saveCodexEntry(dao, sourceId, titleId, existing, name, kind, note, aliases)
            if (term != null) lookupTerm(name.takeIf { term.equals(existing?.name, true) || existing == null } ?: term)
        }
    }

    fun deleteCodex(entry: CodexEntryEntity) {
        val term = lookupState?.term
        viewModelScope.launch {
            dao.deleteCodexEntry(entry.id)
            if (term != null) lookupTerm(term)
        }
    }

    /** Drops this title's stored text and indexes the chapters on screen again; the rest comes back on the next lookup. */
    fun rebuildIndexForTitle() {
        val state = lookupState ?: return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            lookupState = state.copy(busy = true)
            runCatching {
                book.storyIndex.clearTitle(sourceId, titleId)
                loaded.forEach { book.storyIndex.index(sourceId, titleId, it) }
            }.onFailure { if (it is CancellationException) throw it }
            search(state.term, state.original)
        }
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        lookupState = null
    }

    /** The paragraphs before the spot the reader comes back to; null when there is nothing to recall. */
    var recap by mutableStateOf<List<RecapParagraph>?>(null)
        private set

    fun dismissRecap() {
        recap = null
        aiRecapJob?.cancel()
        aiRecap = null
    }

    private val aiRecapper = AiRecap(app, sourceId, titleId)

    /** The AI summary shown above the recap's paragraphs; null when the AI recap is off or not set up. */
    var aiRecap by mutableStateOf<AiRecapState?>(null)
        private set
    private var aiRecapJob: Job? = null

    /** The language AI recaps are written in: the title's translation language, else the app's. */
    private suspend fun recapLanguage(): String =
        dao.getTitlePrefs(sourceId, titleId)?.novelTranslateTo ?: Locale.getDefault().language

    /** A chapter's paragraphs in the original: from the story index, the loaded list, or loaded now. */
    private suspend fun chapterText(chapter: Chapter): List<String>? {
        val stored = dao.chapterBlocks(sourceId, titleId, chapter.id).map { it.text }
        if (stored.isNotEmpty()) return stored
        val lc = loaded.firstOrNull { it.chapter.id == chapter.id }
            ?: runCatching { book.loadChapter(chapter) }.getOrNull()
            ?: return null
        return (lc.original ?: lc.blocks).mapNotNull { it.originalText() }
    }

    /** Starts the AI summary for a reader coming back to [start] in [first] (the AI recap setting). */
    private fun startAiRecap(first: LoadedNovelChapter, start: NovelAnchor, current: List<String>) {
        if (!app.prefs.aiRecap.value) return
        val config = llmConfig(app) ?: return
        val upto = if (start.fraction > 0f) start.block + 1 else start.block
        val soFar = current.take(upto.coerceIn(0, current.size))
        aiRecapJob?.cancel()
        aiRecap = AiRecapState.Loading
        aiRecapJob = viewModelScope.launch {
            aiRecap = try {
                aiRecapper.recap(LlmClient(app.http.okHttp, config), chapters, first.chapter, soFar, recapLanguage(), ::chapterText)
                    ?.let { AiRecapState.Ready(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiRecapState.Failed(e.message ?: e.toString())
            }
        }
    }

    /** A finished chapter gets its digest now, so the next recap only has to join digests. */
    private fun digestFinished(chapterId: String) {
        if (!app.prefs.aiRecap.value) return
        val config = llmConfig(app) ?: return
        val chapter = chapters.firstOrNull { it.id == chapterId } ?: return
        val lc = loaded.firstOrNull { it.chapter.id == chapterId }
        app.appScope.launch {
            runCatching {
                aiRecapper.digest(LlmClient(app.http.okHttp, config), chapter, recapLanguage()) {
                    lc?.let { (it.original ?: it.blocks).mapNotNull { b -> b.originalText() } } ?: chapterText(chapter)
                }
            }
        }
    }

    /**
     * Sets [recap] when the title was last read [lastRead] long enough ago (the setting): the last
     * paragraphs before [start], with the previous chapter's end when this chapter has too few.
     * Always in the original language: the recap isn't translated, because chapter translations are
     * cached per whole chapter and a few paragraphs on their own would be a new engine request.
     */
    private suspend fun buildRecap(first: LoadedNovelChapter, start: NovelAnchor, lastRead: Long) {
        val hours = app.prefs.recapAfterHours.value
        if (hours <= 0 || app.prefs.incognito.value) return
        if (System.currentTimeMillis() - lastRead < hours * MS_PER_HOUR) return
        val current = (first.original ?: first.blocks).map { it.originalText().orEmpty() }
        var paragraphs = recapBlocks(current, start.block, start.fraction, emptyList())
        if (paragraphs.size < RECAP_PARAGRAPHS) {
            val tail = previousChapterTail(first.chapter)
            if (tail.isNotEmpty()) paragraphs = recapBlocks(current, start.block, start.fraction, tail)
        }
        // The reader may have moved on or opened another chapter while this was read.
        if (paragraphs.isNotEmpty() && currentChapterId == start.chapterId) {
            recap = paragraphs
            startAiRecap(first, start, current)
        }
    }

    /** The last paragraphs of the chapter before [chapter]: from the story index, else from the chapter itself. */
    private suspend fun previousChapterTail(chapter: Chapter): List<String> {
        val previous = chapterNeighbours(chapters, chapter.id).previous ?: return emptyList()
        val stored = dao.chapterTail(sourceId, titleId, previous.id, RECAP_TAIL_BLOCKS).map { it.text }
        if (stored.isNotEmpty()) return stored
        val lc = runCatching { book.loadChapter(previous) }.getOrNull() ?: return emptyList()
        return (lc.original ?: lc.blocks).mapNotNull { it.originalText() }.takeLast(RECAP_TAIL_BLOCKS)
    }

    // ---- Reading statistics ---------------------------------------------------------------

    private val sessionTracker = ReadingSessionTracker()

    private fun trackReading(next: NovelAnchor) {
        if (app.prefs.incognito.value) return
        sessionTracker.onPage(next.chapterId, next.block, System.currentTimeMillis())?.let(::saveSession)
    }

    /** The reader stopped or closed: the stretch of reading ends here. */
    private fun finishReading() {
        sessionTracker.finish(System.currentTimeMillis())?.let(::saveSession)
    }

    private fun saveSession(session: ReadingSessionTracker.Session) = book.saveSession(session)

    private fun markFinished(chapterId: String) {
        // Incognito: nothing about this reading is kept (read marks, trackers).
        if (app.prefs.incognito.value) return
        if (!markedRead.add(chapterId)) return
        // Left behind at its end: reopening it from the list starts from the top.
        val leftBehind = loaded.firstOrNull { it.chapter.id == chapterId }?.takeIf { it.chapter.id != anchor.chapterId }
        book.markRead(chapterId, chapters, leftBehind)
        digestFinished(chapterId)
    }

    /** Save now, in a scope that survives this ViewModel (Back, stop, process going away). */
    fun flush() {
        finishReading()
        if (!dirty) return
        val a = anchor
        val atEnd = atChapterEnd
        app.appScope.launch { withContext(NonCancellable) { persist(a, atEnd) } }
    }

    private suspend fun persist(a: NovelAnchor, atEnd: Boolean = atChapterEnd) {
        dirty = false
        savedState[KEY_CHAPTER] = a.chapterId
        savedState[KEY_BLOCK] = a.block
        savedState[KEY_FRACTION] = a.fraction
        // Incognito: the spot survives rotation (saved state) but never reaches history.
        if (app.prefs.incognito.value) return
        if (readAloudKeepsProgress()) return
        val lc = loaded.firstOrNull { it.chapter.id == a.chapterId } ?: return
        book.saveProgress(a, lc, completed = atEnd)
    }

    // ---- Navigation inside the reader -----------------------------------------------------

    /** Previous / next chapter without leaving the reader. */
    fun goChapter(delta: Int) {
        val next = (if (delta < 0) neighbours.previous else neighbours.next) ?: return
        openChapter(next.id)
    }

    /** Opens [chapterId] from its own saved spot (the top when it was finished). */
    fun openChapter(chapterId: String) {
        flush()
        load(NovelAnchor(chapterId, 0, 0f), explicit = false)
    }

    /**
     * Reads [copy] (another team's translation of the current chapter) from the top, and follows
     * its team from here on: next / previous, and the title's preferred team.
     */
    fun switchTranslation(copy: Chapter) {
        if (copy.id == currentChapterId) return
        flush()
        val branch = copy.branchId
        chapters = book.readingList(branch, copy)
        if (branch != null) {
            viewModelScope.launch {
                titleWrites.withLock {
                    val row = dao.getTitlePrefs(sourceId, titleId) ?: TitlePrefsEntity(sourceId = sourceId, titleId = titleId)
                    dao.upsertTitlePrefs(row.copy(branch = branch))
                }
            }
        }
        load(NovelAnchor(copy.id, 0, 0f), explicit = true)
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
        const val KEY_CHAPTER = "novel.chapterId"
        const val KEY_BLOCK = "novel.block"
        const val KEY_FRACTION = "novel.fraction"
        const val SAVE_DEBOUNCE_MS = 400L
        const val MS_PER_HOUR = 3_600_000L

        /** The recap wants this many paragraphs; fewer in the chapter so far are topped up from the one before. */
        const val RECAP_PARAGRAPHS = 4
        const val RECAP_TAIL_BLOCKS = 6

        /** Load the next chapter when this many rows or fewer are left below the screen. */
        const val APPEND_WHEN_REMAINING = 15

        /** After a failed chapter load, wait this long before trying the same chapter again. */
        const val APPEND_RETRY_MS = 10_000L
    }
}
