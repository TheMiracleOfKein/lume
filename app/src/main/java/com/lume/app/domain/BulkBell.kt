package com.lume.app.domain

/** What the bell button of the library selection does. */
sealed class BulkBell {
    /** Every selected title had the bell: [removed] titles lost it. */
    data class Off(val removed: Int) : BulkBell()

    /** [added] titles got the bell; [skipped] didn't, because the pool was full. */
    data class On(val added: Int, val skipped: Int) : BulkBell()
}

/**
 * Plans the bell for [selected] library titles, [ringing] of which have it already, while
 * [watched] titles of the whole library have it and at most [limit] may.
 */
fun planBulkBell(ringing: Int, selected: Int, watched: Int, limit: Int): BulkBell {
    if (selected > 0 && ringing >= selected) return BulkBell.Off(ringing)
    val wanted = selected - ringing
    val added = minOf(wanted, (limit - watched).coerceAtLeast(0))
    return BulkBell.On(added = added, skipped = wanted - added)
}
