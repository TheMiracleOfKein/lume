package com.lume.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.lume.app.data.novel.Span
import com.lume.app.data.prefs.NovelFont
import com.lume.app.data.prefs.NovelTheme
import com.lume.app.ui.theme.AppColors

/**
 * Colours of the text reader. They belong to the reader only; the fixed themes ignore the app's
 * light / dark choice. Every text colour meets WCAG AA (4.5:1) on its background and on the bars
 * (see `NovelPaletteContrastTest`).
 */
@Immutable
class NovelPalette(
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    /** Top and bottom bars. */
    val bar: Color,
    /** Icons and the progress line on the bars. */
    val accent: Color,
) {
    /** Light system bar icons are needed on a dark reader. */
    val dark: Boolean get() = background.luminance() < 0.5f
}

object NovelPalettes {
    val Light = NovelPalette(
        background = Color(0xFFFAFAF8),
        text = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF555555),
        bar = Color(0xFFECECE8),
        accent = Color(0xFF8A5200),
    )
    val Sepia = NovelPalette(
        background = Color(0xFFF4ECD8),
        text = Color(0xFF3B2F20),
        textSecondary = Color(0xFF6B5A45),
        bar = Color(0xFFE8DDC4),
        accent = Color(0xFF8A4B00),
    )
    val Dark = NovelPalette(
        background = Color(0xFF1E1E1E),
        text = Color(0xFFD8D8D8),
        textSecondary = Color(0xFFA6A6A6),
        bar = Color(0xFF2C2C2C),
        accent = Color(0xFFF5A524),
    )
    val Black = NovelPalette(
        background = Color(0xFF000000),
        text = Color(0xFFC8C8C8),
        textSecondary = Color(0xFF9A9A9A),
        bar = Color(0xFF121212),
        accent = Color(0xFFF5A524),
    )

    /** The fixed palettes, for tests. */
    val fixed: Map<NovelTheme, NovelPalette> = mapOf(
        NovelTheme.Light to Light,
        NovelTheme.Sepia to Sepia,
        NovelTheme.Dark to Dark,
        NovelTheme.Black to Black,
    )
}

/** The palette for [theme]; [NovelTheme.App] follows the app's colours. */
@Composable
fun novelPalette(theme: NovelTheme): NovelPalette = when (theme) {
    NovelTheme.App -> NovelPalette(
        background = AppColors.background,
        text = AppColors.textPrimary,
        textSecondary = AppColors.textSecondary,
        bar = AppColors.surface,
        accent = AppColors.accentText,
    )
    NovelTheme.Light -> NovelPalettes.Light
    NovelTheme.Sepia -> NovelPalettes.Sepia
    NovelTheme.Dark -> NovelPalettes.Dark
    NovelTheme.Black -> NovelPalettes.Black
}

fun NovelFont.family(): FontFamily = when (this) {
    NovelFont.Serif -> FontFamily.Serif
    NovelFont.Sans -> FontFamily.SansSerif
}

/** The styled text of a block: bold and italic runs become span styles. */
fun List<Span>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    for (span in this@toAnnotatedString) {
        if (span.bold || span.italic) {
            withStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                ),
            ) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}

/** Heading size relative to body text: level 1 is the largest. */
fun headingScale(level: Int): Float = when (level) {
    1 -> 1.6f
    2 -> 1.4f
    3 -> 1.25f
    4 -> 1.15f
    else -> 1.05f
}
