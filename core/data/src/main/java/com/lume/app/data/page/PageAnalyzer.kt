package com.lume.app.data.page

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.lume.app.domain.pageData
import com.lume.app.domain.page.LumaGrid
import com.lume.app.domain.page.PageAnalysis
import com.lume.app.domain.page.PageRect
import com.lume.app.domain.page.PanelOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What [PageAnalysis] found on one page. */
data class PageLayout(
    /** Width / height of the page. */
    val aspect: Float,
    /** The content inside a plain border; null when there is no border worth cropping. */
    val contentBounds: PageRect?,
    val panelsLeftToRight: List<PageRect>,
    val panelsRightToLeft: List<PageRect>,
    /** Horizontal bands only (webtoon strips). */
    val bands: List<PageRect>,
) {
    fun panels(order: PanelOrder): List<PageRect> = when (order) {
        PanelOrder.LeftToRight -> panelsLeftToRight
        PanelOrder.RightToLeft -> panelsRightToLeft
        PanelOrder.Vertical -> bands
    }

    /** The panel holding a saved spot at [y], for bookmark thumbnails; see [PageAnalysis.spotPanel]. */
    fun spotPanel(y: Float): PageRect? = PageAnalysis.spotPanel(panelsLeftToRight, bands, aspect, y)
}

/**
 * Analyses pages from a small copy (a few hundred px wide, through Coil's disk cache or the
 * downloaded file) and keeps the results in memory. Nothing is stored or synced.
 */
class PageAnalyzer(
    private val context: Context,
    private val imageLoader: () -> ImageLoader,
) {
    private val cache = LruCache<String, PageLayout>(CACHE_PAGES)

    /** The layout when this page was analysed before in this process. */
    fun cached(imageUrl: String): PageLayout? = cache.get(imageUrl)

    /** The page's layout, or null when the page can't be loaded. */
    suspend fun analyze(imageUrl: String): PageLayout? {
        cache.get(imageUrl)?.let { return it }
        val bitmap = load(imageUrl) ?: return null
        val layout = withContext(Dispatchers.Default) { runCatching { compute(bitmap) }.getOrNull() } ?: return null
        cache.put(imageUrl, layout)
        return layout
    }

    private suspend fun load(imageUrl: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(pageData(imageUrl))
            // Fit inside GRID_PX wide; tall strips get up to 8x that in height so their bands survive.
            .size(GRID_PX, GRID_PX * 8)
            .scale(Scale.FIT)
            .allowHardware(false)
            // Small copies would only push real pages out of the memory cache.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = imageLoader().execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap()
    }

    private fun compute(bitmap: Bitmap): PageLayout {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val grid = LumaGrid.fromArgb(pixels, w, h)
        return PageLayout(
            aspect = w.toFloat() / h,
            contentBounds = PageAnalysis.contentBounds(grid),
            panelsLeftToRight = PageAnalysis.panels(grid, PanelOrder.LeftToRight),
            panelsRightToLeft = PageAnalysis.panels(grid, PanelOrder.RightToLeft),
            bands = PageAnalysis.panels(grid, PanelOrder.Vertical),
        )
    }

    private companion object {
        const val GRID_PX = 400
        const val CACHE_PAGES = 300
    }
}
