package com.lume.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.tracker.Tracker
import com.lume.app.data.tracker.TrackerEntry
import com.lume.app.data.tracker.cleanDate
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Edits a title's entry on one tracker: status, score (0–10), chapters read and, where the
 * tracker keeps them, start and finish dates. [onSaved] gets the saved entry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackerEntryDialog(
    tracker: Tracker,
    remoteId: String,
    onDismiss: () -> Unit,
    onSaved: (TrackerEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<TrackerEntry?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var finish by remember { mutableStateOf("") }

    LaunchedEffect(tracker.id, remoteId) {
        val loaded = runCatching { tracker.fetchEntry(remoteId) }.getOrNull()
        if (loaded == null) {
            failed = true
        } else {
            entry = loaded
            chapters = loaded.chaptersRead.toString()
            start = loaded.startDate.orEmpty()
            finish = loaded.finishDate.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_edit_title, tracker.displayName)) },
        text = {
            val current = entry
            when {
                failed -> Text(stringResource(R.string.tracker_edit_failed), color = AppColors.danger)
                current == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.accent, modifier = Modifier.size(28.dp))
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Label(stringResource(R.string.tracker_edit_status))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tracker.statuses.forEach { status ->
                            ChoiceChip(
                                label = stringResource(status.labelRes),
                                selected = current.status == status,
                                onClick = { entry = current.copy(status = status) },
                            )
                        }
                    }
                    Label(stringResource(R.string.tracker_edit_score))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..10).forEach { n ->
                            ChoiceChip(
                                label = if (n == 0) "–" else n.toString(),
                                selected = current.score.toInt() == n,
                                onClick = { entry = current.copy(score = n.toFloat()) },
                            )
                        }
                    }
                    Label(
                        current.totalChapters?.let { stringResource(R.string.tracker_edit_chapters_of, it) }
                            ?: stringResource(R.string.tracker_edit_chapters),
                    )
                    OutlinedTextField(
                        value = chapters,
                        onValueChange = { v -> chapters = v.filter(Char::isDigit).take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (tracker.supportsDates) {
                        DateField(stringResource(R.string.tracker_edit_started), start) { start = it }
                        DateField(stringResource(R.string.tracker_edit_finished), finish) { finish = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = entry != null && !saving,
                onClick = {
                    val next = entry?.copy(
                        chaptersRead = chapters.toIntOrNull() ?: 0,
                        startDate = cleanDate(start),
                        finishDate = cleanDate(finish),
                    ) ?: return@TextButton
                    scope.launch {
                        saving = true
                        val ok = runCatching { tracker.updateEntry(remoteId, next) }.getOrDefault(false)
                        saving = false
                        if (ok) onSaved(next) else failed = true
                    }
                },
            ) { Text(stringResource(if (saving) R.string.ellipsis else R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

/** A `yyyy-MM-dd` field with Today / Clear shortcuts. */
@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    Label(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '-' }.take(10)) },
            singleLine = true,
            placeholder = { Text("2026-01-31", color = AppColors.textTertiary) },
            isError = value.isNotEmpty() && cleanDate(value) == null,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(LocalDate.now().toString()) }) { Text(stringResource(R.string.tracker_edit_today)) }
        if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("×") }
    }
}
