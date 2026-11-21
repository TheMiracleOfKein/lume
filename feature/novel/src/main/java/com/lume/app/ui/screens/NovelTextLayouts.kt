package com.lume.app.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult

/**
 * The text layout of each block the scrolling list has on screen, so a long press can be turned into
 * a character offset. A block adds its layout when it is measured and removes it when it leaves the
 * list. Read from gesture callbacks only, so it is not snapshot state.
 */
internal class NovelTextLayouts {
    /** A block's layout and the space around its text inside its row, in pixels. */
    private class Entry(val layout: TextLayoutResult, val padStart: Float, val padTop: Float)

    private val entries = HashMap<String, Entry>()

    fun put(key: String, layout: TextLayoutResult, padStart: Float, padTop: Float) {
        entries[key] = Entry(layout, padStart, padTop)
    }

    fun remove(key: String) {
        entries.remove(key)
    }

    /**
     * The character of the block [key] at ([x], [yInRow]), where [x] is measured from the list's
     * start edge and [yInRow] from the top of the block's row. Null when the block has no layout
     * or the point is outside its text.
     */
    fun offsetAt(key: String, x: Float, yInRow: Float): Int? {
        val e = entries[key] ?: return null
        return characterAt(e.layout, x - e.padStart, yInRow - e.padTop)
    }
}

/** The character offset at ([x], [y]) inside [layout]'s text, or null when the point is beside or below it. */
internal fun characterAt(layout: TextLayoutResult, x: Float, y: Float): Int? {
    if (x < 0f || y < 0f || y >= layout.size.height) return null
    // Past the end of a short last line the layout would answer with its last character.
    if (x > layout.getLineRight(layout.getLineForVerticalPosition(y))) return null
    return layout.getOffsetForPosition(Offset(x, y)).takeIf { it in 0 until layout.layoutInput.text.length }
}
