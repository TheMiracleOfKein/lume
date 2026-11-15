package com.lume.app.data.locale

import androidx.annotation.StringRes
import com.lume.app.AppContextHolder

/** Resolve a string with the current app locale (for non-Composable / engine code). */
fun appString(@StringRes id: Int, vararg formatArgs: Any): String {
    val app = runCatching { AppContextHolder.context }.getOrNull() ?: return ""
    return if (formatArgs.isEmpty()) app.getString(id) else app.getString(id, *formatArgs)
}
