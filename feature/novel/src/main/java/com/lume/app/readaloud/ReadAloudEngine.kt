package com.lume.app.readaloud

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.SpeechScript
import com.lume.app.data.novel.Utterance
import com.lume.app.data.novel.plainText
import com.lume.app.data.prefs.ReadAloudSettings
import com.lume.app.data.userMessage
import com.lume.app.domain.Chapter
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.domain.chapterNeighbours
import com.lume.app.ui.screens.LoadedNovelChapter
import com.lume.app.ui.screens.NovelAnchor
import com.lume.app.ui.screens.startsWithOwnTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Where read aloud loads chapters and keeps what was listened to. */
interface ReadAloudStore {
    val incognito: Boolean
    suspend fun loadChapter(chapter: Chapter): LoadedNovelChapter

    /** Saves the spot (chapter progress and Continue reading); returns at once. */
    fun saveProgress(anchor: NovelAnchor, chapter: LoadedNovelChapter)

    /** Marks a chapter read to its end; returns at once. */
    fun markRead(chapterId: String, chapters: List<Chapter>, finished: LoadedNovelChapter)

    fun saveSession(session: ReadingSessionTracker.Session)
}

/**
 * Reads a title aloud sentence by sentence, chapter after chapter. A few sentences are queued
 * ahead so there is no gap between them. Every utterance id carries a generation number; a seek,
 * pause or voice change starts a new generation, so late callbacks of dropped speech are ignored.
 *
 * Runs on the main thread. Android parts (audio focus, notification, wake lock) live around it in
 * [ReadAloud] and [ReadAloudService]; this class only talks to a [Speaker] and a [ReadAloudStore].
 */
