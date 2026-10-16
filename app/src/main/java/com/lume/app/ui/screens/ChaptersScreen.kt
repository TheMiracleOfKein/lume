package com.lume.app.ui.screens

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.fadingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(nav: NavHostController, sourceId: String, titleId: String) {
    val vm: ChaptersViewModel = viewModel { ChaptersViewModel(sourceId, titleId) }
    val chapters = vm.chapters
    var ascending by rememberSaveable { mutableStateOf(false) }
    val readIds by vm.readIds.collectAsState()
    val downloadedIds by vm.downloadedIds.collectAsState()
    val currentChapterId by vm.currentChapterId.collectAsState()
    val partialProgress by vm.partialProgress.collectAsState()
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val queueItems by LumeApp.instance.downloadQueue.items.collectAsState()

    val displayed = remember(chapters, ascending) {
        if (ascending) chapters else chapters.asReversed()
    }
    val queueByChapter = remember(queueItems, sourceId, titleId) {
        queueItems
            .filter { it.sourceId == sourceId && it.titleId == titleId }
            .associateBy { it.chapterId }
    }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectMode) {
                            stringResource(R.string.chapters_selected, selectedIds.size)
                        } else {
                            stringResource(R.string.chapters_title)
                        },
                        color = AppColors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectMode) {
                            selectMode = false
                            selectedIds = emptySet()
                        } else {
                            nav.popBackStack()
                        }
                    }) {
                        Icon(
                            if (selectMode) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack,
                            null,
                            tint = AppColors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (selectMode) {
                        IconButton(
                            onClick = {
                                selectedIds = displayed
                                    .filter { it.id !in downloadedIds }
                                    .map { it.id }
                                    .toSet()
                            },
                        ) {
                            Icon(Icons.Outlined.SelectAll, stringResource(R.string.cd_select_all), tint = AppColors.textPrimary)
                        }
                        TextButton(
                            onClick = {
                                vm.enqueue(displayed.filter { it.id in selectedIds })
                                selectMode = false
                                selectedIds = emptySet()
                            },
                        ) {
                            Text(stringResource(R.string.action_download), color = AppColors.accent)
                        }
                    } else {
                        IconButton(onClick = { selectMode = true }) {
                            Icon(Icons.Outlined.Download, stringResource(R.string.cd_selection), tint = AppColors.textPrimary)
                        }
                        IconButton(onClick = { ascending = !ascending }) {
                            Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.cd_sort), tint = AppColors.textPrimary)
                        }
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        floatingActionButton = {
            if (selectMode && selectedIds.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        vm.enqueue(displayed.filter { it.id in selectedIds })
                        selectMode = false
                        selectedIds = emptySet()
                    },
                    containerColor = AppColors.accent,
                ) {
                    Icon(Icons.Outlined.Download, null, tint = androidx.compose.ui.graphics.Color.Black)
                }
            }
        },
    ) { padding ->
        AnimatedLoadState(
            state = loadStateOf(vm.loading, vm.error),
            modifier = Modifier.fillMaxSize(),
        ) { pane ->
            when (pane) {
                LoadState.Loading -> LoadingBox(Modifier.padding(padding))
                LoadState.Error -> ErrorRetryBox(
                    message = vm.error.orEmpty(),
                    onRetry = vm::refresh,
                    onCloudflare = vm.cloudflareUri?.let { { vm.solveChallenge() } },
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(AppColors.background),
                ) {
                    items(displayed, key = { it.id }) { chapter ->
                        val queueItem = queueByChapter[chapter.id]
                        val isDownloaded = chapter.id in downloadedIds
                        val isRead = chapter.id in readIds
                        val isCurrent = chapter.id == currentChapterId
                        val selected = chapter.id in selectedIds
                        Row(
                            modifier = fadingItem()
                                .fillMaxWidth()
                                .clickable {
                                    if (selectMode) {
                                        if (isDownloaded) return@clickable
                                        selectedIds = if (selected) {
                                            selectedIds - chapter.id
                                        } else {
                                            selectedIds + chapter.id
                                        }
                                    } else {
                                        nav.openReader(sourceId, titleId, chapter.id)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectMode) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = {
                                        if (isDownloaded) return@Checkbox
                                        selectedIds = if (selected) {
                                            selectedIds - chapter.id
                                        } else {
                                            selectedIds + chapter.id
                                        }
                                    },
                                    enabled = !isDownloaded,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chapter.title, color = AppColors.textPrimary, fontSize = 14.sp)
                                val partial = partialProgress[chapter.id]
                                val meta = listOfNotNull(
                                    chapter.dateLabel,
                                    partial?.let { stringResource(R.string.chapter_progress_pages, it.pageIndex + 1, it.pageCount) },
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Text(meta, color = AppColors.textTertiary, fontSize = 11.sp)
                                }
                            }
                            // Fixed-width slots: a row never changes width as its status or download
                            // state changes, so titles don't re-wrap and the list doesn't twitch.
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                when {
                                    isCurrent -> Icon(
                                        Icons.Outlined.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_current),
                                        tint = AppColors.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    isRead -> Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = stringResource(R.string.cd_read),
                                        tint = AppColors.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                if (!selectMode) when {
                                    queueItem != null &&
                                        (queueItem.status == DownloadQueueEntity.STATUS_PENDING ||
                                            queueItem.status == DownloadQueueEntity.STATUS_RUNNING) -> {
                                        IconButton(
                                            onClick = {
                                                LumeApp.instance.downloadQueue.cancel(
                                                    sourceId,
                                                    titleId,
                                                    chapter.id,
                                                )
                                            },
                                        ) {
                                            Box(
                                                modifier = Modifier.size(40.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (queueItem.status == DownloadQueueEntity.STATUS_RUNNING) {
                                                    CircularProgressIndicator(
                                                        progress = { queueItem.progress },
                                                        color = AppColors.accent,
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                }
                                                Icon(
                                                    Icons.Outlined.Close,
                                                    contentDescription = stringResource(R.string.cd_cancel),
                                                    tint = AppColors.danger,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                    }
                                    isDownloaded -> Icon(
                                        Icons.Outlined.DownloadDone,
                                        contentDescription = stringResource(R.string.cd_downloaded),
                                        tint = AppColors.accent,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    else -> IconButton(
                                        onClick = {
                                            vm.enqueue(listOf(chapter))
                                        },
                                    ) {
                                        Icon(Icons.Outlined.Download, null, tint = AppColors.textSecondary)
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
