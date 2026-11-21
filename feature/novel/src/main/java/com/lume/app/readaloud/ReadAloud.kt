package com.lume.app.readaloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.lume.app.AppContainer
import com.lume.app.data.prefs.ReadAloudSettings
import com.lume.app.domain.Chapter
import com.lume.app.domain.ReadingSessionTracker
import com.lume.app.ui.screens.LoadedNovelChapter
import com.lume.app.ui.screens.NovelAnchor
import com.lume.app.ui.screens.NovelBook
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Read aloud for the whole process: one title at a time, playing on in [ReadAloudService] after
 * the reader closes or the screen goes off. The reader and the media session both talk to it
 * here, on the main thread. Around the [ReadAloudEngine] it keeps audio focus, a wake lock while
 * speaking, and pauses when headphones are unplugged.
 */
object ReadAloud {
    private val _state = MutableStateFlow<ReadAloudState?>(null)

    /** What is being read, or null when read aloud is off. */
    val state: StateFlow<ReadAloudState?> = _state.asStateFlow()

    private var engine: ReadAloudEngine? = null
    private var app: AppContainer? = null
    private var pauseTimeout: Job? = null

    /** Starts reading [chapter] of [book] from [anchor], replacing whatever was being read. */
    fun start(
        context: Context,
        app: AppContainer,
        book: NovelBook,
        chapters: List<Chapter>,
        chapter: LoadedNovelChapter,
        anchor: NovelAnchor,
    ) {
        stop()
        this.app = app
        val appContext = context.applicationContext
        val e = ReadAloudEngine(
            store = BookStore(app, book),
            scope = app.appScope,
            speakerFactory = { pkg -> AndroidSpeaker(appContext, pkg) },
            publish = { s -> onState(appContext, s) },
        )
        engine = e
        e.start(
            ReadAloudEngine.Start(
                sourceId = book.sourceId,
                titleId = book.titleId,
                titleName = book.titleName,
                coverUrl = book.coverUrl,
                chapters = chapters,
                chapter = chapter,
                anchor = anchor,
                settings = app.prefs.readAloudSettings.value,
            ),
        )
        appContext.startService(Intent(appContext, ReadAloudService::class.java))
    }

    fun pause() {
        // Paused by hand: the end of a phone call doesn't start it again.
        resumeOnFocus = false
        engine?.pause()
    }

    fun resume() {
        engine?.resume()
    }

    fun togglePlay() {
        val s = _state.value ?: return
        if (s.status == ReadAloudStatus.Paused) resume() else pause()
    }

    fun stop() {
        resumeOnFocus = false
        val e = engine ?: return
        engine = null
        e.stop()
    }

    fun nextParagraph() {
        engine?.nextParagraph()
    }

    fun previousParagraph() {
        engine?.previousParagraph()
    }

    fun setSleep(timer: SleepTimer) {
        engine?.setSleep(timer)
    }

    fun voices(): List<VoiceOption> = engine?.voices().orEmpty()
    fun engines(): List<EngineOption> = engine?.engines().orEmpty()
    val enginePackage: String? get() = engine?.enginePackage

    /** The language of the chapter being read, as read aloud guessed it. */
    val textLocale: java.util.Locale get() = engine?.textLocale ?: java.util.Locale.getDefault()

    /** New speed, voice or engine: used from the next sentence on, and kept for next time. */
    fun updateSettings(settings: ReadAloudSettings) {
        val clean = settings.clamped()
        engine?.applySettings(clean)
        val a = app ?: return
        a.appScope.launch { a.prefs.setReadAloudSettings(clean) }
    }

    // ---- Android around the engine ----------------------------------------------------------

    private fun onState(context: Context, s: ReadAloudState?) {
        _state.value = s
        val playing = s?.status == ReadAloudStatus.Playing
        if (playing) hold(context) else letGo(context)
        // Paused for a long time: stop, so the notification and the engine go away.
        pauseTimeout?.cancel()
        pauseTimeout = null
        if (s != null && s.status == ReadAloudStatus.Paused) {
            pauseTimeout = app?.appScope?.launch {
                delay(PAUSED_STOP_MS)
                stop()
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false
    private var resumeOnFocus = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private fun hold(context: Context) {
        // Keeps the CPU awake between sentences and while the next chapter loads, screen off.
        val lock = wakeLock ?: (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lume:read_aloud")
            .also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
        lock.acquire(WAKE_LOCK_MS)
        startSilence()

        if (focusRequest == null) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                // Speech isn't ducked under another sound: it pauses and comes back.
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
                .build()
            focusRequest = request
            if (am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                // A phone call is going on, for example.
                focusRequest = null
                // Not from inside the engine's own state update.
                Handler(Looper.getMainLooper()).post { pause() }
                return
            }
        }
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisy,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
    }

    /**
     * A silent track played by this process while speaking. The speech itself is played by the
     * TTS engine's process, and Android gives media keys (headsets, Bluetooth, keyboards) to the
     * session of the app that plays audio; without this the keys would never reach read aloud.
     */
    private var silence: AudioTrack? = null

    private fun startSilence() {
        if (silence != null) return
        silence = runCatching {
            val frames = SILENCE_RATE // one second, looped
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SILENCE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 2)
                .build()
                .apply {
                    write(ShortArray(frames), 0, frames)
                    setLoopPoints(0, frames, -1)
                    play()
                }
        }.getOrNull()
    }

    private fun stopSilence() {
        silence?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        silence = null
    }

    private fun letGo(context: Context) {
        stopSilence()
        wakeLock?.takeIf { it.isHeld }?.release()
        if (noisyRegistered) {
            runCatching { context.unregisterReceiver(noisy) }
            noisyRegistered = false
        }
        // A pause for a phone call keeps the focus request, so the call's end can resume.
        if (!resumeOnFocus) {
            focusRequest?.let { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
            focusRequest = null
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_state.value?.status == ReadAloudStatus.Playing) {
                    resumeOnFocus = true
                    engine?.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocus) {
                    resumeOnFocus = false
                    resume()
                }
            }
        }
    }

    /** Paused this long, read aloud stops by itself. */
    private const val PAUSED_STOP_MS = 10 * 60_000L

    /** Renewed on every sentence; only runs out if something hangs. */
    private const val WAKE_LOCK_MS = 10 * 60_000L

    /** Sample rate of the silent track: low, it carries nothing. */
    private const val SILENCE_RATE = 8_000
}

/** [ReadAloudStore] on the title's [NovelBook]: the same rows the reader writes. */
private class BookStore(private val app: AppContainer, private val book: NovelBook) : ReadAloudStore {
    override val incognito: Boolean get() = app.prefs.incognito.value

    override suspend fun loadChapter(chapter: Chapter): LoadedNovelChapter = book.loadChapter(chapter)

    override fun saveProgress(anchor: NovelAnchor, chapter: LoadedNovelChapter) {
        app.appScope.launch { book.saveProgress(anchor, chapter, completed = false) }
    }

    override fun markRead(chapterId: String, chapters: List<Chapter>, finished: LoadedNovelChapter) {
        book.markRead(chapterId, chapters, leftBehind = finished)
    }

    override fun saveSession(session: ReadingSessionTracker.Session) = book.saveSession(session)
}
