package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.db.BlockText
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.story.AiRecapState
import com.lume.app.data.story.RecapParagraph
import com.lume.app.data.story.StoryContext
import com.lume.app.data.story.StoryMention
import com.lume.app.data.story.highlightRanges
import com.lume.app.data.story.snippetAround
import com.lume.app.ui.components.SheetHeader
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * What the lookup sheet shows: the searched [term], whether the search still runs, its result
 * ([context], null when it failed or has not come yet) and the pressed paragraph's [original] when
 * the text is shown translated.
 */
data class LookupState(
    val term: String,
    val busy: Boolean,
    val context: StoryContext?,
    val original: String?,
)

/**
 * The content of the lookup sheet (long press on a name): an editable search field, the glossary
 * translation, the codex entry, where the name first appeared and its latest mentions before this
 * spot. Tapping a snippet shows the paragraphs around it in place; nothing here moves the reader.
 */
@Composable
internal fun LookupSheet(
    state: LookupState,
    onSearch: (String) -> Unit,
    loadContext: suspend (StoryMention) -> List<BlockText>,
    onSaveCodex: (existing: CodexEntryEntity?, name: String, kind: String, note: String, aliases: List<String>) -> Unit,
    onDeleteCodex: (CodexEntryEntity) -> Unit,
    onShowOriginal: (() -> Unit)?,
    onAllEntries: () -> Unit,
    onRebuild: () -> Unit,
) {
    val context = state.context
    var field by rememberSaveable(state.term) { mutableStateOf(state.term) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text(stringResource(R.string.lookup_term_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onSearch(field) }) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.action_search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(field) }),
                // A hardware Enter skips the IME action.
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.key == Key.Enter && e.type == KeyEventType.KeyUp) {
                        onSearch(field)
                        true
                    } else {
                        e.key == Key.Enter
                    }
                },
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.lookup_more), tint = AppColors.textSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onShowOriginal != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lookup_show_original)) },
                            onClick = {
                                menuOpen = false
                                onShowOriginal()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lookup_all_entries)) },
                        onClick = {
                            menuOpen = false
                            onAllEntries()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lookup_rebuild)) },
                        onClick = {
                            menuOpen = false
                            onRebuild()
                        },
                    )
                }
            }
        }

        if (state.busy) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = AppColors.accent, strokeWidth = 3.dp)
            }
        }
        context?.glossaryTarget?.let {
            Text(stringResource(R.string.lookup_glossary_translation, it), color = AppColors.accentText, fontSize = 14.sp)
        }

        if (!state.busy) {
            val entry = context?.codexEntry
            if (entry != null) {
                CodexCard(entry, onEdit = { editing = true })
            } else {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.lookup_codex_save)) }
            }
        }

        if (!state.busy && context != null) {
            val first = context.firstMention
            if (first == null) {
                Text(stringResource(R.string.lookup_not_found), color = AppColors.textSecondary, fontSize = 14.sp)
            } else {
                Text(
                    stringResource(R.string.lookup_first_appeared, first.chapter.title),
                    color = AppColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Snippet(first, context.searchTerms, loadContext)
                Text(
                    pluralStringResource(R.plurals.lookup_mentioned_in, context.chapterCount, context.chapterCount),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                )
                val recent = context.recentMentions
                if (recent.isNotEmpty()) {
                    Text(stringResource(R.string.lookup_recent), color = AppColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    recent.forEach { m ->
                        Text(m.chapter.title, color = AppColors.textTertiary, fontSize = 12.sp)
                        Snippet(m, context.searchTerms, loadContext)
                    }
                }
            }
            Text(
                stringResource(R.string.lookup_coverage, context.coverage.indexed, context.coverage.total),
                color = AppColors.textTertiary,
                fontSize = 12.sp,
            )
        } else if (!state.busy) {
            Text(stringResource(R.string.lookup_not_found), color = AppColors.textSecondary, fontSize = 14.sp)
        }
    }

    if (editing) {
        CodexEditDialog(
            initial = context?.codexEntry,
            initialName = state.term,
            onSave = { name, kind, note, aliases ->
                editing = false
                onSaveCodex(context?.codexEntry?.entry, name, kind, note, aliases)
            },
            onDelete = context?.codexEntry?.let { e ->
                {
                    editing = false
                    onDeleteCodex(e.entry)
                }
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun CodexCard(entry: CodexEntryWithAliases, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.entry.name, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            Text(
                stringResource(codexKindLabel(entry.entry.kind)),
                color = AppColors.accentText,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.accentMuted)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Box(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.lookup_codex_edit)) }
        }
        if (entry.aliases.isNotEmpty()) {
            Text(entry.aliases.joinToString(", ") { it.alias }, color = AppColors.textSecondary, fontSize = 13.sp)
        }
        entry.entry.note?.let { Text(it, color = AppColors.textPrimary, fontSize = 14.sp) }
    }
}

/**
 * A mention's paragraph with the searched spellings in bold. A tap shows the paragraph before and
 * after it too (read from the index); another tap folds it back.
 */
@Composable
private fun Snippet(mention: StoryMention, terms: List<String>, loadContext: suspend (StoryMention) -> List<BlockText>) {
    var around by remember(mention) { mutableStateOf<List<BlockText>?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (around != null) {
                    around = null
                } else {
                    scope.launch { around = runCatching { loadContext(mention) }.getOrNull() }
                }
            }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val blocks = around
        if (blocks == null) {
            val (text, ranges) = snippetAround(mention.text, highlightRanges(mention.text, terms))
            Text(highlighted(text, ranges), color = AppColors.textPrimary, fontSize = 14.sp)
        } else {
            blocks.forEach { b ->
                Text(
                    highlighted(b.text, highlightRanges(b.text, terms)),
                    color = if (b.block == mention.block) AppColors.textPrimary else AppColors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** [text] with [ranges] in bold. */
private fun highlighted(text: String, ranges: List<IntRange>): AnnotatedString =
    buildAnnotatedString {
        append(text)
        for (r in ranges) addStyle(SpanStyle(fontWeight = FontWeight.Bold), r.first, r.last + 1)
    }

/** The content of the "Previously…" sheet: the paragraphs before the spot, and a button to go on. */
@Composable
internal fun RecapSheet(paragraphs: List<RecapParagraph>, ai: AiRecapState?, onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetHeader(stringResource(R.string.recap_title))
        when (ai) {
            null -> Unit
            AiRecapState.Loading -> {
                Text(stringResource(R.string.recap_ai_writing), color = AppColors.textTertiary, fontSize = 12.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is AiRecapState.Ready -> {
                Text(stringResource(R.string.recap_ai_label), color = AppColors.textTertiary, fontSize = 12.sp)
                Text(ai.text, color = AppColors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                HorizontalDivider(color = AppColors.textTertiary.copy(alpha = 0.3f))
                Text(stringResource(R.string.recap_last_paragraphs), color = AppColors.textTertiary, fontSize = 12.sp)
            }
            is AiRecapState.Failed -> Text(stringResource(R.string.recap_ai_failed, ai.message), color = AppColors.textTertiary, fontSize = 12.sp)
        }
        if (paragraphs.firstOrNull()?.fromPreviousChapter == true) {
            Text(stringResource(R.string.recap_previous_chapter), color = AppColors.textTertiary, fontSize = 12.sp)
        }
        paragraphs.forEach { p ->
            Text(p.text, color = if (p.fromPreviousChapter) AppColors.textSecondary else AppColors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
        ) { Text(stringResource(R.string.recap_continue)) }
    }
}
