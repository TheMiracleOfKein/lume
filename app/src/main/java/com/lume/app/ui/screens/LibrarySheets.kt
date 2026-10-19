package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.domain.LibraryDisplay
import com.lume.app.domain.LibraryFilter
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.LibrarySort
import com.lume.app.domain.TriState
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.theme.AppColors


/* The library options sheet (filter, sort, display) and its dialogs. */

@Composable
internal fun LibrarySheet(settings: LibrarySettings, onChange: ((LibrarySettings) -> LibrarySettings) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.library_tab_filter, R.string.library_tab_sort, R.string.library_tab_display)
    Column(Modifier.padding(bottom = 24.dp)) {
        @OptIn(ExperimentalMaterial3Api::class)
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = AppColors.surface,
            contentColor = AppColors.accent,
            divider = {},
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = AppColors.accent)
            },
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(stringResource(label), color = if (tab == index) AppColors.accent else AppColors.textSecondary) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> LibraryFilter.entries.forEach { filter ->
                val state = settings.filter(filter)
                SheetRow(
                    label = stringResource(filter.labelRes),
                    icon = when (state) {
                        TriState.Off -> Icons.Outlined.CheckBoxOutlineBlank
                        TriState.Include -> Icons.Outlined.CheckBox
                        TriState.Exclude -> Icons.Outlined.IndeterminateCheckBox
                    },
                    iconTint = if (state == TriState.Off) AppColors.textSecondary else AppColors.accent,
                    onClick = { onChange { it.copy(filters = it.filters + (filter to state.next())) } },
                )
            }
            1 -> LibrarySort.entries.forEach { sort ->
                val active = settings.sort == sort
                SheetRow(
                    label = stringResource(sort.labelRes),
                    icon = when {
                        !active -> null
                        settings.ascending -> Icons.Outlined.ArrowUpward
                        else -> Icons.Outlined.ArrowDownward
                    },
                    iconTint = AppColors.accent,
                    onClick = {
                        onChange {
                            if (it.sort == sort) it.copy(ascending = !it.ascending)
                            // Newest / most first is what people want from the time and count sorts.
                            else it.copy(sort = sort, ascending = sort == LibrarySort.Title)
                        }
                    },
                )
            }
            else -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.library_layout),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryDisplay.entries.forEach { mode ->
                        com.lume.app.ui.components.ChoiceChip(
                            label = stringResource(mode.labelRes),
                            selected = settings.display == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { onChange { it.copy(display = mode) } },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    label = stringResource(R.string.library_badge_unread),
                    checked = settings.unreadBadge,
                    onCheckedChange = { value -> onChange { it.copy(unreadBadge = value) } },
                )
                SwitchRow(
                    label = stringResource(R.string.library_badge_downloaded),
                    checked = settings.downloadedBadge,
                    onCheckedChange = { value -> onChange { it.copy(downloadedBadge = value) } },
                )
                SwitchRow(
                    label = stringResource(R.string.library_badge_sources),
                    checked = settings.sourcesBadge,
                    onCheckedChange = { value -> onChange { it.copy(sourcesBadge = value) } },
                )
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, icon: ImageVector?, iconTint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint)
        }
        Spacer(Modifier.width(16.dp))
        Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
    }
}

private val LibraryFilter.labelRes: Int
    get() = when (this) {
        LibraryFilter.Unread -> R.string.library_filter_unread
        LibraryFilter.Downloaded -> R.string.library_filter_downloaded
        LibraryFilter.Started -> R.string.library_filter_started
        LibraryFilter.Tracking -> R.string.library_filter_tracking
        LibraryFilter.Completed -> R.string.library_filter_completed
    }

private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.Title -> R.string.library_sort_title
        LibrarySort.LastRead -> R.string.library_sort_last_read
        LibrarySort.DateAdded -> R.string.library_sort_date_added
        LibrarySort.Unread -> R.string.library_sort_unread
        LibrarySort.LatestChapter -> R.string.library_sort_latest
    }

private val LibraryDisplay.labelRes: Int
    get() = when (this) {
        LibraryDisplay.Grid -> R.string.library_display_grid
        LibraryDisplay.Compact -> R.string.library_display_compact
        LibraryDisplay.List -> R.string.library_display_list
    }

@Composable
internal fun CategoryAssignDialog(
    initial: Set<Long>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_categories)) },
        text = {
            if (categories.isEmpty()) {
                Text(stringResource(R.string.categories_empty_hint), color = AppColors.textSecondary)
            } else {
                Column {
                    categories.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (cat.id in selected) selected - cat.id else selected + cat.id
                                },
                        ) {
                            Checkbox(
                                checked = cat.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + cat.id else selected - cat.id
                                },
                            )
                            Text(cat.name, color = AppColors.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Name a reading list of [count] titles, then share it or save it as a file. */
@Composable
internal fun ExportListDialog(
    initialName: String,
    count: Int,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_list_export_title)) },
        text = {
            Column {
                Text(
                    pluralStringResource(R.plurals.reading_list_export_text, count, count),
                    color = AppColors.textSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.reading_list_name)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onShare(name.trim()) }) {
                Text(stringResource(R.string.action_share))
            }
        },
        dismissButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) {
                Text(stringResource(R.string.reading_list_save_file))
            }
        },
    )
}
