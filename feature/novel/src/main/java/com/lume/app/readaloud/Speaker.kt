package com.lume.app.readaloud

import java.util.Locale

/** A voice of the TTS engine, for the voice picker. */
data class VoiceOption(
    val name: String,
    val locale: Locale,
    /** The voice streams from the internet; it won't work offline. */
    val needsNetwork: Boolean,
)

/** An installed TTS engine (Google, RHVoice, eSpeak, Samsung…). */
data class EngineOption(val packageName: String, val label: String)

/**
 * What read aloud needs from a text-to-speech engine. [AndroidSpeaker] is the real one; tests use
 * a fake. Every [Listener] call comes on the main thread.
 */
interface Speaker {
    interface Listener {
        fun onReady()
        fun onInitFailed()
        fun onStart(id: String)
        fun onDone(id: String)
        fun onError(id: String)
    }

    var listener: Listener?
    val isReady: Boolean

    /** The longest text one [speak] call takes. */
    val maxInputLength: Int

    /** Speaks [text]; [flush] drops whatever was queued before it. */
    fun speak(id: String, text: String, flush: Boolean)

    /** [ms] of silence, reported like an utterance. */
    fun silence(id: String, ms: Long, flush: Boolean)

    /** Stops speaking and drops the queue. */
    fun stop()

    /**
     * Speed and voice for the next utterances. With no [voiceName] (or one that is gone) the
     * engine's default voice is used, or a voice for [textLocale] when the default one is for
     * another alphabet. Returns false when the engine has no voice for the text's language.
     */
    fun configure(speed: Float, voiceName: String?, textLocale: Locale): Boolean

    fun voices(): List<VoiceOption>
    fun engines(): List<EngineOption>

    /** The engine in use (its package), or null before it is ready. */
    val enginePackage: String?

    fun release()
}

/** Languages written in Cyrillic: a voice for one of these can't read Latin text, and back. */
private val cyrillic = setOf("ru", "uk", "be", "bg", "sr", "mk", "kk", "ky", "tg", "mn")

internal fun sameAlphabet(a: Locale, b: Locale): Boolean = (a.language in cyrillic) == (b.language in cyrillic)

/**
 * The language of a chapter's text, as far as read aloud cares: mostly Cyrillic letters is
 * Russian, anything else English (sentence breaks work the same way for Latin-script languages).
 */
internal fun guessTextLocale(sample: String): Locale {
    var letters = 0
    var cyr = 0
    for (c in sample) {
        if (!c.isLetter()) continue
        letters++
        if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) cyr++
        if (letters >= 3000) break
    }
    return if (letters > 0 && cyr * 3 > letters) Locale.forLanguageTag("ru") else Locale.ENGLISH
}
