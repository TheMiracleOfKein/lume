package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lume.app.R
import com.lume.app.domain.LibraryDisplay
import com.lume.app.domain.LibraryItem
import com.lume.app.domain.LibrarySettings
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem


/* The library grid and list, and the count badges on each title. */

@Composable
internal fun LibraryContent(
    items: List<LibraryItem>,
    settings: LibrarySettings,
    selected: Set<String>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClick: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit,
) {
    when (settings.display) {
        LibraryDisplay.List -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            items(items, key = { it.key }) { item ->
                LibraryListRow(
                    item = item,
                    settings = settings,
                    selected = item.key in selected,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    modifier = animatedItem(),
                )
            }
        }
        LibraryDisplay.Grid, LibraryDisplay.Compact -> {
            val compact = settings.display == LibraryDisplay.Compact
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (compact) 4 else 3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = bottomPadding + 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    LibraryGridCell(
                        item = item,
                        settings = settings,
                        compact = compact,
                        selected = item.key in selected,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.LibraryGridCell(
    item: LibraryItem,
    settings: LibrarySettings,
    compact: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .animateItem(
                fadeInSpec = Motion.itemFade(),
                placementSpec = Motion.itemPlacement(),
                fadeOutSpec = Motion.itemFade(),
            )
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(shape)
                .background(AppColors.card)
                .then(if (selected) Modifier.border(2.dp, AppColors.accent, shape) else Modifier),
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (compact) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp),
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CountBadges(
                item = item,
                settings = settings,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AppColors.accent.copy(alpha = 0.18f)),
                )
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = AppColors.onAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.accent)
                        .padding(2.dp)
                        .size(16.dp),
                )
            }
        }
        if (!compact) {
            Text(
                item.title,
                color = AppColors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
            )
        }
    }
}

/** Read of total (accent while unread chapters remain), downloaded (neutral) and source (blue) counts, joined like a split pill. */
@Composable
private fun CountBadges(item: LibraryItem, settings: LibrarySettings, modifier: Modifier = Modifier) {
    val total = item.totalChapters.takeIf { settings.unreadBadge && it > 0 }
    val downloaded = item.downloaded.takeIf { settings.downloadedBadge && it > 0 }
    val sources = item.sources.takeIf { settings.sourcesBadge && it > 1 }
    if (total == null && downloaded == null && sources == null) return
    Row(modifier.clip(RoundedCornerShape(4.dp)), verticalAlignment = Alignment.CenterVertically) {
        total?.let {
            val read = (it - item.unread).coerceIn(0, it)
            val description = stringResource(R.string.cd_library_read_of_total, read, it)
            val caughtUp = item.unread <= 0
            Text(
                stringResource(R.string.library_read_of_total, read, it),
                color = if (caughtUp) Color.White else AppColors.onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(if (caughtUp) Color(0xFF3A3F47) else AppColors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .semantics { contentDescription = description },
            )
        }
        downloaded?.let {
            Text(
                "$it",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(Color(0xFF3A3F47))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        sources?.let {
            val description = stringResource(R.string.library_sources_badge, it)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF2F5A86))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                Text(
                    "$it",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListRow(
    item: LibraryItem,
    settings: LibrarySettings,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) AppColors.accentMuted else Color.Transparent,
        Motion.short(),
        label = "rowSelected",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = item.coverUrl, width = 48.dp, height = 64.dp, radius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = AppColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                item.typeLabel,
                // The read/total badge already says how many chapters there are.
                item.totalChapters.takeIf { it > 0 && !settings.unreadBadge }?.let { stringResource(R.string.chapters_count_short, it) },
                if (item.hasNew) stringResource(R.string.badge_new) else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        CountBadges(item, settings, Modifier.padding(start = 8.dp))
    }
}
