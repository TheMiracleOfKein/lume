package com.lume.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.lume.app.domain.isContentUri
import com.lume.app.ui.theme.AppColors
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lume.app.R
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.domain.AutoDownloadMode
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.ConfirmDialog
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.SettingsViewModel

@Composable
fun DownloadSettings(nav: NavHostController, vm: SettingsViewModel) {
    val prefs = vm.prefs
    val context = LocalContext.current
    val downloadsWifiOnly by prefs.downloadsWifiOnly.collectAsState()
    val autoDownloadMode by prefs.autoDownloadMode.collectAsState()
    val deleteAfterRead by prefs.deleteAfterRead.collectAsState()
    val downloadsCbz by prefs.downloadsAsCbz.collectAsState()
    val parallelPages by prefs.downloadParallelPages.collectAsState()
    val downloadsRoot by prefs.downloadsRoot.collectAsState()
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setDownloadsFolder(uri)
    }
    // The permission can be taken away in the system settings: look again when the page opens.
    LaunchedEffect(downloadsRoot) { vm.refreshDownloadsFolder() }

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
            SettingsLabel(stringResource(R.string.settings_downloads_auto_new))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutoDownloadMode.entries.forEach { mode ->
                    ChoiceChip(
                        label = stringResource(
                            when (mode) {
                                AutoDownloadMode.OFF -> R.string.settings_downloads_auto_new_off
                                AutoDownloadMode.ALL -> R.string.settings_downloads_auto_new_all
                                AutoDownloadMode.SUBSCRIBED -> R.string.settings_downloads_auto_new_subscribed
                            },
                        ),
                        selected = mode == autoDownloadMode,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setAutoDownloadMode(mode) },
                    )
                }
            }
            SettingsNote(
                stringResource(
                    if (autoDownloadMode == AutoDownloadMode.SUBSCRIBED) R.string.settings_downloads_auto_new_subscribed_hint
                    else R.string.settings_downloads_auto_new_hint,
                ),
                Modifier.padding(top = 8.dp),
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
            SettingsNote(stringResource(R.string.settings_downloads_parallel_hint), Modifier.padding(top = 8.dp))
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
            val folderPicked = isContentUri(downloadsRoot)
            StorageRow(
                label = if (folderPicked) vm.downloadsFolderName ?: stringResource(R.string.settings_storage_folder) else stringResource(R.string.settings_storage_folder_pick),
                detail = stringResource(if (folderPicked) R.string.settings_storage_folder_picked else R.string.settings_storage_folder_pick_hint),
                selected = folderPicked,
                onClick = { pickFolder.launch(null) },
            )
            if (folderPicked && vm.downloadsFolderLost) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_storage_folder_lost),
                        color = AppColors.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.settings_storage_reconnect)) }
                }
            }
            if (vm.downloadPath.isNotEmpty()) SettingsNote(vm.downloadPath)
            SettingsNote(stringResource(R.string.settings_storage_note))
        }
        SettingItem("downloads_manage") {
            SettingsLink(stringResource(R.string.settings_downloads), stringResource(R.string.settings_downloads_hint)) {
                nav.navigate(Routes.Downloads)
            }
        }
        SettingItem("restore_downloads") {
            SettingsLink(stringResource(R.string.settings_restore_downloads), stringResource(R.string.settings_restore_downloads_hint)) {
                confirmRestore = true
            }
            if (confirmRestore) {
                ConfirmDialog(
                    title = stringResource(R.string.settings_restore_downloads),
                    text = stringResource(R.string.settings_restore_downloads_hint),
                    confirmLabel = stringResource(R.string.settings_restore_downloads_confirm),
                    onConfirm = { vm.recoverDownloads { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } },
                    onDismiss = { confirmRestore = false },
                )
            }
        }
    }
}
