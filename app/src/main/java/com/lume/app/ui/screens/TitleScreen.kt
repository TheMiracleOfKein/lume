package com.lume.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.similar.SimilarResult
import com.lume.app.data.similar.SimilarTitle
import com.lume.app.data.tracker.TrackerSearchResult
import com.lume.app.ui.components.TrackerEntryDialog
import com.lume.app.domain.AutoDownloadMode
import com.lume.app.domain.Chapter
import com.lume.app.domain.inReadingOrder
import com.lume.app.domain.dedupeChapters
import com.lume.app.data.db.TitleSourceEntity
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(nav: NavHostController, sourceId: String, titleId: String) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val vm: TitleViewModel = viewModel { TitleViewModel(container, sourceId, titleId) }
    val details = vm.details
    val chapters = vm.chapters
    val bookmarked by vm.inLibrary.collectAsState()
    val subscribed by vm.subscribed.collectAsState()
    val autoDownloadMode by vm.autoDownloadMode.collectAsState()
    val progressState by vm.progress.collectAsState()
    val links by vm.trackerLinks.collectAsState()
    val pageBookmarks by vm.pageBookmarks.collectAsState()
    val titleSources by vm.titleSources.collectAsState()
    var showTrackers by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    // One row per chapter, whatever number of teams or sources have it.
    val chapterCount = remember(chapters) { dedupeChapters(chapters).size }

    // Moved to another main source: that title replaces this screen.
    LaunchedEffect(vm.movedTo) {
        val (newSource, newTitle) = vm.movedTo ?: return@LaunchedEffect
        nav.navigate(Routes.title(newSource, newTitle)) {
            popUpTo(Routes.Title) { inclusive = true }
        }
    }

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
                    BackButton(onClick = { nav.popBackStack() })
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
                    // The bell: update checks look only at titles that have it.
                    if (bookmarked) {
                        IconButton(
                            onClick = {
                                vm.toggleSubscribed { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Icon(
                                if (subscribed) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                                contentDescription = stringResource(if (subscribed) R.string.cd_unsubscribe else R.string.cd_subscribe),
                                tint = if (subscribed) AppColors.accent else AppColors.textPrimary,
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
                                if (d.authors.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.title_by, d.authors.joinToString()),
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                val artOnly = d.artists.filter { it !in d.authors }
                                if (artOnly.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.title_art_by, artOnly.joinToString()),
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                // "Manhwa · 2018 · ★ 9.3"
                                val facts = listOfNotNull(
                                    d.typeLabel,
                                    d.year?.toString(),
                                    d.score?.let { stringResource(R.string.title_score, String.format(Locale.US, "%.1f", it)) },
                                )
                                if (facts.isNotEmpty()) {
                                    Text(facts.joinToString(" · "), color = AppColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                        val suggestions = titleSources.filter { it.state == TitleSourceEntity.SUGGESTED }
                        AnimatedVisibility(
                            visible = suggestions.isNotEmpty(),
                            enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                            exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                        ) {
                            AlsoOnCard(
                                names = suggestions.map { container.sources.sourceById(it.linkSourceId)?.manifest?.name ?: it.linkSourceId }.distinct(),
                                enabled = !vm.busy,
                                onOpen = { showSources = true },
                                onLink = vm::linkAllSuggestions,
                                onDismiss = vm::dismissAllSuggestions,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.chapters(sourceId, titleId)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.title_chapters_list, chapterCount),
                                color = AppColors.textPrimary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showSources = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val names = listOf(sourceId) + titleSources
                                .filter { it.state == TitleSourceEntity.LINKED }
                                .map { it.linkSourceId }
                            Text(
                                stringResource(
                                    R.string.title_sources,
                                    names.distinct().joinToString { container.sources.sourceById(it)?.manifest?.name ?: it },
                                ),
                                color = AppColors.textPrimary,
                                maxLines = 1,
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
                        val similar = vm.similar
                        AnimatedVisibility(
                            visible = similar != null && similar.items.isNotEmpty(),
                            enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                            exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                        ) {
                            similar?.let { result ->
                                SimilarTitlesRow(result) { nav.navigate(Routes.searchAll(it.title)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSources) {
        TitleSourcesDialog(vm = vm, titleName = details?.title.orEmpty(), onDismiss = { showSources = false })
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

/** "Also on MangaDex, Comick": sources Lume found the title on, with Link / Not now. */
@Composable
private fun AlsoOnCard(
    names: List<String>,
    enabled: Boolean,
    onOpen: () -> Unit,
    onLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            stringResource(R.string.title_also_on, names.joinToString()),
            color = AppColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Text(
            stringResource(R.string.title_also_on_hint),
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
        )
        Row(modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
            TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.action_not_now)) }
            TextButton(onClick = onLink, enabled = enabled) { Text(stringResource(R.string.action_link)) }
        }
    }
}

/** "Similar titles": covers in a row; a tap searches every source for that title. */
@Composable
private fun SimilarTitlesRow(result: SimilarResult, onOpen: (SimilarTitle) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(R.string.title_similar),
            color = AppColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.title_similar_from, result.from),
            color = AppColors.textTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(result.items, key = { it.title }) { item ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = stringResource(R.string.cd_similar_search)) { onOpen(item) },
                ) {
                    CoverImage(url = item.coverUrl, width = 96.dp, height = 136.dp, radius = 8.dp)
                    Text(
                        item.title,
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
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
