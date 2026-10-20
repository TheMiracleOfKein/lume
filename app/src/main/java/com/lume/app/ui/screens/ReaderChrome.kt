package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Settings
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.87f), 0.7f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent))
            // Stable position whether or not the status bar is currently shown.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenTitle),
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
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.cd_settings), tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderBottomBar(
    pageLabel: String?,
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
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.87f))))
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pageLabel != null) {
            Text(
                text = pageLabel,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.cd_prev_chapter), tint = Color.White)
            }
            IconButton(onClick = onChapters) {
                Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.cd_chapters), tint = Color.White)
            }
            // Tap pins this spot; long-press lists this title's bookmarks.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onBookmark, onLongClick = onBookmarkList),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.cd_bookmark_spot), tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.cd_settings), tint = Color.White)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.SkipNext, stringResource(R.string.cd_next_chapter), tint = Color.White)
            }
        }
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
                color = AppColors.accent,
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
