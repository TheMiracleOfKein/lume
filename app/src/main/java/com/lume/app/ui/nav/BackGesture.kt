package com.lume.app.ui.nav

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** True while the user's finger is on a system back gesture (predictive back). */
val LocalBackGestureActive = compositionLocalOf { false }

private val idle: StateFlow<NavigationEventTransitionState> =
    MutableStateFlow(NavigationEventTransitionState.Idle)

/**
 * Whether a back gesture is in progress, read from the same dispatcher the NavHost listens to.
 * Read it outside the NavHost and hand it down through [LocalBackGestureActive].
 */
@Composable
fun rememberBackGestureActive(): Boolean {
    val owner = LocalNavigationEventDispatcherOwner.current
    val flow = remember(owner) { owner?.navigationEventDispatcher?.transitionState ?: idle }
    val state: State<NavigationEventTransitionState> = flow.collectAsState()
    return state.value is NavigationEventTransitionState.InProgress
}

/**
 * Gives a leaving screen the shape of a card while the back gesture pulls it away: corners
 * matching the display's, and a soft shadow so its edge doesn't cut sharply across the screen
 * underneath. At rest the screen stays square and edge-to-edge.
 *
 * [leaving] is true from the moment the screen starts going away (also while a gesture scrubs it).
 * Corners appear only when that leave started under a gesture, or on a pop ([popped]), so a
 * screen being covered by a forward navigation keeps its square edges.
 */
@Composable
fun BackGestureCard(
    leaving: Boolean,
    popped: Boolean,
    background: Color,
    content: @Composable () -> Unit,
) {
    val gesture = LocalBackGestureActive.current
    // Remember that this leave began under a finger: when the finger lifts, the gesture state goes
    // idle but the screen still has to finish sliding away with its corners.
    // A plain holder, not state: `leaving` and `gesture` already recompose this when they change.
    val byGesture = remember { booleanArrayOf(false) }
    if (!leaving) byGesture[0] = false else if (gesture) byGesture[0] = true
    val card = leaving && (byGesture[0] || popped)

    val maxRadius = rememberDisplayCornerRadius()
    val radius by animateDpAsState(
        targetValue = if (card) maxRadius else 0.dp,
        animationSpec = Motion.short(),
        label = "backGestureCorners",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val px = radius.toPx()
                if (px > 0f) {
                    shape = RoundedCornerShape(px)
                    clip = true
                    shadowElevation = px / 2f
                }
            }
            .background(background),
    ) {
        content()
    }
}

@Composable
private fun rememberDisplayCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val fallback = 28.dp
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@remember fallback
        val insets = view.rootWindowInsets ?: return@remember fallback
        val maxPx = listOf(
            RoundedCorner.POSITION_TOP_LEFT,
            RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT,
        ).mapNotNull { insets.getRoundedCorner(it)?.radius }.maxOrNull()
        if (maxPx != null && maxPx > 0) with(density) { maxPx.toDp() } else fallback
    }
}
