package com.lume.app.translate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lume.app.AppContainer
import com.lume.app.data.db.GLOSSARY_SUGGESTED
import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.llm.LlmClient
import com.lume.app.llm.LlmConfig
import com.lume.app.data.novel.NovelBlock
import com.lume.app.ui.screens.LoadedNovelChapter
import com.lume.translate.ModelPair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How the current chapter's translation is going. */
sealed interface TranslationProgress {
    data object Idle : TranslationProgress
    data class Working(val done: Int, val total: Int) : TranslationProgress
    data class Failed(val message: String) : TranslationProgress
}

/** Why translation can't run right now with the chosen engine. */
enum class TranslationBlocker { NoEngine, AddonMissing, PackMissing, NoDeepLKey, NoLlmSetup }

/**
 * Translation for one open title in the text reader. The title's language comes from its source;
 * the target language is saved per title, the engine (on the phone, Google, DeepL) for the app.
 * Loaded chapters are translated block for block, nearest the reading spot first, and handed
 * back through [replace]; the originals stay on each chapter for the long press and "off".
 */
@OptIn(FlowPreview::class)
class ReaderTranslation(
    private val app: AppContainer,
    private val scope: CoroutineScope,
    val sourceId: String,
    val titleId: String,
    /** Puts a chapter's new blocks into the reader (same count as the original). */
    private val replace: (chapterId: String, blocks: List<NovelBlock>, original: List<NovelBlock>?) -> Unit,
) {
    private val dao = app.dao
    private val translator = ChapterTranslator(app.app)
    private val queue = Mutex()
    private val jobs = HashMap<String, Job>()

    /** The title's language (from its source); local books count as English. */
    val sourceLanguage: String? = app.sources.sourceById(sourceId)?.manifest?.language
        ?.let { if (it == "local") "en" else it }

    /** Languages this title can be read in (Russian for English titles, English for Russian ones). */
    val targets: List<String> = when (sourceLanguage) {
        "en" -> listOf("ru")
        "ru" -> listOf("en")
        null -> emptyList()
        else -> listOf("ru", "en")
    }

    /** The language chosen for this title, or null when it reads in the original. */
    var target by mutableStateOf<String?>(null)
        private set

    /** The app's translation engine; null until the user picks one. */
    val engine: TranslationEngine? get() = TranslationEngine.fromId(app.prefs.translationEngine.value)

    var progress by mutableStateOf<TranslationProgress>(TranslationProgress.Idle)
        private set

    var glossary by mutableStateOf<List<GlossaryTermEntity>>(emptyList())
        private set

    private val entries: List<GlossaryEntry> get() = glossary.map { GlossaryEntry(it.source, it.target) }

    /** Terms AI translation proposed that the user hasn't accepted or rejected yet. */
    val suggestedCount: Int get() = glossary.count { it.status == GLOSSARY_SUGGESTED }

    /** Every glossary source of the title, rejected ones included: AI translation doesn't propose these again. */
    private var knownSources: List<String> = emptyList()

    /** The title's style note for AI translation. */
    var style by mutableStateOf<String?>(null)
        private set

    /** The chapter the reader is in, translated first; its progress is the one shown. */
    var currentChapterId: String? = null

    private var loaded: () -> List<LoadedNovelChapter> = { emptyList() }
    private var anchorBlock: () -> Int = { 0 }

    /** The offline model for [to], if Mozilla has one. */
    fun offlinePair(to: String): ModelPair? = sourceLanguage?.let { AddonProvider.pairFor(it, to) }

    /** What stops [engine] from translating into [to], or null when it can. */
    fun blocker(engine: TranslationEngine?, to: String): TranslationBlocker? = when (engine) {
        null -> TranslationBlocker.NoEngine
        TranslationEngine.Offline -> {
            val pair = offlinePair(to)
            when {
                pair == null -> TranslationBlocker.PackMissing
                TranslateAddon.state.value !is AddonState.Ready -> TranslationBlocker.AddonMissing
                !TranslateAddon.isReady(pair) -> TranslationBlocker.PackMissing
                else -> null
            }
        }
        TranslationEngine.Google -> null
        TranslationEngine.DeepL -> if (app.prefs.deeplKey.value.isNullOrBlank()) TranslationBlocker.NoDeepLKey else null
        TranslationEngine.Llm -> if (llmConfig(app) == null) TranslationBlocker.NoLlmSetup else null
    }

    private fun route(): TranslationRoute? {
        val from = sourceLanguage ?: return null
        val to = target ?: return null
        val provider = when (engine) {
            TranslationEngine.Offline -> offlinePair(to)?.takeIf { TranslateAddon.isReady(it) }?.let { AddonProvider(app.app, it) }
            TranslationEngine.Google -> GoogleProvider(app.http.okHttp)
            TranslationEngine.DeepL -> app.prefs.deeplKey.value?.takeIf { it.isNotBlank() }?.let { DeepLProvider(app.http.okHttp, it) }
            TranslationEngine.Llm -> llmConfig(app)?.let { config ->
                LlmProvider(LlmClient(app.http.okHttp, config), glossary, knownSources, style, ::saveSuggestions)
            }
            null -> null
        } ?: return null
        return TranslationRoute(provider, from, to)
    }

    /** New terms from AI translation go to the glossary as suggestions, used from the next chapter part on. */
    private suspend fun saveSuggestions(terms: List<SuggestedTerm>) {
        val now = System.currentTimeMillis()
        dao.insertGlossaryTermsIfNew(
            terms.map { GlossaryTermEntity(sourceId, titleId, it.source, it.target, now, GLOSSARY_SUGGESTED, it.kind) },
        )
    }

    /** Reads the saved language, follows the glossary and the engine setting. Call once. */
    fun start(loaded: () -> List<LoadedNovelChapter>, anchorBlock: () -> Int) {
        this.loaded = loaded
        this.anchorBlock = anchorBlock
        scope.launch {
            val prefs = dao.getTitlePrefs(sourceId, titleId)
            style = prefs?.translateStyle
            knownSources = dao.glossarySources(sourceId, titleId)
            val saved = prefs?.novelTranslateTo
            if (saved != null && saved in targets) {
                if (engine == TranslationEngine.Offline) TranslateAddon.refresh(app.app)
                target = saved
                translateAll()
            }
        }
        scope.launch {
            // Several quick edits (a pasted list, a few adds) translate again once.
            dao.glossary(sourceId, titleId).debounce(GLOSSARY_SETTLE_MS).collect { list ->
                val old = glossary
                glossary = list
                knownSources = dao.glossarySources(sourceId, titleId)
                if (target != null && needsRetranslate(old, list)) retranslate()
            }
        }
        // Another engine (or DeepL key, AI service or model) picked while reading: translate again with it.
        scope.launch { app.prefs.translationEngine.drop(1).collect { if (target != null) retranslate() } }
        scope.launch {
            combine(app.prefs.llmPreset, app.prefs.llmBaseUrl, app.prefs.llmModel, app.prefs.llmKeys) { _, _, _, _ -> llmConfig(app) }
                .distinctUntilChanged().drop(1)
                .collect { if (target != null && engine == TranslationEngine.Llm) retranslate() }
        }
    }

    /**
     * Whether a glossary change means translating again. Suggestions that AI translation itself
     * just added don't: the running translation already uses them, and restarting would throw its
     * work away. Any other change (an edit, a removal, a term of the user's) does.
     */
    private fun needsRetranslate(old: List<GlossaryTermEntity>, new: List<GlossaryTermEntity>): Boolean {
        val before = old.associate { it.source to it.target }
        val after = new.associate { it.source to it.target }
        if (before == after) return false
        val removedOrChanged = before.any { (s, t) -> after[s] != t }
        val addedByUser = new.any { it.source !in before && it.status != GLOSSARY_SUGGESTED }
        return removedOrChanged || addedByUser
    }

    /** Turns translation on into [to] with the current engine (which must be ready). */
    fun enable(to: String) {
        target = to
        save(to)
        retranslate()
    }

    /** Back to the original text, saved for the title. */
    fun disable() {
        target = null
        save(null)
        cancelAll()
        progress = TranslationProgress.Idle
        for (lc in loaded()) lc.original?.let { replace(lc.chapter.id, it, null) }
    }

    fun setEngine(engine: TranslationEngine) {
        scope.launch { app.prefs.setTranslationEngine(engine.id) }
    }

    fun setDeepLKey(key: String) {
        scope.launch {
            app.prefs.setDeeplKey(key)
            if (target != null && engine == TranslationEngine.DeepL) retranslate()
        }
    }

    fun setLlmService(preset: String, baseUrl: String?, model: String?, key: String?) {
        scope.launch {
            app.prefs.setLlmService(preset, baseUrl, model)
            if (key != null) app.prefs.setLlmKey(preset, key)
        }
    }

    /** Saves the title's style note for AI translation; chapters are translated again with it. */
    fun setStyleNote(text: String) {
        val clean = text.trim().ifEmpty { null }
        if (clean == style) return
        style = clean
        scope.launch {
            val row = dao.getTitlePrefs(sourceId, titleId) ?: TitlePrefsEntity(sourceId = sourceId, titleId = titleId)
            dao.upsertTitlePrefs(row.copy(translateStyle = clean))
            if (target != null && engine == TranslationEngine.Llm) retranslate()
        }
    }

    /** Asks the engine again for the chapter being read, skipping its cached translation. */
    fun translateCurrentAgain() {
        val lc = loaded().firstOrNull { it.chapter.id == currentChapterId } ?: return
        val route = route() ?: return
        val original = lc.original ?: lc.blocks
        val glossaryNow = entries
        jobs.remove(lc.chapter.id)?.cancel()
        scope.launch {
            translator.forget(original, route, glossaryNow)
            translate(lc)
        }
    }

    /** A chapter joined the list (first load, next chapter): translate it if translation is on. */
    fun onChapterLoaded(lc: LoadedNovelChapter) {
        if (target != null) translate(lc)
    }

    /** The list was rebuilt (another chapter opened): old work is dropped. */
    fun onListReset() {
        cancelAll()
        progress = TranslationProgress.Idle
    }

    fun retry() = translateAll()

    fun addTerm(source: String, target: String) = saveTerm(app, sourceId, titleId, source, target)

    private fun translateAll() {
        val list = loaded()
        val current = list.indexOfFirst { it.chapter.id == currentChapterId }.coerceAtLeast(0)
        // The chapter being read first, then the ones after it.
        (list.drop(current) + list.take(current)).forEach(::translate)
    }

    private fun retranslate() {
        cancelAll()
        progress = TranslationProgress.Idle
        for (lc in loaded()) lc.original?.let { replace(lc.chapter.id, it, null) }
        translateAll()
    }

    private fun translate(lc: LoadedNovelChapter) {
        val id = lc.chapter.id
        if (jobs[id]?.isActive == true) return
        val route = route()
        if (route == null) {
            if (id == currentChapterId) progress = TranslationProgress.Idle
            return
        }
        val original = lc.original ?: lc.blocks
        val glossaryNow = entries
        jobs[id] = scope.launch {
            // A chapter in the cache shows at once; others wait their turn.
            translator.cached(original, route, glossaryNow)?.let {
                replace(id, it, original)
                return@launch
            }
            queue.withLock {
                try {
                    val start = if (id == currentChapterId) anchorBlock() else 0
                    val result = translator.translate(original, route, glossaryNow, start) { partial, done, total ->
                        replace(id, partial, original)
                        if (id == currentChapterId) progress = TranslationProgress.Working(done, total)
                    }
                    replace(id, result, original)
                    if (id == currentChapterId) progress = TranslationProgress.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (id == currentChapterId) progress = TranslationProgress.Failed(e.message ?: e.toString())
                }
            }
        }
    }

    private fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    private fun save(target: String?) {
        scope.launch {
            val row = dao.getTitlePrefs(sourceId, titleId) ?: TitlePrefsEntity(sourceId = sourceId, titleId = titleId)
            dao.upsertTitlePrefs(row.copy(novelTranslateTo = target))
        }
    }

    /**
     * [lc] translated whole (for read aloud), or as it is when translation is off or the engine
     * isn't ready. Doesn't touch the reader's list.
     */
    suspend fun forListening(lc: LoadedNovelChapter): LoadedNovelChapter {
        val route = route() ?: return lc
        val original = lc.original ?: lc.blocks
        return LoadedNovelChapter(lc.chapter, translator.translate(original, route, entries), original)
    }

    /** The translation cache, for "Clear cache" style actions. */
    fun clearCache() = translator.clearCache()

    private companion object {
        const val GLOSSARY_SETTLE_MS = 800L
    }
}

/** The AI translation service from the settings, or null when its key, URL or model is missing. */
fun llmConfig(app: AppContainer): LlmConfig? =
    LlmConfig.from(app.prefs.llmPreset.value, app.prefs.llmBaseUrl.value, app.prefs.llmModel.value, app.prefs.llmKeys.value)

/** Adds or replaces a glossary term of a title (blank parts are ignored). */
fun saveTerm(app: AppContainer, sourceId: String, titleId: String, source: String, target: String) {
    val s = source.trim()
    val t = target.trim()
    if (s.isEmpty() || t.isEmpty()) return
    app.appScope.launch {
        app.dao.upsertGlossaryTerm(GlossaryTermEntity(sourceId, titleId, s, t, System.currentTimeMillis()))
    }
}
