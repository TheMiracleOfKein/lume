package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import com.lume.app.ui.nav.Routes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import android.os.Build
import com.lume.app.LocalAppContainer
import com.lume.app.data.CrashLog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.BuildConfig
import com.lume.app.R
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.LumeLogo
import com.lume.app.ui.components.UpdateSection
import com.lume.app.ui.components.openInBrowser
import com.lume.app.ui.theme.AppColors

/** One open-source component Lume ships with. */
private data class OpenSourceLib(val name: String, val license: String, val url: String)

private const val APACHE = "Apache License 2.0"
private const val MIT = "MIT License"
private const val ZERO_BSD = "BSD Zero Clause License"
private const val UNRAR = "UnRAR license"

private val LIBRARIES = listOf(
    OpenSourceLib("AndroidX (Compose, Navigation, Room, WorkManager, DataStore, Biometric …)", APACHE, "https://developer.android.com/jetpack/androidx"),
    OpenSourceLib("Kotlin, kotlinx.coroutines, kotlinx.serialization", APACHE, "https://github.com/JetBrains/kotlin"),
    OpenSourceLib("OkHttp (with DNS over HTTPS)", APACHE, "https://github.com/square/okhttp"),
    OpenSourceLib("Coil", APACHE, "https://github.com/coil-kt/coil"),
    OpenSourceLib("Telephoto", APACHE, "https://github.com/saket/telephoto"),
    OpenSourceLib("jsoup", MIT, "https://github.com/jhy/jsoup"),
    OpenSourceLib("JSpecify", APACHE, "https://github.com/jspecify/jspecify"),
    OpenSourceLib("Junrar (CBR files)", UNRAR, "https://github.com/junrar/junrar"),
    OpenSourceLib("Apache Commons Compress (CB7 files)", APACHE, "https://commons.apache.org/proper/commons-compress/"),
    OpenSourceLib("XZ for Java", ZERO_BSD, "https://tukaani.org/xz/java.html"),
    OpenSourceLib("Material Symbols / Icons", APACHE, "https://github.com/google/material-design-icons"),
)

private const val REPO = "https://github.com/TheMiracleOfKein/lume"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    // A small file, written only by a crash: reading it once per visit is enough.
    var crashes by remember { mutableStateOf(container.crashLog.entries()) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.crash_log_clear_title)) },
            confirmButton = {
                TextButton(onClick = {
                    container.crashLog.clear()
                    crashes = emptyList()
                    confirmClear = false
                }) { Text(stringResource(R.string.error_log_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    BackButton(onClick = { nav.popBackStack() })
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(AppColors.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LumeLogo(selected = true, size = 56.dp, contentDescription = null)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.app_name), color = AppColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
            item(key = "updates") {
                UpdateSection()
                Spacer(Modifier.height(12.dp))
            }
            item(key = "links") {
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(AppColors.surface)) {
                    LinkRow(stringResource(R.string.about_source_code)) { openInBrowser(context, REPO) }
                    LinkRow(stringResource(R.string.about_releases)) { openInBrowser(context, "$REPO/releases") }
                    LinkRow(stringResource(R.string.about_license_app)) { openInBrowser(context, "$REPO/blob/main/LICENSE") }
                    LinkRow(stringResource(R.string.error_log_title), external = false) { nav.navigate(Routes.ErrorLog) }
                }
            }
            item(key = "crash-log") {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(AppColors.surface)) {
                    val subject = stringResource(R.string.crash_log_subject, BuildConfig.VERSION_NAME)
                    LinkRow(
                        stringResource(R.string.crash_log_share),
                        subtitle = if (crashes.isEmpty()) {
                            stringResource(R.string.crash_log_none)
                        } else {
                            pluralStringResource(R.plurals.crash_log_count, crashes.size, crashes.size)
                        },
                        icon = Icons.Outlined.Share,
                    ) {
                        val report = CrashLog.report(deviceLine(), crashes, container.errorLog.entries.value)
                        shareText(context, report, subject)
                    }
                    if (crashes.isNotEmpty()) {
                        LinkRow(stringResource(R.string.crash_log_clear), icon = Icons.Outlined.Delete) { confirmClear = true }
                    }
                }
                Text(
                    stringResource(R.string.crash_log_hint),
                    color = AppColors.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                )
            }
            item(key = "libs-title") {
                Text(
                    stringResource(R.string.about_licenses),
                    color = AppColors.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                )
            }
            items(LIBRARIES, key = { it.name }) { lib ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { openInBrowser(context, lib.url) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(lib.name, color = AppColors.textPrimary, fontSize = 14.sp)
                        Text(lib.license, color = AppColors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(
    label: String,
    external: Boolean = true,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = AppColors.textSecondary, fontSize = 12.sp)
        }
        Icon(
            icon ?: if (external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = AppColors.textSecondary,
        )
    }
}

/** App version, Android version and phone model: the first line of a shared log. */
private fun deviceLine(): String =
    "Lume ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
        "${Build.MANUFACTURER} ${Build.MODEL}"
