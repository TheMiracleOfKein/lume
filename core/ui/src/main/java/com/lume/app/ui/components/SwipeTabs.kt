package com.lume.app.ui.components

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The content under a row of tabs, swipeable left and right. [selected] (the tabs' own state) stays
 * the source of truth: a tap on a tab slides the pager there, a swipe reports the page it settles
 * on through [onSelect]. Horizontal scrollers inside a page (chip rows) scroll first; the pager
 * takes the swipe only where nothing inside can.
 */
@Composable
fun SwipeTabPager(
    selected: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    content: @Composable (page: Int) -> Unit,
) {
    val target = selected.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = target) { pageCount }
    val currentSelected by rememberUpdatedState(target)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // A tap on a tab (or a change from outside): slide there.
    LaunchedEffect(target, pageCount) {
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    // A swipe: tell the tabs once the pager comes to rest.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != currentSelected && !pagerState.isScrollInProgress) currentOnSelect(page)
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        verticalAlignment = Alignment.Top,
    ) { page -> content(page) }
}
