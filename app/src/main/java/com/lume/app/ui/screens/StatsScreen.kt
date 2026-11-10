package com.lume.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.AppContainer
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.domain.ReadingStats
import com.lume.app.domain.StatChapterRead
import com.lume.app.domain.StatSession
import com.lume.app.domain.computeReadingStats
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter

class StatsViewModel(app: AppContainer) : ViewModel() {
    /** Null until the first read of the database. */
    val stats: StateFlow<ReadingStats?> = combine(
        app.dao.observeSessions(),
        app.dao.observeChapterReadTimes(),
    ) { sessions, reads ->
        computeReadingStats(
            sessions = sessions.map { StatSession(it.sourceId, it.titleId, it.titleName, it.startedAt, it.durationMs, it.pagesRead) },
            chapterReads = reads.map { StatChapterRead(it.sourceId, it.titleId, it.readAt) },
            now = System.currentTimeMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: StatsViewModel = viewModel { StatsViewModel(container) }
    val stats by vm.stats.collectAsState()

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), color = AppColors.textPrimary) },
                navigationIcon = { BackButton(onClick = { nav.popBackStack() }) },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        val data = stats
        Box(Modifier.padding(padding).fillMaxSize().background(AppColors.background)) {
            when {
                data == null -> Unit
                data.isEmpty -> EmptyBox(stringResource(R.string.stats_empty))
                else -> StatsContent(data, nav)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: ReadingStats, nav: NavHostController) {
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val longest = stats.months.maxOf { it.durationMs }.coerceAtLeast(1L)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "tiles") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(durationText(stats.durationMs), stringResource(R.string.stats_time), Modifier.weight(1f))
                StatTile(stats.chapters.toString(), stringResource(R.string.stats_chapters), Modifier.weight(1f))
                StatTile(stats.pages.toString(), stringResource(R.string.stats_pages), Modifier.weight(1f))
            }
        }
        item(key = "months-title") { SectionLabel(stringResource(R.string.stats_by_month)) }
        items(stats.months, key = { "m-${it.month}" }) { month ->
            val fraction by animateFloatAsState(month.durationMs.toFloat() / longest, Motion.medium(), label = "bar")
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        month.month.format(monthFormat).replaceFirstChar { it.titlecase() },
                        color = AppColors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (month.durationMs == 0L && month.chapters == 0) "-" else durationText(month.durationMs),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.card)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.accent))
                }
                if (month.chapters > 0 || month.pages > 0) {
                    Text(
                        pluralStringResource(R.plurals.stats_chapters_count, month.chapters, month.chapters) + " · " +
                            pluralStringResource(R.plurals.stats_pages_count, month.pages, month.pages),
                        color = AppColors.textTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (stats.titles.isNotEmpty()) {
            item(key = "titles-title") { SectionLabel(stringResource(R.string.stats_by_title)) }
            items(stats.titles, key = { "t-${it.sourceId}:${it.titleId}" }) { title ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { nav.navigate(Routes.title(title.sourceId, title.titleId)) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        title.titleName,
                        color = AppColors.textPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        durationText(title.durationMs) + " · " +
                            pluralStringResource(R.plurals.stats_chapters_count, title.chapters, title.chapters) + " · " +
                            pluralStringResource(R.plurals.stats_pages_count, title.pages, title.pages),
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AppColors.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(AppColors.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = AppColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "3 h 20 min", "45 min" or "under a minute". */
@Composable
private fun durationText(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes == 0 -> stringResource(R.string.stats_under_minute)
        hours == 0 -> pluralStringResource(R.plurals.stats_minutes, rest, rest)
        rest == 0 -> pluralStringResource(R.plurals.stats_hours, hours, hours)
        else -> pluralStringResource(R.plurals.stats_hours, hours, hours) + " " + pluralStringResource(R.plurals.stats_minutes, rest, rest)
    }
}
