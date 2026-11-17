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
) {
    fun clamped(): NovelReaderSettings = copy(
        fontSize = fontSize.coerceIn(FONT_SIZE_RANGE),
        lineHeight = (Math.round(lineHeight.coerceIn(LINE_HEIGHT_RANGE.start, LINE_HEIGHT_RANGE.endInclusive) * 20) / 20f),
        sideMargin = sideMargin.coerceIn(SIDE_MARGIN_RANGE),
        paragraphSpacing = paragraphSpacing.coerceIn(PARAGRAPH_SPACING_RANGE),
    )

    companion object {
        val FONT_SIZE_RANGE = 14..28
        val LINE_HEIGHT_RANGE = 1.2f..2.0f
        val SIDE_MARGIN_RANGE = 8..48
        val PARAGRAPH_SPACING_RANGE = 0..24
    }
}
