package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.library.LibImportStage
import com.lume.app.data.library.LibImportStatus
import com.lume.app.data.source.SourceEngines
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.PackState
import com.lume.app.domain.SourceSetting
import com.lume.app.ui.components.CheckRow
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.source.SourceLoginActivity
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.animatedItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val vm: SourcesViewModel = viewModel { SourcesViewModel(container) }
    val sources by vm.sources.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val disabled by vm.disabled.collectAsState()
    val pinned by vm.pinned.collectAsState()
    val importStatus by vm.importStatus.collectAsState()
    val snack = remember { SnackbarHostState() }
    var settingsFor by remember { mutableStateOf<CatalogSource?>(null) }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }

    // Pinned sources first, then by language, each group alphabetical.
    val grouped = remember(sources, pinned) {
        val sorted = sources.sortedBy { it.manifest.name.lowercase() }
        val pinnedList = sorted.filter { it.manifest.id in pinned }
        val rest = sorted.filterNot { it.manifest.id in pinned }
            .groupBy { languageGroupKey(it.manifest.language) }
            .toList()
            .sortedBy { languageGroupSort(it.first) }
        (if (pinnedList.isEmpty()) emptyList() else listOf(PINNED_GROUP to pinnedList)) + rest
    }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sources_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    BackButton(onClick = { nav.popBackStack() })
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            importStatus?.let { status ->
                item(key = "bookmark-import") {
                    BookmarkImportCard(status, onDismiss = vm::dismissImportStatus, modifier = animatedItem())
                }
            }
            item(key = "import") { ImportSection(vm) }
            item(key = "packs") { PackCatalogue(vm) }
            grouped.forEach { (langKey, list) ->
                item(key = "lang-$langKey") {
                    SectionLabel(
                        if (langKey == PINNED_GROUP) stringResource(R.string.source_pinned) else languageGroupLabel(langKey),
                    )
                }
                items(list, key = { "$langKey-${it.manifest.id}" }) { source ->
                    val id = source.manifest.id
                    val isLib = source.manifest.engine == SourceEngines.LIBSOCIAL
                    val token by vm.loginToken(id).collectAsState(initial = null)
                    SourceRow(
                        source = source,
                        selected = id == activeId,
                        disabled = id in disabled,
                        pinned = id in pinned,
                        removable = vm.isRemovable(id),
                        loggedIn = token != null,
                        showLogin = isLib,
                        hasSettings = source.settings().isNotEmpty(),
                        modifier = animatedItem(),
                        onSelect = { vm.setActive(id) },
                        onBrowse = { nav.navigate(Routes.browse(id)) },
                        onSettings = { settingsFor = source },
                        onPin = { vm.setPinned(id, it) },
                        onDisable = { vm.setDisabled(id, it) },
                        onRemove = { vm.remove(id) },
                        onLogin = { SourceLoginActivity.start(context, id) },
                        onLogout = { vm.logout(id) },
                        onImportBookmarks = if (isLib && token != null) ({ vm.importBookmarks(id) }) else null,
                        importingBookmarks = importStatus?.let { it.running && it.sourceId == id } == true,
                    )
                }
                item(key = "gap-$langKey") { Spacer(Modifier.height(8.dp)) }
            }
            item(key = "disabled-hint") {
                Text(
                    stringResource(R.string.sources_disabled_hint),
                    color = AppColors.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    settingsFor?.let { source ->
        SourceSettingsDialog(
            name = source.manifest.name,
            settings = source.settings(),
            initial = vm.settingsOf(source.manifest.id),
            onDismiss = { settingsFor = null },
            onSave = { values ->
                vm.saveSettings(source.manifest.id, values)
                settingsFor = null
            },
        )
    }
}

private const val PINNED_GROUP = "pinned"

/** The bookmark import: its stage and a progress bar while it runs, its summary once done. */
@Composable
private fun BookmarkImportCard(status: LibImportStatus, onDismiss: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.surface)
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.source_bookmarks_card_title, status.sourceName),
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        when (val stage = status.stage) {
            LibImportStage.Fetching -> ImportStageLine(stringResource(R.string.source_bookmarks_stage_fetching), null)
            is LibImportStage.Adding -> ImportStageLine(stringResource(R.string.source_bookmarks_stage_adding, stage.titles), null)
            is LibImportStage.Progress -> ImportStageLine(
                stringResource(R.string.source_bookmarks_stage_progress, stage.done, stage.total),
                if (stage.total > 0) stage.done.toFloat() / stage.total else null,
            )
            LibImportStage.Finished -> {
                val r = status.result
                val summary = when {
                    status.error != null -> stringResource(R.string.source_bookmarks_failed, status.error)
                    r == null || (r.added == 0 && r.alreadyInLibrary == 0) ->
                        stringResource(R.string.source_bookmarks_empty, status.sourceName)
                    else -> stringResource(
                        R.string.source_bookmarks_done, status.sourceName, r.added, r.alreadyInLibrary, r.folders, r.progress,
                    )
                }
                Text(
                    summary,
                    color = if (status.error != null) AppColors.danger else AppColors.textSecondary,
                    fontSize = 13.sp,
                )
                if (r != null && r.progressMissed > 0) {
                    Text(
                        stringResource(R.string.source_bookmarks_progress_missed, r.progressMissed),
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_close), color = AppColors.accent)
                }
            }
        }
    }
}

