package com.lume.app.readaloud

enum class ReadAloudStatus {
    /** Waiting for the engine or for the next chapter's text; plays once it is there. */
    Loading,
    Playing,
    Paused,
}

/** Something read aloud tells the listener. */
sealed interface ReadAloudProblem {
    /** No TTS engine is installed, or it failed to start. */
    data object NoEngine : ReadAloudProblem

    /** The engine has no voice for the text's language; it reads with its default voice. */
    data object NoVoiceForLanguage : ReadAloudProblem

    /** The next chapter's text couldn't be loaded (offline and not downloaded, for example). */
    data class LoadFailed(val chapterTitle: String, val message: String) : ReadAloudProblem

    /** The last chapter has been read. */
    data object Finished : ReadAloudProblem
}

/**
 * What read aloud is doing, for the reader (highlight, controls) and the media notification.
 * [block] is the spoken block's index ([com.lume.app.data.novel.SpeechScript.TITLE] for the
 * chapter title), [start] until [end] the spoken sentence inside its text.
 */
data class ReadAloudState(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String?,
    val chapterId: String,
    val chapterTitle: String,
    val block: Int,
    val start: Int,
    val end: Int,
    val status: ReadAloudStatus,
    val sleep: SleepTimer,
    val problem: ReadAloudProblem? = null,
) {
    fun isFor(sourceId: String, titleId: String): Boolean = this.sourceId == sourceId && this.titleId == titleId
}
