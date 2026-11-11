package com.lume.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.lume.app.data.tracker.TrackerImportStage
import com.lume.app.data.tracker.TrackerImportStatus
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.tracker.Tracker
import com.lume.app.data.tracker.TrackerLogin
import com.lume.app.ui.components.ConfirmDialog
import com.lume.app.ui.components.PasswordField
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.screens.SettingsViewModel
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.tracker.TrackerAuthActivity
import kotlinx.coroutines.launch

@Composable
fun TrackerSettings(vm: SettingsViewModel) {
    val container = LocalAppContainer.current
    val prefs = vm.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val anilistTokenState by prefs.anilistAccessToken.collectAsState()
    val shikiTokenState by prefs.shikimoriAccessToken.collectAsState()
    val trackerValues by prefs.trackerValues.collectAsState()
    val trackerAutoLink by prefs.trackerAutoLink.collectAsState()
    var passwordLoginId by rememberSaveable { mutableStateOf<String?>(null) }
    var logoutTrackerId by rememberSaveable { mutableStateOf<String?>(null) }
    container.trackers.all.firstOrNull { it.id == logoutTrackerId }?.let { tracker ->
        ConfirmDialog(
            title = stringResource(R.string.tracker_logout_title, tracker.displayName),
            text = stringResource(R.string.tracker_logout_text),
            confirmLabel = stringResource(R.string.action_logout),
            onConfirm = { vm.logout(tracker.id) },
            onDismiss = { logoutTrackerId = null },
        )
    }
    var importTrackerId by rememberSaveable { mutableStateOf<String?>(null) }
    val importStatus by vm.trackerImportStatus.collectAsState()
    container.trackers.all.firstOrNull { it.id == importTrackerId }?.let { tracker ->
        AlertDialog(
            onDismissRequest = { importTrackerId = null },
            title = { Text(stringResource(R.string.tracker_import_title, tracker.displayName)) },
            text = { Text(stringResource(R.string.tracker_import_text, tracker.displayName)) },
            confirmButton = {
                TextButton(onClick = { vm.importTrackerList(tracker.id); importTrackerId = null }) {
                    Text(stringResource(R.string.tracker_import_confirm), color = AppColors.accentText)
                }
            },
            dismissButton = { TextButton(onClick = { importTrackerId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val passwordLogin = container.trackers.all.firstOrNull { it.id == passwordLoginId }
    passwordLogin?.let { tracker ->
        PasswordLoginDialog(
            name = tracker.displayName,
            onDismiss = { passwordLoginId = null },
            onLogin = { user, password ->
                passwordLoginId = null
                vm.passwordLogin(tracker, user, password)
            },
        )
    }

    SettingItem("tracker_accounts") {
        SettingsNote(stringResource(R.string.settings_trackers_note))
        Spacer(Modifier.height(4.dp))
        importStatus?.let { status ->
            TrackerImportCard(status, onCancel = vm::cancelTrackerImport, onDismiss = vm::dismissTrackerImport)
            Spacer(Modifier.height(10.dp))
        }
        container.trackers.all.forEach { tracker ->
            // Re-read whenever any stored token changes.
            val loggedIn by produceState(false, anilistTokenState, shikiTokenState, trackerValues) {
                value = tracker.isLoggedIn()
            }
            TrackerLoginCard(
                name = tracker.displayName,
                loggedIn = loggedIn,
                onLogin = {
                    if (tracker.login == TrackerLogin.Password) {
                        passwordLoginId = tracker.id
                    } else {
                        scope.launch {
                            val err = TrackerAuthActivity.start(context, tracker.id)
                            if (err != null) {
                                vm.statusMessage = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onLogout = { logoutTrackerId = tracker.id },
                onImport = if (tracker.supportsListImport) ({ importTrackerId = tracker.id }) else null,
                importing = importStatus?.let { it.running && it.trackerId == tracker.id } == true,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
    SettingsGroup {
        SettingItem("auto_link") {
            SwitchRow(
                label = stringResource(R.string.tracker_auto_link),
                hint = stringResource(R.string.tracker_auto_link_hint),
                checked = trackerAutoLink,
                onCheckedChange = vm::setTrackerAutoLink,
            )
        }
    }
}

@Composable
private fun TrackerLoginCard(
    name: String,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onImport: (() -> Unit)? = null,
    importing: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .padding(14.dp),
    ) {
        Text(
            if (loggedIn) {
                stringResource(R.string.tracker_status_logged_in, name)
            } else {
                stringResource(R.string.tracker_status_logged_out, name)
            },
            color = AppColors.textPrimary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (loggedIn) {
            if (onImport != null) {
                OutlinedButton(onClick = onImport, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tracker_import_action), color = AppColors.accentText)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_logout), color = AppColors.danger)
            }
        } else {
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = AppColors.onAccent,
                ),
            ) {
                Text(stringResource(R.string.action_login))
            }
        }
    }
}

/** The list import: a progress bar while it runs, its summary (with the names not found) once done. */
@Composable
private fun TrackerImportCard(status: TrackerImportStatus, onCancel: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.tracker_import_card_title, status.trackerName),
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        val stage = status.stage
        if (stage !is TrackerImportStage.Finished) {
            val progress = stage as? TrackerImportStage.Progress
            val known = progress != null && progress.total > 0
            Text(
                if (progress != null && known) {
                    stringResource(R.string.tracker_import_progress, progress.done, progress.total)
                } else {
                    stringResource(R.string.tracker_import_fetching)
                },
                color = AppColors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (progress != null && known) {
                LinearProgressIndicator(
                    progress = { progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.accent,
                    trackColor = AppColors.background,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.accent,
                    trackColor = AppColors.background,
                )
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_cancel), color = AppColors.accentText)
            }
        } else {
            val r = status.result
            val summary = when {
                status.cancelled -> stringResource(R.string.tracker_import_cancelled)
                status.error != null -> stringResource(R.string.tracker_import_failed, status.trackerName)
                r == null || (r.added == 0 && r.alreadyInLibrary == 0 && r.notFound.isEmpty()) ->
                    stringResource(R.string.tracker_import_empty, status.trackerName)
                else -> stringResource(R.string.tracker_import_done, r.added, r.alreadyInLibrary, r.notFound.size)
            }
            Text(
                summary,
                color = if (status.error != null) AppColors.danger else AppColors.textSecondary,
                fontSize = 13.sp,
            )
            if (r != null && r.failed > 0) {
                Text(
                    stringResource(R.string.tracker_import_chapters_failed, r.failed),
                    color = AppColors.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (r != null && r.notFound.isNotEmpty()) {
                val shown = r.notFound.take(NOT_FOUND_SHOWN).joinToString()
                val names = if (r.notFound.size > NOT_FOUND_SHOWN) {
                    stringResource(R.string.tracker_import_not_found_more, shown, r.notFound.size - NOT_FOUND_SHOWN)
                } else {
                    shown
                }
                Text(
                    stringResource(R.string.tracker_import_not_found, names),
                    color = AppColors.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_close), color = AppColors.accentText)
            }
        }
    }
}

private const val NOT_FOUND_SHOWN = 15

/** Username / e-mail and password for Kitsu and MangaUpdates; only the tracker's token is kept. */
@Composable
private fun PasswordLoginDialog(name: String, onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val valid = user.isNotBlank() && password.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_login_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tracker_login_user)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.tracker_login_password),
                    keyboardActions = KeyboardActions(
                        onDone = { if (valid) onLogin(user, password) else focus.clearFocus() },
                    ),
                )
                Text(stringResource(R.string.tracker_login_note), color = AppColors.textTertiary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onLogin(user, password) }) {
                Text(stringResource(R.string.action_login))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
