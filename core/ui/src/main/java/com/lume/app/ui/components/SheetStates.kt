package com.lume.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
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
