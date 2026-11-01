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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.tracker.Tracker
import com.lume.app.data.tracker.TrackerLogin
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
                onLogout = { vm.logout(tracker.id) },
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

/** Username / e-mail and password for Kitsu and MangaUpdates; only the tracker's token is kept. */
@Composable
private fun PasswordLoginDialog(name: String, onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tracker_login_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(stringResource(R.string.tracker_login_note), color = AppColors.textTertiary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = user.isNotBlank() && password.isNotEmpty(), onClick = { onLogin(user, password) }) {
                Text(stringResource(R.string.action_login))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
