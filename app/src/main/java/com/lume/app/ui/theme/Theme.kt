package com.lume.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Stored values of the theme setting. */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}

private fun schemeOf(colors: LumeColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.accentMuted,
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.card,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.card,
        surfaceContainerHighest = colors.cardHigh,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        error = colors.danger,
        outline = colors.divider,
    )
}

/** Lume colours taken from the wallpaper (Material You, Android 12+). */
private fun fromDynamic(scheme: ColorScheme, dark: Boolean, pureBlack: Boolean): LumeColors = LumeColors(
    background = if (pureBlack && dark) Color.Black else scheme.background,
    surface = if (pureBlack && dark) Color(0xFF0D0D0D) else scheme.surfaceContainer,
    card = scheme.surfaceContainerHigh,
    cardHigh = scheme.surfaceContainerHighest,
    accent = scheme.primary,
    accentMuted = scheme.primaryContainer,
    danger = scheme.error,
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textTertiary = scheme.outline,
    divider = scheme.outlineVariant,
    navBar = if (pureBlack && dark) Color.Black else scheme.surfaceContainer,
    search = scheme.surfaceContainerHigh,
    onAccent = scheme.onPrimary,
)

private val LumeTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

/**
 * [mode] picks light or dark (System follows the phone); [pureBlack] darkens the dark theme to
 * black; [dynamicColor] uses the wallpaper colours on Android 12 and newer.
 */
@Composable
fun LumeTheme(
    darkTheme: Boolean = true,
    pureBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = remember(darkTheme, pureBlack, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> fromDynamic(
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                darkTheme,
                pureBlack,
            )
            darkTheme && pureBlack -> AmoledLumeColors
            darkTheme -> DarkLumeColors
            else -> LightLumeColors
        }
    }
    CompositionLocalProvider(LocalLumeColors provides colors) {
        MaterialTheme(
            colorScheme = schemeOf(colors, darkTheme),
            typography = LumeTypography,
            content = content,
        )
    }
}

/** Resolves [mode] against the phone's dark mode. */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
