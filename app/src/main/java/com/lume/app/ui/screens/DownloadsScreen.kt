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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem
import kotlinx.coroutines.launch

private data class DownloadTitleGroup(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String?,
    val chapters: List<DownloadEntity>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(nav: NavHostController) {
    val downloads by LumeApp.instance.dao.observeDownloads().collectAsState(initial = emptyList())
    val queue by LumeApp.instance.downloadQueue.items.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    val groups = remember(downloads) {
        downloads
            .groupBy { "${it.sourceId}|${it.titleId}" }
            .map { (_, items) ->
                val first = items.first()
                DownloadTitleGroup(
                    sourceId = first.sourceId,
                    titleId = first.titleId,
                    titleName = first.titleName,
                    coverUrl = first.coverUrl,
                    chapters = items.sortedBy { it.chapterName },
                )
            }
            .sortedBy { it.titleName.lowercase() }
    }
    val activeQueue = remember(queue) {
        queue.filter {
            it.status == DownloadQueueEntity.STATUS_PENDING ||
                it.status == DownloadQueueEntity.STATUS_RUNNING
        }
    }

    val failedQueue = remember(queue) { queue.filter { it.status == DownloadQueueEntity.STATUS_ERROR } }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
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
                    if (activeQueue.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.downloads_queued),
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(activeQueue, key = { "q-${it.id}" }) { item ->
                            Row(
                                modifier = animatedItem()
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
                                IconButton(onClick = { LumeApp.instance.downloadQueue.cancel(item.id) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel), tint = AppColors.danger)
                                }
                            }
                        }
                    }
                    if (failedQueue.isNotEmpty()) {
                        item(key = "failed-header") {
                            Text(
                                stringResource(R.string.downloads_failed),
                                color = AppColors.danger,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(failedQueue, key = { "f-${it.id}" }) { item ->
                            Row(
                                modifier = animatedItem()
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
                                TextButton(onClick = { LumeApp.instance.downloadQueue.retry(item.id) }) {
                                    Text(stringResource(R.string.action_retry), color = AppColors.accent)
                                }
                                IconButton(onClick = { LumeApp.instance.downloadQueue.cancel(item.id) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel), tint = AppColors.textSecondary)
                                }
                            }
                        }
                    }
                    items(groups, key = { "${it.sourceId}:${it.titleId}" }) { group ->
                        val key = "${group.sourceId}|${group.titleId}"
                        val isOpen = key in expanded
                        Column(modifier = animatedItem().fillMaxWidth()) {
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
                                    Text(
                                        stringResource(R.string.chapters_count_short, group.chapters.size),
                                        color = AppColors.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            LumeApp.instance.downloads.deleteTitle(
                                                group.sourceId,
                                                group.titleId,
                                            )
                                        }
                                    },
                                ) {
                                    Icon(Icons.Outlined.Delete, null, tint = AppColors.danger)
                                }
                                IconButton(
                                    onClick = {
                                        expanded = if (isOpen) expanded - key else expanded + key
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
                                                    nav.navigate(
                                                        Routes.reader(
                                                            item.sourceId,
                                                            item.titleId,
                                                            item.chapterId,
                                                        ),
                                                    )
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
                                                    scope.launch {
                                                        LumeApp.instance.downloads.delete(
                                                            item.sourceId,
                                                            item.titleId,
                                                            item.chapterId,
                                                        )
                                                    }
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
