package com.lume.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.LocalShellBottomPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.animatedItem

@Composable
fun HistoryScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(container) }
    val history by vm.history.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(LumeInsets.statusBar),
        contentAlignment = Alignment.Center,
    ) {
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                color = AppColors.textSecondary,
                fontSize = 15.sp,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalShellBottomPadding.current)) {
                items(history, key = { "${it.sourceId}:${it.titleId}" }) { item ->
                    TitleListRow(
                        modifier = animatedItem(),
                        title = item.titleName,
                        coverUrl = item.coverUrl,
                        subtitle = item.chapterName,
                        typeLabel = item.typeLabel,
                        onClick = {
                            nav.openReader(item.sourceId, item.titleId, item.chapterId, item.pageIndex, item.pageOffset)
                        },
                    )
                }
            }
        }
    }
}
