package com.lume.app.data.prefs

/** The typeface family of the text reader. */
enum class NovelFont { Serif, Sans }

/** The reader's colours; [App] follows the app theme, the rest are fixed. */
enum class NovelTheme { App, Light, Sepia, Dark, Black }

/**
 * How the novel reader lays out text. Every value is kept inside its range by [clamped], so a
 * bad stored value or a restored backup can't break the layout.
 */
data class NovelReaderSettings(
    /** Text size in sp before the system font scale, [FONT_SIZE_RANGE]. */
    val fontSize: Int = 18,
    /** Line height as a multiple of the font size, [LINE_HEIGHT_RANGE]. */
    val lineHeight: Float = 1.5f,
    val font: NovelFont = NovelFont.Serif,
    /** Space left and right of the text in dp, [SIDE_MARGIN_RANGE]. */
    val sideMargin: Int = 20,
    /** Space between paragraphs in dp, [PARAGRAPH_SPACING_RANGE]. */
    val paragraphSpacing: Int = 12,
    val justify: Boolean = false,
    val theme: NovelTheme = NovelTheme.App,
    /** Page by page (turned sideways) instead of one long scroll. */
    val paged: Boolean = false,
    /** The chapter's number and title are shown above its text (the top bar keeps it either way). */
    val showChapterTitle: Boolean = true,
    /** A vertical swipe along the left edge changes the screen brightness. */
    val brightnessSwipe: Boolean = true,
    /** The reader's own screen brightness 0.01..1, or [BRIGHTNESS_SYSTEM] to follow the system. */
    val brightness: Float = BRIGHTNESS_SYSTEM,
    /** The volume keys turn the page (or scroll by a screen). */
    val volumeKeys: Boolean = false,
    /** A small line at the bottom with the time, battery and chapter progress while the bars are hidden. */
    val statusLine: Boolean = true,
    /** First-line indent of paragraphs in em, [PARAGRAPH_INDENT_RANGE]. */
    val paragraphIndent: Int = 0,
    /** Auto-scroll speed step, [AUTO_SCROLL_SPEED_RANGE]. */
    val autoScrollSpeed: Int = 3,
) {
    fun clamped(): NovelReaderSettings = copy(
        fontSize = fontSize.coerceIn(FONT_SIZE_RANGE),
        lineHeight = (Math.round(lineHeight.coerceIn(LINE_HEIGHT_RANGE.start, LINE_HEIGHT_RANGE.endInclusive) * 20) / 20f),
        sideMargin = sideMargin.coerceIn(SIDE_MARGIN_RANGE),
        paragraphSpacing = paragraphSpacing.coerceIn(PARAGRAPH_SPACING_RANGE),
        brightness = if (brightness < 0f || brightness.isNaN()) BRIGHTNESS_SYSTEM else brightness.coerceIn(BRIGHTNESS_MIN, 1f),
        paragraphIndent = paragraphIndent.coerceIn(PARAGRAPH_INDENT_RANGE),
        autoScrollSpeed = autoScrollSpeed.coerceIn(AUTO_SCROLL_SPEED_RANGE),
    )

    companion object {
        val FONT_SIZE_RANGE = 14..28
        val LINE_HEIGHT_RANGE = 1.2f..2.0f
        val SIDE_MARGIN_RANGE = 8..48
        val PARAGRAPH_SPACING_RANGE = 0..24
        val PARAGRAPH_INDENT_RANGE = 0..2
        val AUTO_SCROLL_SPEED_RANGE = 1..10
        const val BRIGHTNESS_SYSTEM = -1f
        const val BRIGHTNESS_MIN = 0.01f
    }
}
