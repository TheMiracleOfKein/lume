package com.lume.app.translate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lume.app.AppContainer
import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.novel.NovelBlock
import com.lume.app.ui.screens.LoadedNovelChapter
import com.lume.translate.ModelCatalog
import com.lume.translate.ModelPair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How the current chapter's translation is going. */
sealed interface TranslationProgress {
    data object Idle : TranslationProgress
    data class Working(val done: Int, val total: Int) : TranslationProgress
    data class Failed(val message: String) : TranslationProgress
}

/**
 * Translation for one open title in the text reader. The title's language comes from its source;
 * [target] is saved per title. Loaded chapters are translated block for block, nearest the reading
 * spot first, and handed back through [replace]; the originals stay on each chapter for the long
 * press and for "Show original".
 */
class ReaderTranslation(
    private val app: AppContainer,
    private val scope: CoroutineScope,
    private val sourceId: String,
    private val titleId: String,
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

    /** Pairs from this title's language, in catalogue order. Empty: no translate button. */
    val pairs: List<ModelPair> = ModelCatalog.pairs.filter { it.from == sourceLanguage }

    /** The language chosen for this title, or null when it reads in the original. */
    var target by mutableStateOf<String?>(null)
        private set

    val pair: ModelPair? get() = target?.let { t -> pairs.firstOrNull { it.to == t } }

    var progress by mutableStateOf<TranslationProgress>(TranslationProgress.Idle)
        private set

    var glossary by mutableStateOf<List<GlossaryTermEntity>>(emptyList())
        private set

    private val entries: List<GlossaryEntry> get() = glossary.map { GlossaryEntry(it.source, it.target) }

    /** The chapter the reader is in, translated first; its progress is the one shown. */
    var currentChapterId: String? = null

    /** Reads the saved language and follows the glossary. Call once. */
    fun start(loaded: () -> List<LoadedNovelChapter>, anchorBlock: () -> Int) {
        this.loaded = loaded
        this.anchorBlock = anchorBlock
        scope.launch {
            val saved = dao.getTitlePrefs(sourceId, titleId)?.novelTranslateTo
            if (saved != null && pairs.any { it.to == saved }) {
                TranslateAddon.refresh(app.app)
                target = saved
                translateAll()
            }
        }
        scope.launch {
            dao.glossary(sourceId, titleId).collect { list ->
                val changed = list.map { it.source to it.target } != glossary.map { it.source to it.target }
                glossary = list
                if (changed && target != null) retranslate()
            }
        }
    }

    private var loaded: () -> List<LoadedNovelChapter> = { emptyList() }
    private var anchorBlock: () -> Int = { 0 }

    /** Turns translation on into [pair]'s language (the add-on and its pack must be ready). */
    fun enable(pair: ModelPair) {
        target = pair.to
        save(pair.to)
        translateAll()
    }

    /** Back to the original text, saved for the title. */
    fun disable() {
        target = null
        save(null)
        cancelAll()
        progress = TranslationProgress.Idle
        for (lc in loaded()) lc.original?.let { replace(lc.chapter.id, it, null) }
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

    fun addTerm(source: String, target: String) {
        val s = source.trim()
        val t = target.trim()
        if (s.isEmpty() || t.isEmpty()) return
        scope.launch {
            dao.upsertGlossaryTerm(GlossaryTermEntity(sourceId, titleId, s, t, System.currentTimeMillis()))
        }
    }

    fun removeTerm(source: String) {
        scope.launch { dao.deleteGlossaryTerm(sourceId, titleId, source) }
    }

    private fun translateAll() {
        val list = loaded()
        // The chapter being read first, then the ones after it.
        val current = list.indexOfFirst { it.chapter.id == currentChapterId }.coerceAtLeast(0)
        (list.drop(current) + list.take(current)).forEach(::translate)
    }

    private fun retranslate() {
        cancelAll()
        for (lc in loaded()) {
            lc.original?.let { replace(lc.chapter.id, it, null) }
        }
        translateAll()
    }

    private fun translate(lc: LoadedNovelChapter) {
        val pair = pair ?: return
        val id = lc.chapter.id
        if (jobs[id]?.isActive == true) return
        val original = lc.original ?: lc.blocks
        val glossaryNow = entries
        jobs[id] = scope.launch {
            // A chapter in the cache shows at once; others wait their turn.
            translator.cached(original, pair, glossaryNow)?.let {
                replace(id, it, original)
                return@launch
            }
            queue.withLock {
                val mine = id == currentChapterId
                try {
                    val start = if (mine) anchorBlock() else 0
                    val result = translator.translate(original, pair, glossaryNow, start) { partial, done, total ->
                        replace(id, partial, original)
                        if (id == currentChapterId) progress = TranslationProgress.Working(done, total)
                    }
                    replace(id, result, original)
                    if (id == currentChapterId) progress = TranslationProgress.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (id == currentChapterId || mine) progress = TranslationProgress.Failed(e.message ?: e.toString())
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
     * [lc] translated whole (for read aloud), or as it is when translation is off or the pack is
     * missing. Doesn't touch the reader's list.
     */
    suspend fun forListening(lc: LoadedNovelChapter): LoadedNovelChapter {
        val pair = pair ?: return lc
        if (!TranslateAddon.isReady(pair)) return lc
        val original = lc.original ?: lc.blocks
        return LoadedNovelChapter(lc.chapter, translator.translate(original, pair, entries), original)
    }

    /** The translation cache, for "Clear cache" style actions. */
    fun clearCache() = translator.clearCache()
}
