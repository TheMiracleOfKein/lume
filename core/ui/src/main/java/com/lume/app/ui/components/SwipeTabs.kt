package com.lume.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlin.math.roundToInt

/** The pager state shared by a [SwipeTabRow] and its [SwipeTabPager]. */
@Composable
fun rememberSwipeTabState(selected: Int, pageCount: Int): PagerState =
    rememberPagerState(initialPage = selected.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { pageCount }

/** Share of a tab's width the underline takes. */
private const val INDICATOR_FRACTION = 0.4f

/**
 * Equal-width text tabs over a [SwipeTabPager]. The underline follows the finger: it slides along
 * the row with the pages, and the highlighted label changes as soon as the next page is the nearer one.
 */
@Composable
fun SwipeTabRow(
    labels: List<String>,
    state: PagerState,
    selected: Int,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / labels.size.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val color by animateColorAsState(
                    if (isSelected) AppColors.accentText else AppColors.textSecondary,
                    Motion.short(),
                    label = "tabColor",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onTap(index) })
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        color = color,
                        // Same weight either way: a bolder label is wider and would shift beside the line.
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        // One underline for the row, placed from the pager's live position (read at layout, so the
        // slide costs no recomposition).
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset {
                    // Moving: with the pages. At rest: on the chosen tab (the pager may not be on
                    // screen, e.g. Search while its history shows).
                    val live = if (state.isScrollInProgress) state.currentPage + state.currentPageOffsetFraction else selected.toFloat()
                    val position = live.coerceIn(0f, (labels.size - 1).coerceAtLeast(0).toFloat())
                    val tab = tabWidth.toPx()
                    IntOffset((tab * position + tab * (1 - INDICATOR_FRACTION) / 2).roundToInt(), 0)
                }
                .width(tabWidth * INDICATOR_FRACTION)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AppColors.accentText),
        )
    }
}

/**
 * The content under a row of tabs, swipeable left and right. [selected] (the tabs' own state) stays
 * the source of truth: a tap on a tab slides the pager there, and a swipe reports the page as soon
 * as it becomes the nearer one through [onSelect], so the tabs answer while the finger still moves.
 * The pages next to the current one are kept ready, so a swipe shows them at once. Horizontal
 * scrollers inside a page (chip rows) scroll first; the pager takes the swipe where nothing inside can.
 */
@Composable
fun SwipeTabPager(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageCount: Int = 0,
    state: PagerState = rememberSwipeTabState(selected, pageCount),
    userScrollEnabled: Boolean = true,
    content: @Composable (page: Int) -> Unit,
) {
    val count = state.pageCount
    val target = selected.coerceIn(0, (count - 1).coerceAtLeast(0))
    val currentSelected by rememberUpdatedState(target)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // True while the pager slides to a tapped tab: the pages it passes are not choices.
    val sliding = remember { booleanArrayOf(false) }
    // Coming (back) on screen on another tab than the pages show: go there at once, no slide.
    val placed = remember { booleanArrayOf(false) }
    LaunchedEffect(target, count) {
        if (!placed[0]) {
            placed[0] = true
            if (state.currentPage != target) {
                sliding[0] = true
                try {
                    state.scrollToPage(target)
                } finally {
                    sliding[0] = false
                }
            }
            return@LaunchedEffect
        }
        if (state.currentPage != target) {
            sliding[0] = true
            try {
                state.animateScrollToPage(target, animationSpec = Motion.medium())
            } finally {
                sliding[0] = false
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.collect { page ->
            // A late value from a slide that has already moved on is not a choice either.
            if (!sliding[0] && page == state.currentPage && page != currentSelected) currentOnSelect(page)
        }
    }
    HorizontalPager(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
        verticalAlignment = Alignment.Top,
    ) { page -> content(page) }
}
