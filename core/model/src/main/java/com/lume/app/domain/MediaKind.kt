package com.lume.app.domain

/** The kind of content a source serves; each kind has its own reader and its own mode in the app. */
enum class MediaKind(val id: String) {
    Comics("comics"),
    Novel("novel"),
    Video("video"),
    ;

    companion object {
        /** Unknown or missing ids fall back to [Comics]. */
        fun fromId(id: String?): MediaKind = entries.firstOrNull { it.id == id } ?: Comics
    }
}
