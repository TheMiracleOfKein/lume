package com.lume.app.domain

import kotlin.random.Random

/**
 * The chapters after [currentId] (in reading order) whose images should be loaded ahead while
 * reading, at most [count]. Chapters for which [skip] is true (downloaded ones) don't count.
 */
fun chaptersToPreload(
    chapters: List<Chapter>,
    currentId: String,
    count: Int,
    skip: (Chapter) -> Boolean = { false },
): List<Chapter> {
    if (count <= 0) return emptyList()
    val ordered = chapters.inReadingOrder()
    val index = ordered.indexOfFirst { it.id == currentId || it.id.trimEnd('/') == currentId.trimEnd('/') }
    if (index < 0) return emptyList()
    return ordered.drop(index + 1).filterNot(skip).take(count)
}

/**
 * Pacing for loading whole chapters ahead. Images go one at a time with a short random pause in
 * between, so a site sees a reader turning pages rather than a burst of requests (bursts are what
 * nginx limits and Cloudflare rules block). Failures back off exponentially; too many in a row
 * stop the run for this reading session.
 */
class PreloadPacer(
    private val random: Random = Random.Default,
    private val baseDelayMs: Long = BASE_DELAY_MS,
    private val jitterMs: Long = JITTER_MS,
) {
    private var failuresInRow = 0

    /** True after [MAX_FAILURES_IN_ROW] failures without a success in between: stop loading ahead. */
    val gaveUp: Boolean
        get() = failuresInRow >= MAX_FAILURES_IN_ROW

    fun onSuccess() {
        failuresInRow = 0
    }

    fun onFailure() {
        failuresInRow++
    }

    /** Pause before the next image request. */
    fun nextImageDelayMs(): Long {
        val backoff = if (failuresInRow == 0) 0L else (BACKOFF_STEP_MS shl (failuresInRow - 1)).coerceAtMost(MAX_BACKOFF_MS)
        return baseDelayMs + random.nextLong(jitterMs + 1) + backoff
    }

    /** Pause before asking the source for the next chapter's page list. */
    fun nextChapterDelayMs(): Long = CHAPTER_DELAY_MS + random.nextLong(jitterMs + 1)

    companion object {
        const val BASE_DELAY_MS = 350L
        const val JITTER_MS = 400L
        const val BACKOFF_STEP_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val CHAPTER_DELAY_MS = 1_500L
        const val MAX_FAILURES_IN_ROW = 4

        /** Wait after a chapter opens before loading ahead, so its own pages go first. */
        const val START_DELAY_MS = 3_000L

        /** A page list fetched ahead is reused this long; image URLs may be signed and expire. */
        const val PAGE_LIST_FRESH_MS = 10 * 60_000L
    }
}
