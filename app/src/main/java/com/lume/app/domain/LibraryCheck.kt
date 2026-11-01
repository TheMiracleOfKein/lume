package com.lume.app.domain

/** What a library update check did: titles tried and titles that failed. */
data class LibraryCheckOutcome(val checked: Int, val failed: Int) {
    /** Every title failed (no network, sources down); nothing was learned. */
    val allFailed: Boolean get() = checked > 0 && failed >= checked
    val someFailed: Boolean get() = failed > 0 && !allFailed

    /** WorkManager should try again later only when the whole check came to nothing. */
    fun shouldRetry(attempt: Int, maxAttempts: Int = MAX_ATTEMPTS): Boolean = allFailed && attempt + 1 < maxAttempts

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}
