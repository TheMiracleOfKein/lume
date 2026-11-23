package com.lume.app.translate

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lume.app.AppContainer
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.GLOSSARY_CONFIRMED
import com.lume.app.data.db.GLOSSARY_REJECTED
import com.lume.app.data.db.GLOSSARY_SUGGESTED
import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.data.story.saveCodexEntry
import com.lume.app.llm.nameKey
import com.lume.app.ui.screens.codexKindLabel
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/** Route of the glossary screen; [glossaryRoute] builds it. */
const val GLOSSARY_ROUTE = "novel_glossary/{sourceId}/{titleId}?name={name}"

fun glossaryRoute(sourceId: String, titleId: String, titleName: String): String =
    "novel_glossary/${Uri.encode(sourceId)}/${Uri.encode(titleId)}?name=${Uri.encode(titleName)}"

/**
 * A title's whole glossary: search, sort, add, edit and delete terms, paste many at once and copy
 * them all. Changes reach a reader showing the title translated at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryScreen(nav: NavHostController, sourceId: String, titleId: String, titleName: String) {
    val app = LocalAppContainer.current
    val terms by remember(sourceId, titleId) { app.dao.glossary(sourceId, titleId) }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var byName by rememberSaveable { mutableStateOf(true) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Suggestions from before names were merged may repeat a name in another form.
    androidx.compose.runtime.LaunchedEffect(sourceId, titleId) { tidyGlossary(app.dao, sourceId, titleId) }

    val suggested = terms.filter { it.status == GLOSSARY_SUGGESTED }.sortedBy { it.source.lowercase() }
    val duplicates = if (query.isBlank()) confirmedDuplicates(terms) else emptyList()
    val shown = filterGlossary(terms.filter { it.status != GLOSSARY_SUGGESTED }, query).let { list ->
        if (byName) list.sortedBy { it.source.lowercase() } else list.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.translate_glossary), color = AppColors.textPrimary)
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
                    IconButton(onClick = { importing = true }) {
                        Icon(Icons.Outlined.ContentPaste, stringResource(R.string.glossary_import), tint = AppColors.textSecondary)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(formatGlossary(terms))) }, enabled = terms.isNotEmpty()) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.glossary_copy_all), tint = AppColors.textSecondary)
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = AppColors.accent) {
                Icon(Icons.Outlined.Add, stringResource(R.string.translate_glossary_add), tint = AppColors.onAccent)
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
                        placeholder = { Text(stringResource(R.string.glossary_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (terms.isEmpty()) stringResource(R.string.glossary_empty)
                        else pluralStringResource(R.plurals.glossary_count, terms.size, terms.size),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(stringResource(R.string.translate_glossary_hint), color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            if (suggested.isNotEmpty()) {
                item(key = "suggested-head") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pluralStringResource(R.plurals.glossary_suggested_title, suggested.size, suggested.size),
                                color = AppColors.textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { scope.launch { acceptSuggestions(app, suggested) } }) {
                                Text(stringResource(R.string.glossary_accept_all))
                            }
                        }
                        Text(stringResource(R.string.glossary_suggested_hint), color = AppColors.textSecondary, fontSize = 12.sp)
                    }
                }
                items(suggested, key = { "s:" + it.source }) { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.surface)
                            .clickable { editing = term.source }
                            .heightIn(min = 52.dp)
                            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${term.source} → ${term.target}", color = AppColors.textPrimary, fontSize = 15.sp)
                            term.kind?.let { Text(stringResource(codexKindLabel(it)), color = AppColors.textSecondary, fontSize = 12.sp) }
                        }
                        IconButton(onClick = { scope.launch { acceptSuggestions(app, listOf(term)) } }) {
                            Icon(Icons.Outlined.Check, stringResource(R.string.glossary_accept), tint = AppColors.accent)
                        }
                        IconButton(onClick = {
                            scope.launch { app.dao.setGlossaryStatus(sourceId, titleId, term.source, GLOSSARY_REJECTED, System.currentTimeMillis()) }
                        }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.glossary_reject), tint = AppColors.textSecondary)
                        }
                    }
                }
                item(key = "suggested-end") { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
            }
            if (duplicates.isNotEmpty()) {
                item(key = "dup-head") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.glossary_duplicates_title), color = AppColors.textPrimary, fontSize = 15.sp)
                        Text(stringResource(R.string.glossary_duplicates_hint), color = AppColors.textSecondary, fontSize = 12.sp)
                    }
                }
                items(duplicates, key = { "d:" + it.first().source }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.surface)
                            .heightIn(min = 52.dp)
                            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            group.forEach { t -> Text("${t.source} → ${t.target}", color = AppColors.textPrimary, fontSize = 14.sp) }
                        }
                        TextButton(onClick = { scope.launch { mergeTerms(app, group) } }) { Text(stringResource(R.string.glossary_merge)) }
                    }
                }
                item(key = "dup-end") { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
            }
            items(shown, key = { it.source }) { term ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.surface)
                        .clickable { editing = term.source }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(term.source, color = AppColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("→", color = AppColors.textSecondary, fontSize = 15.sp)
                    Text(term.target, color = AppColors.accentText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
            item(key = "end") { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 88.dp)) }
        }
    }

    val current = terms.firstOrNull { it.source == editing }
    if (adding || current != null) {
        TermDialog(
            initial = current,
            onSave = { source, target ->
                scope.launch {
                    // A renamed term replaces the old one.
                    if (current != null && current.source != source.trim()) app.dao.deleteGlossaryTerm(sourceId, titleId, current.source)
                    saveTerm(app, sourceId, titleId, source, target)
                }
                adding = false
                editing = null
            },
            onDelete = current?.let { t -> { scope.launch { app.dao.deleteGlossaryTerm(sourceId, titleId, t.source) }; editing = null } },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    if (importing) {
        ImportDialog(
            onImport = { lines ->
                parseGlossary(lines).forEach { (s, t) -> saveTerm(app, sourceId, titleId, s, t) }
                importing = false
            },
            onDismiss = { importing = false },
        )
    }
}

@Composable
private fun TermDialog(
    initial: GlossaryTermEntity?,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var source by rememberSaveable { mutableStateOf(initial?.source.orEmpty()) }
    var target by rememberSaveable { mutableStateOf(initial?.target.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.glossary_new_term else R.string.glossary_edit_term)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(source, { source = it }, label = { Text(stringResource(R.string.translate_glossary_source)) }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.translate_glossary_target)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(source, target) }, enabled = source.isNotBlank() && target.isNotBlank()) {
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

@Composable
private fun ImportDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val count = parseGlossary(text).size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.glossary_import)) },
        text = {
            Column {
                Text(stringResource(R.string.glossary_import_hint), color = AppColors.textSecondary, fontSize = 13.sp)
                OutlinedTextField(text, { text = it }, minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = count > 0) {
                Text(pluralStringResource(R.plurals.glossary_import_count, count, count))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The codex kinds a confirmed AI suggestion also becomes a codex entry for. */
