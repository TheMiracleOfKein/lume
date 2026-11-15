package com.lume.app.domain

/**
 * Which library titles an update check leaves out, to spend less battery and data. All off by
 * default, so a check looks at every title of its pool as before.
 */
data class UpdateRules(
    /** The site marks the title as finished. */
    val skipCompleted: Boolean = false,
    /** Nothing read yet. */
    val skipNotStarted: Boolean = false,
    /** Chapters already waiting to be read. */
    val skipUnread: Boolean = false,
    /** Background checks wait for the charger (WorkManager constraint; "Update now" ignores it). */
    val chargingOnly: Boolean = false,
    /** Category ids (0 = uncategorized) a check is limited to; empty means every category. */
    val categories: Set<Long> = emptySet(),
) {
    val skipsAny: Boolean get() = skipCompleted || skipNotStarted || skipUnread || categories.isNotEmpty()

    /** True when a title in [titleCats] (empty = uncategorized) is inside the chosen categories. */
    fun inCategories(titleCats: Set<Long>): Boolean =
        categories.isEmpty() || effectiveCategories(titleCats).any { it in categories }

    /**
     * True when a check should leave the title out. [total] and [unread] count chapters as the
     * library shows them; [lastReadAt] is the title's last reading time, if any.
     */
    fun skips(completed: Boolean, total: Int, unread: Int, lastReadAt: Long?): Boolean {
        val started = lastReadAt != null || (total > 0 && unread < total)
        return (skipCompleted && completed) ||
            (skipNotStarted && !started) ||
            (skipUnread && unread > 0)
    }
}
