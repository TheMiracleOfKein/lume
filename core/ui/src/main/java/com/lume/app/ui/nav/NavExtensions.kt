package com.lume.app.ui.nav

import androidx.navigation.NavController

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
