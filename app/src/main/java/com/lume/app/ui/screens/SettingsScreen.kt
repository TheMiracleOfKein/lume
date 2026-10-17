package com.lume.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.locale.AppLanguage
import com.lume.app.data.locale.AppLocaleController
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SettingsSection
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.tracker.TrackerAuthActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val prefs = vm.prefs
    val darkTheme by prefs.darkTheme.collectAsState()
    val libraryUpdates by prefs.libraryUpdatesEnabled.collectAsState()
    val libraryInterval by prefs.libraryUpdateIntervalHours.collectAsState()
    val libraryWifiOnly by prefs.libraryUpdateWifiOnly.collectAsState()
    val downloadsWifiOnly by prefs.downloadsWifiOnly.collectAsState()
    val autoDownloadNew by prefs.autoDownloadNew.collectAsState()
    val deleteAfterRead by prefs.deleteAfterRead.collectAsState()
    val downloadsCbz by prefs.downloadsAsCbz.collectAsState()
    val parallelPages by prefs.downloadParallelPages.collectAsState()
    val downloadsRoot by prefs.downloadsRoot.collectAsState()
    val searchNsfw by prefs.globalSearchNsfw.collectAsState()
    val reader by prefs.readerSettings.collectAsState()
    val appLanguageTag by prefs.appLanguage.collectAsState()
    val anilistTokenState by prefs.anilistAccessToken.collectAsState()
    val shikiTokenState by prefs.shikimoriAccessToken.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val statusMessage = vm.statusMessage
    val downloadPath = vm.downloadPath
    val anilistLoggedIn = !anilistTokenState.isNullOrBlank()
    val shikiLoggedIn = !shikiTokenState.isNullOrBlank()
    val currentLanguage = AppLanguage.fromStored(appLanguageTag)

    // The worker re-checks the permission before every notification, so the answer needs no handling.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pickLocalFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importLocalFolder(uri)
    }

    fun updateReader(next: ReaderSettings) = vm.updateReader(next)

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // ---------------- General ----------------
            SettingsSection(stringResource(R.string.settings_section_general))
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.settings_dark_theme),
                    checked = darkTheme,
                    onCheckedChange = vm::setDarkTheme,
                )
                Text(
                    stringResource(R.string.settings_language),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        ChoiceChip(
                            label = stringResource(lang.labelRes),
                            selected = currentLanguage == lang,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.setAppLanguage(lang.tag) { AppLocaleController.apply(lang) }
                            },
                        )
                    }
                }
            }

            // ---------------- Reader (defaults; the reader sheet edits the same values) ----------------
            SettingsSection(stringResource(R.string.settings_section_reader))
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.reader_immersive),
                    hint = stringResource(R.string.reader_immersive_hint),
                    checked = reader.immersive,
                    onCheckedChange = { updateReader(reader.copy(immersive = it)) },
                )
                SwitchRow(
                    label = stringResource(R.string.reader_keep_screen_on),
                    checked = reader.keepScreenOn,
                    onCheckedChange = { updateReader(reader.copy(keepScreenOn = it)) },
                )
                SwitchRow(
                    label = stringResource(R.string.reader_seamless),
                    checked = reader.seamlessReading,
                    onCheckedChange = { updateReader(reader.copy(seamlessReading = it)) },
                )
                SwitchRow(
                    label = stringResource(R.string.reader_tap_zones),
                    checked = reader.tapZones,
                    onCheckedChange = { updateReader(reader.copy(tapZones = it)) },
                )
                SwitchRow(
                    label = stringResource(R.string.reader_volume_keys),
                    checked = reader.volumeKeys,
                    onCheckedChange = { updateReader(reader.copy(volumeKeys = it)) },
                )
                SwitchRow(
                    label = stringResource(R.string.reader_double_tap_zoom),
                    checked = reader.doubleTapZoom,
                    onCheckedChange = { updateReader(reader.copy(doubleTapZoom = it)) },
                )
            }

            // ---------------- Library ----------------
            SettingsSection(stringResource(R.string.settings_section_library))
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.settings_library_auto_check),
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
                AnimatedVisibility(
                    visible = libraryUpdates,
                    enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                    exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.settings_library_interval),
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppPreferences.LIBRARY_INTERVALS.forEach { hours ->
                                ChoiceChip(
                                    label = stringResource(R.string.settings_interval_hours, hours),
                                    selected = hours == libraryInterval,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        vm.setLibraryInterval(hours)
                                    },
                                )
                            }
                        }
                        SwitchRow(
                            label = stringResource(R.string.settings_wifi_only),
                            checked = libraryWifiOnly,
                            modifier = Modifier.padding(top = 4.dp),
                            onCheckedChange = vm::setLibraryWifiOnly,
                        )
                    }
                }
            }

            // ---------------- Downloads ----------------
            SettingsSection(stringResource(R.string.settings_section_downloads))
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.settings_downloads_wifi_only),
                    checked = downloadsWifiOnly,
                    onCheckedChange = vm::setDownloadsWifiOnly,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_downloads_auto_new),
                    hint = stringResource(R.string.settings_downloads_auto_new_hint),
                    checked = autoDownloadNew,
                    onCheckedChange = vm::setAutoDownloadNew,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_downloads_delete_read),
                    hint = stringResource(R.string.settings_downloads_delete_read_hint),
                    checked = deleteAfterRead,
                    onCheckedChange = vm::setDeleteAfterRead,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_downloads_cbz),
                    hint = stringResource(R.string.settings_downloads_cbz_hint),
                    checked = downloadsCbz,
                    onCheckedChange = vm::setDownloadsCbz,
                )
                Text(
                    stringResource(R.string.settings_downloads_parallel),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
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
                Text(
                    stringResource(R.string.settings_download_folder),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                )
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
                Text(
                    text = downloadPath,
                    color = AppColors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
                )
                Text(
                    stringResource(R.string.settings_storage_note),
                    color = AppColors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsLink(stringResource(R.string.settings_downloads)) { nav.navigate(Routes.Downloads) }
                SettingsLink(stringResource(R.string.settings_restore_downloads)) {
                    vm.recoverDownloads { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }
            }

            // ---------------- Local files ----------------
            SettingsSection(stringResource(R.string.settings_section_local))
            SettingsGroup {
                SettingsLink(
                    if (vm.localBusy) stringResource(R.string.local_importing) else stringResource(R.string.local_import_folder),
                ) {
                    if (!vm.localBusy) pickLocalFolder.launch(null)
                }
                SettingsLink(stringResource(R.string.local_open_library)) { nav.navigate(Routes.browse("local")) }
                SettingsLink(stringResource(R.string.local_clean_temp)) { vm.cleanTemporaryFiles() }
                Text(
                    stringResource(R.string.local_folder_hint, vm.localPath),
                    color = AppColors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
                )
            }

            // ---------------- Search & sources ----------------
            SettingsSection(stringResource(R.string.settings_section_search))
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.settings_search_nsfw),
                    checked = searchNsfw,
                    onCheckedChange = vm::setSearchNsfw,
                )
                SettingsLink(stringResource(R.string.settings_sources)) { nav.navigate(Routes.Sources) }
            }

            // ---------------- Data & accounts ----------------
            SettingsSection(stringResource(R.string.settings_section_data))
            Text(stringResource(R.string.settings_trackers), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TrackerLoginCard(
                name = "Shikimori",
                loggedIn = shikiLoggedIn,
                onLogin = {
                    scope.launch {
                        val err = TrackerAuthActivity.start(context, "shikimori")
                        if (err != null) {
                            vm.statusMessage = err
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLogout = { vm.logout("shikimori") },
            )
            Spacer(Modifier.height(10.dp))
            TrackerLoginCard(
                name = "AniList",
                loggedIn = anilistLoggedIn,
                onLogin = {
                    scope.launch {
                        val err = TrackerAuthActivity.start(context, "anilist")
                        if (err != null) {
                            vm.statusMessage = err
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLogout = { vm.logout("anilist") },
            )
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                SettingsLink(stringResource(R.string.settings_create_backup), onClick = vm::createBackup)
                SettingsLink(stringResource(R.string.settings_restore_backup), onClick = vm::restoreBackup)
            }
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
            ) {
                Text(
                    statusMessage.orEmpty(),
                    color = AppColors.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** Rounded surface that groups related settings rows. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { content() }
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
                    contentColor = Color.Black,
                ),
            ) {
                Text(stringResource(R.string.action_login))
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.accent, unselectedColor = AppColors.textSecondary),
        )
        Column {
            Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
            Text(detail, color = AppColors.textTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.textSecondary,
        )
    }
}
