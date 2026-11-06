package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lume.app.ui.components.lumeSliderColors
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.ui.theme.AppColors

// Reader controls: top and bottom bars, chapter dividers, hiding the controls on scroll.

/** Dark scrim behind a top bar: solid enough over the whole bar, fading out in an extra strip below it. */
private fun Modifier.topScrim(): Modifier = drawBehind {
    val fade = 20.dp.toPx()
    val total = size.height + fade
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.87f),
            size.height / total to Color.Black.copy(alpha = 0.7f),
            1f to Color.Transparent,
            startY = 0f,
            endY = total,
        ),
        size = Size(size.width, total),
    )
}

/** Side insets (cutout, landscape navigation bar) for bar content; the scrim stays full width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modifier.barSideInsets(): Modifier = windowInsetsPadding(
    WindowInsets.displayCutout.union(WindowInsets.navigationBarsIgnoringVisibility).only(WindowInsetsSides.Horizontal),
)

/**
 * Bar shown while a chapter loads or failed to load: just Back and the names, if known.
 * On a light reader background ([dark] false) it is a plain bar in the reader theme colours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderBackBar(titleName: String, chapterTitle: String, onBack: () -> Unit, dark: Boolean = true) {
    val main = if (dark) Color.White else AppColors.textPrimary
    val sub = if (dark) Color.White.copy(alpha = 0.75f) else AppColors.textSecondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dark) Modifier.topScrim() else Modifier)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(
            modifier = Modifier
                .barSideInsets()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = main)
            Column(modifier = Modifier.weight(1f).padding(end = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (titleName.isNotEmpty()) {
                    Text(
                        text = titleName,
                        color = main,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (chapterTitle.isNotEmpty()) {
                    Text(
                        text = chapterTitle,
                        color = sub,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderTopBar(
    titleName: String,
    chapterTitle: String,
    inLibrary: Boolean,
    onBack: () -> Unit,
    onOpenTitle: () -> Unit,
    onToggleLibrary: () -> Unit,
    onSettings: () -> Unit,
    onSharePage: () -> Unit,
    onSavePage: () -> Unit,
) {
    var pageMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .topScrim()
            // Stable position whether or not the status bar is currently shown.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(
            modifier = Modifier
                .barSideInsets()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = Color.White)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onOpenTitle),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = titleName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = chapterTitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onToggleLibrary) {
                Icon(
                    if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (inLibrary) R.string.cd_remove_from_library else R.string.cd_add_to_library),
                    tint = if (inLibrary) AppColors.accent else Color.White,
                )
            }
            Box {
                IconButton(onClick = { pageMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.reader_page_menu), tint = Color.White)
                }
                DropdownMenu(expanded = pageMenu, onDismissRequest = { pageMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_page_share)) },
                        onClick = { pageMenu = false; onSharePage() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_page_save)) },
                        onClick = { pageMenu = false; onSavePage() },
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.cd_settings), tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderBottomBar(
    pageLabel: String?,
    /** The page slider: current page, page count of this chapter, and whether it runs right to left. */
    pageIndex: Int,
    pageCount: Int,
    reversed: Boolean,
    onSeek: (Int) -> Unit,
    /** Webtoon auto-scroll; null in the paged modes. */
    autoScroll: Boolean?,
    onToggleAutoScroll: () -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onBookmark: () -> Unit,
    onBookmarkList: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0f),
                    0.15f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.87f),
                ),
            )
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
            .barSideInsets()
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pageCount > 1) {
            PageSlider(pageLabel, pageIndex, pageCount, reversed, onSeek)
        } else if (pageLabel != null) {
            Text(
                text = pageLabel,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = hasPrev) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    stringResource(R.string.cd_prev_chapter),
                    modifier = Modifier.mirrorInRtl(),
                    tint = Color.White.copy(alpha = if (hasPrev) 1f else 0.35f),
                )
            }
            IconButton(onClick = onChapters) {
                Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.cd_chapters), tint = Color.White)
            }
            // Tap pins this spot; long-press lists this title's bookmarks.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onBookmark, onLongClickLabel = stringResource(R.string.cd_long_press_bookmarks), onLongClick = onBookmarkList),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.cd_bookmark_spot), tint = Color.White)
            }
            if (autoScroll != null) {
                IconButton(onClick = onToggleAutoScroll) {
                    Icon(
                        if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        stringResource(if (autoScroll) R.string.reader_auto_scroll_stop else R.string.reader_auto_scroll_start),
                        tint = if (autoScroll) AppColors.accent else Color.White,
                    )
                }
            } else {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Tune, stringResource(R.string.cd_settings), tint = Color.White)
                }
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Outlined.SkipNext,
                    stringResource(R.string.cd_next_chapter),
                    modifier = Modifier.mirrorInRtl(),
                    tint = Color.White.copy(alpha = if (hasNext) 1f else 0.35f),
                )
            }
        }
    }
}

