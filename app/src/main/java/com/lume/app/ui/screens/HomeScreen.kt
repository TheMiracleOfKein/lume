package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.ProgressEntity
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.HomeFeed
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.CoverCard
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.QuickSearchBar
import com.lume.app.ui.components.SectionHeader
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.animatedItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val resources = LocalResources.current
    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sourceId by remember { mutableStateOf("") }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val history by LumeApp.instance.dao.observeHistory().collectAsState(initial = emptyList())
    val networkEpoch by LumeApp.instance.http.networkEpoch.collectAsState()
    val online by LumeApp.instance.network.online.collectAsState()
    val downloads by LumeApp.instance.dao.observeDownloads().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val offlineTitles = remember(downloads) {
        downloads
            .groupBy { it.sourceId to it.titleId }
            .map { (key, items) ->
                val first = items.first()
                Triple(key.first, key.second, first)
            }
            .sortedBy { it.third.titleName.lowercase() }
    }

    LaunchedEffect(networkEpoch, refreshEpoch, online) {
        if (!online) {
            loading = false
            error = null
            feed = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        cloudflareUri = null
        runCatching {
            val source = LumeApp.instance.sources.activeSource()
                ?: error(resources.getString(R.string.error_no_active_sources))
            sourceId = source.manifest.id
            source.getHome()
        }.onSuccess {
            feed = it
            loading = false
        }.onFailure { e ->
            loading = false
            if (e is CloudflareException) {
                cloudflareUri = e.uri
                scope.launch { LumeApp.instance.http.requestChallenge(e.uri) }
                error = resources.getString(R.string.error_cloudflare_needed)
            } else {
                error = e.message ?: e.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding(),
    ) {
        QuickSearchBar(
            value = "",
            onValueChange = {},
            onSubmit = { nav.navigate(Routes.Search) },
            readOnly = true,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        AnimatedLoadState(
            state = when {
            !online -> LoadState.Empty
            loading && feed == null -> LoadState.Loading
            error != null && feed == null -> LoadState.Error
            else -> LoadState.Content
        },
            modifier = Modifier.fillMaxSize(),
        ) { pane ->
            when (pane) {
                LoadState.Empty -> {
                    Text(
                        stringResource(R.string.home_offline_downloaded),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (offlineTitles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.home_no_downloaded), color = AppColors.textSecondary, fontSize = 15.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(offlineTitles, key = { "${it.first}:${it.second}" }) { (sid, tid, item) ->
                                TitleListRow(
                                    modifier = animatedItem(),
                                    title = item.titleName,
                                    coverUrl = item.coverUrl,
                                    subtitle = stringResource(R.string.chapters_count_short, downloads.count { it.sourceId == sid && it.titleId == tid }),
                                    onClick = { nav.navigate(Routes.title(sid, tid)) },
                                )
                            }
                        }
                    }
                }
                LoadState.Loading -> LoadingBox()
                LoadState.Error -> ErrorRetryBox(
                    message = error.orEmpty(),
                    onRetry = { refreshEpoch++ },
                    onCloudflare = cloudflareUri?.let { uri ->
                        { scope.launch { LumeApp.instance.http.requestChallenge(uri) } }
                    },
                )
                else -> {
                    val ptrState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = loading,
                        onRefresh = { refreshEpoch++ },
                        state = ptrState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            val spotlight = feed?.spotlight.orEmpty()
                            if (spotlight.isNotEmpty()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.height(220.dp),
                                ) {
                                    items(spotlight, key = { it.id }) { item ->
                                        CoverCard(
                                            title = item.title,
                                            coverUrl = item.coverUrl,
                                            badge = item.latestChapter,
                                            onClick = { nav.navigate(Routes.title(sourceId, item.id)) },
                                        )
                                    }
                                }
                            }
                        }

                        if (history.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_continue_reading),
                                        color = AppColors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { confirmClear = true }) {
                                        Text(stringResource(R.string.action_clear), color = AppColors.accent, fontSize = 12.sp)
                                    }
                                }
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(history.take(20), key = { "${it.sourceId}:${it.titleId}" }) { item ->
                                        ContinueCard(item) {
                                            nav.openReader(
                                                item.sourceId,
                                                item.titleId,
                                                item.chapterId,
                                                item.pageIndex,
                                                item.pageOffset,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            SectionHeader(
                                title = stringResource(R.string.home_now_reading),
                                onTap = { nav.navigate(Routes.Catalog) },
                            )
                            Text(
                                text = stringResource(R.string.home_new_releases),
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }

                        items(feed?.latest.orEmpty(), key = { it.id }) { item ->
                            TitleListRow(
                                modifier = animatedItem(),
                                title = item.title,
                                coverUrl = item.coverUrl,
                                subtitle = item.latestChapter ?: item.updatedLabel,
                                typeLabel = item.typeLabel,
                                sourceId = sourceId,
                                onClick = { nav.navigate(Routes.title(sourceId, item.id)) },
                            )
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.home_clear_confirm_title)) },
            text = { Text(stringResource(R.string.home_clear_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch { LumeApp.instance.dao.clearAllProgress() }
                    },
                ) { Text(stringResource(R.string.action_clear), color = AppColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ContinueCard(item: ProgressEntity, onClick: () -> Unit) {
    val progress = if (item.pageCount > 0) {
        (item.pageIndex + 1).toFloat() / item.pageCount
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
    ) {
        CoverImage(url = item.coverUrl, width = 110.dp, height = 150.dp, radius = 8.dp)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = AppColors.accent,
            trackColor = AppColors.card,
        )
        Text(
            text = item.titleName,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = item.chapterName,
            color = AppColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
