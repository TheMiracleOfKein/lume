package com.lume.app.ui.screens

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast of the reader's own colours: text, secondary text and bar icons. */
class NovelPaletteContrastTest {
    @Test
    fun everyFixedThemeMeetsWcagAa() {
        val failures = buildList {
            for ((theme, p) in NovelPalettes.fixed) {
                fun check(name: String, fg: Color, bg: Color, min: Double = 4.5) {
                    val ratio = contrast(fg, bg)
                    if (ratio < min) add("$theme: $name = ${"%.2f".format(ratio)} (needs $min)")
                }
                check("text on background", p.text, p.background)
                check("secondary on background", p.textSecondary, p.background)
                check("text on bar", p.text, p.bar)
                check("secondary on bar", p.textSecondary, p.bar)
                check("accent on bar", p.accent, p.bar)
                check("accent on background", p.accent, p.background)
            }
        }
        assertTrue("Failing pairs:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun darkFlagFollowsTheBackground() {
        assertTrue(NovelPalettes.Dark.dark && NovelPalettes.Black.dark)
        assertTrue(!NovelPalettes.Light.dark && !NovelPalettes.Sepia.dark)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }
}
