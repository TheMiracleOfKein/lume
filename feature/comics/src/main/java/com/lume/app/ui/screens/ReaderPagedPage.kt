package com.lume.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lume.app.R
import com.lume.app.domain.pageData
import com.lume.app.domain.ComicPage
import com.lume.app.domain.page.PageFit
import com.lume.app.domain.page.PageRect
import com.lume.app.ui.components.placedAt
import com.lume.app.ui.theme.AppColors
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.zoomable
import kotlin.math.roundToInt

// Pages in the paged modes: subsampled decoding and sharp zoom (Telephoto), a region of the page
// (cropped borders, one half of a wide page) filling the screen, two-page spreads, loading and retry.

/** Double tap zooms to this factor of the page's own size (or the fitted size, if larger). */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Pinch zoom limit, as in the webtoon reader. */
internal const val PAGED_MAX_ZOOM = 5f

/** The rectangle of a page that [part] shows; null for the whole page. */
internal fun PagePart.region(): PageRect? = when (this) {
    PagePart.Whole -> null
    PagePart.LeftHalf -> PageRect(0f, 0f, 0.5f, 1f)
    PagePart.RightHalf -> PageRect(0.5f, 0f, 1f, 1f)
}

/** Load state of one page image, with a request that starts over on retry. */
private class PageLoad {
    var attempt by mutableIntStateOf(0)
    var failed by mutableStateOf(false)
    var loading by mutableStateOf(true)
}

@Composable
private fun rememberPageRequest(page: ComicPage, onAspect: (Float) -> Unit): Pair<ImageRequest, PageLoad> {
    val context = LocalContext.current
    val load = remember(page.imageUrl) { PageLoad() }
    val aspectCallback by rememberUpdatedState(onAspect)
    val request = remember(page.imageUrl, load.attempt) {
        ImageRequest.Builder(context)
            .data(pageData(page.imageUrl))
            .crossfade(false)
            // A different key makes Coil treat a retry as a new request.
            .apply { if (load.attempt > 0) memoryCacheKeyExtra("attempt", load.attempt.toString()) }
            .listener(
                onStart = { load.loading = true },
                onSuccess = { _, result ->
                    load.failed = false
                    load.loading = false
                    val image = result.image
                    if (image.width > 0 && image.height > 0) aspectCallback(image.width.toFloat() / image.height)
                },
                onError = { _, _ ->
                    load.failed = true
                    load.loading = false
                },
            )
            .build()
    }
    return request to load
}

/** Spinner while loading; message and Retry on failure. */
@Composable
private fun PageLoadOverlay(load: PageLoad) {
    if (load.loading && !load.failed) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AppColors.accent, strokeWidth = 3.dp)
    }
    if (load.failed) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.page_load_failed), color = AppColors.textSecondary, fontSize = 13.sp)
            TextButton(onClick = { load.failed = false; load.attempt++ }) {
                Text(stringResource(R.string.action_retry), color = AppColors.accentText)
            }
        }
    }
}

@Composable
internal fun PagedPageImage(
    page: ComicPage,
    /** Width / height when known; needed to place a [region]. */
    aspect: Float?,
    onAspect: (Float) -> Unit,
    /** The part of the page to fill the screen with (crop, half page); null = the whole page. */
    region: PageRect?,
    /** Pinch and double-tap zoom with sharp detail; off = instant single taps, no zoom. */
    zoomEnabled: Boolean,
    /** Guided panel view moves [zoomState] even when the zoom gestures are off. */
    guided: Boolean,
    /** The page's zoom, hoisted so guided panel view can move it. */
    zoomState: ZoomableState,
    /** Single tap at (x, viewport width). */
    onTap: (Float, Int) -> Unit,
    colorFilter: ColorFilter?,
) {
    val (request, load) = rememberPageRequest(page, onAspect)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val placement = if (region != null && aspect != null) PageFit.placeRegion(viewportW, viewportH, aspect, region) else null
        val shift = placement?.left ?: 0f
        val scale = if (placement != null) ContentScale.FillBounds else ContentScale.Fit
        if (zoomEnabled || guided) {
            ZoomableAsyncImage(
                model = request,
                contentDescription = null,
                state = rememberZoomableImageState(zoomState),
                modifier = Modifier.placedAt(placement),
                gestures = if (zoomEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
                colorFilter = colorFilter,
                contentScale = scale,
                onClick = { offset -> onTap(offset.x + shift, viewportW.roundToInt()) },
                onDoubleClick = if (zoomEnabled) {
                    DoubleClickToZoomListener.cycle(maxZoomFactor = DOUBLE_TAP_ZOOM)
                } else {
                    DoubleClickToZoomListener { _, _ -> }
                },
            )
        } else {
            // No zoom: a plain image, and single taps fire at once (no wait for a double tap).
            val currentTap by rememberUpdatedState(onTap)
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier
                    .placedAt(placement)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { o -> currentTap(o.x + shift, viewportW.roundToInt()) })
                    },
                contentScale = scale,
                colorFilter = colorFilter,
            )
        }
        PageLoadOverlay(load)
    }
}

/**
 * Two pages side by side with one shared zoom: [first] on the reading side ([rightToLeft]: first
 * on the right), the pair meeting in the middle. Spread pages are plain bitmaps (no subsampling):
 * each is at most half the screen wide.
 */
@Composable
internal fun SpreadPages(
    first: ComicPage,
    second: ComicPage?,
    rightToLeft: Boolean,
    zoomEnabled: Boolean,
    zoomState: ZoomableState,
    onAspect: (ComicPage, Float) -> Unit,
    onTap: (Float, Int) -> Unit,
    colorFilter: ColorFilter?,
) {
    var width by remember { mutableIntStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width }
            .zoomable(
                state = zoomState,
                gestures = if (zoomEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
                onClick = { offset -> onTap(offset.x, width) },
                onDoubleClick = if (zoomEnabled) DoubleClickToZoomListener.cycle(maxZoomFactor = DOUBLE_TAP_ZOOM) else null,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (second == null) {
            SpreadPage(first, Alignment.Center, onAspect, colorFilter, Modifier.fillMaxSize())
        } else {
            val left = if (rightToLeft) second else first
            val right = if (rightToLeft) first else second
            SpreadPage(left, Alignment.CenterEnd, onAspect, colorFilter, Modifier.weight(1f).fillMaxSize())
            SpreadPage(right, Alignment.CenterStart, onAspect, colorFilter, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun SpreadPage(
    page: ComicPage,
    alignment: Alignment,
    onAspect: (ComicPage, Float) -> Unit,
    colorFilter: ColorFilter?,
    modifier: Modifier,
) {
    val (request, load) = rememberPageRequest(page) { onAspect(page, it) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = alignment,
            colorFilter = colorFilter,
        )
        PageLoadOverlay(load)
    }
}
