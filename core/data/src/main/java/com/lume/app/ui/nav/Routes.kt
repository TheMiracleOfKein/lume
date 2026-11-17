package com.lume.app.ui.nav

import com.lume.app.util.urlEncode

object Routes {
    const val Library = "library"
    const val Catalog = "catalog"
    const val Home = "home"
    const val History = "history"
    const val Updates = "updates"
    const val Stats = "stats"
    const val Search = "search"
    const val SearchAll = "search/all?q={q}"
    const val Sources = "sources"
    const val Settings = "settings"
    const val SettingsPage = "settings/{page}?focus={focus}"
    const val Downloads = "downloads"
    const val NearbySend = "downloads/send"
    const val NearbyReceive = "downloads/receive"
    const val Categories = "categories"
    const val Browse = "browse/{sourceId}?genre={genre}&q={q}"
    const val About = "about"
    const val ErrorLog = "error-log"
    const val ReadingList = "reading-list"
    const val Title = "title/{sourceId}/{titleId}"
    const val Chapters = "title/{sourceId}/{titleId}/chapters"
    const val Reader = "read/{sourceId}/{titleId}/{chapterId}?page={page}&offset={offset}"

    /** A settings section; [focus] is the id of a setting to scroll to and highlight. */
    fun settingsPage(page: String, focus: String? = null): String =
        if (focus == null) "settings/$page" else "settings/$page?focus=${encode(focus)}"

    /** Global search (all sources) for [query], started at once. */
    fun searchAll(query: String) = "search/all?q=${encode(query)}"

    /**
     * Browse a source; with [genre], its catalogue opens filtered by that genre; with [query], it
     * opens on its search results for that text.
     */
    fun browse(sourceId: String, genre: String? = null, query: String? = null): String {
        val params = listOfNotNull(genre?.let { "genre=${encode(it)}" }, query?.let { "q=${encode(it)}" })
        val base = "browse/${encode(sourceId)}"
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    fun title(sourceId: String, titleId: String) =
        "title/$sourceId/${encode(titleId)}"

    fun chapters(sourceId: String, titleId: String) =
        "title/$sourceId/${encode(titleId)}/chapters"

    /** [page] / [offset] open an exact spot (bookmark, Continue); without them the chapter resumes itself. */
    fun reader(sourceId: String, titleId: String, chapterId: String, page: Int? = null, offset: Float? = null): String {
        val base = "read/$sourceId/${encode(titleId)}/${encode(chapterId)}"
        return if (page == null) base else "$base?page=$page&offset=${offset ?: 0f}"
    }

    /** Path arguments come back already decoded by Navigation; never decode them a second time. */
    fun encode(value: String): String =
        urlEncode(value).replace("+", "%20")
}
