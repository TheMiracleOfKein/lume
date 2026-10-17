package com.lume.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LumeColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardHigh: Color,
    val accent: Color,
    val accentMuted: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val navBar: Color,
    val search: Color,
    /** Text and icons drawn on [accent]. */
    val onAccent: Color = Color.Black,
)

val DarkLumeColors = LumeColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1E),
    card = Color(0xFF2C2C2E),
    cardHigh = Color(0xFF3A3A3C),
    accent = Color(0xFFF5A524),
    accentMuted = Color(0xFFB87321),
    danger = Color(0xFFE57373),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9A9A9A),
    textTertiary = Color(0xFF6E6E6E),
    divider = Color(0xFF2A2A2A),
    navBar = Color(0xFF161616),
    search = Color(0xFF2A2A2C),
)

val LightLumeColors = LumeColors(
    background = Color(0xFFF4F4F5),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFE8E8EA),
    cardHigh = Color(0xFFDCDCE0),
    accent = Color(0xFFF5A524),
    accentMuted = Color(0xFFB87321),
    danger = Color(0xFFC62828),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF5C5C5C),
    textTertiary = Color(0xFF8A8A8A),
    divider = Color(0xFFD8D8DC),
    navBar = Color(0xFFF0F0F2),
    search = Color(0xFFE8E8EA),
)

/** Dark theme on pure black, for OLED screens. */
val AmoledLumeColors = DarkLumeColors.copy(
    background = Color.Black,
    surface = Color(0xFF0D0D0D),
    card = Color(0xFF1A1A1A),
    cardHigh = Color(0xFF262626),
    divider = Color(0xFF1C1C1C),
    navBar = Color.Black,
    search = Color(0xFF1A1A1A),
)

val LocalLumeColors = staticCompositionLocalOf { DarkLumeColors }

/**
 * Theme-aware colors. Use inside @Composable as `AppColors.background`.
 * Outside composition (e.g. static schemes) use [DarkLumeColors] / [LightLumeColors].
 */
object AppColors {
    val background: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.background
    val surface: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.surface
    val card: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.card
    val cardHigh: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.cardHigh
    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.accent
    val accentMuted: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.accentMuted
    val danger: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.danger
    val textPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.textPrimary
    val textSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.textSecondary
    val textTertiary: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.textTertiary
    val divider: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.divider
    val navBar: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.navBar
    val search: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.search
    val onAccent: Color
        @Composable @ReadOnlyComposable get() = LocalLumeColors.current.onAccent
}
