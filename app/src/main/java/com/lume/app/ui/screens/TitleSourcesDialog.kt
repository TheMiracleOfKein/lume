package com.lume.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.TitleSourceEntity
import com.lume.app.data.title.SourceCandidate
import com.lume.app.data.title.SourceLookup
import com.lume.app.domain.TitleMatch
import com.lume.app.ui.theme.AppColors

/** Something that needs a yes first. */
private sealed interface SourceConfirm {
    data class MakeMain(val row: TitleSourceEntity) : SourceConfirm
    data class MoveTo(val candidate: SourceCandidate) : SourceConfirm
    data class Unlink(val row: TitleSourceEntity) : SourceConfirm
}

/**
 * The title's sources: the main one, the linked ones (fill-in order, make main, unlink), the
 * suggestions, and a search of every other source with link / move actions. Every source is one
 * line: name over title, icon actions on the right, rare actions behind ⋮.
 */
@Composable
fun TitleSourcesDialog(vm: TitleViewModel, titleName: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val rows by vm.titleSources.collectAsState()
    val linked = rows.filter { it.state == TitleSourceEntity.LINKED }
    val suggested = rows.filter { it.state == TitleSourceEntity.SUGGESTED }
    val linkedKeys = linked.map { it.linkSourceId to it.linkTitleId }.toSet()
    var confirm by remember { mutableStateOf<SourceConfirm?>(null) }
    fun sourceName(id: String) = container.sources.sourceById(id)?.manifest?.name ?: id
    val mainName = sourceName(vm.sourceId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_sources)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel(stringResource(R.string.sources_main))
                SourceRow(mainName, titleName) {}

                if (linked.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(stringResource(R.string.sources_linked))
                    Text(stringResource(R.string.sources_linked_hint), color = AppColors.textTertiary, fontSize = 12.sp)
                    linked.forEachIndexed { index, row ->
                        SourceRow(sourceName(row.linkSourceId), row.linkTitleName) {
                            // Order only matters with two or more.
                            if (linked.size > 1) {
                                SmallIconButton(Icons.Outlined.ArrowUpward, stringResource(R.string.cd_move_up), enabled = index > 0 && !vm.busy) {
                                    vm.moveSource(row, -1)
                                }
                                SmallIconButton(Icons.Outlined.ArrowDownward, stringResource(R.string.cd_move_down), enabled = index < linked.lastIndex && !vm.busy) {
                                    vm.moveSource(row, 1)
                                }
                            }
                            MoreMenu(
                                enabled = !vm.busy,
                                items = listOf(
                                    MenuAction(stringResource(R.string.sources_make_main), Icons.Outlined.Star) {
                                        confirm = SourceConfirm.MakeMain(row)
                                    },
                                    MenuAction(stringResource(R.string.action_unlink), Icons.Outlined.LinkOff, danger = true) {
                                        confirm = SourceConfirm.Unlink(row)
                                    },
                                ),
                            )
                        }
                    }
                }

                if (suggested.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(stringResource(R.string.sources_suggested))
                    suggested.forEach { row ->
                        SourceRow(sourceName(row.linkSourceId), row.linkTitleName) {
                            SmallIconButton(Icons.Outlined.Close, stringResource(R.string.action_not_now), enabled = !vm.busy) {
                                vm.dismiss(row)
                            }
                            SmallIconButton(Icons.Outlined.AddLink, stringResource(R.string.action_link), enabled = !vm.busy, tint = AppColors.accent) {
                                vm.linkSuggestion(row)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = vm::findOtherSources,
                    enabled = !vm.finding && vm.details != null,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (vm.finding) R.string.sources_finding else R.string.sources_find))
                }

                // Titles found, one line each; sources with nothing to show share a line.
                val lookups = vm.lookups.sortedBy { it.sourceName.lowercase() }
                lookups.filterIsInstance<SourceLookup.Found>().flatMap { it.candidates }.forEach { candidate ->
                    val isLinked = (candidate.sourceId to candidate.titleId) in linkedKeys
                    SourceRow(
                        source = candidate.sourceName,
                        title = candidate.title,
                        note = when {
                            isLinked -> stringResource(R.string.sources_linked_label)
                            candidate.match == TitleMatch.Same -> stringResource(R.string.sources_match_same)
                            candidate.match == TitleMatch.Maybe -> stringResource(R.string.sources_match_maybe)
                            else -> stringResource(R.string.sources_match_other)
                        },
                        noteAccent = isLinked || candidate.match == TitleMatch.Same,
                    ) {
                        if (isLinked) {
                            Icon(
                                Icons.Outlined.Check,
                                stringResource(R.string.sources_linked_label),
                                tint = AppColors.accent,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .size(20.dp),
                            )
                        } else {
                            SmallIconButton(Icons.Outlined.AddLink, stringResource(R.string.action_link), enabled = !vm.busy, tint = AppColors.accent) {
                                vm.link(candidate)
                            }
                            MoreMenu(
                                enabled = !vm.busy,
                                items = listOf(
                                    MenuAction(stringResource(R.string.sources_move_here), Icons.Outlined.SwapHoriz) {
                                        confirm = SourceConfirm.MoveTo(candidate)
                                    },
                                ),
                            )
                        }
                    }
                }
                val searching = lookups.filterIsInstance<SourceLookup.Searching>().map { it.sourceName }
                if (searching.isNotEmpty()) {
                    StatusLine(stringResource(R.string.sources_searching_list, searching.joinToString()))
                }
                val notFound = lookups.filter { it is SourceLookup.Found && it.candidates.isEmpty() }.map { it.sourceName }
                if (notFound.isNotEmpty()) {
                    StatusLine(stringResource(R.string.sources_not_found_list, notFound.joinToString()))
                }
                lookups.filterIsInstance<SourceLookup.Failed>().forEach { StatusLine("${it.sourceName} · ${it.message}") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )

    confirm?.let { c ->
        val (title, text) = when (c) {
            is SourceConfirm.MakeMain -> stringResource(R.string.dialog_make_main_title, sourceName(c.row.linkSourceId)) to
                stringResource(R.string.dialog_make_main_text, mainName)
            is SourceConfirm.MoveTo -> stringResource(R.string.dialog_move_here_title, c.candidate.sourceName) to
                stringResource(R.string.dialog_move_here_text, c.candidate.sourceName, mainName)
            is SourceConfirm.Unlink -> stringResource(R.string.dialog_unlink_source_title, sourceName(c.row.linkSourceId)) to
                stringResource(R.string.dialog_unlink_source_text)
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        is SourceConfirm.MakeMain -> vm.makeMain(c.row)
                        is SourceConfirm.MoveTo -> vm.moveTo(c.candidate)
                        is SourceConfirm.Unlink -> vm.unlink(c.row)
                    }
                }) {
                    Text(
                        stringResource(
                            when (c) {
                                is SourceConfirm.MakeMain -> R.string.sources_make_main
                                is SourceConfirm.MoveTo -> R.string.sources_move_here
                                is SourceConfirm.Unlink -> R.string.action_unlink
                            },
                        ),
                        color = if (c is SourceConfirm.Unlink) AppColors.danger else AppColors.accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

/** One source on one line: its name over the title (and an optional note), actions on the right. */
@Composable
private fun SourceRow(
    source: String,
    title: String,
    note: String? = null,
    noteAccent: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                source,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(title, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) {
                Text(note, color = if (noteAccent) AppColors.accent else AppColors.textTertiary, fontSize = 12.sp, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    tint: Color = AppColors.textSecondary,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) tint else AppColors.textTertiary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private class MenuAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** The less frequent actions of a row, behind ⋮, so their labels never squeeze the row. */
@Composable
private fun MoreMenu(enabled: Boolean, items: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallIconButton(Icons.Outlined.MoreVert, stringResource(R.string.source_action_more), enabled = enabled) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val color = if (item.danger) AppColors.danger else AppColors.textPrimary
                DropdownMenuItem(
                    text = { Text(item.label, color = color) },
                    leadingIcon = { Icon(item.icon, contentDescription = null, tint = color) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text,
        color = AppColors.textTertiary,
        fontSize = 12.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
}
