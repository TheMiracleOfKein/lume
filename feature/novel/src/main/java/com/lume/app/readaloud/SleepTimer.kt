package com.lume.app.readaloud

/** When read aloud stops by itself. */
sealed interface SleepTimer {
    data object Off : SleepTimer

    /** Stops once the current chapter has been read to its end. */
    data object EndOfChapter : SleepTimer

    /**
     * Stops at the end of the sentence being read when [deadline] (wall clock, ms) has passed;
     * [minutes] is the choice it was set with.
     */
    data class At(val deadline: Long, val minutes: Int) : SleepTimer

    companion object {
        /** The timer choices offered, in minutes. */
        val MINUTES = listOf(15, 30, 45, 60)

        fun minutes(n: Int, now: Long): SleepTimer = At(now + n * 60_000L, n)
    }
}

/** After a sentence: time to stop? */
internal fun SleepTimer.dueAfterSentence(now: Long): Boolean = this is SleepTimer.At && now >= deadline

/** At the end of a chapter: time to stop? */
internal fun SleepTimer.dueAtChapterEnd(now: Long): Boolean = this is SleepTimer.EndOfChapter || dueAfterSentence(now)

/** Milliseconds left on a timed stop, or null for the others. */
fun SleepTimer.remainingMs(now: Long): Long? = (this as? SleepTimer.At)?.let { (it.deadline - now).coerceAtLeast(0) }
