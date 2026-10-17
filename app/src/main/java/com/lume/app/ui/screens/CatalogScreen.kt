package com.lume.app.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.PaddingValues
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.LocalShellBottomPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.SegmentTab
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem

/** [browseSourceId]: browse that source (opened from Sources) instead of the active one. */
@Composable
fun CatalogScreen(nav: NavHostController, browseSourceId: String? = null) {
    val container = LocalAppContainer.current
    val vm: CatalogViewModel = viewModel { CatalogViewModel(container, browseSourceId) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val browseName = remember(browseSourceId) {
        browseSourceId?.let { id -> container.sources.sourceById(id)?.manifest?.name ?: id }
    }
    val bookmarks by container.dao.observeBookmarks().collectAsState(initial = emptyList())
    // One list state per tab: switching tabs keeps each list's scroll position.
    val popularList = rememberLazyListState()
    val latestList = rememberLazyListState()

    LaunchedEffect(tab) {
        when (tab) {
            0 -> vm.ensureLoaded(vm.popular)
            1 -> vm.ensureLoaded(vm.latest)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(LumeInsets.statusBar),
    ) {
        if (browseName != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                }
                Text(browseName, color = AppColors.textPrimary, fontSize = 20.sp, modifier = Modifier.weight(1f))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            SegmentTab(stringResource(R.string.catalog_tab_popular), selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            SegmentTab(stringResource(R.string.catalog_tab_updates), selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
            // A browsed source has no library tab: the library is the same for every source.
            if (browseName == null) {
                SegmentTab(stringResource(R.string.catalog_tab_library), selected = tab == 2, modifier = Modifier.weight(1f)) { tab = 2 }
            }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = Motion.contentSwap(),
            label = "catalogTab",
        ) { tabPane ->
            when (tabPane) {
                0, 1 -> CatalogFeedList(
                    vm = vm,
                    feed = if (tabPane == 0) vm.popular else vm.latest,
                    listState = if (tabPane == 0) popularList else latestList,
                    onOpen = { id -> nav.navigate(Routes.title(vm.sourceId, id)) },
                )
                else -> {
                    if (bookmarks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.library_empty), color = AppColors.textSecondary)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalShellBottomPadding.current)) {
                            items(bookmarks, key = { "${it.sourceId}:${it.titleId}" }) { item ->
                                TitleListRow(
                                    modifier = animatedItem(),
                                    title = item.titleName,
                                    coverUrl = item.coverUrl,
                                    typeLabel = item.typeLabel,
                                    onClick = { nav.navigate(Routes.title(item.sourceId, item.titleId)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CatalogFeedList(
    vm: CatalogViewModel,
    feed: CatalogFeed,
    listState: LazyListState,
    onOpen: (String) -> Unit,
) {
    val shouldLoadMore by remember(feed, listState) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            feed.hasNext && !feed.loadingMore && !feed.loading && last >= feed.items.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore(feed)
    }
    AnimatedLoadState(
        state = loadStateOf(feed.loading && feed.items.isEmpty(), feed.error.takeIf { feed.items.isEmpty() }),
        modifier = Modifier.fillMaxSize(),
    ) { pane ->
        when (pane) {
            LoadState.Loading -> LoadingBox()
            LoadState.Error -> ErrorRetryBox(
                message = feed.error.orEmpty(),
                onRetry = { vm.ensureLoaded(feed, force = true) },
                onCloudflare = feed.cloudflareUri?.let { { vm.solveChallenge(feed) } },
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalShellBottomPadding.current),
            ) {
                items(feed.items, key = { it.id }) { item ->
                    TitleListRow(
                        modifier = animatedItem(),
                        title = item.title,
                        coverUrl = item.coverUrl,
                        subtitle = item.latestChapter ?: item.updatedLabel,
                        typeLabel = item.typeLabel,
                        onClick = { onOpen(item.id) },
                    )
                }
                if (feed.loadingMore) {
                    item(key = "loading-more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AppColors.accent)
                        }
                    }
                }
            }
        }
    }
}
