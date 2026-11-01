package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import com.lume.app.data.library.ReadingLists
import com.lume.app.ui.components.shareFile
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.domain.LibraryItem
import com.lume.app.ui.components.LocalShellBottomPadding
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: LibraryViewModel = viewModel { LibraryViewModel(container) }
    val items by vm.visible.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val categories by vm.categories.collectAsState()
    val settings by vm.settings.collectAsState()
    val online by vm.online.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var categoriesDialog by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    // Titles of the reading list being exported, and its name while the file picker is open.
    var exporting by remember { mutableStateOf<List<LibraryItem>?>(null) }
    var savingName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val saveList = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReadingLists.MIME)) { uri ->
        val items = exporting
        val name = savingName
        exporting = null
        savingName = null
        if (uri != null && items != null && name != null) {
            vm.saveList(uri, name, items) { ok ->
                Toast.makeText(
                    context,
                    if (ok) R.string.reading_list_saved else R.string.reading_list_save_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val bottomPadding = LocalShellBottomPadding.current

    BackHandler(enabled = vm.selecting) { vm.clearSelection() }
    BackHandler(enabled = !vm.selecting && vm.searchOpen) { vm.closeSearch() }

    fun open(item: LibraryItem) = nav.navigate(Routes.title(item.sourceId, item.titleId))
    val onClick: (LibraryItem) -> Unit = { if (vm.selecting) vm.toggle(it) else open(it) }
    val onLongClick: (LibraryItem) -> Unit = { if (vm.selecting) vm.selectRange(it) else vm.toggle(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(LumeInsets.statusBar),
    ) {
        Column(Modifier.fillMaxSize()) {
            LibraryTopBar(
                vm = vm,
                count = items.size,
                filtering = settings.activeFilterCount > 0,
                onOptions = { sheetOpen = true },
                onShare = { exporting = vm.exportItems() },
            )
            if (!online) {
                Text(
                    stringResource(R.string.library_offline_only),
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            CategoryRow(
                categories = categories,
                selectedId = vm.categoryId,
                onSelect = { vm.categoryId = it },
                onEdit = { nav.navigate(Routes.Categories) },
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    !loaded -> Unit
                    items.isEmpty() -> Text(
                        text = stringResource(
                            if (vm.search.isNotBlank() || settings.activeFilterCount > 0) R.string.library_no_results
                            else R.string.library_empty,
                        ),
                        color = AppColors.textSecondary,
                        fontSize = 15.sp,
                    )
                    else -> LibraryContent(
                        items = items,
                        settings = settings,
                        selected = vm.selected,
                        bottomPadding = bottomPadding + if (vm.selecting) SelectionBarHeight else 0.dp,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = vm.selecting,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding),
            enter = Motion.barEnterFromBottom,
            exit = Motion.barExitToBottom,
        ) {
            SelectionBar(
                onCategories = { categoriesDialog = true },
                onMarkRead = vm::markRead,
                onMarkUnread = vm::markUnread,
                onDownload = vm::downloadUnread,
                onShare = { exporting = vm.exportItems() },
                onRemove = { confirmRemove = true },
            )
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.surface,
        ) {
            LibrarySheet(settings = settings, onChange = vm::updateSettings)
        }
    }

    if (categoriesDialog) {
        val initial = remember { vm.commonCategories() }
        CategoryAssignDialog(
            initial = initial,
            categories = categories,
            onDismiss = { categoriesDialog = false },
            onSave = { ids ->
                vm.setCategories(ids)
                categoriesDialog = false
            },
        )
    }

    val exportItems = exporting
    if (exportItems != null && savingName == null) {
        val fallback = stringResource(R.string.nav_library)
        ExportListDialog(
            initialName = remember(exportItems) { vm.exportName(fallback) },
            count = exportItems.size,
            onDismiss = { exporting = null },
            onShare = { name ->
                exporting = null
                vm.clearSelection()
                vm.shareList(name, exportItems) { file -> shareFile(context, file, ReadingLists.MIME, name) }
            },
            onSave = { name ->
                savingName = name
                vm.clearSelection()
                saveList.launch(vm.listFileName(name, exportItems))
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.dialog_remove_titles_title)) },
            text = { Text(stringResource(R.string.dialog_remove_titles_text, vm.selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFromLibrary()
                    confirmRemove = false
                }) { Text(stringResource(R.string.action_remove), color = AppColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private val SelectionBarHeight = 64.dp

@Composable
private fun LibraryTopBar(
    vm: LibraryViewModel,
    count: Int,
    filtering: Boolean,
    onOptions: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            vm.selecting -> {
                IconButton(onClick = vm::clearSelection) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.cd_cancel), tint = AppColors.textPrimary)
                }
                Text(
                    stringResource(R.string.library_selected, vm.selected.size),
                    color = AppColors.textPrimary,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::selectAll) {
                    Icon(Icons.Outlined.SelectAll, stringResource(R.string.cd_select_all), tint = AppColors.textPrimary)
                }
                IconButton(onClick = vm::invertSelection) {
                    Icon(Icons.Outlined.FlipToBack, stringResource(R.string.cd_invert_selection), tint = AppColors.textPrimary)
                }
            }
            vm.searchOpen -> {
                IconButton(onClick = vm::closeSearch) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_close), tint = AppColors.textPrimary)
                }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TextField(
                    value = vm.search,
                    onValueChange = { vm.search = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_hint), color = AppColors.textTertiary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AppColors.textPrimary,
                        unfocusedTextColor = AppColors.textPrimary,
                        cursorColor = AppColors.accent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
                if (vm.search.isNotEmpty()) {
                    IconButton(onClick = { vm.search = "" }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.action_clear), tint = AppColors.textSecondary)
                    }
                }
            }
            else -> {
                Text(
                    stringResource(R.string.nav_library),
                    color = AppColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp),
                )
                if (count > 0) {
                    Text(
                        "$count",
                        color = AppColors.textTertiary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (count > 0) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.reading_list_share), tint = AppColors.textPrimary)
                    }
                }
                IconButton(onClick = vm::openSearch) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.action_search), tint = AppColors.textPrimary)
                }
                IconButton(onClick = onOptions) {
                    Box {
                        Icon(Icons.Outlined.FilterList, stringResource(R.string.cd_library_options), tint = AppColors.textPrimary)
                        if (filtering) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AppColors.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryChip(stringResource(R.string.category_all), selectedId == null) { onSelect(null) }
        categories.forEach { cat -> CategoryChip(cat.name, selectedId == cat.id) { onSelect(cat.id) } }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, stringResource(R.string.cd_edit_categories), tint = AppColors.textSecondary, modifier = Modifier.size(20.dp))
        }
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
private fun SelectionBar(
    onCategories: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(SelectionBarHeight - 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.cardHigh),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionAction(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.dialog_categories), onCategories)
        SelectionAction(Icons.Outlined.DoneAll, stringResource(R.string.action_mark_read), onMarkRead)
        SelectionAction(Icons.Outlined.RemoveDone, stringResource(R.string.action_mark_unread), onMarkUnread)
        SelectionAction(Icons.Outlined.Download, stringResource(R.string.action_download_unread), onDownload)
        SelectionAction(Icons.Outlined.Share, stringResource(R.string.reading_list_share), onShare)
        SelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_remove), onRemove, AppColors.danger)
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = AppColors.textPrimary) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
}
