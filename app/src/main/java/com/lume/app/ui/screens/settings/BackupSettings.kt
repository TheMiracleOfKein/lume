package com.lume.app.ui.screens.settings

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.R
import com.lume.app.ui.nav.Routes
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.screens.SettingsViewModel
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion

@Composable
fun BackupSettings(nav: NavHostController, vm: SettingsViewModel) {
    val prefs = vm.prefs
    val autoBackupDays by prefs.autoBackupDays.collectAsState()
    val autoBackupKeep by prefs.autoBackupKeep.collectAsState()
    val autoBackupFolder by prefs.autoBackupFolder.collectAsState()
    val saveBackupTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveBackupTo(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri)
    }
    val openList = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openReadingList(uri) { nav.navigate(Routes.ReadingList) }
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setAutoBackupFolder(uri)
    }

    SettingsGroup {
        SettingItem("backup_create") {
            SettingsLink(stringResource(R.string.settings_create_backup), stringResource(R.string.backup_create_hint), vm::createBackup)
        }
        SettingItem("backup_save") {
            SettingsLink(stringResource(R.string.backup_save_to), stringResource(R.string.backup_save_to_hint)) {
                saveBackupTo.launch(vm.suggestedBackupName())
            }
        }
    }
    SettingsSectionTitle(R.string.settings_restore_backup)
    SettingsGroup {
        SettingItem("backup_restore") {
            SettingsLink(stringResource(R.string.settings_restore_backup), stringResource(R.string.backup_restore_hint), vm::restoreBackup)
        }
        SettingItem("backup_restore_file") {
            SettingsLink(stringResource(R.string.backup_restore_file), stringResource(R.string.backup_restore_file_hint)) {
                if (vm.importProgress == null) openBackup.launch(arrayOf("*/*"))
            }
        }
        vm.importProgress?.let { (done, total) ->
            Text(
                stringResource(R.string.backup_mihon_progress, done, total),
                color = AppColors.accent,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                color = AppColors.accent,
                trackColor = AppColors.card,
            )
        }
    }
    SettingsSectionTitle(R.string.settings_group_reading_lists)
    SettingsGroup {
        SettingItem("reading_list_open") {
            SettingsLink(stringResource(R.string.reading_list_open), stringResource(R.string.reading_list_open_hint)) {
                openList.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
        }
    }
    SettingsSectionTitle(R.string.backup_auto)
    SettingsGroup {
        SettingItem("auto_backup") {
            SettingsNote(stringResource(R.string.backup_auto_hint), Modifier.padding(top = 6.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPreferences.AUTO_BACKUP_DAYS.forEach { days ->
                    ChoiceChip(
                        label = if (days == 0) stringResource(R.string.backup_auto_off)
                        else pluralStringResource(R.plurals.backup_auto_days, days, days),
                        selected = days == autoBackupDays,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setAutoBackup(days, autoBackupKeep) },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = autoBackupDays > 0,
            enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
            exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
        ) {
            Column {
                SettingsLabel(stringResource(R.string.backup_auto_keep), top = 12)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppPreferences.AUTO_BACKUP_KEEP.forEach { n ->
                        ChoiceChip(
                            label = n.toString(),
                            selected = n == autoBackupKeep,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setAutoBackup(autoBackupDays, n) },
                        )
                    }
                }
                SettingsLink(
                    if (autoBackupFolder == null) stringResource(R.string.backup_auto_folder_pick)
                    else stringResource(R.string.backup_auto_folder, vm.folderName(autoBackupFolder.orEmpty())),
                ) { pickBackupFolder.launch(null) }
                if (autoBackupFolder != null) {
                    SettingsLink(stringResource(R.string.backup_auto_folder_clear)) { vm.setAutoBackupFolder(null) }
                }
            }
        }
    }
}