/** One running stage: its label and a bar ([fraction] null: indeterminate). */
@Composable
private fun ImportStageLine(label: String, fraction: Float?) {
    Text(label, color = AppColors.textSecondary, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    if (fraction == null) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.accent,
            trackColor = AppColors.background,
        )
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.accent,
            trackColor = AppColors.background,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.source_bookmarks_stage_hint), color = AppColors.textTertiary, fontSize = 11.sp)
}

@Composable
private fun ImportSection(vm: SourcesViewModel) {
    Column {
        Text(
            text = stringResource(R.string.sources_engines_hint),
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sources_import_hint),
            color = AppColors.textPrimary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = vm.importUrl,
            onValueChange = { vm.importUrl = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://…/madara-site.json", color = AppColors.textTertiary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.search,
                unfocusedContainerColor = AppColors.search,
                focusedTextColor = AppColors.textPrimary,
                unfocusedTextColor = AppColors.textPrimary,
                cursorColor = AppColors.accent,
                focusedIndicatorColor = AppColors.accent,
                unfocusedIndicatorColor = AppColors.divider,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = vm::import,
            enabled = !vm.busy,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (vm.busy) stringResource(R.string.sources_importing)
                else stringResource(R.string.sources_import_by_url),
            )
        }
    }
}

@Composable
private fun PackCatalogue(vm: SourcesViewModel) {
    val packs = vm.packs
    Column(Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.sources_section_packs))
        if (packs == null) {
            OutlinedButton(onClick = { vm.checkPacks() }, enabled = !vm.packsLoading, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (vm.packsLoading) stringResource(R.string.sources_packs_checking)
                    else stringResource(R.string.sources_packs_check),
                    color = AppColors.textPrimary,
                )
            }
        } else if (packs.isEmpty()) {
            Text(stringResource(R.string.sources_packs_empty), color = AppColors.textSecondary, fontSize = 13.sp)
        } else {
            val updates = packs.count { it.state == PackState.UpdateAvailable }
            if (updates > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.sources_packs_updates, updates, updates),
                        color = AppColors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::updateAllPacks, enabled = !vm.busy) {
                        Text(stringResource(R.string.sources_packs_update_all), color = AppColors.accent)
                    }
                }
            }
            packs.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.entry.name, color = AppColors.textPrimary, fontSize = 15.sp)
                        Text(
                            listOfNotNull(
                                SourceEngines.displayName(row.entry.engine),
                                row.entry.language?.uppercase(),
                                row.entry.version,
                                "18+".takeIf { row.entry.nsfw },
                            ).joinToString(" · "),
                            color = AppColors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    when (row.state) {
                        PackState.Installed -> Text(
                            stringResource(R.string.sources_pack_installed),
                            color = AppColors.textTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        PackState.NotInstalled -> TextButton(onClick = { vm.installPack(row.entry) }, enabled = !vm.busy) {
                            Text(stringResource(R.string.sources_pack_install), color = AppColors.accent)
                        }
                        PackState.UpdateAvailable -> TextButton(onClick = { vm.installPack(row.entry) }, enabled = !vm.busy) {
                            Text(stringResource(R.string.sources_pack_update, row.entry.version), color = AppColors.accent)
                        }
                    }
                }
            }
        }
        vm.packsError?.let { Text(it, color = AppColors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (vm.packsLoading && packs != null) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun languageGroupKey(language: String?): String {
    val tag = language?.trim()?.lowercase().orEmpty()
    return when {
        tag.isEmpty() || tag == "multi" || tag.contains(",") -> "multi"
        tag.startsWith("ru") -> "ru"
        tag.startsWith("en") -> "en"
        else -> tag.take(8)
    }
}

private fun languageGroupSort(key: String): Int = when (key) {
    "ru" -> 0
    "en" -> 1
    "multi" -> 2
    else -> 3
}

@Composable
private fun languageGroupLabel(key: String): String = when (key) {
    "ru" -> stringResource(R.string.sources_lang_ru)
    "en" -> stringResource(R.string.sources_lang_en)
    "multi" -> stringResource(R.string.sources_lang_multi)
    else -> key.uppercase()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AppColors.textTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SourceRow(
    source: CatalogSource,
    selected: Boolean,
    disabled: Boolean,
    pinned: Boolean,
    removable: Boolean,
    loggedIn: Boolean,
    showLogin: Boolean,
    hasSettings: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onPin: (Boolean) -> Unit,
    onDisable: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    /** Shown in the menu for a signed-in Lib source. */
    onImportBookmarks: (() -> Unit)? = null,
    importingBookmarks: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val engine = source.manifest.engine
    val langBadge = languageBadge(source.manifest.language)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppColors.accent,
                unselectedColor = AppColors.textSecondary,
            ),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp).alpha(if (disabled) 0.5f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.manifest.name, color = AppColors.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(langBadge, color = AppColors.accent, fontSize = 11.sp)
                if (pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = AppColors.textTertiary,
                        modifier = Modifier.padding(start = 6.dp).size(13.dp),
                    )
                }
            }
            Text(
                buildString {
                    append(SourceEngines.displayName(engine))
                    append(" · ")
                    append(SourceEngines.kindLabel(context, engine))
                    if (showLogin) {
                        append(" · ")
                        append(
                            if (loggedIn) resources.getString(R.string.source_auth_on)
                            else resources.getString(R.string.source_auth_off),
                        )
                    }
                    if (disabled) {
                        append(" · ")
                        append(resources.getString(R.string.source_disabled))
                    }
                },
                color = AppColors.textSecondary,
                fontSize = 12.sp,
            )
            Text(source.manifest.baseUrl, color = AppColors.textTertiary, fontSize = 11.sp, maxLines = 1)
        }
        if (importingBookmarks) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                color = AppColors.accent,
                strokeWidth = 2.dp,
            )
        }
        if (showLogin) {
            IconButton(onClick = { if (loggedIn) onLogout() else onLogin() }) {
                Icon(
                    if (loggedIn) Icons.AutoMirrored.Outlined.Logout else Icons.AutoMirrored.Outlined.Login,
                    contentDescription = null,
                    tint = if (loggedIn) AppColors.accent else AppColors.textSecondary,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.source_action_more), tint = AppColors.textSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                fun pick(action: () -> Unit) {
                    menuOpen = false
                    action()
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.source_action_browse)) }, onClick = { pick(onBrowse) })
                if (hasSettings) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.source_action_settings)) }, onClick = { pick(onSettings) })
                }
                if (onImportBookmarks != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.source_action_import_bookmarks)) },
                        enabled = !importingBookmarks,
                        onClick = { pick(onImportBookmarks) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (pinned) R.string.source_action_unpin else R.string.source_action_pin)) },
                    onClick = { pick { onPin(!pinned) } },
                )
                // The active source stays enabled.
                if (!selected || disabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (disabled) R.string.source_action_enable else R.string.source_action_disable)) },
                        onClick = { pick { onDisable(!disabled) } },
                    )
                }
                if (removable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove), color = AppColors.danger) },
                        onClick = { pick(onRemove) },
                    )
                }
            }
        }
    }
}

