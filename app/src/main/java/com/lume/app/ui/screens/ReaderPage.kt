package com.lume.app.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lume.app.R
import com.lume.app.domain.ComicPage
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch
import java.io.File

// One reader page: loading, errors, retry and pinch zoom.

private class ReaderZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(raw: Offset, s: Float = scale): Offset {
        if (s <= 1.01f || viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val maxX = viewport.width * (s - 1f) / 2f
        val maxY = viewport.height * (s - 1f) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    fun zoomBy(zoomChange: Float, focal: Offset) {
        // Slight amplification so pinch feels responsive without jumping.
        val amplified = 1f + (zoomChange - 1f) * 1.35f
        val old = scale
        val next = (old * amplified).coerceIn(1f, 5f)
        if (next <= 1.01f) {
            reset()
            return
        }
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        offset = if (old <= 1.01f) {
            (center - focal) * (next - 1f)
        } else {
            val ratio = next / old
            offset * ratio + (focal - center) * (1f - ratio)
        }
        scale = next
        offset = clamp(offset, next)
    }

    /** Read through this (not [scale]) in composition: it changes only when crossing 1x. */
    val isZoomed: Boolean by derivedStateOf { scale > 1.01f }

    /** Double tap: glide to [target] around [focal] (or back to 1x) instead of jumping. */
    suspend fun animateZoomTo(target: Float, focal: Offset) {
        val fromScale = scale
        val fromOffset = offset
        val toScale = target.coerceIn(1f, 5f)
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val toOffset = when {
            toScale <= 1.01f -> Offset.Zero
            fromScale <= 1.01f -> clamp((center - focal) * (toScale - 1f), toScale)
            else -> {
                val ratio = toScale / fromScale
                clamp(fromOffset * ratio + (focal - center) * (1f - ratio), toScale)
            }
        }
        animate(0f, 1f, animationSpec = Motion.medium()) { t, _ ->
            scale = fromScale + (toScale - fromScale) * t
            offset = fromOffset + (toOffset - fromOffset) * t
        }
        if (toScale <= 1.01f) reset()
    }

    suspend fun toggleZoom(focal: Offset, target: Float = 2.5f) {
        animateZoomTo(if (scale > 1.1f) 1f else target, focal)
    }
}

@Composable
internal fun ReaderZoomFrame(
    zoomEnabled: Boolean,
    resetKey: Any?,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (zoomModifier: Modifier, isZoomed: () -> Boolean) -> Unit,
) {
    val state = remember { ReaderZoomState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resetKey) { state.reset() }

    Box(
        modifier = modifier
            .onSizeChanged { state.viewport = it }
            // Keys must NOT include scale — restarting mid-pinch makes zoom weak/jerky.
            .pointerInput(zoomEnabled) {
                if (zoomEnabled) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { pos -> scope.launch { state.toggleZoom(pos) } },
                    )
                } else {
                    // Without a double-tap handler a single tap fires at once (no ~300 ms wait).
                    detectTapGestures(onTap = { onSingleTap() })
                }
            }
            .pointerInput(zoomEnabled) {
                if (!zoomEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                                    state.zoomBy(zoomChange, centroid)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            pressed.size == 1 && state.scale > 1.01f -> {
                                val change = pressed[0]
                                val pan = change.positionChange()
                                if (pan != Offset.Zero) {
                                    state.offset = state.clamp(state.offset + pan)
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        // Scale and offset are read only inside graphicsLayer, so pinching redraws the layer
        // without recomposing the list underneath.
        content(
            Modifier.graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x
                translationY = state.offset.y
                transformOrigin = TransformOrigin.Center
            },
            { state.isZoomed },
        )
    }
}

@Composable
internal fun ReaderPageImage(
    page: ComicPage,
    /** Width / height when known (from this session or saved progress); sizes the placeholder. */
    aspect: Float?,
    onAspect: (Float) -> Unit,
    contentScale: ContentScale = ContentScale.FillWidth,
    zoomEnabled: Boolean = false,
    fillViewport: Boolean = false,
    /** Single tap at (x, width); needed because the double-tap detector consumes taps. */
    onTap: ((Float, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    var attempt by remember(page.imageUrl) { mutableIntStateOf(0) }
    var failed by remember(page.imageUrl) { mutableStateOf(false) }
    val model = remember(page.imageUrl, attempt) {
        ImageRequest.Builder(context)
            .data(if (page.imageUrl.startsWith("http")) page.imageUrl else File(page.imageUrl))
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // A new parameter makes Coil treat a retry as a new request.
            .setParameter("attempt", attempt, memoryCacheKey = null)
            .build()
    }
    val state = remember(page.imageUrl) { ReaderZoomState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .then(
                when {
                    fillViewport -> Modifier.fillMaxSize()
                    // Real height before the bitmap arrives: nothing above the reading spot moves.
                    aspect != null -> Modifier.fillMaxWidth().aspectRatio(aspect)
                    // Never seen this page: reserve a typical height (also kept on error).
                    else -> Modifier.fillMaxWidth().heightIn(min = 420.dp)
                },
            )
            .background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            onSuccess = { success ->
                failed = false
                val size = success.painter.intrinsicSize
                if (size.isSpecified && size.width > 0f && size.height > 0f) onAspect(size.width / size.height)
            },
            onError = { failed = true },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillViewport || aspect != null) Modifier.fillMaxSize() else Modifier)
                .onSizeChanged { state.viewport = it }
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (zoomEnabled) {
                        Modifier
                            .pointerInput(page.imageUrl, onTap) {
                                detectTapGestures(
                                    onTap = onTap?.let { tap -> { pos -> tap(pos.x, size.width) } },
                                    onDoubleTap = { pos -> scope.launch { state.toggleZoom(pos) } },
                                )
                            }
                            .pointerInput(page.imageUrl) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        when {
                                            pressed.size >= 2 -> {
                                                val zoomChange = event.calculateZoom()
                                                val centroid = event.calculateCentroid(useCurrent = true)
                                                if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                                                    state.zoomBy(zoomChange, centroid)
                                                }
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            }
                                            pressed.size == 1 && state.scale > 1.01f -> {
                                                val change = pressed[0]
                                                val pan = change.positionChange()
                                                if (pan != Offset.Zero) {
                                                    state.offset = state.clamp(state.offset + pan)
                                                    change.consume()
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
            contentScale = contentScale,
        )
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.page_load_failed), color = AppColors.textSecondary, fontSize = 13.sp)
                TextButton(onClick = { failed = false; attempt++ }) {
                    Text(stringResource(R.string.action_retry), color = AppColors.accent)
                }
            }
        }
    }
}
