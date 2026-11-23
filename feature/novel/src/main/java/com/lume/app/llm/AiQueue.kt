package com.lume.app.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Background AI work (chapter digests, recaps, summaries) for the whole app. Jobs with the same
 * key (one title's digests) run one after another, so a job that starts after another one can
 * first look at what that one stored instead of asking for it again: a finished chapter and an
 * opened recap never both pay for the same digest. At most [MAX_RUNNING] jobs run at a time, so a
 * reader flipping through chapters doesn't fire a burst of requests. Translation has its own
 * queue in the reader.
 */
object AiQueue {
    private const val MAX_RUNNING = 2

    private val permits = Semaphore(MAX_RUNNING)
    private val locks = HashMap<String, Mutex>()

    /** Runs [block] after any other job with [key] has finished. */
    suspend fun <T> serial(key: String, block: suspend () -> T): T {
        val lock = synchronized(locks) { locks.getOrPut(key) { Mutex() } }
        return lock.withLock { permits.withPermit { block() } }
    }
}
