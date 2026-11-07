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

/** Below this zoom, the normal page bitmap is sharp enough and no detail is decoded. */
internal const val DETAIL_MIN_SCALE = 1.3f

/** Detail is decoded for a little more than what is on screen, so a small pan stays sharp. */
internal const val DETAIL_MARGIN = 0.15f

/**
 * The part of a list item that is on screen while the whole list is zoomed by [scale] around
 * the viewport centre and moved by ([offsetX], [offsetY]), as fractions of the item; null when
 * the item is off screen. Item and viewport are in unzoomed list pixels.
 */
internal fun zoomedVisibleRegion(
    itemLeft: Float,
    itemTop: Float,
    itemWidth: Float,
    itemHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    margin: Float = 0f,
): com.lume.app.domain.page.PageRect? {
    if (itemWidth <= 0f || itemHeight <= 0f || scale <= 0f) return null
    val cx = viewportWidth / 2f
    val cy = viewportHeight / 2f
    // Screen point s shows list point c + (s - c - offset) / scale.
    val spanX = viewportWidth / scale * (1f + margin)
    val spanY = viewportHeight / scale * (1f + margin)
    val midX = cx - offsetX / scale
    val midY = cy - offsetY / scale
    val left = maxOf(midX - spanX / 2f, itemLeft)
    val right = minOf(midX + spanX / 2f, itemLeft + itemWidth)
    val top = maxOf(midY - spanY / 2f, itemTop)
    val bottom = minOf(midY + spanY / 2f, itemTop + itemHeight)
    if (right <= left || bottom <= top) return null
    return com.lume.app.domain.page.PageRect(
        (left - itemLeft) / itemWidth,
        (top - itemTop) / itemHeight,
        (right - itemLeft) / itemWidth,
        (bottom - itemTop) / itemHeight,
    )
}

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
