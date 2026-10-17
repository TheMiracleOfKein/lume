package com.lume.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.UpdateRow
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.LocalShellBottomPadding
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.animatedItem
import java.util.Calendar

/** New chapters across the whole library, newest first, grouped by the day they were found. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: UpdatesViewModel = viewModel { UpdatesViewModel(container) }
    val updates by vm.updates.collectAsState()
    val checking by vm.checking.collectAsState()
    val lastChecked by vm.lastCheckedAt.collectAsState()
    val downloaded by vm.downloaded.collectAsState()
    val queued by vm.queued.collectAsState()
    val groups = remember(updates) { updates.groupBy { startOfDay(it.discoveredAt) }.toList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(LumeInsets.statusBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.nav_updates),
                    color = AppColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (lastChecked > 0) {
                    Text(
                        stringResource(
                            R.string.updates_last_checked,
                            DateUtils.getRelativeTimeSpanString(lastChecked, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                        ),
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                    )
                }
            }
            IconButton(onClick = vm::updateNow, enabled = checking == null) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.action_update_now), tint = AppColors.textPrimary)
            }
        }
        // Fixed height either way, so the list doesn't shift when a check starts or ends.
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            checking?.let { (done, total) ->
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        color = AppColors.accent,
                        trackColor = AppColors.card,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LinearProgressIndicator(color = AppColors.accent, trackColor = AppColors.card, modifier = Modifier.fillMaxSize())
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = checking != null,
            onRefresh = vm::updateNow,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (updates.isEmpty()) {
                // Scrollable, so pull to refresh works on the empty screen too.
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 120.dp)) {
                    item {
                        Text(
                            stringResource(R.string.updates_empty),
                            color = AppColors.textSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalShellBottomPadding.current),
                ) {
                    groups.forEach { (day, rows) ->
                        item(key = "day-$day") {
                            Text(
                                dayLabel(day),
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = animatedItem().padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(rows, key = { "${it.sourceId}\u0000${it.titleId}\u0000${it.chapterId}" }) { row ->
                            val key = "${row.sourceId}\u0000${row.titleId}\u0000${row.chapterId}"
                            UpdateRowItem(
                                row = row,
                                downloaded = key in downloaded,
                                queued = key in queued,
                                onOpen = { nav.openReader(row.sourceId, row.titleId, row.chapterId) },
                                onOpenTitle = { nav.navigate(Routes.title(row.sourceId, row.titleId)) },
                                onDownload = { vm.download(row) },
                                onSetRead = { vm.setRead(row, it) },
                                modifier = animatedItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRowItem(
    row: UpdateRow,
    downloaded: Boolean,
    queued: Boolean,
    onOpen: () -> Unit,
    onOpenTitle: () -> Unit,
    onDownload: () -> Unit,
    onSetRead: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.alpha(if (row.read) 0.45f else 1f)) {
                CoverImage(url = row.coverUrl, width = 40.dp, height = 54.dp, radius = 6.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .alpha(if (row.read) 0.45f else 1f),
            ) {
                Text(row.titleName, color = AppColors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.chapterName, color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                when {
                    downloaded -> Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = stringResource(R.string.cd_downloaded),
                        tint = AppColors.accent,
                    )
                    queued -> CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else -> IconButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Download, stringResource(R.string.action_download), tint = AppColors.textSecondary)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (row.read) R.string.action_mark_unread else R.string.action_mark_read)) },
                onClick = {
                    menu = false
                    onSetRead(!row.read)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_title)) },
                onClick = {
                    menu = false
                    onOpenTitle()
                },
            )
        }
    }
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().run {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

@Composable
private fun dayLabel(day: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    return when {
        day == today -> stringResource(R.string.updates_today)
        today - day in 1..DateUtils.DAY_IN_MILLIS + DateUtils.HOUR_IN_MILLIS -> stringResource(R.string.updates_yesterday)
        else -> DateUtils.formatDateTime(
            LocalContext.current,
            day,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR.takeIf { Calendar.getInstance().get(Calendar.YEAR) == yearOf(day) }.let { it ?: 0 },
        )
    }
}

private fun yearOf(time: Long): Int = Calendar.getInstance().run {
    timeInMillis = time
    get(Calendar.YEAR)
}
