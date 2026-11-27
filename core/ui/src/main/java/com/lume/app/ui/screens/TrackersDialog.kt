package com.lume.app.ui.screens
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.tracker.TrackerSearchResult
import com.lume.app.data.tracker.matchesKind
import com.lume.app.domain.Chapter
import com.lume.app.ui.components.TrackerEntryDialog
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch

@Composable
internal fun TrackersDialog(
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
    // Novels are searched and linked on the trackers' novel lists; trackers without one stay out.
    val kind = remember(sourceId) { container.titles.kindOf(sourceId) ?: com.lume.app.domain.MediaKind.Comics }
    val trackers = remember(kind) { container.trackers.forKind(kind) }
    var selectedTrackerId by rememberSaveable { mutableStateOf(trackers.first().id) }
    val selectedTracker = trackers.firstOrNull { it.id == selectedTrackerId } ?: trackers.first()
    var query by rememberSaveable { mutableStateOf(titleName) }
    var results by remember { mutableStateOf<List<TrackerSearchResult>>(emptyList()) }
    var manualId by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var remotePreview by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
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
                    isError = false
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
                        color = if (isError) AppColors.danger else AppColors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.cardHigh)
                            .padding(10.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    trackers.forEach { t ->
                        val selected = t.id == selectedTracker.id
                        val color by animateColorAsState(
                            if (selected) AppColors.accentText else AppColors.textSecondary,
                            Motion.short(),
                            label = "tracker",
                        )
                        Text(
                            text = t.displayName,
                            color = color,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { selectedTrackerId = t.id }
                                .heightIn(min = 48.dp)
                                .wrapContentHeight(Alignment.CenterVertically)
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                if (kind == com.lume.app.domain.MediaKind.Novel) {
                    Text(
                        stringResource(R.string.tracker_novel_only_hint),
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                    )
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
                                    isError = false
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
                            isError = false
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
                            isError = result.isFailure
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
                            isError = false
                            results = runCatching { selectedTracker.search(query, kind) }
                                .onFailure {
                                    isError = true
                                    message = it.message
                                }
                                .getOrDefault(emptyList())
                                .filter { it.matchesKind(kind) }
                            if (results.isEmpty() && message == null) {
                                message = if (kind == com.lume.app.domain.MediaKind.Novel) {
                                    resources.getString(R.string.tracker_novel_empty, selectedTracker.displayName)
                                } else {
                                    resources.getString(R.string.search_empty)
                                }
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.accent,
                        contentColor = AppColors.onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (loading) stringResource(R.string.tracker_searching)
                        else stringResource(R.string.action_search),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                results.forEach { item ->
                    val facts = listOfNotNull(
                        stringResource(item.format?.let(::titleFormatLabel) ?: R.string.tracker_result_unknown_format),
                        item.year?.toString(),
                    ).joinToString(" · ")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    selectedTracker.link(
                                        sourceId,
                                        titleId,
                                        item.remoteId,
                                        item.url,
                                        kind,
                                    )
                                    isError = false
                                    message = resources.getString(R.string.tracker_linked_with, item.title)
                                }
                            }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(item.title, color = AppColors.textPrimary, fontSize = 14.sp)
                        Text(facts, color = AppColors.textSecondary, fontSize = 12.sp)
                    }
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
                            selectedTracker.link(sourceId, titleId, id, kind = kind)
                            isError = false
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
