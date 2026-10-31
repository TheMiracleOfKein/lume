package com.lume.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.BuildConfig
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.update.AppRelease
import com.lume.app.data.update.UpdateState
import com.lume.app.ui.theme.AppColors

/**
 * The update dialog over the whole app: a found release with Download and install, the
 * download's progress, then Install. A version the user skipped isn't offered by the
 * automatic check again.
 */
@Composable
fun UpdatePrompt() {
    val updater = LocalAppContainer.current.updates
    val context = LocalContext.current
    val state by updater.state.collectAsState()
    // Hiding the dialog while the APK downloads; it comes back once the file is ready.
    var hiddenWhileDownloading by remember { mutableStateOf(false) }
    if (state !is UpdateState.Downloading) hiddenWhileDownloading = false

    when (val s = state) {
        is UpdateState.Available -> {
            if (!s.manual && updater.isSkipped(s.release)) return
            AlertDialog(
                onDismissRequest = updater::dismiss,
                title = { Text(stringResource(R.string.update_available_title, s.release.version)) },
                text = { ReleaseNotes(s.release) },
                confirmButton = {
                    if (s.release.apkUrl != null) {
                        TextButton(onClick = { updater.download(s.release) }) {
                            Text(stringResource(R.string.update_download_install))
                        }
                    } else {
                        TextButton(onClick = { openInBrowser(context, s.release.pageUrl); updater.dismiss() }) {
                            Text(stringResource(R.string.update_open_page))
                        }
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { updater.skip(s.release); updater.dismiss() }) {
                            Text(stringResource(R.string.update_skip))
                        }
                        TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.update_later)) }
                    }
                },
            )
        }
        is UpdateState.Downloading -> if (!hiddenWhileDownloading) {
            AlertDialog(
                onDismissRequest = { hiddenWhileDownloading = true },
                title = { Text(stringResource(R.string.update_downloading_title, s.release.version)) },
                text = { DownloadBar(s.fraction) },
                confirmButton = {
                    TextButton(onClick = { hiddenWhileDownloading = true }) { Text(stringResource(R.string.update_hide)) }
                },
            )
        }
        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_ready_title, s.release.version)) },
            text = { Text(stringResource(R.string.update_ready_text)) },
            confirmButton = {
                TextButton(onClick = { updater.install(context, s.apk) }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.update_later)) }
            },
        )
        is UpdateState.Failed -> if (s.release != null) {
            AlertDialog(
                onDismissRequest = updater::dismiss,
                title = { Text(stringResource(R.string.update_failed_title)) },
                text = { Text(s.message) },
                confirmButton = {
                    TextButton(onClick = { updater.download(s.release) }) { Text(stringResource(R.string.action_retry)) }
                },
                dismissButton = {
                    TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.action_close)) }
                },
            )
        }
        else -> Unit
    }
}

/** About → Updates: the installed version's status, a check button and the automatic check switch. */
@Composable
fun UpdateSection() {
    val updater = LocalAppContainer.current.updates
    val state by updater.state.collectAsState()
    val auto by updater.autoCheck.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.surface)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.update_section_title), color = AppColors.textPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        val status = when (val s = state) {
            UpdateState.Idle -> stringResource(R.string.update_status_idle, BuildConfig.VERSION_NAME)
            UpdateState.Checking -> stringResource(R.string.update_status_checking)
            UpdateState.UpToDate -> stringResource(R.string.update_status_latest, BuildConfig.VERSION_NAME)
            is UpdateState.Available -> stringResource(R.string.update_available_title, s.release.version)
            is UpdateState.Downloading -> stringResource(R.string.update_downloading_title, s.release.version)
            is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready_title, s.release.version)
            is UpdateState.Failed -> stringResource(R.string.update_status_failed, s.message)
        }
        Text(status, color = AppColors.textSecondary, fontSize = 13.sp)
        (state as? UpdateState.Downloading)?.let {
            Spacer(Modifier.height(8.dp))
            DownloadBar(it.fraction)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val busy = state is UpdateState.Checking || state is UpdateState.Downloading
            Button(
                onClick = { updater.check() },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
            ) { Text(stringResource(R.string.update_check_now)) }
        }
        SwitchRow(
            label = stringResource(R.string.update_auto_check),
            checked = auto,
            onCheckedChange = updater::setAutoCheck,
        )
    }
}

@Composable
private fun ReleaseNotes(release: AppRelease) {
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME), fontSize = 13.sp)
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(release.notes.trim(), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DownloadBar(fraction: Float?) {
    Column {
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.accent)
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = AppColors.accent)
            Text(
                "${(fraction * 100).toInt()}%",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
