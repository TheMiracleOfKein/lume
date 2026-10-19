package com.lume.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lume.app.R
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.SettingsViewModel

@Composable
fun DownloadSettings(nav: NavHostController, vm: SettingsViewModel) {
    val prefs = vm.prefs
    val context = LocalContext.current
    val downloadsWifiOnly by prefs.downloadsWifiOnly.collectAsState()
    val autoDownloadNew by prefs.autoDownloadNew.collectAsState()
    val deleteAfterRead by prefs.deleteAfterRead.collectAsState()
    val downloadsCbz by prefs.downloadsAsCbz.collectAsState()
    val parallelPages by prefs.downloadParallelPages.collectAsState()
    val downloadsRoot by prefs.downloadsRoot.collectAsState()

    SettingsGroup {
        SettingItem("downloads_wifi") {
            SwitchRow(
                label = stringResource(R.string.settings_downloads_wifi_only),
                hint = stringResource(R.string.settings_downloads_wifi_only_hint),
                checked = downloadsWifiOnly,
                onCheckedChange = vm::setDownloadsWifiOnly,
            )
        }
        SettingItem("auto_download") {
            SwitchRow(
                label = stringResource(R.string.settings_downloads_auto_new),
                hint = stringResource(R.string.settings_downloads_auto_new_hint),
                checked = autoDownloadNew,
                onCheckedChange = vm::setAutoDownloadNew,
            )
        }
        SettingItem("delete_read") {
            SwitchRow(
                label = stringResource(R.string.settings_downloads_delete_read),
                hint = stringResource(R.string.settings_downloads_delete_read_hint),
                checked = deleteAfterRead,
                onCheckedChange = vm::setDeleteAfterRead,
            )
        }
        SettingItem("cbz") {
            SwitchRow(
                label = stringResource(R.string.settings_downloads_cbz),
                hint = stringResource(R.string.settings_downloads_cbz_hint),
                checked = downloadsCbz,
                onCheckedChange = vm::setDownloadsCbz,
            )
        }
        SettingItem("parallel") {
            SettingsLabel(stringResource(R.string.settings_downloads_parallel))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..AppPreferences.MAX_PARALLEL_PAGES).forEach { n ->
                    ChoiceChip(
                        label = n.toString(),
                        selected = n == parallelPages,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setParallelPages(n) },
                    )
                }
            }
            SettingsNote(stringResource(R.string.settings_downloads_parallel_hint))
        }
    }
    SettingsSectionTitle(R.string.settings_group_storage)
    SettingsGroup {
        SettingItem("storage") {
            SettingsLabel(stringResource(R.string.settings_download_folder))
            vm.storageChoices.forEachIndexed { index, choice ->
                val label = when {
                    choice.path.isEmpty() -> stringResource(R.string.settings_storage_internal)
                    choice.removable -> stringResource(R.string.settings_storage_sd)
                    else -> stringResource(R.string.settings_storage_external, index)
                }
                StorageRow(
                    label = label,
                    detail = stringResource(
                        R.string.settings_storage_free,
                        android.text.format.Formatter.formatShortFileSize(context, choice.freeBytes),
                    ),
                    selected = choice.path == downloadsRoot,
                    onClick = { vm.setDownloadsRoot(choice.path) },
                )
            }
            SettingsNote(vm.downloadPath)
            SettingsNote(stringResource(R.string.settings_storage_note))
        }
        SettingItem("downloads_manage") {
            SettingsLink(stringResource(R.string.settings_downloads), stringResource(R.string.settings_downloads_hint)) {
                nav.navigate(Routes.Downloads)
            }
        }
        SettingItem("restore_downloads") {
            SettingsLink(stringResource(R.string.settings_restore_downloads), stringResource(R.string.settings_restore_downloads_hint)) {
                vm.recoverDownloads { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }
}
