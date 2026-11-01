package com.lume.app.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
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
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Very tall webtoon pages: drawn as stacked slices, since one bitmap that tall will not draw.

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
    val path = if (imageUrl.startsWith("http")) {
        context.imageLoader.diskCache?.openSnapshot(imageUrl)?.use { it.data.toFile().absolutePath }
    } else {
        File(imageUrl).takeIf { it.isFile }?.absolutePath
    } ?: return@runCatching null
    @Suppress("DEPRECATION")
    val decoder = BitmapRegionDecoder.newInstance(path, false) ?: return@runCatching null
    val sample = tileSampleSize(decoder.width, targetWidth)
    if (!needsTiling(decoder.height, sample)) {
        decoder.recycle()
        null
    } else {
        TallSource(decoder, decoder.width, decoder.height, sample, tileBounds(decoder.height, sample))
    }
}.getOrNull()

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
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = source.sample }
                source.decoder.decodeRegion(Rect(0, tile.top, source.width, tile.bottom), options)?.asImageBitmap()
            }.getOrNull()
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
        Spacer(modifier)
    }
}
