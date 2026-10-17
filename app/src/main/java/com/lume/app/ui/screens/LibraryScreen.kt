package com.lume.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.LocalShellBottomPadding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Composable
fun LibraryScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val bookmarks by container.dao.observeBookmarks().collectAsState(initial = emptyList())
    val categories by container.dao.observeCategories().collectAsState(initial = emptyList())
    val titleMeta by container.dao.observeTitleMeta().collectAsState(initial = emptyList())
    val downloads by container.dao.observeDownloads().collectAsState(initial = emptyList())
    val online by container.network.online.collectAsState()
    val metaMap = remember(titleMeta) {
        titleMeta.associateBy { "${it.sourceId}:${it.titleId}" }
    }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var editBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var showAddCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val dao = container.dao
    val downloadedKeys = remember(downloads) {
        downloads.map { "${it.sourceId}:${it.titleId}" }.toSet()
    }

    val filtered = remember(bookmarks, selectedCategoryId, online, downloadedKeys) {
        val base = if (selectedCategoryId == null) bookmarks
        else bookmarks.filter { selectedCategoryId in parseCategoryIds(it.categoryIdsJson) }
        if (online) base
        else base.filter { "${it.sourceId}:${it.titleId}" in downloadedKeys }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(LumeInsets.statusBar),
    ) {
        if (!online) {
            Text(
                stringResource(R.string.library_offline_only),
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChip(
                label = stringResource(R.string.category_all),
                selected = selectedCategoryId == null,
                onClick = { selectedCategoryId = null },
            )
            categories.forEach { cat ->
                CategoryChip(
                    label = cat.name,
                    selected = selectedCategoryId == cat.id,
                    onClick = { selectedCategoryId = cat.id },
                )
            }
            Text(
                text = stringResource(R.string.category_add),
                color = AppColors.accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { showAddCategory = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_empty),
                    color = AppColors.textSecondary,
                    fontSize = 15.sp,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalShellBottomPadding.current)) {
                    items(filtered, key = { "${it.sourceId}:${it.titleId}" }) { item ->
                        val meta = metaMap["${item.sourceId}:${item.titleId}"]
                        val showNew = (meta?.unreadNewCount ?: 0) > 0
                        TitleListRow(
                            modifier = animatedItem(),
                            title = item.titleName,
                            coverUrl = item.coverUrl,
                            typeLabel = item.typeLabel,
                            badge = if (showNew) stringResource(R.string.badge_new) else null,
                            onLongClick = { editBookmark = item },
                            onClick = { nav.navigate(Routes.title(item.sourceId, item.titleId)) },
                        )
                    }
                }
            }
        }
    }

    editBookmark?.let { bookmark ->
        CategoryAssignDialog(
            bookmark = bookmark,
            categories = categories,
            onDismiss = { editBookmark = null },
            onSave = { ids ->
                scope.launch {
                    dao.upsertBookmark(
                        bookmark.copy(categoryIdsJson = encodeCategoryIds(ids)),
                    )
                    editBookmark = null
                }
            },
        )
    }

    if (showAddCategory) {
        AlertDialog(
            onDismissRequest = { showAddCategory = false },
            title = { Text(stringResource(R.string.dialog_new_category)) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newCategoryName.trim()
                        if (name.isEmpty()) return@TextButton
                        scope.launch {
                            dao.upsertCategory(
                                CategoryEntity(
                                    name = name,
                                    sortOrder = categories.size,
                                ),
                            )
                            newCategoryName = ""
                            showAddCategory = false
                        }
                    },
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategory = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (selected) AppColors.accent else AppColors.textSecondary,
        Motion.short(),
        label = "chipText",
    )
    val background by animateColorAsState(
        if (selected) AppColors.accentMuted else AppColors.card,
        Motion.short(),
        label = "chipBackground",
    )
    Text(
        text = label,
        color = color,
        // Same weight either way: a bolder label is wider and would shove the chips beside it.
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun CategoryAssignDialog(
    bookmark: BookmarkEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var selected by remember {
        mutableStateOf(parseCategoryIds(bookmark.categoryIdsJson))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_categories)) },
        text = {
            if (categories.isEmpty()) {
                Text(stringResource(R.string.categories_empty_hint), color = AppColors.textSecondary)
            } else {
                Column {
                    categories.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (cat.id in selected) selected - cat.id else selected + cat.id
                                },
                        ) {
                            Checkbox(
                                checked = cat.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + cat.id else selected - cat.id
                                },
                            )
                            Text(cat.name, color = AppColors.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun parseCategoryIds(json: String): Set<Long> = runCatching {
    Json.parseToJsonElement(json).jsonArray.mapNotNull {
        it.jsonPrimitive.longOrNull ?: it.jsonPrimitive.contentOrNull?.toLongOrNull()
    }.toSet()
}.getOrDefault(emptySet())

private fun encodeCategoryIds(ids: Set<Long>): String =
    buildJsonArray { ids.sorted().forEach { add(JsonPrimitive(it)) } }.toString()
