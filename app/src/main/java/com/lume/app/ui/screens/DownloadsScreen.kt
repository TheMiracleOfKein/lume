package com.lume.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.text.format.Formatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.fadingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: DownloadsViewModel = viewModel { DownloadsViewModel(container) }
    val groups by vm.groups.collectAsState()
    val activeQueue by vm.activeQueue.collectAsState()
    val failedQueue by vm.failedQueue.collectAsState()
    val paused by vm.paused.collectAsState()
    val expanded = vm.expanded
    val context = LocalContext.current
    val (usageByTitle, usageTotal) = vm.usage

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    BackButton(onClick = { nav.popBackStack() })
                },
                actions = {
                    if (activeQueue.isNotEmpty() || paused) {
                        IconButton(onClick = { if (paused) vm.resume() else vm.pause() }) {
                            Icon(
                                if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                stringResource(if (paused) R.string.downloads_resume else R.string.downloads_pause),
                                tint = AppColors.textPrimary,
                            )
                        }
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background),
        ) {
            if (groups.isEmpty() && activeQueue.isEmpty() && failedQueue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.downloads_empty), color = AppColors.textSecondary, fontSize = 15.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (usageTotal > 0) {
                        item(key = "usage") {
                            Text(
                                stringResource(R.string.downloads_usage, Formatter.formatShortFileSize(context, usageTotal)),
                                color = AppColors.textTertiary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (activeQueue.isNotEmpty()) {
                        item(key = "queue-header") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (paused) stringResource(R.string.downloads_paused) else stringResource(R.string.downloads_queued),
                                    color = if (paused) AppColors.accent else AppColors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                )
                                TextButton(onClick = vm::cancelAll) {
                                    Text(stringResource(R.string.downloads_cancel_all), color = AppColors.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                        items(activeQueue, key = { "q-${it.id}" }) { item ->
                            Row(
                                modifier = fadingItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.titleName, color = AppColors.textPrimary, fontSize = 14.sp)
                                    Text(item.chapterName, color = AppColors.textSecondary, fontSize = 12.sp)
                                    if (item.status == DownloadQueueEntity.STATUS_RUNNING) {
                                        val progress by animateFloatAsState(item.progress, Motion.medium(), label = "dl")
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            color = AppColors.accent,
                                            trackColor = AppColors.card,
                                        )
                                    } else {
                                        Text(stringResource(R.string.downloads_waiting), color = AppColors.textTertiary, fontSize = 11.sp)
                                    }
                                }
                                if (item.status == DownloadQueueEntity.STATUS_PENDING) {
                                    QueueOrderMenu(
                                        onTop = { vm.moveToTop(item.id) },
                                        onUp = { vm.move(item.id, -1) },
                                        onDown = { vm.move(item.id, 1) },
                                    )
                                }
                                IconButton(onClick = { vm.cancel(item.id) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel), tint = AppColors.danger)
                                }
                            }
                        }
                    }
                    if (failedQueue.isNotEmpty()) {
                        item(key = "failed-header") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.downloads_failed),
                                    color = AppColors.danger,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                )
                                if (failedQueue.size > 1) {
                                    TextButton(onClick = vm::retryAll) {
                                        Text(stringResource(R.string.downloads_retry_all), color = AppColors.accent, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        items(failedQueue, key = { "f-${it.id}" }) { item ->
                            Row(
                                modifier = fadingItem()
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.titleName, color = AppColors.textPrimary, fontSize = 14.sp)
                                    Text(item.chapterName, color = AppColors.textSecondary, fontSize = 12.sp)
                                    Text(
                                        stringResource(R.string.downloads_failed_hint),
                                        color = AppColors.textTertiary,
                                        fontSize = 11.sp,
                                    )
                                }
                                TextButton(onClick = { vm.retry(item.id) }) {
                                    Text(stringResource(R.string.action_retry), color = AppColors.accent)
                                }
                                IconButton(onClick = { vm.cancel(item.id) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel), tint = AppColors.textSecondary)
                                }
                            }
                        }
                    }
                    items(groups, key = { "${it.sourceId}:${it.titleId}" }) { group ->
                        val key = "${group.sourceId}|${group.titleId}"
                        val isOpen = key in expanded
                        Column(modifier = fadingItem().fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.clickable {
                                        nav.navigate(Routes.title(group.sourceId, group.titleId))
                                    },
                                ) {
                                    CoverImage(
                                        url = group.coverUrl,
                                        width = 48.dp,
                                        height = 64.dp,
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                        .clickable {
                                            nav.navigate(Routes.title(group.sourceId, group.titleId))
                                        },
                                ) {
                                    Text(group.titleName, color = AppColors.textPrimary, fontSize = 15.sp)
                                    val bytes = usageByTitle[key] ?: 0L
                                    Text(
                                        stringResource(R.string.chapters_count_short, group.chapters.size) +
                                            if (bytes > 0) " · " + Formatter.formatShortFileSize(context, bytes) else "",
                                        color = AppColors.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        vm.deleteTitle(group.sourceId, group.titleId)
                                    },
                                ) {
                                    Icon(Icons.Outlined.Delete, null, tint = AppColors.danger)
                                }
                                IconButton(
                                    onClick = {
                                        vm.toggle(key)
                                    },
                                ) {
                                    Icon(
                                        if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        if (isOpen) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                                        tint = AppColors.textSecondary,
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = isOpen,
                                enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                                exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                            ) {
                                Column {
                                    group.chapters.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    nav.openReader(item.sourceId, item.titleId, item.chapterId)
                                                }
                                                .padding(start = 76.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.chapterName, color = AppColors.textPrimary, fontSize = 13.sp)
                                                Text(
                                                    stringResource(R.string.pages_count_short, item.pageCount),
                                                    color = AppColors.textTertiary,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    vm.deleteChapter(item.sourceId, item.titleId, item.chapterId)
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Delete,
                                                    null,
                                                    tint = AppColors.danger,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Move a waiting download: first, up or down. */
@Composable
private fun QueueOrderMenu(onTop: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.SwapVert, stringResource(R.string.downloads_reorder), tint = AppColors.textSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.downloads_move_top)) }, onClick = { open = false; onTop() })
            DropdownMenuItem(text = { Text(stringResource(R.string.downloads_move_up)) }, onClick = { open = false; onUp() })
            DropdownMenuItem(text = { Text(stringResource(R.string.downloads_move_down)) }, onClick = { open = false; onDown() })
        }
    }
}
