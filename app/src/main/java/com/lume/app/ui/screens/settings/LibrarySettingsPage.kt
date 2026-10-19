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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
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
    val findOtherSources by prefs.findOtherSources.collectAsState()

    // The worker re-checks the permission before every notification, so the answer needs no handling.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pickLocalFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importLocalFolder(uri)
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
                                label = stringResource(R.string.settings_interval_hours, hours),
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
            }
        }
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
        SettingItem("local_clean") {
            SettingsLink(stringResource(R.string.local_clean_temp), stringResource(R.string.local_clean_temp_hint)) {
                vm.cleanTemporaryFiles()
            }
        }
    }
}