class ReadAloudEngine(
    private val store: ReadAloudStore,
    private val scope: CoroutineScope,
    private val speakerFactory: (engine: String?) -> Speaker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val publish: (ReadAloudState?) -> Unit,
) : Speaker.Listener {

    /** What to read: the title, its chapters in reading order, and where to begin. */
    class Start(
        val sourceId: String,
        val titleId: String,
        val titleName: String,
        val coverUrl: String?,
        val chapters: List<Chapter>,
        val chapter: LoadedNovelChapter,
        val anchor: NovelAnchor,
        val settings: ReadAloudSettings,
    )

    private lateinit var info: Start
    private var chapters: List<Chapter> = emptyList()
    private var settings = ReadAloudSettings()
    private var speaker: Speaker? = null

    private var chapter: LoadedNovelChapter? = null
    private var script: List<Utterance> = emptyList()
    var textLocale: Locale = Locale.ENGLISH
        private set
    private var index = 0
    private var queuedTo = -1
    private var generation = 0

    private var status = ReadAloudStatus.Paused
    private var wantPlay = false
    private var sleep: SleepTimer = SleepTimer.Off
    private var problem: ReadAloudProblem? = null
    private var warnedLocale: Locale? = null

    /** The chapter was read to its end; resuming goes on with the next one. */
    private var pendingNext = false
    private var loadJob: Job? = null
    private var lastSaveAt = 0L
    private var released = false
    private val tracker = ReadingSessionTracker()

    val sourceId: String get() = info.sourceId
    val titleId: String get() = info.titleId
    val isPlaying: Boolean get() = status == ReadAloudStatus.Playing

    fun start(req: Start) {
        info = req
        chapters = req.chapters
        settings = req.settings
        speaker = speakerFactory(settings.engine).also { it.listener = this }
        setChapter(req.chapter, req.anchor)
        wantPlay = true
        play()
    }

    // ---- Controls ---------------------------------------------------------------------------

    fun pause() {
        wantPlay = false
        halt()
        status = ReadAloudStatus.Paused
        saveNow()
        finishSession()
        publishState()
    }

    fun resume() {
        if (problem == ReadAloudProblem.Finished) return
        if (problem == ReadAloudProblem.NoEngine) {
            speaker?.release()
            speaker = speakerFactory(settings.engine).also { it.listener = this }
        }
        problem = null
        wantPlay = true
        if (pendingNext) goNext() else play()
    }

    /** Stops for good: saves the spot and frees the engine. */
    fun stop() {
        if (released) return
        loadJob?.cancel()
        halt()
        saveNow()
        finishSession()
        released = true
        speaker?.release()
        speaker = null
        publish(null)
    }

    fun nextParagraph() {
        if (status == ReadAloudStatus.Loading) return
        val target = nextParagraph(script, index)
        if (target >= 0) {
            jumpTo(target)
        } else if (chapterNeighbours(chapters, chapter?.chapter?.id ?: return).next != null) {
            pendingNext = true
            if (wantPlay) {
                halt()
                goNext()
            }
        }
    }

    fun previousParagraph() {
        if (status == ReadAloudStatus.Loading) return
        jumpTo(previousParagraph(script, index))
    }

    fun setSleep(timer: SleepTimer) {
        sleep = timer
        publishState()
    }

    fun applySettings(next: ReadAloudSettings) {
        val engineChanged = next.engine != settings.engine
        settings = next
        if (engineChanged) {
            halt()
            speaker?.release()
            speaker = speakerFactory(settings.engine).also { it.listener = this }
            if (wantPlay) status = ReadAloudStatus.Loading
            publishState()
        } else if (status == ReadAloudStatus.Playing) {
            applyVoice()
            enqueueFrom(index)
        }
    }

    fun voices(): List<VoiceOption> = speaker?.takeIf { it.isReady }?.voices().orEmpty()
    fun engines(): List<EngineOption> = speaker?.takeIf { it.isReady }?.engines().orEmpty()
    val enginePackage: String? get() = speaker?.enginePackage

    // ---- Speaking ---------------------------------------------------------------------------

    private fun setChapter(lc: LoadedNovelChapter, at: NovelAnchor?) {
        chapter = lc
        textLocale = guessTextLocale(sample(lc.blocks))
        val title = if (startsWithOwnTitle(lc.chapter, lc.blocks)) null else lc.chapter.title
        val max = minOf(SpeechScript.MAX_LENGTH, speaker?.maxInputLength ?: SpeechScript.MAX_LENGTH)
        script = SpeechScript.build(title, lc.blocks, textLocale, max)
        index = if (at == null) 0 else utteranceAt(script, lc.blocks, at.block, at.fraction)
        pendingNext = false
    }

    private fun sample(blocks: List<NovelBlock>): String = buildString {
        for (b in blocks) {
            if (length > 3000) break
            when (b) {
                is NovelBlock.Paragraph -> append(b.spans.plainText)
                is NovelBlock.Heading -> append(b.spans.plainText)
                else -> Unit
            }
        }
    }

    private fun play() {
        val sp = speaker ?: return
        chapter ?: return
        if (!sp.isReady) {
            status = ReadAloudStatus.Loading
            publishState()
            return
        }
        if (index > script.lastIndex) {
            chapterEnd()
            return
        }
        applyVoice()
        status = ReadAloudStatus.Playing
        enqueueFrom(index)
        publishState()
    }

    private fun applyVoice() {
        val ok = speaker?.configure(settings.speed, settings.voiceName, textLocale) ?: return
        if (!ok && warnedLocale != textLocale) {
            warnedLocale = textLocale
            problem = ReadAloudProblem.NoVoiceForLanguage
        }
    }

    /** Drops what is queued and stops the voice; callbacks of the old queue are ignored. */
    private fun halt() {
        generation++
        queuedTo = index - 1
        speaker?.stop()
    }

    private fun enqueueFrom(from: Int) {
        generation++
        queuedTo = from - 1
        fill(flush = true)
    }

    private fun fill(flush: Boolean = false) {
        val sp = speaker ?: return
        var first = flush
        while (queuedTo < script.lastIndex && queuedTo < index + LOOKAHEAD) {
            queuedTo++
            val u = script[queuedTo]
            val id = "$generation:$queuedTo"
            if (u.pause) sp.silence(id, PAUSE_MS, first) else sp.speak(id, u.text, first)
            first = false
        }
    }

    private fun jumpTo(target: Int) {
        index = target.coerceIn(0, (script.size - 1).coerceAtLeast(0))
        if (status == ReadAloudStatus.Playing) enqueueFrom(index)
        saveNow()
        publishState()
    }

    /** The utterance index of a callback id from the current generation, else null. */
    private fun parse(id: String): Int? {
        val gen = id.substringBefore(':').toIntOrNull() ?: return null
        if (gen != generation) return null
        return id.substringAfter(':').toIntOrNull()
    }

    private fun chapterEnd() {
        val lc = chapter ?: return
        halt()
        if (!store.incognito) store.markRead(lc.chapter.id, chapters, lc)
        finishSession()
        val next = chapterNeighbours(chapters, lc.chapter.id).next
        if (next == null) {
            wantPlay = false
            problem = ReadAloudProblem.Finished
            status = ReadAloudStatus.Paused
            publishState()
            return
        }
        pendingNext = true
        if (sleep.dueAtChapterEnd(clock())) {
            sleep = SleepTimer.Off
            wantPlay = false
            status = ReadAloudStatus.Paused
            publishState()
            return
        }
        goNext()
    }

    private fun goNext() {
        val lc = chapter ?: return
        val next = chapterNeighbours(chapters, lc.chapter.id).next ?: return
        status = ReadAloudStatus.Loading
        publishState()
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val loaded = store.loadChapter(next)
                if (released) return@launch
                setChapter(loaded, null)
                saveNow()
                if (wantPlay) {
                    play()
                } else {
                    status = ReadAloudStatus.Paused
                    publishState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (released) return@launch
                wantPlay = false
                problem = ReadAloudProblem.LoadFailed(next.title, e.userMessage())
                status = ReadAloudStatus.Paused
                publishState()
            }
        }
    }

    // ---- Speaker callbacks ------------------------------------------------------------------

    override fun onReady() {
        if (wantPlay && status == ReadAloudStatus.Loading && loadJob?.isActive != true) play() else publishState()
    }

    override fun onInitFailed() {
        wantPlay = false
        problem = ReadAloudProblem.NoEngine
        status = ReadAloudStatus.Paused
        publishState()
    }

    override fun onStart(id: String) {
        val i = parse(id) ?: return
        if (status != ReadAloudStatus.Playing) return
        index = i
        track()
        publishState()
        fill()
    }

    override fun onDone(id: String) {
        val i = parse(id) ?: return
        if (status != ReadAloudStatus.Playing) return
        if (i >= script.lastIndex) {
            index = script.lastIndex
            chapterEnd()
            return
        }
        index = i + 1
        if (sleep.dueAfterSentence(clock())) {
            sleep = SleepTimer.Off
            pause()
            return
        }
        // Engines that never report a start still get the queue topped up.
        fill()
    }

    override fun onError(id: String) = onDone(id)

    // ---- Saving -----------------------------------------------------------------------------

    private fun track() {
        val lc = chapter ?: return
        val now = clock()
        if (!store.incognito) {
            tracker.onPage(lc.chapter.id, script.getOrNull(index)?.block ?: 0, now)?.let(store::saveSession)
        }
        if (now - lastSaveAt >= SAVE_EVERY_MS) saveNow()
    }

    private fun finishSession() {
        tracker.finish(clock())?.let { if (!store.incognito) store.saveSession(it) }
    }

    private fun saveNow() {
        val lc = chapter ?: return
        val u = script.getOrNull(index.coerceAtMost(script.lastIndex)) ?: return
        lastSaveAt = clock()
        if (store.incognito) return
        store.saveProgress(anchorOf(lc.chapter.id, u, lc.blocks), lc)
    }

    private fun publishState() {
        if (released) return
        val lc = chapter ?: return
        val u = script.getOrNull(index)
        val spoken = u?.takeIf { !it.pause }
        publish(
            ReadAloudState(
                sourceId = info.sourceId,
                titleId = info.titleId,
                titleName = info.titleName,
                coverUrl = info.coverUrl,
                chapterId = lc.chapter.id,
                chapterTitle = lc.chapter.title,
                block = u?.block ?: 0,
                start = spoken?.start ?: 0,
                end = spoken?.end ?: 0,
                status = status,
                sleep = sleep,
                problem = problem,
            ),
        )
    }

    private companion object {
        /** Sentences queued after the one being spoken. */
        const val LOOKAHEAD = 2

        /** Silence for a scene break. */
        const val PAUSE_MS = 700L

        /** While playing, the spot is saved at most this often. */
        const val SAVE_EVERY_MS = 5_000L
    }
}
