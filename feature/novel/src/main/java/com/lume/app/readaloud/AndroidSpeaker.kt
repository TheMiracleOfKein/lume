package com.lume.app.readaloud

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * [Speaker] on Android's own [TextToSpeech], with whatever engine is installed: no Google
 * services needed. [engine] is an engine's package, null for the system default.
 */
internal class AndroidSpeaker(context: Context, engine: String?) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    override var listener: Speaker.Listener? = null
    override var isReady = false
        private set
    private var failed = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, { status ->
        main.post {
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                listener?.onReady()
            } else {
                failed = true
                listener?.onInitFailed()
            }
        }
    }, engine)

    init {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                main.post { listener?.onStart(utteranceId) }
            }

            override fun onDone(utteranceId: String) {
                main.post { listener?.onDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { listener?.onError(utteranceId) }
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                main.post { listener?.onError(utteranceId) }
            }
        })
    }

    override val maxInputLength: Int get() = TextToSpeech.getMaxSpeechInputLength()

    override val enginePackage: String? get() = if (isReady) tts.defaultEngine else null

    override fun speak(id: String, text: String, flush: Boolean) {
        tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
    }

    override fun silence(id: String, ms: Long, flush: Boolean) {
        tts.playSilentUtterance(ms, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, id)
    }

    override fun stop() {
        if (!failed) tts.stop()
    }

    override fun configure(speed: Float, voiceName: String?, textLocale: Locale): Boolean {
        if (!isReady) return true
        tts.setSpeechRate(speed)
        val voices = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val chosen = voiceName?.let { name -> voices.firstOrNull { it.name == name } }
        if (chosen != null) {
            tts.voice = chosen
            return true
        }
        val default: Voice? = runCatching { tts.defaultVoice }.getOrNull()
        if (default != null && sameAlphabet(default.locale, textLocale)) {
            tts.voice = default
            return true
        }
        val result = tts.setLanguage(textLocale)
        if (result >= TextToSpeech.LANG_AVAILABLE) return true
        if (default != null) tts.voice = default
        return false
    }

    override fun voices(): List<VoiceOption> = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        // Voices still to be downloaded can't speak yet.
        .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
        .map { VoiceOption(it.name, it.locale, it.isNetworkConnectionRequired) }
        .sortedWith(compareBy<VoiceOption>({ it.locale.displayName }, { it.needsNetwork }, { it.name }))

    override fun engines(): List<EngineOption> = runCatching { tts.engines.orEmpty() }.getOrDefault(emptyList())
        .map { EngineOption(it.name, it.label ?: it.name) }

    override fun release() {
        listener = null
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}
