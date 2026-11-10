package com.lume.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.lume.app.data.db.WatchedFolderEntity
import com.lume.app.ui.theme.AppColors
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.compose.ui.res.pluralStringResource
import com.lume.app.R
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.SettingsViewModel
import com.lume.app.ui.theme.Motion

@Composable
fun LibrarySettingsPage(nav: NavHostController, vm: SettingsViewModel) {
    val prefs = vm.prefs
    val context = LocalContext.current
    val libraryUpdates by prefs.libraryUpdatesEnabled.collectAsState()
    val libraryInterval by prefs.libraryUpdateIntervalHours.collectAsState()
    val libraryWifiOnly by prefs.libraryUpdateWifiOnly.collectAsState()
    val rules by prefs.libraryUpdateRules.collectAsState()
    val findOtherSources by prefs.findOtherSources.collectAsState()
    val categories by vm.categories.collectAsState()
    val choices = categoryChoices(categories)
    val mutedCategories by prefs.notifyMutedCategories.collectAsState()
    var pickingNotify by remember { mutableStateOf(false) }
    var pickingUpdate by remember { mutableStateOf(false) }

    // The worker re-checks the permission before every notification, so the answer needs no handling.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pickLocalFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importLocalFolder(uri)
    }

    val watchedFolders by vm.watchedFolders.collectAsState()
    val folderScanHours by prefs.folderScanHours.collectAsState()
    var reconnecting by remember { mutableStateOf<WatchedFolderEntity?>(null) }
    val addWatchedFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addWatchedFolder(uri)
    }
    val reconnectWatchedFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val folder = reconnecting
        reconnecting = null
        if (uri != null && folder != null) vm.reconnectWatchedFolder(folder, uri)
    }

    SettingsSectionTitle(R.string.settings_group_updates)
    SettingsGroup {
        SettingItem("library_check") {
            SwitchRow(
                label = stringResource(R.string.settings_library_auto_check),
                hint = stringResource(R.string.settings_library_auto_check_hint),
                checked = libraryUpdates,
                onCheckedChange = { value ->
                    if (value && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.setLibraryUpdates(value)
                },
            )
        }
        AnimatedVisibility(
            visible = libraryUpdates,
            enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
            exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
        ) {
            Column {
                SettingItem("library_interval") {
                    SettingsLabel(stringResource(R.string.settings_library_interval))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppPreferences.LIBRARY_INTERVALS.forEach { hours ->
                            ChoiceChip(
                                label = pluralStringResource(R.plurals.settings_interval_hours, hours, hours),
                                selected = hours == libraryInterval,
                                modifier = Modifier.weight(1f),
                                onClick = { vm.setLibraryInterval(hours) },
                            )
                        }
                    }
                }
                SettingItem("library_wifi") {
                    SwitchRow(
                        label = stringResource(R.string.settings_wifi_only),
                        hint = stringResource(R.string.settings_wifi_only_hint),
                        checked = libraryWifiOnly,
                        onCheckedChange = vm::setLibraryWifiOnly,
                    )
                }
                SettingItem("library_charging") {
                    SwitchRow(
                        label = stringResource(R.string.settings_library_charging_only),
                        hint = stringResource(R.string.settings_library_charging_only_hint),
                        checked = rules.chargingOnly,
                        onCheckedChange = { value -> vm.setLibraryUpdateRules { it.copy(chargingOnly = value) } },
                    )
                }
            }
        }
        SettingItem("library_notify_categories") {
            val notified = choices.filter { it.id !in mutedCategories }
            SettingsLink(
                stringResource(R.string.settings_notify_categories),
                when {
                    notified.size == choices.size -> stringResource(R.string.settings_categories_all)
                    notified.isEmpty() -> stringResource(R.string.settings_notify_categories_none)
                    else -> notified.joinToString { it.name }
                },
            ) { pickingNotify = true }
        }
        // These apply to "Update now" too, so they stay visible with the background check off.
        SettingItem("library_update_categories") {
            val chosen = choices.filter { it.id in rules.categories }
            SettingsLink(
                stringResource(R.string.settings_update_categories),
                if (chosen.isEmpty()) stringResource(R.string.settings_categories_all) else chosen.joinToString { it.name },
            ) { pickingUpdate = true }
        }
        SettingItem("library_skip_completed") {
            SwitchRow(
                label = stringResource(R.string.settings_library_skip_completed),
                checked = rules.skipCompleted,
                onCheckedChange = { value -> vm.setLibraryUpdateRules { it.copy(skipCompleted = value) } },
            )
        }
        SettingItem("library_skip_not_started") {
            SwitchRow(
                label = stringResource(R.string.settings_library_skip_not_started),
                checked = rules.skipNotStarted,
                onCheckedChange = { value -> vm.setLibraryUpdateRules { it.copy(skipNotStarted = value) } },
            )
        }
        SettingItem("library_skip_unread") {
            SwitchRow(
                label = stringResource(R.string.settings_library_skip_unread),
                hint = stringResource(R.string.settings_library_skip_unread_hint),
                checked = rules.skipUnread,
                onCheckedChange = { value -> vm.setLibraryUpdateRules { it.copy(skipUnread = value) } },
            )
        }
    }
    if (pickingUpdate) {
        CategoryPickDialog(
            title = stringResource(R.string.settings_update_categories),
            choices = choices,
            selected = rules.categories,
            onDismiss = { pickingUpdate = false },
            onSave = { chosen ->
                vm.setLibraryUpdateRules { it.copy(categories = chosen) }
                pickingUpdate = false
            },
        )
    }
    if (pickingNotify) {
        val allIds = choices.map { it.id }.toSet()
        CategoryPickDialog(
            title = stringResource(R.string.settings_notify_categories),
            choices = choices,
            selected = allIds - mutedCategories,
            onDismiss = { pickingNotify = false },
            onSave = { notified ->
                vm.setNotifyMutedCategories(allIds - notified)
                pickingNotify = false
            },
        )
    }
    SettingsSectionTitle(R.string.settings_section_library)
    SettingsGroup {
        SettingItem("find_sources") {
            SwitchRow(
                label = stringResource(R.string.settings_find_other_sources),
                hint = stringResource(R.string.settings_find_other_sources_hint),
                checked = findOtherSources,
                onCheckedChange = vm::setFindOtherSources,
            )
        }
        SettingItem("reading_stats") {
            SettingsLink(stringResource(R.string.stats_title), stringResource(R.string.settings_stats_hint)) {
                nav.navigate(Routes.Stats)
            }
        }
        SettingItem("categories") {
            SettingsLink(stringResource(R.string.categories_title), stringResource(R.string.settings_categories_hint)) {
                nav.navigate(Routes.Categories)
            }
        }
    }
    SettingsSectionTitle(R.string.settings_section_local)
    SettingsGroup {
        SettingItem("local_import") {
            SettingsLink(
                if (vm.localBusy) stringResource(R.string.local_importing) else stringResource(R.string.local_import_folder),
                stringResource(R.string.local_folder_hint, vm.localPath),
            ) {
                if (!vm.localBusy) pickLocalFolder.launch(null)
            }
        }
        SettingItem("local_open") {
            SettingsLink(stringResource(R.string.local_open_library)) { nav.navigate(Routes.browse("local")) }
        }
        SettingItem("watched_add") {
            SettingsLabel(stringResource(R.string.watched_title))
            if (watchedFolders.isEmpty()) SettingsNote(stringResource(R.string.watched_none))
            watchedFolders.forEach { folder ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(folder.label, color = AppColors.textPrimary, fontSize = 15.sp)
                        Text(
                            when {
                                !folder.available -> stringResource(R.string.watched_unavailable)
                                folder.lastScanAt == 0L -> stringResource(R.string.watched_never_scanned)
                                else -> stringResource(
                                    R.string.watched_last_scan,
                                    DateUtils.formatDateTime(context, folder.lastScanAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
                                )
                            },
                            color = if (folder.available) AppColors.textSecondary else AppColors.danger,
                            fontSize = 12.sp,
                        )
                    }
                    if (!folder.available) {
                        TextButton(onClick = {
                            reconnecting = folder
                            reconnectWatchedFolder.launch(null)
                        }) { Text(stringResource(R.string.watched_reconnect)) }
                    }
                    TextButton(onClick = { vm.removeWatchedFolder(folder.treeUri) }) { Text(stringResource(R.string.watched_remove)) }
                }
            }
            SettingsLink(stringResource(R.string.watched_add), stringResource(R.string.watched_add_hint)) {
                addWatchedFolder.launch(null)
            }
        }
        SettingItem("watched_scan_now") {
            SettingsLink(stringResource(R.string.watched_scan_now)) { vm.scanWatchedFoldersNow() }
        }
        SettingItem("watched_interval") {
            SettingsLabel(stringResource(R.string.watched_interval))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPreferences.FOLDER_SCAN_HOURS.forEach { hours ->
                    ChoiceChip(
                        label = if (hours == 0) stringResource(R.string.backup_auto_off)
                        else pluralStringResource(R.plurals.watched_interval_hours, hours, hours),
                        selected = hours == folderScanHours,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setFolderScanHours(hours) },
                    )
                }
            }
        }
        SettingItem("local_clean") {
            SettingsLink(stringResource(R.string.local_clean_temp), stringResource(R.string.local_clean_temp_hint)) {
                vm.cleanTemporaryFiles()
            }
        }
    }
}
