package com.lume.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lume.app.ui.theme.AppColors

/** Pull-to-refresh indicator in app colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumeRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        color = AppColors.accent,
        containerColor = AppColors.card,
    )
}
