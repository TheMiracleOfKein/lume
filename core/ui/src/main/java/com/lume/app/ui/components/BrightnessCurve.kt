package com.lume.app.ui.components

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Converts between the brightness level the reader shows (0..1, spaced the way the eye sees it,
 * like the system slider) and the value a window's `screenBrightness` takes, which is linear in
 * light output. Without this, 1% is far above the panel's minimum and everything past ~50% looks
 * the same. Same curve as the platform's slider (HLG, `BrightnessUtils` in SystemUI).
 */
object BrightnessCurve {
    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f

    /** The smallest window value set, so the screen dims to its minimum and never reads as "off" on old devices. */
    private const val MIN_WINDOW = 0.0001f

    /** The window's `screenBrightness` (0..1, linear) for a shown [level] (0..1). */
    fun levelToWindow(level: Float): Float {
        val v = level.coerceIn(0f, 1f)
        val linear = if (v <= R) (v / R) * (v / R) else exp((v - C) / A) + B
        return (linear / 12f).coerceIn(MIN_WINDOW, 1f)
    }

    /** The shown level (0..1) for a linear brightness [value] (0..1), e.g. the system setting / 255. */
    fun windowToLevel(value: Float): Float {
        val n = value.coerceIn(0f, 1f) * 12f
        val level = if (n <= 1f) R * sqrt(n) else A * ln(n - B) + C
        return level.coerceIn(0f, 1f)
    }
}
