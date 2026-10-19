package com.lume.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.AppContainer
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.domain.ReadingList
import com.lume.app.domain.ReadingListRow
import com.lume.app.domain.ReadingListState
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadingListViewModel(private val app: AppContainer) : ViewModel() {
    val list: StateFlow<ReadingList?> = app.readingLists.opened
    val categories: StateFlow<List<CategoryEntity>> =
        app.dao.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The list's titles and where each stands here; null while that is worked out. */
    var rows by mutableStateOf<List<ReadingListRow>?>(null)
        private set

    /** Indices of the new titles that will be added (all of them at first). */
    var chosen by mutableStateOf<Set<Int>>(emptySet())
        private set

    var categoryId by mutableStateOf(app.prefs.defaultCategoryId.value)

    var adding by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            list.filterNotNull().collect { opened ->
                val planned = app.readingLists.plan(opened)
                rows = planned
                chosen = planned.indices.filter { planned[it].state == ReadingListState.New }.toSet()
            }
        }
    }

    fun toggle(index: Int) {
        chosen = if (index in chosen) chosen - index else chosen + index
    }

    fun add(onDone: (Int) -> Unit) {
        val all = rows ?: return
        if (adding) return
        adding = true
        viewModelScope.launch {
            val picked = chosen.sorted().map { all[it] }
            val category = categoryId?.takeIf { id -> categories.value.any { it.id == id } }
            val added = runCatching { app.readingLists.add(picked, category) }.getOrDefault(0)
            adding = false
            app.readingLists.close()
            onDone(added)
        }
    }
}

/** A reading list someone shared: what is in it, what is already in the library, and adding the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: ReadingListViewModel = viewModel { ReadingListViewModel(container) }
    val list by vm.list.collectAsState()
    val categories by vm.categories.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val rows = vm.rows

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.reading_list_title), color = AppColors.textPrimary, fontSize = 20.sp)
                        list?.name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_close), tint = AppColors.textPrimary)
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        bottomBar = {
            if (rows != null && rows.any { it.state == ReadingListState.New }) {
                Button(
                    onClick = {
                        vm.add { added ->
                            val message = resources.getQuantityString(R.plurals.reading_list_added, added, added)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            nav.popBackStack()
                        }
                    },
                    enabled = vm.chosen.isNotEmpty() && !vm.adding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
                ) {
                    Text(pluralStringResource(R.plurals.reading_list_add, vm.chosen.size, vm.chosen.size))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                list == null -> EmptyBox(stringResource(R.string.reading_list_none))
                rows == null -> LoadingBox()
                rows.isEmpty() -> EmptyBox(stringResource(R.string.reading_list_empty))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        ReadingListSummary(rows)
                        if (categories.isNotEmpty() && rows.any { it.state == ReadingListState.New }) {
                            CategoryPicker(categories, vm.categoryId) { vm.categoryId = it }
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> "${row.entry.sourceId}\u0000${row.entry.titleId}" }) { index, row ->
                        ReadingListEntryRow(row, checked = index in vm.chosen, onToggle = { vm.toggle(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListSummary(rows: List<ReadingListRow>) {
    val inLibrary = rows.count { it.state == ReadingListState.InLibrary }
    val noSource = rows.count { it.state == ReadingListState.NoSource }
    val parts = buildList {
        add(pluralStringResource(R.plurals.reading_list_titles, rows.size, rows.size))
        if (inLibrary > 0) add(stringResource(R.string.reading_list_in_library_count, inLibrary))
        if (noSource > 0) add(stringResource(R.string.reading_list_no_source_count, noSource))
    }
    Text(
        parts.joinToString(" · "),
        color = AppColors.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CategoryPicker(categories: List<CategoryEntity>, selected: Long?, onSelect: (Long?) -> Unit) {
    Text(
        stringResource(R.string.reading_list_category),
        color = AppColors.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceChip(stringResource(R.string.reading_list_category_none), selected == null, onClick = { onSelect(null) })
        categories.forEach { cat -> ChoiceChip(cat.name, selected == cat.id, onClick = { onSelect(cat.id) }) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ReadingListEntryRow(row: ReadingListRow, checked: Boolean, onToggle: () -> Unit) {
    val new = row.state == ReadingListState.New
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (new) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = row.entry.coverUrl, width = 44.dp, height = 60.dp, radius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.entry.title,
                color = if (new) AppColors.textPrimary else AppColors.textSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val source = row.entry.sourceName ?: row.entry.sourceId
            Text(source, color = AppColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            when (row.state) {
                ReadingListState.InLibrary -> StateNote(stringResource(R.string.reading_list_in_library), AppColors.accent)
                ReadingListState.NoSource -> StateNote(stringResource(R.string.reading_list_no_source), AppColors.danger)
                ReadingListState.New -> Unit
            }
        }
        if (new) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = AppColors.accent, checkmarkColor = AppColors.onAccent),
            )
        }
    }
}

@Composable
private fun StateNote(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(color.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
