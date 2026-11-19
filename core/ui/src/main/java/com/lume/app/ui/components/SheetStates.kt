package com.lume.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** A drag past this closes the sheet when the finger lets go slowly. */
private val DismissDistance = 56.dp

/**
 * Only a flick faster than this decides by direction. Material's own 125 dp/s is less than the
 * small backwards twitch of a finger lifting after a slow drag, which threw the sheet back up.
 */
private val FlingVelocity = 700.dp

/**
 * The state of a full-height sheet that closes as soon as it is dragged down a little and let go,
 * however slowly: the reader's settings, chapters and bookmarks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberEasyDismissSheetState(): SheetState {
    val density = LocalDensity.current
    val positional = { with(density) { DismissDistance.toPx() } }
    val velocity = { with(density) { FlingVelocity.toPx() } }
    return rememberSaveable(
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = positional,
            velocityThreshold = velocity,
            confirmValueChange = { true },
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = positional,
            velocityThreshold = velocity,
            initialValue = SheetValue.Hidden,
        )
    }
}

/** Room left above a capped sheet for its drag handle and a strip of the screen behind it. */
private val SheetTopRoom = 72.dp

/**
 * The only bottom sheet in the app: every sheet uses this, never Material's `ModalBottomSheet`
 * directly (`BottomSheetRuleTest` fails the build otherwise).
 *
 * Material's sheet pads its top by the part of the status bar it overlaps. A sheet tall enough to
 * reach the status bar then changes height with its own position, re-anchors, moves, and twitches up
 * and down without end, even with no finger on it, most often after a slow drag or a hard fling. The
 * filter sheet (v0.9.14, v0.9.16) and the reader sheets (v1.5.2) all hit it. This sheet takes only
 * the bottom insets and caps its content below the status bar, so that padding is always zero. The
 * status bar counts even while the reader hides it, so the cap doesn't change when the bars come and
 * go. Content taller than the cap must scroll inside (`verticalScroll` or a lazy list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumeBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight()), content = content)
    }
}

/** The tallest a [LumeBottomSheet]'s content may be: the window less the status bar, bottom insets and [SheetTopRoom]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun sheetMaxHeight(): Dp {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val topInset = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }
    val bottomInset = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    return (windowHeight - topInset - bottomInset - SheetTopRoom).coerceAtLeast(200.dp)
}
