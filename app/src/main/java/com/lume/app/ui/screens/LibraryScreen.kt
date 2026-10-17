package com.lume.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.domain.LibraryDisplay
import com.lume.app.domain.LibraryFilter
import com.lume.app.domain.LibraryItem
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.LibrarySort
import com.lume.app.domain.TriState
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.LocalShellBottomPadding
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem

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
    var categoriesDialog by remember { mutableStateOf<Set<Long>?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
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
            LibraryTopBar(vm = vm, count = items.size, filtering = settings.activeFilterCount > 0, onOptions = { sheetOpen = true })
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
                onCategories = { categoriesDialog = vm.commonCategories() },
                onMarkRead = vm::markRead,
                onMarkUnread = vm::markUnread,
                onDownload = vm::downloadUnread,
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

    categoriesDialog?.let { initial ->
        CategoryAssignDialog(
            initial = initial,
            categories = categories,
            onDismiss = { categoriesDialog = null },
            onSave = { ids ->
                vm.setCategories(ids)
                categoriesDialog = null
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
private fun LibraryTopBar(vm: LibraryViewModel, count: Int, filtering: Boolean, onOptions: () -> Unit) {
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
private fun LibraryContent(
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

/** Unread (accent) and downloaded (neutral) counts, joined like a split pill. */
@Composable
private fun CountBadges(item: LibraryItem, settings: LibrarySettings, modifier: Modifier = Modifier) {
    val unread = item.unread.takeIf { settings.unreadBadge && it > 0 }
    val downloaded = item.downloaded.takeIf { settings.downloadedBadge && it > 0 }
    if (unread == null && downloaded == null) return
    Row(modifier.clip(RoundedCornerShape(4.dp))) {
        unread?.let {
            Text(
                "$it",
                color = AppColors.onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(AppColors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
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
                item.totalChapters.takeIf { it > 0 }?.let { stringResource(R.string.chapters_count_short, it) },
                if (item.hasNew) stringResource(R.string.badge_new) else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        CountBadges(item, settings, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SelectionBar(
    onCategories: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
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
        SelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_remove), onRemove, AppColors.danger)
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = AppColors.textPrimary) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
}

@Composable
private fun LibrarySheet(settings: LibrarySettings, onChange: ((LibrarySettings) -> LibrarySettings) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.library_tab_filter, R.string.library_tab_sort, R.string.library_tab_display)
    Column(Modifier.padding(bottom = 24.dp)) {
        @OptIn(ExperimentalMaterial3Api::class)
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = AppColors.surface,
            contentColor = AppColors.accent,
            divider = {},
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = AppColors.accent)
            },
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(stringResource(label), color = if (tab == index) AppColors.accent else AppColors.textSecondary) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> LibraryFilter.entries.forEach { filter ->
                val state = settings.filter(filter)
                SheetRow(
                    label = stringResource(filter.labelRes),
                    icon = when (state) {
                        TriState.Off -> Icons.Outlined.CheckBoxOutlineBlank
                        TriState.Include -> Icons.Outlined.CheckBox
                        TriState.Exclude -> Icons.Outlined.IndeterminateCheckBox
                    },
                    iconTint = if (state == TriState.Off) AppColors.textSecondary else AppColors.accent,
                    onClick = { onChange { it.copy(filters = it.filters + (filter to state.next())) } },
                )
            }
            1 -> LibrarySort.entries.forEach { sort ->
                val active = settings.sort == sort
                SheetRow(
                    label = stringResource(sort.labelRes),
                    icon = when {
                        !active -> null
                        settings.ascending -> Icons.Outlined.ArrowUpward
                        else -> Icons.Outlined.ArrowDownward
                    },
                    iconTint = AppColors.accent,
                    onClick = {
                        onChange {
                            if (it.sort == sort) it.copy(ascending = !it.ascending)
                            // Newest / most first is what people want from the time and count sorts.
                            else it.copy(sort = sort, ascending = sort == LibrarySort.Title)
                        }
                    },
                )
            }
            else -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.library_layout),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryDisplay.entries.forEach { mode ->
                        com.lume.app.ui.components.ChoiceChip(
                            label = stringResource(mode.labelRes),
                            selected = settings.display == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { onChange { it.copy(display = mode) } },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    label = stringResource(R.string.library_badge_unread),
                    checked = settings.unreadBadge,
                    onCheckedChange = { value -> onChange { it.copy(unreadBadge = value) } },
                )
                SwitchRow(
                    label = stringResource(R.string.library_badge_downloaded),
                    checked = settings.downloadedBadge,
                    onCheckedChange = { value -> onChange { it.copy(downloadedBadge = value) } },
                )
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, icon: ImageVector?, iconTint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint)
        }
        Spacer(Modifier.width(16.dp))
        Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
    }
}

private val LibraryFilter.labelRes: Int
    get() = when (this) {
        LibraryFilter.Unread -> R.string.library_filter_unread
        LibraryFilter.Downloaded -> R.string.library_filter_downloaded
        LibraryFilter.Started -> R.string.library_filter_started
        LibraryFilter.Tracking -> R.string.library_filter_tracking
        LibraryFilter.Completed -> R.string.library_filter_completed
    }

private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.Title -> R.string.library_sort_title
        LibrarySort.LastRead -> R.string.library_sort_last_read
        LibrarySort.DateAdded -> R.string.library_sort_date_added
        LibrarySort.Unread -> R.string.library_sort_unread
        LibrarySort.LatestChapter -> R.string.library_sort_latest
    }

private val LibraryDisplay.labelRes: Int
    get() = when (this) {
        LibraryDisplay.Grid -> R.string.library_display_grid
        LibraryDisplay.Compact -> R.string.library_display_compact
        LibraryDisplay.List -> R.string.library_display_list
    }

@Composable
private fun CategoryAssignDialog(
    initial: Set<Long>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
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
