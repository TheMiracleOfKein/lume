package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.SettingsSection
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.animatedItem

/** Add, rename, delete and reorder categories; pick the one new library titles go into. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: CategoriesViewModel = viewModel { CategoriesViewModel(container) }
    val categories by vm.categories.collectAsState()
    val defaultId by vm.defaultCategoryId.collectAsState()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = AppColors.accent) {
                Icon(Icons.Outlined.Add, stringResource(R.string.dialog_new_category), tint = AppColors.onAccent)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(key = "default") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsSection(stringResource(R.string.categories_default))
                    Text(
                        stringResource(R.string.categories_default_hint),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    DefaultOption(stringResource(R.string.categories_default_none), defaultId == null) { vm.setDefault(null) }
                    categories.forEach { cat ->
                        DefaultOption(cat.name, defaultId == cat.id) { vm.setDefault(cat.id) }
                    }
                    SettingsSection(stringResource(R.string.categories_title))
                    if (categories.isEmpty()) {
                        Text(stringResource(R.string.categories_empty), color = AppColors.textSecondary, fontSize = 14.sp)
                    }
                }
            }
            itemsIndexed(categories, key = { _, it -> it.id }) { index, cat ->
                Row(
                    modifier = animatedItem()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.surface)
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.name, color = AppColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.move(cat.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.cd_move_up), tint = AppColors.textSecondary)
                    }
                    IconButton(onClick = { vm.move(cat.id, 1) }, enabled = index < categories.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.cd_move_down), tint = AppColors.textSecondary)
                    }
                    IconButton(onClick = { editing = cat }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.action_rename), tint = AppColors.textSecondary)
                    }
                    IconButton(onClick = { deleting = cat }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), tint = AppColors.danger)
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 96.dp)) }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.dialog_new_category),
            initial = "",
            confirm = stringResource(R.string.action_create),
            onDismiss = { creating = false },
            onConfirm = {
                vm.create(it)
                creating = false
            },
        )
    }
    editing?.let { cat ->
        NameDialog(
            title = stringResource(R.string.dialog_rename_category),
            initial = cat.name,
            confirm = stringResource(R.string.action_save),
            onDismiss = { editing = null },
            onConfirm = {
                vm.rename(cat.id, it)
                editing = null
            },
        )
    }
    deleting?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dialog_delete_category_title)) },
            text = { Text(stringResource(R.string.dialog_delete_category_text, cat.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(cat.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete), color = AppColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DefaultOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.accent),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.label_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
