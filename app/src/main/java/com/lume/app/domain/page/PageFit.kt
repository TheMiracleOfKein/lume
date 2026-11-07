package com.lume.app.domain.page

/** Where the whole page goes, in viewport pixels, so that a region of it fits the viewport. */
data class PagePlacement(val left: Float, val top: Float, val width: Float, val height: Float)

object PageFit {

    /**
     * Places a page of [imageAspect] (width / height) so that [region] (fractions of the page) is
     * as large as fits a [viewportWidth] x [viewportHeight] viewport, centred. The rest of the
     * page lies outside the viewport and is clipped away.
     */
    fun placeRegion(viewportWidth: Float, viewportHeight: Float, imageAspect: Float, region: PageRect): PagePlacement? {
        if (viewportWidth <= 0f || viewportHeight <= 0f || imageAspect <= 0f) return null
        if (region.width <= 0f || region.height <= 0f) return null
        val regionAspect = imageAspect * region.width / region.height
        val viewportAspect = viewportWidth / viewportHeight
        val (shownWidth, shownHeight) = if (regionAspect > viewportAspect) {
            viewportWidth to viewportWidth / regionAspect
        } else {
            viewportHeight * regionAspect to viewportHeight
        }
        val width = shownWidth / region.width
        val height = shownHeight / region.height
        val left = (viewportWidth - shownWidth) / 2f - region.left * width
        val top = (viewportHeight - shownHeight) / 2f - region.top * height
        return PagePlacement(left, top, width, height)
    }

    /** [a] inside [b]: both as fractions of the same page. Null when they don't overlap. */
    fun intersect(a: PageRect, b: PageRect): PageRect? {
        val r = PageRect(maxOf(a.left, b.left), maxOf(a.top, b.top), minOf(a.right, b.right), minOf(a.bottom, b.bottom))
        return r.takeIf { it.width > 0f && it.height > 0f }
    }
}
