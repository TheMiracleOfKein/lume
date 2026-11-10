package com.lume.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lume.app.data.local.pdf.PdfPageUrl
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import com.lume.app.domain.page.PageRect
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Very tall webtoon pages: drawn as stacked slices over the normal (shrunk) page bitmap, since
// one bitmap that tall will not draw.

internal class TallSource(
    val decoder: BitmapRegionDecoder,
    val width: Int,
    val height: Int,
    val sample: Int,
    val tiles: List<TileBounds>,
)

/**
 * Opens the page's original file (the downloaded copy, or Coil's disk cache) for slice decoding.
 * Null when it isn't on disk yet, can't be region-decoded, or doesn't actually need slicing.
 */
internal fun openTallSource(context: Context, imageUrl: String, targetWidth: Int): TallSource? = runCatching {
    val decoder = openRegionDecoder(context, imageUrl) ?: return@runCatching null
    val sample = tileSampleSize(decoder.width, targetWidth)
    if (!needsTiling(decoder.height, sample)) {
        decoder.recycle()
        null
    } else {
        TallSource(decoder, decoder.width, decoder.height, sample, tileBounds(decoder.height, sample))
    }
}.getOrNull()

/**
 * [region] of [decoder] at [sample], moved into GPU memory (a hardware bitmap, like Coil's pages).
 * A plain bitmap would be copied to the GPU on its first draw, and again whenever the GPU cache
 * dropped it while the page sat still: several MB per slice in the first frame of the next drag,
 * which shows as a jump. Falls back to the plain bitmap when the copy isn't possible.
 */
private fun decodeForDrawing(decoder: BitmapRegionDecoder, region: Rect, sample: Int): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val plain = decoder.decodeRegion(region, options) ?: return null
    val gpu = runCatching { plain.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
    return if (gpu != null) {
        plain.recycle()
        gpu.asImageBitmap()
    } else {
        plain.asImageBitmap()
    }
}

/** The page's original file on disk: the downloaded copy, or Coil's disk cache. */
private fun originalPath(context: Context, imageUrl: String): String? = if (PdfPageUrl.isPdfPage(imageUrl)) {
    null // rendered on demand, no file to slice
} else if (imageUrl.startsWith("http")) {
    context.imageLoader.diskCache?.openSnapshot(imageUrl)?.use { it.data.toFile().absolutePath }
} else {
    File(imageUrl).takeIf { it.isFile }?.absolutePath
}

/** A region decoder over the page's original, or null when it isn't on disk or can't be read. */
private fun openRegionDecoder(context: Context, imageUrl: String): BitmapRegionDecoder? = runCatching {
    val path = originalPath(context, imageUrl) ?: return@runCatching null
    @Suppress("DEPRECATION")
    BitmapRegionDecoder.newInstance(path, false)
}.getOrNull()

/**
 * Sharp detail over a zoomed webtoon page: [region] (fractions of the page) decoded from the
 * original at about [shownWidthPx] (the page's on-screen width at the current zoom), drawn over
 * the normal bitmap. Only what is on screen is decoded, and it is dropped when the zoom ends.
 */
@Composable
internal fun PageDetail(imageUrl: String, region: PageRect, shownWidthPx: Int, colorFilter: ColorFilter?) {
    val context = LocalContext.current
    // The last decoded detail stays on screen until the next one is ready: dropping it while the
    // new one decodes would flash the blurry page in between.
    var shown by remember(imageUrl) { mutableStateOf<DecodedDetail?>(null) }
    LaunchedEffect(imageUrl, region, shownWidthPx) {
        val decoded = withContext(Dispatchers.IO) { decodeDetail(context, imageUrl, region, shownWidthPx) }
        if (decoded != null) shown = decoded
    }
    val detail = shown ?: return
    Canvas(Modifier.fillMaxSize()) {
        // Drawn over exactly the source pixels it was decoded from, without rounding to whole
        // screen pixels: zoomed in, a rounding error of half a pixel shows as a visible jump.
        val r = detail.region
        val image = detail.bitmap
        withTransform({
            translate(r.left * size.width, r.top * size.height)
            scale(r.width * size.width / image.width, r.height * size.height / image.height, pivot = Offset.Zero)
        }) {
            drawImage(image, colorFilter = colorFilter)
        }
    }
}

/** A decoded detail bitmap and the part of the page (fractions) its pixels cover. */
private class DecodedDetail(val bitmap: ImageBitmap, val region: PageRect)

private fun decodeDetail(context: Context, imageUrl: String, region: PageRect, shownWidthPx: Int): DecodedDetail? {
    val decoder = openRegionDecoder(context, imageUrl) ?: return null
    return try {
        val w = decoder.width
        val h = decoder.height
        val rect = Rect(
            (region.left * w).toInt().coerceIn(0, w - 1),
            (region.top * h).toInt().coerceIn(0, h - 1),
            (region.right * w).roundToInt().coerceIn(1, w),
            (region.bottom * h).roundToInt().coerceIn(1, h),
        )
        if (rect.width() <= 0 || rect.height() <= 0) return null
        // Downsample only as far as the zoomed screen still gets every pixel it can show.
        val shownRegionWidth = (shownWidthPx * region.width).toInt().coerceAtLeast(1)
        var sample = tileSampleSize(rect.width(), shownRegionWidth)
        // One drawable bitmap: keep both sides under the GPU limit.
        while (rect.height() / sample > MAX_DRAW_PX || rect.width() / sample > MAX_DRAW_PX) sample *= 2
        val bitmap = runCatching { decodeForDrawing(decoder, rect, sample) }.getOrNull() ?: return null
        // The rectangle actually decoded (whole source pixels), not the requested fractions.
        DecodedDetail(
            bitmap,
            PageRect(rect.left.toFloat() / w, rect.top.toFloat() / h, rect.right.toFloat() / w, rect.bottom.toFloat() / h),
        )
    } finally {
        decoder.recycle()
    }
}

@Composable
internal fun TiledPage(source: TallSource, colorFilter: ColorFilter?, modifier: Modifier) {
    // Each slice takes its share of the page's height, so together they fill it exactly.
    Column(modifier = modifier.fillMaxSize()) {
        source.tiles.forEach { tile ->
            key(tile.top) {
                TileImage(source, tile, colorFilter, Modifier.fillMaxWidth().weight(tile.height.toFloat()))
            }
        }
    }
}

@Composable
private fun TileImage(source: TallSource, tile: TileBounds, colorFilter: ColorFilter?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, source, tile) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeForDrawing(source.decoder, Rect(0, tile.top, source.width, tile.bottom), source.sample) }
                .getOrNull()
        }
    }
    val ready = bitmap
    if (ready != null) {
        Image(
            bitmap = ready,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
        )
    } else {
        // Still decoding (or failed): empty, so the normal page bitmap underneath shows through.
        Spacer(modifier)
    }
}
