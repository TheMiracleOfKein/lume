package com.lume.app.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.ui.components.BrightnessCurve
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Width of the strip along the left edge that takes the brightness swipe. */
private val EDGE_WIDTH = 40.dp

/** A swipe over this share of the screen height goes from darkest to brightest. */
private const val FULL_SWIPE = 0.6f

/** How long the brightness level stays shown after the finger lifts. */
private const val INDICATOR_LINGER_MS = 900L

/**
 * The brightness after a vertical drag of [dy] pixels (negative is up) on a screen [height] pixels
 * tall, starting at [start]: up brightens. Kept in 0.01..1.
 */
internal fun brightnessAfterDrag(start: Float, dy: Float, height: Float): Float {
    if (height <= 0f) return start.coerceIn(NovelReaderSettings.BRIGHTNESS_MIN, 1f)
    return (start - dy / (height * FULL_SWIPE)).coerceIn(NovelReaderSettings.BRIGHTNESS_MIN, 1f)
}

/** The system brightness as a shown level 0..1, roughly (the stored value is linear 0..255), or 0.5 when it can't be read. */
private fun systemBrightness(context: Context): Float =
    runCatching { BrightnessCurve.windowToLevel(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f) }
        .getOrDefault(0.5f)
        .coerceIn(NovelReaderSettings.BRIGHTNESS_MIN, 1f)

/**
 * Wraps the reader's text: a vertical swipe that starts on the left edge changes the brightness
 * ([onLive] while dragging, [onDone] once on release) and shows the level for a moment. Taps and
 * sideways moves on the edge go on to the text as before; only a vertical drag is taken.
 */
@Composable
internal fun BrightnessEdge(
    enabled: Boolean,
    brightness: Float,
    palette: NovelPalette,
    onLive: (Float) -> Unit,
    onDone: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val current by rememberUpdatedState(brightness)
    val live by rememberUpdatedState(onLive)
    val done by rememberUpdatedState(onDone)
    /** The level on screen while swiping (and briefly after), else null. */
    var shown by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging, shown) {
        if (!dragging && shown != null) {
            delay(INDICATOR_LINGER_MS)
            shown = null
        }
    }
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.x > EDGE_WIDTH.toPx()) return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                var moved = Offset.Zero
                var taking = false
                var startY = 0f
                var from = 0f
                var value = 0f
                while (true) {
                    // The Initial pass sees the move before the list or pager below, so a taken drag never scrolls.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (taking) {
                            change.consume()
                            dragging = false
                            done(value)
                        }
                        break
                    }
                    if (!taking) {
                        moved += change.positionChange()
                        if (abs(moved.y) > slop && abs(moved.y) > abs(moved.x)) {
                            taking = true
                            dragging = true
                            startY = change.position.y
                            from = if (current < 0f) systemBrightness(context) else current
                            value = from
                            shown = value
                        } else if (abs(moved.x) > slop) {
                            break
                        }
                    }
                    if (taking) {
                        change.consume()
                        value = brightnessAfterDrag(from, change.position.y - startY, size.height.toFloat())
                        shown = value
                        live(value)
                    }
                }
                if (dragging) {
                    // Cancelled (another finger, the screen went away): keep what was set.
                    dragging = false
                    done(value)
                }
            }
        },
    ) {
        content()
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = EDGE_WIDTH + 8.dp),
        ) {
            val level = ((shown ?: current.coerceAtLeast(0f)) * 100).roundToInt()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.bar.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.LightMode, contentDescription = null, tint = palette.text, modifier = Modifier.size(18.dp))
                Text("$level%", color = palette.text, fontSize = 14.sp)
            }
        }
    }
}
