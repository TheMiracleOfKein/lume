package com.lume.app.ui.components

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The reader's own brightness on [window]: [level] is the shown level (see [BrightnessCurve]), a
 * negative one follows the system. Back to the system's when the reader closes.
 */
@Composable
fun BrightnessWindowEffect(window: Window?, level: Float) {
    DisposableEffect(window, level) {
        window?.attributes = window.attributes.apply {
            screenBrightness = if (level < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else BrightnessCurve.levelToWindow(level)
        }
        onDispose {
            window?.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}
