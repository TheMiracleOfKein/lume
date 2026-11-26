package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.domain.Chapter
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.SheetHeader
import com.lume.app.ui.theme.AppColors

// Reader controls: the top and bottom bars and the chapter list. Bars take their colours from the
// reader palette and their height from the content, so they grow with a large system font.

/** Side insets (cutout, landscape navigation bar) for bar content; the bar background stays full width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modifier.barSideInsets(): Modifier = windowInsetsPadding(
    WindowInsets.displayCutout.union(WindowInsets.navigationBarsIgnoringVisibility).only(WindowInsetsSides.Horizontal),
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun NovelTopBar(
    titleName: String,
    chapterTitle: String,
    palette: NovelPalette,
    onBack: () -> Unit,
    onOpenTitle: () -> Unit,
    modifier: Modifier = Modifier,
    /** The team that translated this chapter; null when there is no other translation to pick. */
    translation: String? = null,
    onTranslation: () -> Unit = {},
    /** Opens the on-device translation sheet; null hides the button (no model for this title's language). */
    onTranslate: (() -> Unit)? = null,
    /** The chapter is shown translated (the button is highlighted). */
    translated: Boolean = false,
    /** "Waiting for OpenRouter… 12 s", "Translating: 3 of 40 paragraphs", "Translation failed"; tapping it opens the sheet. */
    translateStatus: String? = null,
    /** Opens the title's codex of saved names; null hides the button. */
    onCodex: (() -> Unit)? = null,
    /** Shows "Previously…" for the reading spot now; null hides the button. */
    onRecap: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.bar)
            // Stable position whether or not the status bar is currently shown.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(
            modifier = Modifier
                .barSideInsets()
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = palette.text)
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp).padding(vertical = 6.dp)) {
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenTitle)) {
                    Text(
                        text = titleName,
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chapterTitle.isNotEmpty()) {
                        Text(
                            text = chapterTitle,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (translation != null) {
                    val label = stringResource(R.string.reader_translation_switch, translation)
                    Row(
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onTranslation)
                            .semantics(mergeDescendants = true) { contentDescription = label }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = translation,
                            color = palette.accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    }
                }
                if (translateStatus != null) {
                    Text(
                        text = translateStatus,
                        color = palette.accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onTranslate != null) Modifier.clickable(role = Role.Button, onClick = onTranslate) else Modifier,
                    )
                }
            }
            if (onRecap != null) {
                IconButton(onClick = onRecap) {
                    Icon(Icons.Outlined.History, stringResource(R.string.recap_open), tint = palette.text)
                }
            }
            if (onCodex != null) {
                IconButton(onClick = onCodex) {
                    Icon(Icons.Outlined.AutoStories, stringResource(R.string.codex_open), tint = palette.text)
                }
            }
            if (onTranslate != null) {
                IconButton(onClick = onTranslate) {
                    Icon(
                        Icons.Outlined.Translate,
                        stringResource(R.string.translate_title),
                        tint = if (translated) palette.accent else palette.text,
                    )
                }
            }
        }
    }
}

/** "Back after a break? See where you stopped": a quiet offer of the recap, above the bottom bar. */
@Composable
internal fun RecapOffer(palette: NovelPalette, onShow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .barSideInsets()
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bar)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.recap_offer),
            color = palette.text,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        TextButton(onClick = onShow) { Text(stringResource(R.string.recap_offer_show), color = palette.accent) }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, stringResource(R.string.action_close), tint = palette.textSecondary)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun NovelBottomBar(
    percent: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    palette: NovelPalette,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Starts read aloud (or opens its settings while it runs); null hides the button. */
    onReadAloud: (() -> Unit)? = null,
    /** Auto-scroll is running (true), stopped (false), or not offered (null, e.g. page by page). */
    autoScroll: Boolean? = null,
    onToggleAutoScroll: () -> Unit = {},
) {
    val progressLabel = stringResource(R.string.novel_chapter_progress, percent)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.bar)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
            .barSideInsets()
            .padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = progressLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "$percent%",
                color = palette.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                softWrap = false,
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.weight(1f),
                color = palette.accent,
                trackColor = palette.textSecondary.copy(alpha = 0.3f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = hasPrev) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    stringResource(R.string.cd_prev_chapter),
                    tint = palette.text.copy(alpha = if (hasPrev) 1f else 0.35f),
                )
            }
            IconButton(onClick = onChapters) {
                Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.novel_chapter_list), tint = palette.text)
            }
            if (onReadAloud != null) {
                IconButton(onClick = onReadAloud) {
                    Icon(Icons.Outlined.Headphones, stringResource(R.string.read_aloud), tint = palette.text)
                }
            }
            if (autoScroll != null) {
                IconButton(onClick = onToggleAutoScroll) {
                    Icon(
                        if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        stringResource(if (autoScroll) R.string.reader_auto_scroll_stop else R.string.reader_auto_scroll_start),
                        tint = if (autoScroll) palette.accent else palette.text,
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.cd_settings), tint = palette.text)
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Outlined.SkipNext,
                    stringResource(R.string.cd_next_chapter),
                    tint = palette.text.copy(alpha = if (hasNext) 1f else 0.35f),
                )
            }
        }
    }
}

/** The title's chapters in reading order; the one being read is marked and scrolled into view. */
@Composable
internal fun NovelChapterList(
    chapters: List<Chapter>,
    currentId: String,
    onPick: (Chapter) -> Unit,
    onClose: () -> Unit,
) {
    val current = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val state = rememberLazyListState()
    LaunchedEffect(current) { state.scrollToItem((current - 2).coerceAtLeast(0)) }
    val currentLabel = stringResource(R.string.novel_current_chapter)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SheetHeader(stringResource(R.string.novel_chapter_list), onClose = onClose)
        LazyColumn(state = state, modifier = Modifier.padding(bottom = 16.dp)) {
            itemsIndexed(chapters, key = { _, c -> c.id }) { index, chapter ->
                val selected = index == current
                Text(
                    text = chapter.title,
                    color = if (selected) AppColors.accentText else AppColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onPick(chapter) }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 12.dp)
                        .semantics { if (selected) stateDescription = currentLabel },
                )
            }
        }
    }
}
