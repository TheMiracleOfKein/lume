package com.lume.app.ui.components

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.filter

/**
 * The content under a row of tabs, swipeable left and right. [selected] (the tabs' own state) stays
 * the source of truth: a tap on a tab slides the pager there, a swipe reports the page it comes to
 * rest on through [onSelect]. Horizontal scrollers inside a page (chip rows) scroll first; the pager
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
    // True while the pager slides to a tapped tab: that scroll is not the user's and reports nothing.
    val sliding = remember { booleanArrayOf(false) }
    LaunchedEffect(target, pageCount) {
        if (pagerState.currentPage != target) {
            sliding[0] = true
            try {
                pagerState.animateScrollToPage(target)
            } finally {
                sliding[0] = false
            }
        }
    }
    // A swipe: once the pager has stopped, its page becomes the selected tab. (Watching settledPage
    // alone misses swipes: it changes while the scroll still counts as running.)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.filter { !it }.collect {
            val page = pagerState.currentPage
            if (!sliding[0] && page != currentSelected) currentOnSelect(page)
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        verticalAlignment = Alignment.Top,
    ) { page -> content(page) }
}
