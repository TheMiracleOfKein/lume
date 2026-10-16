package com.lume.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lume.app.R
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * A slice of the bookmarked page, cropped around the saved spot (vertical bias follows the
 * offset: 0 = page top, 1 = page bottom).
 */
@Composable
fun PageBookmarkThumb(url: String?, offset: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 72.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.card),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Outlined.Bookmark, null, tint = AppColors.textTertiary)
        } else {
            AsyncImage(
                model = if (url.startsWith("http")) url else File(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, (offset.coerceIn(0f, 1f) * 2f) - 1f),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One bookmark: thumbnail, chapter, page, note. Swipe either way (or the bin) deletes it. */
@Composable
fun PageBookmarkRow(
    bookmark: PageBookmarkEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss = rememberSwipeToDismissBoxState()
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismiss) {
        snapshotFlow { dismiss.currentValue }
            .first { it != SwipeToDismissBoxValue.Settled }
        currentOnDelete()
    }
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.danger.copy(alpha = 0.25f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, null, tint = AppColors.danger)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.background)
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBookmarkThumb(bookmark.pageImageUrl, bookmark.pageOffset)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bookmark.chapterName,
                    color = AppColors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.bookmark_page, bookmark.pageIndex + 1),
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                )
                bookmark.note?.let {
                    Text(
                        it,
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), tint = AppColors.textSecondary)
            }
        }
    }
}

@Composable
fun BookmarkNoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmark_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.bookmark_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save), color = AppColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
