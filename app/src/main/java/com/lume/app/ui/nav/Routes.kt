package com.lume.app.ui.nav

import androidx.navigation.NavController
import com.lume.app.util.urlEncode

object Routes {
    const val Library = "library"
    const val Catalog = "catalog"
    const val Home = "home"
    const val History = "history"
    const val Updates = "updates"
    const val Search = "search"
    const val Sources = "sources"
    const val Settings = "settings"
    const val Downloads = "downloads"
    const val Categories = "categories"
    const val Browse = "browse/{sourceId}"
    const val Title = "title/{sourceId}/{titleId}"
    const val Chapters = "title/{sourceId}/{titleId}/chapters"
    const val Reader = "read/{sourceId}/{titleId}/{chapterId}?page={page}&offset={offset}"

    fun browse(sourceId: String) = "browse/${encode(sourceId)}"

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

/**
 * Opens the reader, replacing any reader already in the back stack (and whatever was opened from
 * it, like its chapter list). Only one reader ever lives in the stack, so a stale one can't come
 * back to the top and save its old position over the new one.
 */
fun NavController.openReader(
    sourceId: String,
    titleId: String,
    chapterId: String,
    page: Int? = null,
    offset: Float? = null,
) {
    navigate(Routes.reader(sourceId, titleId, chapterId, page, offset)) {
        popUpTo(Routes.Reader) { inclusive = true }
    }
}

/** From the reader: go back to this title's screen if it is right below, else open it. */
fun NavController.openTitleFromReader(sourceId: String, titleId: String) {
    val below = runCatching { getBackStackEntry(Routes.Title) }.getOrNull()
    val args = below?.arguments
    if (args?.getString("sourceId") == sourceId && args.getString("titleId") == titleId) {
        popBackStack(Routes.Title, inclusive = false)
    } else {
        navigate(Routes.title(sourceId, titleId))
    }
}
