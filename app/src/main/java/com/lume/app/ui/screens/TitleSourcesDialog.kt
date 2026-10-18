package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
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
 * suggestions, and a search of every other source with link / move buttons.
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
                SourceLine(mainName, titleName)

                if (linked.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel(stringResource(R.string.sources_linked))
                    Text(stringResource(R.string.sources_linked_hint), color = AppColors.textTertiary, fontSize = 12.sp)
                    linked.forEachIndexed { index, row ->
                        Column(Modifier.padding(top = 8.dp)) {
                            SourceLine(sourceName(row.linkSourceId), row.linkTitleName)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.moveSource(row, -1) }, enabled = index > 0 && !vm.busy) {
                                    Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.cd_move_up), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { vm.moveSource(row, 1) }, enabled = index < linked.lastIndex && !vm.busy) {
                                    Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.cd_move_down), modifier = Modifier.size(18.dp))
                                }
                                TextButton(onClick = { confirm = SourceConfirm.MakeMain(row) }, enabled = !vm.busy) {
                                    Text(stringResource(R.string.sources_make_main))
                                }
                                TextButton(onClick = { confirm = SourceConfirm.Unlink(row) }, enabled = !vm.busy) {
                                    Text(stringResource(R.string.action_unlink), color = AppColors.danger)
                                }
                            }
                        }
                    }
                }

                if (suggested.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel(stringResource(R.string.sources_suggested))
                    suggested.forEach { row ->
                        Column(Modifier.padding(top = 8.dp)) {
                            SourceLine(sourceName(row.linkSourceId), row.linkTitleName)
                            Row {
                                TextButton(onClick = { vm.linkSuggestion(row) }, enabled = !vm.busy) {
                                    Text(stringResource(R.string.action_link))
                                }
                                TextButton(onClick = { vm.dismiss(row) }, enabled = !vm.busy) {
                                    Text(stringResource(R.string.action_not_now))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::findOtherSources,
                    enabled = !vm.finding && vm.details != null,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (vm.finding) R.string.sources_finding else R.string.sources_find))
                }

                vm.lookups.sortedBy { it.sourceName.lowercase() }.forEach { lookup ->
                    Spacer(Modifier.height(10.dp))
                    when (lookup) {
                        is SourceLookup.Searching -> LookupStatus(lookup.sourceName, stringResource(R.string.sources_searching_one))
                        is SourceLookup.Failed -> LookupStatus(lookup.sourceName, lookup.message)
                        is SourceLookup.Found -> if (lookup.candidates.isEmpty()) {
                            LookupStatus(lookup.sourceName, stringResource(R.string.sources_not_found))
                        } else {
                            Text(lookup.sourceName, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            lookup.candidates.forEach { candidate ->
                                CandidateRow(
                                    candidate = candidate,
                                    linked = (candidate.sourceId to candidate.titleId) in linkedKeys,
                                    enabled = !vm.busy,
                                    onLink = { vm.link(candidate) },
                                    onMove = { confirm = SourceConfirm.MoveTo(candidate) },
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun SourceLine(source: String, title: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(source, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(title, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LookupStatus(source: String, status: String) {
    Text(
        "$source · $status",
        color = AppColors.textTertiary,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CandidateRow(
    candidate: SourceCandidate,
    linked: Boolean,
    enabled: Boolean,
    onLink: () -> Unit,
    onMove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(candidate.title, color = AppColors.textPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val label = when {
            linked -> stringResource(R.string.sources_linked_label)
            candidate.match == TitleMatch.Same -> stringResource(R.string.sources_match_same)
            candidate.match == TitleMatch.Maybe -> stringResource(R.string.sources_match_maybe)
            else -> stringResource(R.string.sources_match_other)
        }
        Text(
            label,
            color = if (candidate.match == TitleMatch.Same || linked) AppColors.accent else AppColors.textTertiary,
            fontSize = 12.sp,
        )
        if (!linked) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.action_link),
                    color = if (enabled) AppColors.accent else AppColors.textTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled, onClick = onLink)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Text(
                    stringResource(R.string.sources_move_here),
                    color = if (enabled) AppColors.textSecondary else AppColors.textTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled, onClick = onMove)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