private val CODEX_LINKED_KINDS = setOf("character", "place", "group", "item")

/**
 * Accepts AI suggestions (all of one title): they become confirmed terms. A character, place, group
 * or item that the codex doesn't know yet (by any form of its name or other spelling) is added
 * there too, with the translation as another spelling, so story lookup finds it in either language.
 */
internal suspend fun acceptSuggestions(app: AppContainer, terms: List<GlossaryTermEntity>) {
    val first = terms.firstOrNull() ?: return
    val now = System.currentTimeMillis()
    terms.forEach { app.dao.setGlossaryStatus(it.sourceId, it.titleId, it.source, GLOSSARY_CONFIRMED, now) }
    val linked = terms.filter { it.kind in CODEX_LINKED_KINDS }
    if (linked.isEmpty()) return
    val known = app.dao.codexOnce(first.sourceId, first.titleId)
        .flatMap { e -> listOf(e.entry.name) + e.aliases.map { it.alias } }
        .mapTo(HashSet(), ::nameKey)
    for (term in linked) {
        if (!known.add(nameKey(term.source))) continue
        saveCodexEntry(app.dao, term.sourceId, term.titleId, null, term.source, term.kind!!, "", listOf(term.target))
    }
}

/**
 * Merges forms of one name: the first of [group] stays, the others are deleted. Their spellings
 * become other spellings of the name's codex entry, if it has one, so lookup still finds them.
 */
internal suspend fun mergeTerms(app: AppContainer, group: List<GlossaryTermEntity>) {
    val keep = group.firstOrNull() ?: return
    val others = group.drop(1)
    others.forEach { app.dao.deleteGlossaryTerm(it.sourceId, it.titleId, it.source) }
    val key = nameKey(keep.source)
    val entry = app.dao.codexOnce(keep.sourceId, keep.titleId).firstOrNull { e ->
        nameKey(e.entry.name) == key || e.aliases.any { nameKey(it.alias) == key }
    } ?: return
    val aliases = (entry.aliases.map { it.alias } + others.map { it.source }).distinctBy { it.lowercase() }
    saveCodexEntry(app.dao, keep.sourceId, keep.titleId, entry.entry, entry.entry.name, entry.entry.kind, entry.entry.note.orEmpty(), aliases)
}

/** Terms whose original or translation contains [query], ignoring case. */
internal fun filterGlossary(terms: List<GlossaryTermEntity>, query: String): List<GlossaryTermEntity> {
    val q = query.trim()
    if (q.isEmpty()) return terms
    return terms.filter { it.source.contains(q, ignoreCase = true) || it.target.contains(q, ignoreCase = true) }
}

private val SEPARATORS = listOf("→", "=>", "->", "=", "\t", " - ", " — ")

/**
 * Lines like `Shirou = Широ`, `mana core → ядро маны` or tab-separated, one term per line. The
 * first separator splits a line; lines without one, or with an empty side, are skipped.
 */
internal fun parseGlossary(text: String): List<Pair<String, String>> = text.lines().mapNotNull { line ->
    val sep = SEPARATORS.mapNotNull { s -> line.indexOf(s).takeIf { it > 0 }?.let { it to s } }.minByOrNull { it.first } ?: return@mapNotNull null
    val source = line.substring(0, sep.first).trim()
    val target = line.substring(sep.first + sep.second.length).trim()
    if (source.isEmpty() || target.isEmpty()) null else source to target
}

/** All terms as `source = target` lines, sorted by name: what [parseGlossary] reads back. */
internal fun formatGlossary(terms: List<GlossaryTermEntity>): String =
    terms.sortedBy { it.source.lowercase() }.joinToString("\n") { "${it.source} = ${it.target}" }
