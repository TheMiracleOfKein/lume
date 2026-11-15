package com.lume.app.domain

/**
 * Measures one stretch of reading in one chapter. Time only counts between page events, and a gap
 * longer than [idleCapMs] counts as [idleCapMs], so a phone left open does not inflate it.
 * All times are passed in, so the tracker has no clock of its own.
 */
class ReadingSessionTracker(
    private val idleCapMs: Long = IDLE_CAP_MS,
    private val minDurationMs: Long = MIN_DURATION_MS,
) {
    /** A finished session: what the statistics store. */
    data class Session(
        val chapterId: String,
        val startedAt: Long,
        val durationMs: Long,
        val pagesRead: Int,
    )

    private var chapterId: String? = null
    private var startedAt = 0L
    private var activeMs = 0L
    private var lastEventAt = 0L
    private var paused = false
    private val pages = mutableSetOf<Int>()

    /**
     * The reader is on page [index] of [chapter]. Returns the session of the previous chapter when
     * this one differs from it, so the caller can store it.
     */
    fun onPage(chapter: String, index: Int, now: Long): Session? {
        var finished: Session? = null
        if (chapterId != null && chapterId != chapter) finished = finish(now)
        if (chapterId == null) start(chapter, now)
        accrue(now)
        pages += index
        return finished
    }

    /** The reader left the screen; time stops until [resume]. */
    fun pause(now: Long) {
        if (chapterId == null || paused) return
        accrue(now)
        paused = true
    }

    /** The reader is back; time counts from [now]. */
    fun resume(now: Long) {
        if (chapterId == null || !paused) return
        paused = false
        lastEventAt = now
    }

    /** Ends the current session. Null when there is none or it was too short to mean anything. */
    fun finish(now: Long): Session? {
        val chapter = chapterId ?: return null
        accrue(now)
        val session = Session(chapter, startedAt, activeMs, pages.size)
        chapterId = null
        pages.clear()
        activeMs = 0L
        paused = false
        return session.takeUnless { it.durationMs < minDurationMs && it.pagesRead <= 1 }
    }

    private fun start(chapter: String, now: Long) {
        chapterId = chapter
        startedAt = now
        activeMs = 0L
        lastEventAt = now
        paused = false
        pages.clear()
    }

    private fun accrue(now: Long) {
        if (!paused) activeMs += (now - lastEventAt).coerceIn(0L, idleCapMs)
        lastEventAt = now
    }

    companion object {
        const val IDLE_CAP_MS = 2 * 60_000L
        const val MIN_DURATION_MS = 5_000L
    }
}
