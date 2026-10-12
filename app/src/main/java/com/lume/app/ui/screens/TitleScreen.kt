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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.navigation.NavHostController
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.offline.networkOr
import com.lume.app.data.tracker.TrackerSearchResult
import com.lume.app.domain.Chapter
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.inReadingOrder
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.components.openInBrowser
import com.lume.app.ui.components.shareLink
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(nav: NavHostController, sourceId: String, titleId: String) {
    val resources = LocalResources.current
    val context = LocalContext.current
    var details by remember { mutableStateOf<TitleDetails?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var loading by remember { mutableStateOf(true) }
    var bookmarked by remember { mutableStateOf(false) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    var showTrackers by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dao = LumeApp.instance.dao
    val networkEpoch by LumeApp.instance.http.networkEpoch.collectAsState()
    var progressState by remember { mutableStateOf<com.lume.app.data.db.ProgressEntity?>(null) }
    val links by dao.observeTrackerLinks(sourceId, titleId).collectAsState(initial = emptyList())

    LaunchedEffect(sourceId, titleId, networkEpoch, refreshEpoch) {
        loading = true
        error = null
        cloudflareUri = null
        runCatching {
            val offline = LumeApp.instance.offlineCache
            val cached = offline.get(sourceId, titleId)
            val source = LumeApp.instance.sources.sourceById(sourceId)
            val d = cached?.details
                ?: networkOr(source, { getTitle(titleId) }) {
                    offline.detailsFromDownloads(sourceId, titleId)
                }
                ?: error(resources.getString(R.string.error_no_title_data))
            val c = cached?.chapters
                ?: networkOr(source, { getChapters(titleId) }, isUsable = { it.isNotEmpty() }) {
                    offline.chaptersFromDownloads(sourceId, titleId)
                }.orEmpty()
            if (source != null && c.isNotEmpty()) {
                offline.save(sourceId, titleId, d, c)
            }
            bookmarked = dao.getBookmark(sourceId, titleId) != null
            progressState = dao.getProgress(sourceId, titleId)
            val meta = dao.getTitleMeta(sourceId, titleId)
            if (meta != null && meta.unreadNewCount > 0) {
                dao.upsertTitleMeta(meta.copy(unreadNewCount = 0))
            }
            d to c
        }.onSuccess { (d, c) ->
            details = d
            chapters = c
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

    Scaffold(
        containerColor = AppColors.background,
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
                        LumeApp.instance.sources.sourceById(sourceId)?.webUrl(titleId)
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
                        onClick = {
                            val d = details ?: return@IconButton
                            scope.launch {
                                if (bookmarked) {
                                    dao.deleteBookmark(sourceId, titleId)
                                    bookmarked = false
                                } else {
                                    dao.upsertBookmark(
                                        BookmarkEntity(
                                            sourceId = sourceId,
                                            titleId = titleId,
                                            titleName = d.title,
                                            coverUrl = d.coverUrl,
                                            typeLabel = d.typeLabel,
                                        ),
                                    )
                                    bookmarked = true
                                }
                            }
                        },
                    ) {
                        Icon(
                            if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_bookmark),
                            tint = if (bookmarked) AppColors.accent else AppColors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        AnimatedLoadState(
            state = loadStateOf(loading, error),
            modifier = Modifier.fillMaxSize(),
        ) { pane ->
            when (pane) {
                LoadState.Loading -> LoadingBox(Modifier.padding(padding))
                LoadState.Error -> ErrorRetryBox(
                    message = error.orEmpty(),
                    onRetry = { refreshEpoch++ },
                    onCloudflare = cloudflareUri?.let { uri ->
                        { scope.launch { LumeApp.instance.http.requestChallenge(uri) } }
                    },
                    modifier = Modifier.padding(padding),
                )
                else -> {
                    val d = details!!
                    Column(
                        modifier = Modifier
                            .padding(padding)
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
                                        // No progress yet: start from the first chapter, not the newest one.
                                        val chId = progressState?.chapterId
                                            ?: chapters.inReadingOrder().firstOrNull()?.id
                                        if (chId != null) {
                                            nav.navigate(Routes.reader(sourceId, titleId, chId))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.accent,
                                        contentColor = Color.Black,
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
            onSynced = { progressState = dao.getProgress(sourceId, titleId) },
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
    val trackers = LumeApp.instance.trackers.all
    var selectedTracker by remember { mutableStateOf(trackers.first()) }
    var query by remember { mutableStateOf(titleName) }
    var results by remember { mutableStateOf<List<TrackerSearchResult>>(emptyList()) }
    var manualId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remotePreview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val links by LumeApp.instance.dao.observeTrackerLinks(sourceId, titleId)
        .collectAsState(initial = emptyList())
    val scroll = rememberScrollState()

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
                    TextButton(
                        onClick = {
                            scope.launch {
                                selectedTracker.unlink(sourceId, titleId)
                                message = resources.getString(R.string.tracker_unlinked)
                            }
                        },
                    ) { Text(stringResource(R.string.action_unlink)) }
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
                                LumeApp.instance.trackerSync.syncTitle(
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
                            val link = LumeApp.instance.dao.getTrackerLinks(sourceId, titleId)
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
                        contentColor = Color.Black,
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
                        contentColor = Color.Black,
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
