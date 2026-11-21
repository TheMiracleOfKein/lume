package com.lume.app.data.prefs

/**
 * How the text reader reads aloud. Kept on this phone only (not in backups or sync): the voices
 * and engines differ from phone to phone.
 */
data class ReadAloudSettings(
    /** Speech rate, 1.0 is the engine's normal speed, [SPEED_RANGE] in steps of 0.1. */
    val speed: Float = 1.0f,
    /** The chosen voice's name; null means the engine's default voice for the text's language. */
    val voiceName: String? = null,
    /** The chosen TTS engine's package; null means the system default engine. */
    val engine: String? = null,
) {
    fun clamped(): ReadAloudSettings = copy(
        speed = Math.round(speed.coerceIn(SPEED_RANGE.start, SPEED_RANGE.endInclusive) * 10) / 10f,
        voiceName = voiceName?.takeIf { it.isNotBlank() },
        engine = engine?.takeIf { it.isNotBlank() },
    )

    companion object {
        val SPEED_RANGE = 0.5f..3.0f
    }
}
