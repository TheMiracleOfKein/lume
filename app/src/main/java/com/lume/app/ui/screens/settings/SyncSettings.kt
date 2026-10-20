package com.lume.app.ui.screens.settings

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.prefs.SyncSettings
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.screens.SettingsViewModel
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion

/** Settings → Sync: where the sync files live, the passphrase, how often, and "Sync now". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncSettingsPage(vm: SettingsViewModel) {
    val settings by vm.prefs.syncSettings.collectAsState()
    val status by vm.prefs.syncStatus.collectAsState()
    val running by vm.syncRunning.collectAsState()
    var webDavOpen by rememberSaveable { mutableStateOf(false) }
    var passphraseOpen by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setSyncFolder(uri)
    }

    SettingsNote(stringResource(R.string.sync_intro), Modifier.padding(bottom = 6.dp))
    SettingsGroup {
        SettingItem("sync_place") {
            SettingsLabel(stringResource(R.string.sync_place), top = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip(
                    label = stringResource(R.string.sync_off),
                    selected = settings.backend == SyncSettings.OFF,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.saveSync(settings.copy(backend = SyncSettings.OFF)) },
                )
                ChoiceChip(
                    label = stringResource(R.string.sync_folder),
                    selected = settings.backend == SyncSettings.FOLDER,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (settings.folder == null) pickFolder.launch(null)
                        else vm.saveSync(settings.copy(backend = SyncSettings.FOLDER), syncNow = true)
                    },
                )
                ChoiceChip(
                    label = stringResource(R.string.sync_webdav),
                    selected = settings.backend == SyncSettings.WEBDAV,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (settings.webDavUrl.isBlank()) webDavOpen = true
                        else vm.saveSync(settings.copy(backend = SyncSettings.WEBDAV), syncNow = true)
                    },
                )
            }
            AnimatedVisibility(
                visible = settings.backend != SyncSettings.OFF,
                enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
            ) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (settings.backend == SyncSettings.FOLDER) {
                        SettingsLink(
                            settings.folder?.let { stringResource(R.string.sync_folder_name, vm.folderName(it)) }
                                ?: stringResource(R.string.sync_folder_pick),
                            stringResource(R.string.sync_folder_hint),
                        ) { pickFolder.launch(null) }
                    } else {
                        SettingsLink(
                            stringResource(R.string.sync_webdav_server),
                            settings.webDavUrl.ifBlank { stringResource(R.string.sync_webdav_hint) },
                        ) { webDavOpen = true }
                    }
                }
            }
        }
    }

    SettingsSectionTitle(R.string.sync_section_options)
    SettingsGroup {
        SettingItem("sync_passphrase") {
            SettingsLink(
                stringResource(R.string.sync_passphrase),
                stringResource(if (settings.passphrase.isEmpty()) R.string.sync_passphrase_off else R.string.sync_passphrase_on) +
                    "\n" + stringResource(R.string.sync_passphrase_hint),
            ) { passphraseOpen = true }
        }
        SettingItem("sync_interval") {
            SettingsLabel(stringResource(R.string.sync_interval), top = 10)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPreferences.SYNC_INTERVALS.forEach { hours ->
                    ChoiceChip(
                        label = if (hours == 0) stringResource(R.string.sync_interval_manual)
                        else pluralStringResource(R.plurals.sync_interval_hours, hours, hours),
                        selected = hours == settings.intervalHours,
                        onClick = { vm.saveSync(settings.copy(intervalHours = hours)) },
                    )
                }
            }
            SettingsNote(stringResource(R.string.sync_interval_hint), Modifier.padding(top = 6.dp))
        }
    }

    SettingsSectionTitle(R.string.sync_section_status)
    SettingsGroup {
        SettingItem("sync_now") {
            SettingsLink(stringResource(R.string.sync_now), syncStatusLine(status.lastAt, status.error, status.devices)) {
                if (!running && settings.ready) vm.syncNow()
            }
            if (running) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    color = AppColors.accent,
                    trackColor = AppColors.card,
                )
            }
        }
        SettingsNote(stringResource(R.string.sync_what), Modifier.padding(top = 4.dp))
    }

    if (webDavOpen) {
        WebDavDialog(
            settings = settings,
            onDismiss = { webDavOpen = false },
            onSave = { url, user, password ->
                webDavOpen = false
                vm.saveSync(
                    settings.copy(backend = SyncSettings.WEBDAV, webDavUrl = url, webDavUser = user, webDavPassword = password),
                    syncNow = true,
                )
            },
        )
    }
    if (passphraseOpen) {
        PassphraseDialog(
            current = settings.passphrase,
            onDismiss = { passphraseOpen = false },
            onSave = {
                passphraseOpen = false
                vm.saveSync(settings.copy(passphrase = it), syncNow = true)
            },
        )
    }
}

@Composable
private fun syncStatusLine(lastAt: Long, error: String?, devices: Int): String {
    val last = if (lastAt <= 0L) {
        stringResource(R.string.sync_never)
    } else {
        stringResource(
            R.string.sync_last,
            DateUtils.getRelativeTimeSpanString(lastAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        ) + " · " + pluralStringResource(R.plurals.sync_devices, devices, devices)
    }
    return if (error != null) last + "\n" + stringResource(R.string.sync_failed, error) else last
}

@Composable
private fun WebDavDialog(
    settings: SyncSettings,
    onDismiss: () -> Unit,
    onSave: (url: String, user: String, password: String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(settings.webDavUrl) }
    var user by rememberSaveable { mutableStateOf(settings.webDavUser) }
    var password by rememberSaveable { mutableStateOf(settings.webDavPassword) }
    val valid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = { Text(stringResource(R.string.sync_webdav_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_webdav_url)) },
                    placeholder = { Text("https://cloud.example.com/remote.php/dav/files/me/Lume/") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_webdav_user)) },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_webdav_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(stringResource(R.string.sync_webdav_note), color = AppColors.textTertiary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(url.trim(), user.trim(), password) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PassphraseDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = { Text(stringResource(R.string.sync_passphrase)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(stringResource(R.string.sync_passphrase_note), color = AppColors.textTertiary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
