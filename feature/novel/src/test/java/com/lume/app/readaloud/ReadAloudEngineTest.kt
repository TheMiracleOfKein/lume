package com.lume.app.readaloud

import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import com.lume.app.data.prefs.ReadAloudSettings
import com.lume.app.domain.Chapter
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.ui.screens.LoadedNovelChapter
import com.lume.app.ui.screens.NovelAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReadAloudEngineTest {
    private class FakeSpeaker : Speaker {
        override var listener: Speaker.Listener? = null
        override var isReady = false
        override val maxInputLength = 4000
        override val enginePackage: String? = "fake"
        val queue = mutableListOf<Pair<String, String>>()
        var stops = 0
        var speed = 0f
        var released = false

        override fun speak(id: String, text: String, flush: Boolean) {
            if (flush) queue.clear()
            queue += id to text
        }

        override fun silence(id: String, ms: Long, flush: Boolean) = speak(id, "", flush)

        override fun stop() {
            stops++
            queue.clear()
        }

        override fun configure(speed: Float, voiceName: String?, textLocale: Locale): Boolean {
            this.speed = speed
            return true
        }

        override fun voices() = emptyList<VoiceOption>()
        override fun engines() = emptyList<EngineOption>()
        override fun release() {
            released = true
        }

        fun ready() {
            isReady = true
            listener?.onReady()
        }

        /** The engine speaks the head of the queue to its end. */
        fun speakNext(): String {
            val (id, text) = queue.removeAt(0)
            listener?.onStart(id)
            listener?.onDone(id)
            return text
        }
    }

    private class FakeStore(val texts: Map<String, List<NovelBlock>>) : ReadAloudStore {
        override var incognito = false
        val saved = mutableListOf<NovelAnchor>()
        val read = mutableListOf<String>()
        override suspend fun loadChapter(chapter: Chapter) = LoadedNovelChapter(chapter, texts.getValue(chapter.id))
        override fun saveProgress(anchor: NovelAnchor, chapter: LoadedNovelChapter) {
            saved += anchor
        }

        override fun markRead(chapterId: String, chapters: List<Chapter>, finished: LoadedNovelChapter) {
            read += chapterId
        }

        override fun saveSession(session: ReadingSessionTracker.Session) = Unit
    }

    private fun para(text: String) = NovelBlock.Paragraph(listOf(Span(text)))

    private val c1 = Chapter("c1", "Chapter one", "u1", number = "1")
    private val c2 = Chapter("c2", "Chapter two", "u2", number = "2")
    private val blocks1 = listOf(
        para("The first sentence of chapter one. The second sentence of chapter one."),
        para("Another paragraph of chapter one is here."),
    )
    private val blocks2 = listOf(para("Chapter two opens with this sentence."))

    private var now = 0L
    private val speaker = FakeSpeaker()
    private val store = FakeStore(mapOf("c1" to blocks1, "c2" to blocks2))
    private var state: ReadAloudState? = null
    private val engine = ReadAloudEngine(
        store = store,
        scope = CoroutineScope(Dispatchers.Unconfined),
        speakerFactory = { speaker },
        clock = { now },
        publish = { state = it },
    )

    private fun start(anchor: NovelAnchor = NovelAnchor("c1", 0, 0f), sleep: SleepTimer = SleepTimer.Off) {
        engine.start(
            ReadAloudEngine.Start("s", "t", "Book", null, listOf(c1, c2), LoadedNovelChapter(c1, blocks1), anchor, ReadAloudSettings(speed = 1.5f)),
        )
        engine.setSleep(sleep)
    }

    @Test
    fun waitsForTheEngineThenQueuesTheTitleAndTwoMore() {
        start()
        assertEquals(ReadAloudStatus.Loading, state?.status)
        speaker.ready()
        assertEquals(ReadAloudStatus.Playing, state?.status)
        assertEquals(1.5f, speaker.speed)
        assertEquals(listOf("Chapter one", "The first sentence of chapter one.", "The second sentence of chapter one."), speaker.queue.map { it.second })
    }

    @Test
    fun startsAtTheSentenceOnScreenAndHighlightsIt() {
        start(NovelAnchor("c1", 0, 0.6f))
        speaker.ready()
        assertEquals("The second sentence of chapter one.", speaker.queue.first().second)
        speaker.listener?.onStart(speaker.queue.first().first)
        assertEquals(0, state?.block)
        assertEquals(blocks1[0].let { (it as NovelBlock.Paragraph).spans[0].text.indexOf("The second") }, state?.start)
    }

    @Test
    fun goesOnToTheNextChapterAndMarksTheFirstRead() {
        start()
        speaker.ready()
        repeat(4) { speaker.speakNext() }
        assertEquals(listOf("c1"), store.read)
        assertEquals("c2", state?.chapterId)
        assertEquals(ReadAloudStatus.Playing, state?.status)
        assertEquals("Chapter two", speaker.speakNext())
        assertEquals(NovelAnchor("c2", 0, 0f), store.saved.last())
    }

    @Test
    fun theLastChapterEndsReadAloud() {
        engine.start(
            ReadAloudEngine.Start("s", "t", "Book", null, listOf(c1, c2), LoadedNovelChapter(c2, blocks2), NovelAnchor("c2", 0, 0f), ReadAloudSettings()),
        )
        speaker.ready()
        repeat(2) { speaker.speakNext() }
        assertEquals(ReadAloudProblem.Finished, state?.problem)
        assertEquals(ReadAloudStatus.Paused, state?.status)
    }

    @Test
    fun pauseDropsTheQueueAndIgnoresLateCallbacksAndResumeRepeatsTheSentence() {
        start()
        speaker.ready()
        speaker.speakNext()
        val (lateId, _) = speaker.queue.first()
        engine.pause()
        assertEquals(ReadAloudStatus.Paused, state?.status)
        assertTrue(speaker.queue.isEmpty())
        speaker.listener?.onStart(lateId)
        speaker.listener?.onDone(lateId)
        assertEquals(ReadAloudStatus.Paused, state?.status)
        engine.resume()
        assertEquals("The first sentence of chapter one.", speaker.queue.first().second)
    }

    @Test
    fun nextAndPreviousParagraph() {
        start()
        speaker.ready()
        engine.nextParagraph() // from the title to the first paragraph
        assertEquals("The first sentence of chapter one.", speaker.queue.first().second)
        engine.nextParagraph()
        assertEquals("Another paragraph of chapter one is here.", speaker.queue.first().second)
        engine.previousParagraph()
        assertEquals("The first sentence of chapter one.", speaker.queue.first().second)
    }

    @Test
    fun timedSleepPausesAfterTheSentence() {
        start(sleep = SleepTimer.minutes(1, now = 0))
        speaker.ready()
        speaker.speakNext()
        now = 61_000
        speaker.speakNext()
        assertEquals(ReadAloudStatus.Paused, state?.status)
        assertEquals(SleepTimer.Off, state?.sleep)
        engine.resume()
        assertEquals("The second sentence of chapter one.", speaker.queue.first().second)
    }

    @Test
    fun endOfChapterSleepStopsThereAndResumeGoesOn() {
        start(sleep = SleepTimer.EndOfChapter)
        speaker.ready()
        repeat(4) { speaker.speakNext() }
        assertEquals(ReadAloudStatus.Paused, state?.status)
        assertEquals("c1", state?.chapterId)
        engine.resume()
        assertEquals("c2", state?.chapterId)
        assertEquals(ReadAloudStatus.Playing, state?.status)
    }

    @Test
    fun noEngineIsReported() {
        start()
        speaker.listener?.onInitFailed()
        assertEquals(ReadAloudProblem.NoEngine, state?.problem)
    }

    @Test
    fun incognitoSavesNothingAndStopClearsTheState() {
        store.incognito = true
        start()
        speaker.ready()
        repeat(4) { speaker.speakNext() }
        assertTrue(store.saved.isEmpty())
        assertTrue(store.read.isEmpty())
        engine.stop()
        assertNull(state)
        assertTrue(speaker.released)
    }
}
