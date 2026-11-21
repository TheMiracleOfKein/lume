package com.lume.app.ui.screens

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.story.CODEX_KINDS
import com.lume.app.data.story.parseAliases
import com.lume.app.data.story.saveCodexEntry
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/** Route of the codex screen; [codexRoute] builds it. */
const val CODEX_ROUTE = "novel_codex/{sourceId}/{titleId}?name={name}"

fun codexRoute(sourceId: String, titleId: String, titleName: String): String =
    "novel_codex/${Uri.encode(sourceId)}/${Uri.encode(titleId)}?name=${Uri.encode(titleName)}"

/** The label of a codex entry kind; an unknown kind reads as "Other". */
@StringRes
internal fun codexKindLabel(kind: String): Int = when (kind) {
    "character" -> R.string.codex_kind_character
    "place" -> R.string.codex_kind_place
    "group" -> R.string.codex_kind_group
    "item" -> R.string.codex_kind_item
    "term" -> R.string.codex_kind_term
    else -> R.string.codex_kind_other
}

/**
 * A title's codex: the names saved from the reader's lookup (characters, places, terms) with their
 * notes and other spellings. Search, sort, add, edit and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexScreen(nav: NavHostController, sourceId: String, titleId: String, titleName: String) {
    val app = LocalAppContainer.current
    val entries by remember(sourceId, titleId) { app.dao.codexFor(sourceId, titleId) }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var byName by rememberSaveable { mutableStateOf(true) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shown = filterCodex(entries, query).let { list ->
        if (byName) list.sortedBy { it.entry.name.lowercase() } else list.sortedByDescending { it.entry.updatedAt }
    }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.codex_title), color = AppColors.textPrimary)
                        if (titleName.isNotBlank()) {
                            Text(titleName, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { BackButton(onClick = { nav.popBackStack() }) },
                actions = {
                    IconButton(onClick = { byName = !byName }) {
                        Icon(
                            Icons.Outlined.SortByAlpha,
                            stringResource(if (byName) R.string.glossary_sort_newest else R.string.glossary_sort_name),
                            tint = if (byName) AppColors.accent else AppColors.textSecondary,
                        )
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = AppColors.accent) {
                Icon(Icons.Outlined.Add, stringResource(R.string.codex_add), tint = AppColors.onAccent)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text(stringResource(R.string.codex_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (entries.isEmpty()) stringResource(R.string.codex_empty)
                        else pluralStringResource(R.plurals.codex_count, entries.size, entries.size),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(shown, key = { it.entry.id }) { e ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.surface)
                        .clickable { editing = e.entry.id }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.entry.name, color = AppColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                        Text(stringResource(codexKindLabel(e.entry.kind)), color = AppColors.accentText, fontSize = 12.sp)
                    }
                    if (e.aliases.isNotEmpty()) {
                        Text(e.aliases.joinToString(", ") { it.alias }, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    e.entry.note?.let { Text(it, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            item(key = "end") { Spacer(Modifier.padding(bottom = 88.dp)) }
        }
    }

    val current = entries.firstOrNull { it.entry.id == editing }
    if (adding || current != null) {
        CodexEditDialog(
            initial = current,
            initialName = "",
            onSave = { name, kind, note, aliases ->
                scope.launch { saveCodexEntry(app.dao, sourceId, titleId, current?.entry, name, kind, note, aliases) }
                adding = false
                editing = null
            },
            onDelete = current?.let { c -> { scope.launch { app.dao.deleteCodexEntry(c.entry.id) }; editing = null } },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
}

/**
 * Name, kind, other spellings (comma-separated) and note of a codex entry. [initial] is the entry
 * being changed (null for a new one, which starts with [initialName]); [onDelete] is null for a new one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodexEditDialog(
    initial: CodexEntryWithAliases?,
    initialName: String,
    onSave: (name: String, kind: String, note: String, aliases: List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.entry?.name ?: initialName) }
    var kind by rememberSaveable { mutableStateOf(initial?.entry?.kind ?: "other") }
    var aliases by rememberSaveable { mutableStateOf(initial?.aliases?.joinToString(", ") { it.alias }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.entry?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.codex_new else R.string.codex_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.codex_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.codex_kind), color = AppColors.textSecondary, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CODEX_KINDS.forEach { k ->
                        ChoiceChip(label = stringResource(codexKindLabel(k)), selected = k == kind, onClick = { kind = k })
                    }
                }
                OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.codex_aliases)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.codex_note)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), kind, note, parseAliases(aliases, name)) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = AppColors.danger) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Entries whose name, aliases or note contain [query], ignoring case. */
internal fun filterCodex(entries: List<CodexEntryWithAliases>, query: String): List<CodexEntryWithAliases> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { e ->
        e.entry.name.contains(q, ignoreCase = true) ||
            e.entry.note.orEmpty().contains(q, ignoreCase = true) ||
            e.aliases.any { it.alias.contains(q, ignoreCase = true) }
    }
}
