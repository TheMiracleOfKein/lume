package com.lume.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.domain.SearchableSetting
import com.lume.app.domain.searchSettings
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.QuickSearchBar
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.SettingsViewModel
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.delay

/** Sections, grouped the way the main settings page shows them. */
private val SECTION_GROUPS = listOf(
    listOf(SettingsPage.Appearance, SettingsPage.Reader),
    listOf(SettingsPage.Library, SettingsPage.Downloads, SettingsPage.Sources, SettingsPage.Trackers),
    listOf(SettingsPage.Sync, SettingsPage.Backup, SettingsPage.Privacy, SettingsPage.Network),
    listOf(SettingsPage.About),
)

/** The main settings page: a search field and the list of sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val searchable = remember(resources, configuration) { SettingsCatalog.searchable(resources) }
    val results = remember(query, searchable) { searchSettings(query, searchable) }
    val focusManager = LocalFocusManager.current

    fun open(page: SettingsPage, focus: String?) {
        focusManager.clearFocus()
        if (page == SettingsPage.About) nav.navigate(Routes.About) else nav.navigate(Routes.settingsPage(page.key, focus))
    }

    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = { nav.popBackStack() }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            QuickSearchBar(
                value = query,
                onValueChange = { query = it },
                onSubmit = { focusManager.clearFocus() },
                hint = stringResource(R.string.settings_search_hint),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.action_clear), tint = AppColors.textSecondary)
                }
            }
        }
        when {
            query.isBlank() -> SECTION_GROUPS.forEach { pages ->
                Spacer(Modifier.height(12.dp))
                SettingsGroup {
                    pages.forEach { page ->
                        SectionRow(
                            icon = page.icon,
                            title = stringResource(page.titleRes),
                            summary = stringResource(page.summaryRes),
                            onClick = { open(page, null) },
                        )
                    }
                }
            }
            results.isEmpty() -> Text(
                stringResource(R.string.settings_search_empty, query.trim()),
                color = AppColors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, top = 24.dp),
            )
            else -> {
                Spacer(Modifier.height(4.dp))
                SettingsGroup {
                    results.forEach { result ->
                        val info = SettingsCatalog.byId(result.id) ?: return@forEach
                        SearchResultRow(result, icon = info.page.icon, onClick = { open(info.page, info.id) })
                    }
                }
            }
        }
    }
}

/** One section's page; [focus] is the setting a search result opened it for. */
@Composable
fun SettingsPageScreen(nav: NavHostController, pageKey: String, focus: String?) {
    val page = SettingsPage.fromKey(pageKey) ?: SettingsPage.Appearance
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    // Highlight once; coming back from a screen opened here doesn't flash the row again.
    var focusShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(FOCUS_TIMEOUT_MS)
        focusShown = true
    }

    SettingsScaffold(title = stringResource(page.titleRes), onBack = { nav.popBackStack() }) {
        CompositionLocalProvider(LocalSettingsFocus provides focus.takeUnless { focusShown }) {
            when (page) {
                SettingsPage.Appearance -> AppearanceSettings(vm)
                SettingsPage.Reader -> ReaderDefaultsSettings(vm)
                SettingsPage.Library -> LibrarySettingsPage(nav, vm)
                SettingsPage.Downloads -> DownloadSettings(nav, vm)
                SettingsPage.Sources -> SourcesSettings(nav, vm)
                SettingsPage.Trackers -> TrackerSettings(vm)
                SettingsPage.Sync -> SyncSettingsPage(vm)
                SettingsPage.Backup -> BackupSettings(nav, vm)
                SettingsPage.Privacy -> PrivacySettings(vm)
                SettingsPage.Network -> NetworkSettings(vm)
                SettingsPage.About -> Unit
            }
        }
        SettingsStatus(vm.statusMessage)
    }
}

private const val FOCUS_TIMEOUT_MS = 3_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(title, color = AppColors.textPrimary) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // On tablets and in landscape the rows stay at a readable width, centred.
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) { content() }
        }
    }
}

@Composable
private fun SectionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.textPrimary, fontSize = 15.sp)
            Text(
                summary,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppColors.textSecondary)
    }
}

@Composable
private fun SearchResultRow(result: SearchableSetting, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, color = AppColors.textPrimary, fontSize = 15.sp)
            Text(result.section, color = AppColors.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            if (result.hint.isNotBlank()) {
                Text(
                    result.hint,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.accentMuted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
    }
}
