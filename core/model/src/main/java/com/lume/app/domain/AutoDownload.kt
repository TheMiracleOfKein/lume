package com.lume.app.domain

/** Which library titles get their new chapters downloaded by the library check. */
enum class AutoDownloadMode {
    OFF,
    ALL,

    /** Only titles marked "Download new chapters" on their screen. */
    SUBSCRIBED,
    ;

    fun downloads(subscribed: Boolean): Boolean = when (this) {
        OFF -> false
        ALL -> true
        SUBSCRIBED -> subscribed
    }

    companion object {
        /** [stored] is the saved ordinal; before it existed, an on/off switch ([legacyOn]) meant all or nothing. */
        fun fromStored(stored: Int?, legacyOn: Boolean?): AutoDownloadMode =
            stored?.let { entries.getOrNull(it) } ?: if (legacyOn == true) ALL else OFF
    }
}
