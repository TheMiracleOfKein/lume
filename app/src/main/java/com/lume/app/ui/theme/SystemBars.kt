package com.lume.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** Edge-to-edge with bar icons that follow the app theme, not the phone's dark mode. */
fun ComponentActivity.applyLumeSystemBars(dark: Boolean) {
    val style = if (dark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

@Composable
fun LumeSystemBars(dark: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    LaunchedEffect(activity, dark) { activity.applyLumeSystemBars(dark) }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
