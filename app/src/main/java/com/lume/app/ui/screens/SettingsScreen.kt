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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.lume.app.data.network.DohProvider
import com.lume.app.ui.theme.ThemeMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.lume.app.data.tracker.Tracker
import com.lume.app.data.tracker.TrackerLogin
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.res.pluralStringResource
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val prefs = vm.prefs
    val themeMode by prefs.themeMode.collectAsState()
    val pureBlack by prefs.pureBlack.collectAsState()
    val dynamicColor by prefs.dynamicColor.collectAsState()
    val translateLabels by prefs.translateChapterLabels.collectAsState()
    val incognito by prefs.incognito.collectAsState()
    val appLock by prefs.appLock.collectAsState()
    val secureScreen by prefs.secureScreen.collectAsState()
    val doh by prefs.dnsOverHttps.collectAsState()
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
    val trackerValues by prefs.trackerValues.collectAsState()
    val trackerAutoLink by prefs.trackerAutoLink.collectAsState()
    val findOtherSources by prefs.findOtherSources.collectAsState()
    var passwordLogin by remember { mutableStateOf<Tracker?>(null) }
    passwordLogin?.let { tracker ->
        PasswordLoginDialog(
            name = tracker.displayName,
            onDismiss = { passwordLogin = null },
            onLogin = { user, password ->
                passwordLogin = null
                vm.passwordLogin(tracker, user, password)
            },
        )
    }
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
    val saveBackupTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveBackupTo(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri)
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setAutoBackupFolder(uri)
    }
    val autoBackupDays by prefs.autoBackupDays.collectAsState()
    val autoBackupKeep by prefs.autoBackupKeep.collectAsState()
    val autoBackupFolder by prefs.autoBackupFolder.collectAsState()

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
                Text(
                    stringResource(R.string.settings_theme),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.System to R.string.settings_theme_system,
                        ThemeMode.Light to R.string.settings_theme_light,
                        ThemeMode.Dark to R.string.settings_theme_dark,
                    ).forEach { (mode, label) ->
                        ChoiceChip(
                            label = stringResource(label),
                            selected = themeMode == mode.key,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setThemeMode(mode.key) },
                        )
                    }
                }
                SwitchRow(
                    label = stringResource(R.string.settings_pure_black),
                    hint = stringResource(R.string.settings_pure_black_hint),
                    checked = pureBlack,
                    onCheckedChange = vm::setPureBlack,
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        hint = stringResource(R.string.settings_dynamic_color_hint),
                        checked = dynamicColor,
                        onCheckedChange = vm::setDynamicColor,
                    )
                }
                SwitchRow(
                    label = stringResource(R.string.settings_translate_labels),
                    hint = stringResource(R.string.settings_translate_labels_hint),
                    checked = translateLabels,
                    onCheckedChange = vm::setTranslateLabels,
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
                SwitchRow(
                    label = stringResource(R.string.settings_find_other_sources),
                    hint = stringResource(R.string.settings_find_other_sources_hint),
                    checked = findOtherSources,
                    onCheckedChange = vm::setFindOtherSources,
                )
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

            // ---------------- Privacy ----------------
            SettingsSection(stringResource(R.string.settings_section_privacy))
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.settings_incognito),
                    hint = stringResource(R.string.settings_incognito_hint),
                    checked = incognito,
                    onCheckedChange = vm::setIncognito,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_app_lock),
                    hint = stringResource(R.string.settings_app_lock_hint),
                    checked = appLock,
                    onCheckedChange = vm::setAppLock,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_secure_screen),
                    hint = stringResource(R.string.settings_secure_screen_hint),
                    checked = secureScreen,
                    onCheckedChange = vm::setSecureScreen,
                )
            }

            // ---------------- Network & storage ----------------
            SettingsSection(stringResource(R.string.settings_section_network))
            SettingsGroup {
                Text(
                    stringResource(R.string.settings_doh),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip(
                        label = stringResource(R.string.settings_doh_off),
                        selected = doh.isEmpty(),
                        onClick = { vm.setDoh("") },
                    )
                    DohProvider.entries.forEach { provider ->
                        ChoiceChip(label = provider.label, selected = doh == provider.id, onClick = { vm.setDoh(provider.id) })
                    }
                }
                Text(
                    stringResource(R.string.settings_doh_hint),
                    color = AppColors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
                )
                SettingsLink(stringResource(R.string.settings_clear_cache)) { vm.clearCache() }
                SettingsLink(stringResource(R.string.settings_clear_cookies)) { vm.clearCookies() }
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
                            passwordLogin = tracker
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
            SettingsGroup {
                SwitchRow(
                    label = stringResource(R.string.tracker_auto_link),
                    hint = stringResource(R.string.tracker_auto_link_hint),
                    checked = trackerAutoLink,
                    onCheckedChange = vm::setTrackerAutoLink,
                )
            }
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                SettingsLink(stringResource(R.string.settings_create_backup), onClick = vm::createBackup)
                SettingsLink(stringResource(R.string.backup_save_to)) { saveBackupTo.launch(vm.suggestedBackupName()) }
                SettingsLink(stringResource(R.string.settings_restore_backup), onClick = vm::restoreBackup)
                SettingsLink(stringResource(R.string.backup_restore_file)) {
                    if (vm.importProgress == null) openBackup.launch(arrayOf("*/*"))
                }
                Text(
                    stringResource(R.string.backup_restore_file_hint),
                    color = AppColors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
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
                Text(
                    stringResource(R.string.backup_auto),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                )
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
                AnimatedVisibility(
                    visible = autoBackupDays > 0,
                    enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                    exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.backup_auto_keep),
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                        )
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
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                SettingsLink(stringResource(R.string.menu_about)) { nav.navigate(Routes.About) }
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
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