/** Edits one source's [settings]; [initial] holds the stored values (missing keys use defaults). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceSettingsDialog(
    name: String,
    settings: List<SourceSetting>,
    initial: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    var values by remember { mutableStateOf(initial) }
    fun valueOf(setting: SourceSetting): String = values[setting.key] ?: when (setting) {
        is SourceSetting.Toggle -> setting.default.toString()
        is SourceSetting.Choice -> setting.default
        is SourceSetting.MultiChoice -> setting.default.joinToString(",")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_settings_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (settings.isEmpty()) Text(stringResource(R.string.source_settings_none))
                settings.forEach { setting ->
                    when (setting) {
                        is SourceSetting.Toggle -> SwitchRow(
                            label = setting.title,
                            checked = valueOf(setting).toBoolean(),
                            onCheckedChange = { values = values + (setting.key to it.toString()) },
                        )
                        is SourceSetting.Choice -> {
                            Text(setting.title, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                setting.options.forEach { (value, label) ->
                                    ChoiceChip(label = label, selected = valueOf(setting) == value, onClick = {
                                        values = values + (setting.key to value)
                                    })
                                }
                            }
                        }
                        is SourceSetting.MultiChoice -> {
                            Text(
                                setting.title,
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                            val chosen = valueOf(setting).split(',').filter { it.isNotBlank() }.toSet()
                            setting.options.forEach { (value, label) ->
                                CheckRow(label, value in chosen) { on ->
                                    val next = if (on) chosen + value else chosen - value
                                    // Keep the order of the options, never an empty choice.
                                    val ordered = setting.options.map { it.first }.filter { it in next }
                                    if (ordered.isNotEmpty()) values = values + (setting.key to ordered.joinToString(","))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun languageBadge(language: String?): String {
    val key = languageGroupKey(language)
    return when (key) {
        "ru" -> "RU"
        "en" -> "EN"
        "multi" -> stringResource(R.string.sources_lang_badge_multi)
        else -> key.uppercase()
    }
}
