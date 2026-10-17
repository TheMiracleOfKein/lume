package com.lume.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space a destination must leave at the bottom for the shell's bottom bar (bar + navigation bar
 * on tab screens, 0 elsewhere). Fixed per destination, so nothing re-pads mid-transition.
 */
val LocalShellBottomPadding = staticCompositionLocalOf { 0.dp }

/** The shell shows a navigation rail (wide screens) instead of the bottom bar. */
val LocalUseRail = staticCompositionLocalOf { false }

/** True while this destination is animating out (including a predictive-back gesture). */
val LocalNavExiting = compositionLocalOf { false }

/**
 * Insets that ignore whether the system bars are currently shown. Coming back from the
 * fullscreen reader, bars reappear; padding that followed them would make screens jump.
 */
object LumeInsets {
    @OptIn(ExperimentalLayoutApi::class)
    val topBar: WindowInsets
        @Composable get() = WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    @OptIn(ExperimentalLayoutApi::class)
    val statusBar: WindowInsets
        @Composable get() = WindowInsets.statusBarsIgnoringVisibility

    @OptIn(ExperimentalLayoutApi::class)
    val scaffold: WindowInsets
        @Composable get() = WindowInsets.systemBarsIgnoringVisibility

    /** Height of the shell's bottom bar without the navigation-bar inset. */
    val BottomBarHeight: Dp = 59.dp

    /** Navigation rail width on wide screens (divider included). */
    val RailWidth: Dp = 81.dp
}
