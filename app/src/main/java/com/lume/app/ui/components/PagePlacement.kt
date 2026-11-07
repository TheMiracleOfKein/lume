package com.lume.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.lume.app.domain.page.PagePlacement
import kotlin.math.roundToInt

/**
 * Lays the child out at [placement] (pixels in the parent), or fills the parent when null. The
 * child keeps its full size; the parent clips what lies outside.
 */
internal fun Modifier.placedAt(placement: PagePlacement?): Modifier = if (placement == null) {
    this.fillMaxSize()
} else {
    layout { measurable, _ ->
        val placeable = measurable.measure(
            Constraints.fixed(placement.width.roundToInt().coerceAtLeast(1), placement.height.roundToInt().coerceAtLeast(1)),
        )
        layout(placeable.width, placeable.height) {
            placeable.place(placement.left.roundToInt(), placement.top.roundToInt())
        }
    }
}
