package com.lume.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.ChapterBranch
import com.lume.app.domain.ChapterFilter
import com.lume.app.domain.ChapterListOptions
import com.lume.app.domain.ChapterReadState
import com.lume.app.domain.LinkedChapterId
import com.lume.app.domain.chapterBranches
import com.lume.app.domain.chapterListView
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.fadingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(nav: NavHostController, sourceId: String, titleId: String) {
    val container = LocalAppContainer.current
    val vm: ChaptersViewModel = viewModel { ChaptersViewModel(container, sourceId, titleId) }
    val chapters = vm.chapters
    val readIds by vm.readIds.collectAsState()
    val downloadedIds by vm.downloadedIds.collectAsState()
    val bookmarkedIds by vm.bookmarkedIds.collectAsState()
    val currentChapterId by vm.currentChapterId.collectAsState()
    val partialProgress by vm.partialProgress.collectAsState()
    val options by vm.options.collectAsState()
    val queueItems by container.downloadQueue.items.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var downloadMenu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val readState = remember(chapters, readIds) { ChapterReadState(chapters, readIds) }
    val branches = remember(chapters) { chapterBranches(chapters) }
    // Linked sources: each row says where its chapter comes from.
    val multiSource = remember(chapters) { chapters.any { LinkedChapterId.isLinked(it.id) } }
    val sourceNames = remember(chapters, multiSource) {
        if (!multiSource) emptyMap() else chapters.map { LinkedChapterId.sourceOf(it.id, sourceId) }.distinct()
            .associateWith { container.sources.sourceById(it)?.manifest?.name ?: it }
    }
    val displayed = remember(chapters, options, readState, downloadedIds, bookmarkedIds) {
        chapterListView(chapters, options, readState, downloadedIds, bookmarkedIds)
    }
    val queueByChapter = remember(queueItems, sourceId, titleId) {
        queueItems
            .filter { it.sourceId == sourceId && it.titleId == titleId }
            .associateBy { it.chapterId }
    }

    BackHandler(enabled = vm.selecting) { vm.clearSelection() }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vm.selecting) stringResource(R.string.chapters_selected, vm.selected.size)
                        else stringResource(R.string.chapters_title),
                        color = AppColors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (vm.selecting) vm.clearSelection() else nav.popBackStack() }) {
                        Icon(
                            if (vm.selecting) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack,
                            null,
                            tint = AppColors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (vm.selecting) {
                        IconButton(onClick = { vm.selectAll(displayed) }) {
                            Icon(Icons.Outlined.SelectAll, stringResource(R.string.cd_select_all), tint = AppColors.textPrimary)
                        }
                    } else {
                        Box {
                            IconButton(onClick = { downloadMenu = true }) {
                                Icon(Icons.Outlined.Download, stringResource(R.string.cd_download_menu), tint = AppColors.textPrimary)
                            }
                            DropdownMenu(expanded = downloadMenu, onDismissRequest = { downloadMenu = false }) {
                                listOf(1, 5, 10).forEach { n ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chapters_download_next, n)) },
                                        onClick = {
                                            downloadMenu = false
                                            vm.downloadNext(n, displayed)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_download_unread)) },
                                    onClick = {
                                        downloadMenu = false
                                        vm.downloadNext(null, displayed)
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chapters_delete_downloads), color = AppColors.danger) },
                                    enabled = downloadedIds.isNotEmpty(),
                                    onClick = {
                                        downloadMenu = false
                                        confirmDeleteAll = true
                                    },
                                )
                            }
                        }
                        IconButton(onClick = vm::toggleSort) {
                            Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.cd_sort), tint = AppColors.textPrimary)
                        }
                        IconButton(onClick = { sheetOpen = true }) {
                            Box {
                                Icon(Icons.Outlined.FilterList, stringResource(R.string.cd_chapter_filters), tint = AppColors.textPrimary)
                                if (options.filter != 0) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AppColors.accent),
                                    )
                                }
                            }
                        }
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = vm.selecting,
                enter = Motion.barEnterFromBottom,
                exit = Motion.barExitToBottom,
            ) {
                ChapterSelectionBar(
                    single = vm.selected.size == 1,
                    onRead = { vm.markSelected(read = true) },
                    onUnread = { vm.markSelected(read = false) },
                    onPreviousRead = vm::markPreviousRead,
                    onDownload = vm::downloadSelected,
                    onDelete = vm::deleteSelectedDownloads,
                )
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
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (branches.isNotEmpty() || multiSource) {
                        item(key = "teams") {
                            TeamChips(
                                branches = branches,
                                options = options,
                                autoLabel = stringResource(if (branches.isEmpty()) R.string.chapters_source_auto else R.string.chapters_team_auto),
                                onPrefer = vm::preferBranch,
                                onShowAll = vm::showDuplicates,
                            )
                        }
                    }
                    if (displayed.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.chapters_none_match),
                                color = AppColors.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(displayed, key = { it.id }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            showTeam = branches.isNotEmpty(),
                            sourceLabel = sourceNames[LinkedChapterId.sourceOf(chapter.id, sourceId)],
                            isRead = readState.isRead(chapter),
                            isCurrent = chapter.id == currentChapterId,
                            isDownloaded = chapter.id in downloadedIds,
                            queueItem = queueByChapter[chapter.id],
                            partial = partialProgress[chapter.id]?.let { it.pageIndex + 1 to it.pageCount },
                            selected = chapter.id in vm.selected,
                            selecting = vm.selecting,
                            onClick = {
                                if (vm.selecting) vm.toggle(chapter) else nav.openReader(sourceId, titleId, chapter.id)
                            },
                            onLongClick = { if (vm.selecting) vm.selectRange(chapter, displayed) else vm.toggle(chapter) },
                            onDownload = { vm.enqueue(listOf(chapter)) },
                            onCancel = { vm.cancelDownload(chapter.id) },
                            modifier = fadingItem(),
                        )
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.surface,
        ) {
            ChapterFilterSheet(options = options, onToggle = vm::toggleFilter, onSort = vm::toggleSort)
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.dialog_delete_downloads_title)) },
            text = { Text(stringResource(R.string.dialog_delete_downloads_text, downloadedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAllDownloads()
                    confirmDeleteAll = false
                }) { Text(stringResource(R.string.action_delete), color = AppColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TeamChips(
    branches: List<ChapterBranch>,
    options: ChapterListOptions,
    autoLabel: String,
    onPrefer: (String?) -> Unit,
    onShowAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TeamChip(autoLabel, !options.showDuplicates && options.branch == null) { onPrefer(null) }
        branches.forEach { b ->
            TeamChip("${b.name} · ${b.chapterCount}", !options.showDuplicates && options.branch == b.id) { onPrefer(b.id) }
        }
        TeamChip(stringResource(R.string.chapters_team_all), options.showDuplicates, onShowAll)
    }
}

@Composable
private fun TeamChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) AppColors.accent else AppColors.textSecondary, Motion.short(), label = "teamText")
    val background by animateColorAsState(if (selected) AppColors.accentMuted else AppColors.card, Motion.short(), label = "teamBg")
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    showTeam: Boolean,
    /** The chapter's source, for titles with linked sources. */
    sourceLabel: String?,
    isRead: Boolean,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    queueItem: DownloadQueueEntity?,
    partial: Pair<Int, Int>?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) AppColors.accentMuted else Color.Transparent,
        Motion.short(),
        label = "chapterSelected",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chapter.title,
                color = if (isRead && !isCurrent) AppColors.textTertiary else AppColors.textPrimary,
                fontSize = 14.sp,
            )
            val meta = listOfNotNull(
                chapter.dateLabel,
                sourceLabel,
                chapter.branchName?.takeIf { showTeam },
                partial?.let { (page, count) -> stringResource(R.string.chapter_progress_pages, page, count) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = AppColors.textTertiary, fontSize = 11.sp)
            }
        }
        // Fixed-width slots: a row never changes width as its status or download state changes,
        // so titles don't re-wrap and the list doesn't twitch.
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
            when {
                queueItem != null &&
                    (queueItem.status == DownloadQueueEntity.STATUS_PENDING ||
                        queueItem.status == DownloadQueueEntity.STATUS_RUNNING) -> {
                    IconButton(onClick = onCancel, enabled = !selecting) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
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
                !selecting -> IconButton(onClick = onDownload) {
                    Icon(Icons.Outlined.Download, stringResource(R.string.action_download), tint = AppColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ChapterSelectionBar(
    single: Boolean,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onPreviousRead: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.cardHigh)
            .navigationBarsPadding()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarAction(Icons.Outlined.DoneAll, stringResource(R.string.action_mark_read), onRead)
        BarAction(Icons.Outlined.RemoveDone, stringResource(R.string.action_mark_unread), onUnread)
        if (single) BarAction(Icons.Outlined.VerticalAlignBottom, stringResource(R.string.action_mark_previous_read), onPreviousRead)
        BarAction(Icons.Outlined.Download, stringResource(R.string.action_download), onDownload)
        BarAction(Icons.Outlined.Delete, stringResource(R.string.cd_delete_download), onDelete, AppColors.danger)
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = AppColors.textPrimary) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
}

@Composable
private fun ChapterFilterSheet(options: ChapterListOptions, onToggle: (Int) -> Unit, onSort: () -> Unit) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.library_tab_filter),
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
        )
        ChapterFilter.all.forEach { bit ->
            val on = (options.filter and bit) != 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(bit) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (on) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (on) AppColors.accent else AppColors.textSecondary,
                )
                Spacer(Modifier.width(16.dp))
                Text(stringResource(chapterFilterLabel(bit)), color = AppColors.textPrimary, fontSize = 15.sp)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSort)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = AppColors.accent)
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(if (options.ascending) R.string.chapters_sort_oldest else R.string.chapters_sort_newest),
                color = AppColors.textPrimary,
                fontSize = 15.sp,
            )
        }
    }
}

private fun chapterFilterLabel(bit: Int): Int = when (bit) {
    ChapterFilter.UNREAD -> R.string.chapters_filter_unread
    ChapterFilter.READ -> R.string.chapters_filter_read
    ChapterFilter.DOWNLOADED -> R.string.library_filter_downloaded
    else -> R.string.chapters_filter_bookmarked
}
