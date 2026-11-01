package com.lume.app.ui.screens

/**
 * Math for very tall pages (webtoon strips of 800 x 14,000 px and more). The GPU cannot draw a
 * bitmap taller than its texture limit (4096 px is the safe floor), so such a page is decoded in
 * horizontal slices that each stay well under it.
 */

/** A drawn bitmap taller than this risks drawing black (texture limit). */
internal const val MAX_DRAW_PX = 4096

/** Height of one slice in decoded pixels. */
internal const val TILE_PX = 2048

/** A slice of the source image: rows [top] until [bottom]. */
internal data class TileBounds(val top: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/**
 * Largest power-of-two downsample that keeps the decoded width at or above [targetWidth], so
 * text stays sharp on screen and memory is not spent on pixels the screen can't show.
 */
internal fun tileSampleSize(sourceWidth: Int, targetWidth: Int): Int {
    if (sourceWidth <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}

/** True when the page, decoded at [sample], is taller than one drawable bitmap can be. */
internal fun needsTiling(sourceHeight: Int, sample: Int): Boolean =
    sourceHeight / sample.coerceAtLeast(1) > MAX_DRAW_PX

/**
 * Slices of a [sourceHeight]-tall image, top to bottom, with no gaps or overlap. Each slice is
 * [TILE_PX] decoded pixels tall at [sample] (the last one is whatever remains).
 */
internal fun tileBounds(sourceHeight: Int, sample: Int): List<TileBounds> {
    if (sourceHeight <= 0) return emptyList()
    val step = TILE_PX * sample.coerceAtLeast(1)
    val out = ArrayList<TileBounds>((sourceHeight + step - 1) / step)
    var top = 0
    while (top < sourceHeight) {
        val bottom = minOf(top + step, sourceHeight)
        out += TileBounds(top, bottom)
        top = bottom
    }
    return out
}
