package com.lume.app.ui.theme

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * Single source of truth for motion. Every animated surface in the app uses these durations and
 * easings so screens, sheets, bars and content swaps feel like one system (Material 3 motion).
 */
object Motion {
    /** Small state changes: colors, icons, chips, selection. */
    const val SHORT = 150

    /** Content swaps, bars, list items. */
    const val MEDIUM = 250

    /** Screen transitions and drawers. */
    const val LONG = 350

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** Material 3 standard curve (it was also declared a second time as "Emphasized"). */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Standard)
    fun <T> long(): FiniteAnimationSpec<T> = tween(LONG, easing = Standard)

    // --- Screen transitions -------------------------------------------------------------

    /** Forward navigation: the new screen slides in a little from the end and fades in. */
    val screenEnter: EnterTransition =
        slideInHorizontally(tween(LONG, easing = EmphasizedDecelerate)) { it / 10 } +
            fadeIn(tween(MEDIUM, delayMillis = 50, easing = Standard))

    val screenExit: ExitTransition =
        slideOutHorizontally(tween(LONG, easing = EmphasizedAccelerate)) { -it / 20 } +
            fadeOut(tween(SHORT, easing = Standard))

    // Going back is instant everywhere (back button, arrow and predictive-back gesture alike):
    // the gesture-scrubbed animations never looked the same on every screen.
    val screenPopEnter: EnterTransition = EnterTransition.None

    val screenPopExit: ExitTransition = ExitTransition.None

    /** Switching between bottom-bar tabs: fade-through, no directional slide. */
    val tabEnter: EnterTransition = fadeIn(tween(MEDIUM, delayMillis = 60, easing = Standard))
    val tabExit: ExitTransition = fadeOut(tween(SHORT, easing = Standard))

    // --- Bars & overlays ----------------------------------------------------------------

    val barEnterFromBottom: EnterTransition =
        slideInVertically(tween(MEDIUM, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(MEDIUM))
    val barExitToBottom: ExitTransition =
        slideOutVertically(tween(MEDIUM, easing = EmphasizedAccelerate)) { it } + fadeOut(tween(SHORT))
    val barEnterFromTop: EnterTransition =
        slideInVertically(tween(MEDIUM, easing = EmphasizedDecelerate)) { -it } + fadeIn(tween(MEDIUM))
    val barExitToTop: ExitTransition =
        slideOutVertically(tween(MEDIUM, easing = EmphasizedAccelerate)) { -it } + fadeOut(tween(SHORT))

    val scrimEnter: EnterTransition = fadeIn(tween(LONG, easing = Standard))
    val scrimExit: ExitTransition = fadeOut(tween(MEDIUM, easing = Standard))
    val drawerEnterFromEnd: EnterTransition =
        slideInHorizontally(tween(LONG, easing = EmphasizedDecelerate)) { it }
    val drawerExitToEnd: ExitTransition =
        slideOutHorizontally(tween(MEDIUM, easing = EmphasizedAccelerate)) { it }

    // --- Content ------------------------------------------------------------------------

    /** Loading → content → error swaps inside a screen. */
    fun <S> contentSwap(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        fadeIn(tween(MEDIUM, delayMillis = 40, easing = Standard)) togetherWith
            fadeOut(tween(SHORT, easing = Standard))
    }

    /** Spec for `Modifier.animateItem()` so list inserts/moves match the rest of the app. */
    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = tween(MEDIUM, easing = Standard)
    fun <T> itemFade(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Standard)
}

/**
 * Fade in / out only, no placement animation: for rows that expand or change height, where a
 * sliding neighbour would look like the list is wobbling.
 *
 * Not `Modifier.fadingItem()`: `animateItem` needs the [LazyItemScope] receiver.
 */
@SuppressLint("ModifierFactoryExtensionFunction")
fun LazyItemScope.fadingItem(): Modifier = Modifier.animateItem(
    fadeInSpec = Motion.itemFade(),
    placementSpec = null,
    fadeOutSpec = Motion.itemFade(),
)

/** List item insert / move / remove animation with the shared [Motion] specs. */
@SuppressLint("ModifierFactoryExtensionFunction")
fun LazyItemScope.animatedItem(): Modifier = Modifier.animateItem(
    fadeInSpec = Motion.itemFade(),
    placementSpec = Motion.itemPlacement(),
    fadeOutSpec = Motion.itemFade(),
)