/** Drag to any page of the chapter; the page jumps while the thumb moves. */
@Composable
private fun PageSlider(pageLabel: String?, pageIndex: Int, pageCount: Int, reversed: Boolean, onSeek: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: pageIndex
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (pageLabel != null || dragging != null) "${shown + 1}" else "",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        CompositionLocalProvider(LocalLayoutDirection provides if (reversed) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Slider(
                value = (dragging ?: pageIndex.toFloat()).coerceIn(0f, (pageCount - 1).toFloat()),
                onValueChange = { value ->
                    val before = dragging?.roundToInt()
                    dragging = value
                    if (value.roundToInt() != before) onSeek(value.roundToInt())
                },
                onValueChangeFinished = { dragging = null },
                valueRange = 0f..(pageCount - 1).toFloat(),
                colors = lumeSliderColors(),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (pageLabel != null || dragging != null) "$pageCount" else "",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp),
        )
    }
}

@Composable
internal fun ChapterDividerBand(
    title: String,
    fillViewport: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxSize() else Modifier)
            .padding(vertical = if (fillViewport) 0.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(AppColors.accent.copy(alpha = 0.55f)),
            )
            Text(
                text = stringResource(R.string.reader_chapter_separator, title),
                color = AppColors.accentText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(2f, fill = false),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(AppColors.accent.copy(alpha = 0.55f)),
            )
        }
    }
}

/**
 * Reports a drag that runs into the end of the strip (the scroll left over after the list or
 * pager took what it could). [forwardIsNegative]: dragging toward negative x / y reads forward.
 */
@Composable
internal fun rememberEdgeNotifier(
    horizontal: Boolean,
    forwardIsNegative: Boolean,
    canForward: () -> Boolean,
    canBackward: () -> Boolean,
    onEdge: (forward: Boolean) -> Unit,
): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 16.dp.toPx() }
    val currentOnEdge by rememberUpdatedState(onEdge)
    val currentCanForward by rememberUpdatedState(canForward)
    val currentCanBackward by rememberUpdatedState(canBackward)
    return remember(thresholdPx, horizontal, forwardIsNegative) {
        object : NestedScrollConnection {
            private var leftover = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = if (horizontal) available.x else available.y
                if (delta == 0f) return Offset.Zero
                val forward = (delta < 0f) == forwardIsNegative
                if (forward && currentCanForward() || !forward && currentCanBackward()) {
                    leftover = 0f
                    return Offset.Zero
                }
                leftover += kotlin.math.abs(delta)
                if (leftover >= thresholdPx) {
                    leftover = 0f
                    currentOnEdge(forward)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                leftover = 0f
                return Velocity.Zero
            }
        }
    }
}

/**
 * Hides the reader chrome after real scrolling (about 24 dp), not on every touch that starts a
 * drag; a sloppy tap or a tiny nudge leaves the controls alone.
 */
@Composable
internal fun rememberChromeHider(enabled: Boolean, onHide: () -> Unit): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val currentOnHide by rememberUpdatedState(onHide)
    val currentEnabled by rememberUpdatedState(enabled)
    return remember(thresholdPx) {
        object : NestedScrollConnection {
            private var travelled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && currentEnabled) {
                    travelled += kotlin.math.abs(consumed.x) + kotlin.math.abs(consumed.y)
                    if (travelled >= thresholdPx) {
                        travelled = 0f
                        currentOnHide()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                travelled = 0f
                return Velocity.Zero
            }
        }
    }
}

/** Flips a directional glyph (skip previous/next) in right-to-left layouts, which have no auto-mirrored variant. */
@Composable
private fun Modifier.mirrorInRtl(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) graphicsLayer { scaleX = -1f } else this
