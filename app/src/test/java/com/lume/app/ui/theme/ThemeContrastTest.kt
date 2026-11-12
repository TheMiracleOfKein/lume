package com.lume.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast of the Lume colour pairs that carry text and icons. */
class ThemeContrastTest {
    private val schemes = mapOf(
        "dark" to DarkLumeColors,
        "light" to LightLumeColors,
        "amoled" to AmoledLumeColors,
    )

    private class Pair(val name: String, val min: Double, val fg: (LumeColors) -> Color, val bg: (LumeColors) -> Color)

    private val surfaces: List<kotlin.Pair<String, (LumeColors) -> Color>> = listOf(
        "background" to { c -> c.background },
        "surface" to { c -> c.surface },
        "card" to { c -> c.card },
        "cardHigh" to { c -> c.cardHigh },
        "search" to { c -> c.search },
        "selection" to { c -> c.selection },
    )

    private fun pairs(): List<Pair> = buildList {
        val bodyText = listOf<kotlin.Pair<String, (LumeColors) -> Color>>(
            "textPrimary" to { c -> c.textPrimary },
            "textSecondary" to { c -> c.textSecondary },
            "textTertiary" to { c -> c.textTertiary },
        )
        for ((fgName, fg) in bodyText) {
            for ((bgName, bg) in surfaces) {
                // Tertiary text is never drawn on selected rows (they switch to secondary) or on cardHigh.
                if (fgName == "textTertiary" && (bgName == "selection" || bgName == "cardHigh")) continue
                add(Pair("$fgName on $bgName", 4.5, fg, bg))
            }
        }
        for ((bgName, bg) in surfaces.take(4)) {
            add(Pair("accentText on $bgName", 4.5, { c -> c.accentText }, bg))
            add(Pair("danger on $bgName", 4.5, { c -> c.danger }, bg))
            // Field borders sit on the page, surfaces and cards, never on cardHigh.
            if (bgName != "cardHigh") add(Pair("outline on $bgName", 3.0, { c -> c.outline }, bg))
        }
        add(Pair("accentText on selection", 4.5, { c -> c.accentText }, { c -> c.selection }))
        add(Pair("onAccent on accent", 4.5, { c -> c.onAccent }, { c -> c.accent }))
        add(Pair("textPrimary on accentMuted", 4.5, { c -> c.textPrimary }, { c -> c.accentMuted }))
    }

    @Test
    fun allPairsMeetWcagAa() {
        val failures = buildList {
            for ((scheme, colors) in schemes) {
                for (p in pairs()) {
                    val ratio = contrast(p.fg(colors), p.bg(colors))
                    if (ratio < p.min) add("$scheme: ${p.name} = ${"%.2f".format(ratio)} (needs ${p.min})")
                }
            }
        }
        assertTrue("Failing pairs:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun contrastMatchesKnownValues() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.001)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.001)
        // #767676 on white is the classic 4.54:1 AA boundary grey.
        assertEquals(4.54, contrast(Color(0xFF767676), Color.White), 0.01)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }
}
