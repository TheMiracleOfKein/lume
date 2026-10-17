package com.lume.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.tracker.TrackerSearchResult
import com.lume.app.ui.components.TrackerEntryDialog
import com.lume.app.domain.Chapter
import com.lume.app.domain.inReadingOrder
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.components.openInBrowser
import com.lume.app.ui.components.shareLink
import com.lume.app.ui.components.PageBookmarkRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(nav: NavHostController, sourceId: String, titleId: String) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val vm: TitleViewModel = viewModel { TitleViewModel(container, sourceId, titleId) }
    val details = vm.details
    val chapters = vm.chapters
    val bookmarked by vm.inLibrary.collectAsState()
    val progressState by vm.progress.collectAsState()
    val links by vm.trackerLinks.collectAsState()
    val pageBookmarks by vm.pageBookmarks.collectAsState()
    var showTrackers by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        details?.title ?: stringResource(R.string.title_fallback),
                        color = AppColors.textPrimary,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    val webUrl = remember(sourceId, titleId) {
                        container.sources.sourceById(sourceId)?.webUrl(titleId)
                    }
                    if (webUrl != null) {
                        IconButton(onClick = { openInBrowser(context, webUrl) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.cd_open_in_browser),
                                tint = AppColors.textPrimary,
                            )
                        }
                        IconButton(onClick = { shareLink(context, details?.title, webUrl) }) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.cd_share),
                                tint = AppColors.textPrimary,
                            )
                        }
                    }
                    IconButton(
                        onClick = { vm.toggleLibrary() },
                    ) {
                        Icon(
                            if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_bookmark),
                            tint = if (bookmarked) AppColors.accent else AppColors.textPrimary,
                        )
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
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
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    val d = details ?: return@PullToRefreshBox
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Row {
                            CoverImage(
                                url = d.coverUrl,
                                width = 120.dp,
                                height = 170.dp,
                                radius = 8.dp,
                                sourceId = sourceId,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    d.title,
                                    color = AppColors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                d.altTitle?.let {
                                    Text(it, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                                d.status?.let {
                                    Text(
                                        stringResource(R.string.title_status, it),
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                d.typeLabel?.let {
                                    Text(it, color = AppColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val p = progressState
                                        if (p != null) {
                                            // The exact spot, even if that chapter's own row says "finished".
                                            nav.openReader(sourceId, titleId, p.chapterId, p.pageIndex, p.pageOffset)
                                        } else {
                                            // No progress yet: start from the first chapter, not the newest one.
                                            chapters.inReadingOrder().firstOrNull()?.let {
                                                nav.openReader(sourceId, titleId, it.id)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.accent,
                                        contentColor = AppColors.onAccent,
                                    ),
                                    enabled = chapters.isNotEmpty(),
                                ) {
                                    Text(
                                        if (progressState != null) {
                                            stringResource(R.string.action_continue)
                                        } else {
                                            stringResource(R.string.action_read)
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.chapters(sourceId, titleId)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.title_chapters_list, chapters.size),
                                color = AppColors.textPrimary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showTrackers = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val linked = if (links.isEmpty()) {
                                stringResource(R.string.none_short)
                            } else {
                                links.joinToString { it.tracker }
                            }
                            Text(stringResource(R.string.title_trackers, linked), color = AppColors.textPrimary)
                        }
                        if (d.genres.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                d.genres.forEach { genre ->
                                    Text(
                                        text = genre,
                                        color = AppColors.textSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AppColors.card)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        if (pageBookmarks.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.bookmarks_title),
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Column {
                                pageBookmarks.forEach { bookmark ->
                                    key(bookmark.id) {
                                        PageBookmarkRow(
                                            bookmark = bookmark,
                                            onOpen = {
                                                nav.openReader(
                                                    sourceId,
                                                    titleId,
                                                    bookmark.chapterId,
                                                    bookmark.pageIndex,
                                                    bookmark.pageOffset,
                                                )
                                            },
                                            onDelete = { vm.deletePageBookmark(bookmark.id) },
                                        )
                                    }
                                }
                            }
                        }
                        d.description?.let {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.title_description),
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }

    if (showTrackers) {
        TrackersDialog(
            sourceId = sourceId,
            titleId = titleId,
            titleName = details?.title.orEmpty(),
            chapters = chapters,
            coverUrl = details?.coverUrl,
            typeLabel = details?.typeLabel,
            onDismiss = { showTrackers = false },
        )
    }
}

@Composable
private fun TrackersDialog(
    sourceId: String,
    titleId: String,
    titleName: String,
    chapters: List<Chapter>,
    coverUrl: String?,
    typeLabel: String?,
    onDismiss: () -> Unit,
    onSynced: suspend () -> Unit = {},
) {
    val resources = LocalResources.current
    val container = LocalAppContainer.current
    val trackers = container.trackers.all
    var selectedTracker by remember { mutableStateOf(trackers.first()) }
    var query by remember { mutableStateOf(titleName) }
    var results by remember { mutableStateOf<List<TrackerSearchResult>>(emptyList()) }
    var manualId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remotePreview by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val links by container.dao.observeTrackerLinks(sourceId, titleId)
        .collectAsState(initial = emptyList())
    val scroll = rememberScrollState()
    editing?.let { remoteId ->
        TrackerEntryDialog(
            tracker = selectedTracker,
            remoteId = remoteId,
            onDismiss = { editing = null },
            onSaved = { saved ->
                editing = null
                scope.launch {
                    container.dao.getTrackerLinks(sourceId, titleId).firstOrNull { it.tracker == selectedTracker.id }?.let { link ->
                        container.dao.upsertTrackerLink(
                            link.copy(status = saved.status?.name, score = saved.score, lastSync = System.currentTimeMillis()),
                        )
                    }
                    message = resources.getString(R.string.tracker_edit_saved, selectedTracker.displayName)
                    remotePreview = resources.getString(R.string.tracker_preview_progress, remoteId, saved.chaptersRead.toString())
                }
            },
        )
    }

    LaunchedEffect(selectedTracker.id, links) {
        val linked = links.firstOrNull { it.tracker == selectedTracker.id }
        if (linked == null) {
            remotePreview = null
            return@LaunchedEffect
        }
        if (!selectedTracker.isLoggedIn()) {
            remotePreview = resources.getString(R.string.tracker_preview_login, linked.remoteId)
            return@LaunchedEffect
        }
        remotePreview = resources.getString(R.string.tracker_preview_loading, linked.remoteId)
        val remote = runCatching { selectedTracker.fetchProgress(linked.remoteId) }.getOrNull()
        remotePreview = if (remote == null) {
            resources.getString(R.string.tracker_preview_unavailable, linked.remoteId)
        } else {
            val ch = remote.chaptersRead.toString()
            remote.status?.let {
                resources.getString(R.string.tracker_preview_progress_status, linked.remoteId, ch, it)
            } ?: resources.getString(R.string.tracker_preview_progress, linked.remoteId, ch)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_trackers)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(scroll),
            ) {
                message?.let { text ->
                    Text(
                        text = text,
                        color = AppColors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.surface)
                            .padding(10.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    trackers.forEach { t ->
                        val selected = t.id == selectedTracker.id
                        val color by animateColorAsState(
                            if (selected) AppColors.accent else AppColors.textSecondary,
                            Motion.short(),
                            label = "tracker",
                        )
                        Text(
                            text = t.displayName,
                            color = color,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { selectedTracker = t }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                val linked = links.firstOrNull { it.tracker == selectedTracker.id }
                if (linked != null) {
                    Text(
                        remotePreview ?: stringResource(R.string.tracker_linked, linked.remoteId),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row {
                        TextButton(onClick = { editing = linked.remoteId }) {
                            Text(stringResource(R.string.tracker_edit))
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    selectedTracker.unlink(sourceId, titleId)
                                    message = resources.getString(R.string.tracker_unlinked)
                                }
                            },
                        ) { Text(stringResource(R.string.action_unlink)) }
                    }
                } else {
                    Text(
                        stringResource(R.string.tracker_not_linked),
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            syncing = true
                            message = resources.getString(R.string.tracker_syncing)
                            val result = runCatching {
                                container.trackerSync.syncTitle(
                                    sourceId = sourceId,
                                    titleId = titleId,
                                    chapters = chapters,
                                    titleName = titleName,
                                    coverUrl = coverUrl,
                                    typeLabel = typeLabel,
                                )
                            }
                            message = result.fold(
                                onSuccess = { it.displayText() },
                                onFailure = {
                                    resources.getString(R.string.error_prefix, it.message ?: it.toString())
                                },
                            )
                            runCatching { onSynced() }
                            syncing = false
                            val link = container.dao.getTrackerLinks(sourceId, titleId)
                                .firstOrNull { it.tracker == selectedTracker.id }
                            if (link != null && selectedTracker.isLoggedIn()) {
                                val remote = runCatching {
                                    selectedTracker.fetchProgress(link.remoteId)
                                }.getOrNull()
                                if (remote != null) {
                                    val ch = remote.chaptersRead.toString()
                                    remotePreview = remote.status?.let {
                                        resources.getString(
                                            R.string.tracker_preview_progress_status,
                                            link.remoteId,
                                            ch,
                                            it,
                                        )
                                    } ?: resources.getString(
                                        R.string.tracker_preview_progress,
                                        link.remoteId,
                                        ch,
                                    )
                                }
                            }
                            scroll.animateScrollTo(0)
                        }
                    },
                    enabled = !syncing && linked != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.accent,
                        contentColor = AppColors.onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (syncing) {
                            stringResource(R.string.tracker_syncing)
                        } else {
                            stringResource(R.string.action_sync)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            message = null
                            results = runCatching { selectedTracker.search(query) }
                                .onFailure { message = it.message }
                                .getOrDefault(emptyList())
                            if (results.isEmpty() && message == null) {
                                message = resources.getString(R.string.search_empty)
                            }
                            loading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.accent,
                        contentColor = AppColors.onAccent,
                    ),
                ) {
                    Text(
                        if (loading) stringResource(R.string.ellipsis)
                        else stringResource(R.string.action_search),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                results.forEach { item ->
                    Text(
                        text = item.title,
                        color = AppColors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    selectedTracker.link(
                                        sourceId,
                                        titleId,
                                        item.remoteId,
                                        item.url,
                                    )
                                    message = resources.getString(R.string.tracker_linked_with, item.title)
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualId,
                    onValueChange = { manualId = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_manual_id)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val id = manualId.trim()
                        if (id.isEmpty()) return@TextButton
                        scope.launch {
                            selectedTracker.link(sourceId, titleId, id)
                            message = resources.getString(R.string.tracker_linked, id)
                        }
                    },
                ) { Text(stringResource(R.string.action_bind_id)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
