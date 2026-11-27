package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lume.app.data.security.SecretSealer
import com.lume.app.domain.AutoDownloadMode
import com.lume.app.domain.LibraryCheckOutcome
import com.lume.app.domain.LibraryDisplay
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.UpdateRules
import com.lume.app.domain.LibrarySort
import com.lume.app.domain.decodeLibraryFilters
import com.lume.app.domain.encodeLibraryFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Every preference is a [StateFlow] that already holds the stored value: the file is read once,
 * synchronously, when the app starts. Screens read `.value` on their first frame, so switches,
 * expanded sections and the theme never start from a default and then animate to the real value.
 *
 * The preferences sit in a chain of classes, one per area, each in its own file: [PrefsBase]
 * (stores, helpers, backup of settings), [TrackerPrefs], [AiPrefs], [SyncPrefs], [ReaderPrefs]
 * and this one with the rest (appearance, privacy, library, sources, downloads, backups).
 */
class AppPreferences(
    context: Context,
    scope: CoroutineScope,
    secrets: SecretSealer = SecretSealer(),
) : ReaderPrefs(context, scope, secrets) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private fun activeSourceKey(kind: com.lume.app.domain.MediaKind) = stringPreferencesKey(
        // The Comics key is the one used before reading modes, so nothing is lost on update.
        if (kind == com.lume.app.domain.MediaKind.Comics) "active_source_id" else "active_source_id_${kind.id}",
    )
    private val activeModeKey = stringPreferencesKey("active_mode")
    private val libraryIntervalKey = intPreferencesKey("library_update_interval_hours")
    private val libraryWifiOnlyKey = booleanPreferencesKey("library_update_wifi_only")
    private val librarySkipCompletedKey = booleanPreferencesKey("library_update_skip_completed")
    private val librarySkipNotStartedKey = booleanPreferencesKey("library_update_skip_not_started")
    private val librarySkipUnreadKey = booleanPreferencesKey("library_update_skip_unread")
    private val libraryChargingOnlyKey = booleanPreferencesKey("library_update_charging_only")
    private val libraryUpdateCategoriesKey = stringSetPreferencesKey("library_update_categories")
    private val libraryNotifyMutedKey = stringSetPreferencesKey("library_notify_muted_categories")
    private val downloadsWifiOnlyKey = booleanPreferencesKey("downloads_wifi_only")
    private val globalSearchNsfwKey = booleanPreferencesKey("global_search_nsfw")
    private val libraryUpdatesKey = booleanPreferencesKey("library_updates_enabled")
    private val appLanguageKey = stringPreferencesKey("app_language")

    private val libraryDisplayKey = stringPreferencesKey("library_display")
    private val librarySortKey = stringPreferencesKey("library_sort")
    private val librarySortAscKey = booleanPreferencesKey("library_sort_asc")
    private val libraryFiltersKey = stringPreferencesKey("library_filters")
    private val libraryGenresKey = stringSetPreferencesKey("library_genre_filter")
    private val libraryUnreadBadgeKey = booleanPreferencesKey("library_badge_unread")
    private val libraryDownloadedBadgeKey = booleanPreferencesKey("library_badge_downloaded")
    private val librarySourcesBadgeKey = booleanPreferencesKey("library_badge_sources")
    private val libraryFilterChipsKey = booleanPreferencesKey("library_filter_chips")
    private val libraryContinueButtonKey = booleanPreferencesKey("library_continue_button")
    private val defaultCategoryKey = longPreferencesKey("default_category_id")
    private val libraryLastCheckedKey = longPreferencesKey("library_last_checked_at")
    private val libraryLastCheckFailedKey = intPreferencesKey("library_last_check_failed")
    private val libraryLastCheckTotalKey = intPreferencesKey("library_last_check_total")

    private val sourceSettingsKey = stringPreferencesKey("source_settings")
    private val disabledSourcesKey = stringSetPreferencesKey("disabled_sources")
    private val pinnedSourcesKey = stringSetPreferencesKey("pinned_sources")
    private val searchHistoryKey = stringPreferencesKey("search_history")
    private val packIndexUrlKey = stringPreferencesKey("pack_index_url")

    private val downloadsPausedKey = booleanPreferencesKey("downloads_paused")
    private val downloadPagesKey = intPreferencesKey("download_parallel_pages")
    private val downloadsCbzKey = booleanPreferencesKey("downloads_cbz")
    private val downloadsRootKey = stringPreferencesKey("downloads_root")
    private val autoDownloadNewKey = booleanPreferencesKey("downloads_auto_new")
    private val autoDownloadModeKey = intPreferencesKey("downloads_auto_new_mode")
    private val deleteAfterReadKey = booleanPreferencesKey("downloads_delete_after_read")

    private val findOtherSourcesKey = booleanPreferencesKey("find_other_sources")
    private val similarTitlesKey = booleanPreferencesKey("similar_titles")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val pureBlackKey = booleanPreferencesKey("theme_pure_black")
    private val dynamicColorKey = booleanPreferencesKey("theme_dynamic_color")
    private val incognitoKey = booleanPreferencesKey("incognito")
    private val appLockKey = booleanPreferencesKey("app_lock")
    private val secureScreenKey = booleanPreferencesKey("secure_screen")
    private val dohKey = stringPreferencesKey("dns_over_https")
    private val remoteHostsKey = booleanPreferencesKey("remote_hosts")
    private val hostsFetchedAtKey = longPreferencesKey("hosts_fetched_at")
    private val translateLabelsKey = booleanPreferencesKey("translate_chapter_labels")
    private val autoBackupDaysKey = intPreferencesKey("auto_backup_days")
    private val autoBackupKeepKey = intPreferencesKey("auto_backup_keep")
    private val folderScanHoursKey = intPreferencesKey("folder_scan_hours")
    private val autoBackupFolderKey = stringPreferencesKey("auto_backup_folder")

    val darkTheme: StateFlow<Boolean> = state { it[darkThemeKey] ?: true }

    /** "system" | "light" | "dark"; before v0.5 only [darkTheme] existed and still decides. */
    val themeMode: StateFlow<String> = state {
        it[themeModeKey] ?: if (it[darkThemeKey] ?: true) "dark" else "light"
    }
    val pureBlack: StateFlow<Boolean> = state { it[pureBlackKey] ?: false }
    val dynamicColor: StateFlow<Boolean> = state { it[dynamicColorKey] ?: false }

    suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit {
            it[themeModeKey] = mode
            if (mode != "system") it[darkThemeKey] = mode == "dark"
        }
    }

    suspend fun setPureBlack(value: Boolean) = put(pureBlackKey, value)

    suspend fun setDynamicColor(value: Boolean) = put(dynamicColorKey, value)

    /** Incognito: reading leaves no history, progress or read marks. */
    val incognito: StateFlow<Boolean> = state { it[incognitoKey] ?: false }

    /** Ask for the screen lock (fingerprint, PIN …) when the app opens. */
    val appLock: StateFlow<Boolean> = state { it[appLockKey] ?: false }

    /** Hide the app's content in the recent-apps screen and block screenshots. */
    val secureScreen: StateFlow<Boolean> = state { it[secureScreenKey] ?: false }

    /** DNS over HTTPS provider id ("" = the system resolver); see `data/network/Doh.kt`. */
    val dnsOverHttps: StateFlow<String> = state { it[dohKey].orEmpty() }

    /** Source addresses are refreshed from the hosts file (`docs/packs/hosts.json`); on by default. */
    val remoteHosts: StateFlow<Boolean> = state { it[remoteHostsKey] ?: true }

    /** When the hosts file was last downloaded (epoch millis), 0 if never. */
    val hostsFetchedAt: StateFlow<Long> = state { it[hostsFetchedAtKey] ?: 0L }

    /** Russian-only chapter labels ("Том 1 Глава 5") are shown in the app language. */
    val translateChapterLabels: StateFlow<Boolean> = state { it[translateLabelsKey] ?: true }

    suspend fun setIncognito(value: Boolean) = put(incognitoKey, value)

    suspend fun setAppLock(value: Boolean) = put(appLockKey, value)

    suspend fun setSecureScreen(value: Boolean) = put(secureScreenKey, value)

    suspend fun setDnsOverHttps(value: String) = put(dohKey, value)

    suspend fun setRemoteHosts(value: Boolean) = put(remoteHostsKey, value)

    suspend fun setHostsFetchedAt(value: Long) = put(hostsFetchedAtKey, value)

    suspend fun setTranslateChapterLabels(value: Boolean) = put(translateLabelsKey, value)

    /** The kind of content the library, history and sources show. */
    val activeMode: StateFlow<com.lume.app.domain.MediaKind> =
        state { com.lume.app.domain.MediaKind.fromId(it[activeModeKey]) }

    /** The chosen source of the active mode (each mode keeps its own); null until one is chosen. */
    val activeSourceId: StateFlow<String?> = state {
        it[activeSourceKey(com.lume.app.domain.MediaKind.fromId(it[activeModeKey]))]
    }

    /** The chosen source of [kind], whatever the active mode is. */
    suspend fun activeSourceIdOf(kind: com.lume.app.domain.MediaKind): String? =
        context.appDataStore.data.first()[activeSourceKey(kind)]

    suspend fun setActiveMode(kind: com.lume.app.domain.MediaKind) = put(activeModeKey, kind.id)

    val libraryUpdatesEnabled: StateFlow<Boolean> = state { it[libraryUpdatesKey] ?: false }

    /** "system" | "en" | "ru" | … — see [com.lume.app.data.locale.AppLanguage]. */
    val appLanguage: StateFlow<String> = state { it[appLanguageKey] ?: "system" }

    /**
     * The sources' last errors as JSON. They live in [privateDataStore], so they stay on the phone:
     * out of the settings backup, sync and the system backup.
     */
    fun sourceProblemsRaw(): String? = currentPrivate.value[stringPreferencesKey("source_problems")]

    suspend fun setSourceProblemsRaw(json: String) {
        context.privateDataStore.edit { it[stringPreferencesKey("source_problems")] = json }
    }

    /** Look for a title on the other sources when it is added to the library ("Also on …"). */
    val findOtherSources: StateFlow<Boolean> = state { it[findOtherSourcesKey] ?: true }

    suspend fun setFindOtherSources(value: Boolean) = put(findOtherSourcesKey, value)

    /** "Similar titles" on the title screen (asks AniList, or MyAnimeList, by the title's name). */
    val similarTitles: StateFlow<Boolean> = state { it[similarTitlesKey] ?: true }

    suspend fun setSimilarTitles(value: Boolean) = put(similarTitlesKey, value)

    /** Hours between background library checks (one of [LIBRARY_INTERVALS]). */
    val libraryUpdateIntervalHours: StateFlow<Int> = state { it[libraryIntervalKey]?.takeIf { h -> h in LIBRARY_INTERVALS } ?: 6 }
    val libraryUpdateWifiOnly: StateFlow<Boolean> = state { it[libraryWifiOnlyKey] ?: false }

    /** Titles a library check leaves out, and whether background checks wait for the charger. */
    val libraryUpdateRules: StateFlow<UpdateRules> = state {
        UpdateRules(
            skipCompleted = it[librarySkipCompletedKey] ?: false,
            skipNotStarted = it[librarySkipNotStartedKey] ?: false,
            skipUnread = it[librarySkipUnreadKey] ?: false,
            chargingOnly = it[libraryChargingOnlyKey] ?: false,
            categories = it[libraryUpdateCategoriesKey].orEmpty().mapNotNull(String::toLongOrNull).toSet(),
        )
    }
    /** Category ids (0 = uncategorized) whose update notifications are muted. */
    val notifyMutedCategories: StateFlow<Set<Long>> = state {
        it[libraryNotifyMutedKey].orEmpty().mapNotNull(String::toLongOrNull).toSet()
    }
    val downloadsWifiOnly: StateFlow<Boolean> = state { it[downloadsWifiOnlyKey] ?: false }
    val globalSearchNsfw: StateFlow<Boolean> = state { it[globalSearchNsfwKey] ?: false }

    val librarySettings: StateFlow<LibrarySettings> = state { prefs ->
        LibrarySettings(
            display = LibraryDisplay.entries.firstOrNull { it.name == prefs[libraryDisplayKey] } ?: LibraryDisplay.Grid,
            sort = LibrarySort.entries.firstOrNull { it.name == prefs[librarySortKey] } ?: LibrarySort.Title,
            ascending = prefs[librarySortAscKey] ?: true,
            filters = decodeLibraryFilters(prefs[libraryFiltersKey]),
            unreadBadge = prefs[libraryUnreadBadgeKey] ?: true,
            downloadedBadge = prefs[libraryDownloadedBadgeKey] ?: true,
            sourcesBadge = prefs[librarySourcesBadgeKey] ?: true,
            filterChips = prefs[libraryFilterChipsKey] ?: true,
            continueButton = prefs[libraryContinueButtonKey] ?: false,
            genres = prefs[libraryGenresKey].orEmpty(),
        )
    }

    /** Category that titles added to the library go into; null = none. */
    val defaultCategoryId: StateFlow<Long?> = state { it[defaultCategoryKey] }

    suspend fun setLibrarySettings(settings: LibrarySettings) {
        context.appDataStore.edit {
            it[libraryDisplayKey] = settings.display.name
            it[librarySortKey] = settings.sort.name
            it[librarySortAscKey] = settings.ascending
            it[libraryFiltersKey] = encodeLibraryFilters(settings.filters)
            it[libraryUnreadBadgeKey] = settings.unreadBadge
            it[libraryDownloadedBadgeKey] = settings.downloadedBadge
            it[librarySourcesBadgeKey] = settings.sourcesBadge
            it[libraryFilterChipsKey] = settings.filterChips
            it[libraryContinueButtonKey] = settings.continueButton
            it[libraryGenresKey] = settings.genres
        }
    }

    /** When the last library check finished (0 = never). */
    val libraryLastCheckedAt: StateFlow<Long> = state { it[libraryLastCheckedKey] ?: 0L }

    /** How many titles the last library check tried and how many of them failed. */
    val libraryLastCheck: StateFlow<LibraryCheckOutcome> = state {
        LibraryCheckOutcome(it[libraryLastCheckTotalKey] ?: 0, it[libraryLastCheckFailedKey] ?: 0)
    }

    /** Per-source setting values (source id → setting key → value); see [com.lume.app.domain.SourceSetting]. */
    val sourceSettings: StateFlow<Map<String, Map<String, String>>> = state { prefs ->
        prefs[sourceSettingsKey]?.let { raw ->
            runCatching { prefsJson.decodeFromString<Map<String, Map<String, String>>>(raw) }.getOrNull()
        }.orEmpty()
    }

    suspend fun setSourceSettings(sourceId: String, values: Map<String, String>) {
        context.appDataStore.edit {
            val all = it[sourceSettingsKey]?.let { raw ->
                runCatching { prefsJson.decodeFromString<Map<String, Map<String, String>>>(raw) }.getOrNull()
            }.orEmpty().toMutableMap()
            if (values.isEmpty()) all.remove(sourceId) else all[sourceId] = values
            it[sourceSettingsKey] = prefsJson.encodeToString(all)
        }
    }

    /** Sources hidden from global search and the browse lists. */
    val disabledSources: StateFlow<Set<String>> = state { it[disabledSourcesKey].orEmpty() }

    /** Sources listed first. */
    val pinnedSources: StateFlow<Set<String>> = state { it[pinnedSourcesKey].orEmpty() }

    suspend fun setSourceDisabled(id: String, disabled: Boolean) {
        context.appDataStore.edit {
            val now = it[disabledSourcesKey].orEmpty()
            it[disabledSourcesKey] = if (disabled) now + id else now - id
        }
    }

    suspend fun setSourcePinned(id: String, pinned: Boolean) {
        context.appDataStore.edit {
            val now = it[pinnedSourcesKey].orEmpty()
            it[pinnedSourcesKey] = if (pinned) now + id else now - id
        }
    }

    /** Recent search texts, newest first. */
    val searchHistory: StateFlow<List<String>> = state { prefs ->
        prefs[searchHistoryKey]?.let { raw -> runCatching { prefsJson.decodeFromString<List<String>>(raw) }.getOrNull() }
            .orEmpty()
    }

    suspend fun setSearchHistory(items: List<String>) =
        put(searchHistoryKey, prefsJson.encodeToString(items.take(MAX_SEARCH_HISTORY)))

    /** The download queue is on hold (the running chapter goes back to the queue). */
    val downloadsPaused: StateFlow<Boolean> = state { it[downloadsPausedKey] ?: false }

    /** Pages of one chapter fetched at the same time (1 … [MAX_PARALLEL_PAGES]). */
    val downloadParallelPages: StateFlow<Int> = state { (it[downloadPagesKey] ?: 2).coerceIn(1, MAX_PARALLEL_PAGES) }

    /** New downloads are packed into one `.cbz` per chapter. */
    val downloadsAsCbz: StateFlow<Boolean> = state { it[downloadsCbzKey] ?: false }

    /** Folder that holds `downloads/`; empty = the app's internal storage. */
    val downloadsRoot: StateFlow<String> = state { it[downloadsRootKey].orEmpty() }

    /** Which titles' new chapters library checks queue for download. */
    val autoDownloadMode: StateFlow<AutoDownloadMode> =
        state { AutoDownloadMode.fromStored(it[autoDownloadModeKey], it[autoDownloadNewKey]) }

    /** Chapters finished in the reader lose their download when the reader closes. */
    val deleteAfterRead: StateFlow<Boolean> = state { it[deleteAfterReadKey] ?: false }

    suspend fun setDownloadsPaused(value: Boolean) = put(downloadsPausedKey, value)

    suspend fun setDownloadParallelPages(value: Int) = put(downloadPagesKey, value.coerceIn(1, MAX_PARALLEL_PAGES))

    suspend fun setDownloadsAsCbz(value: Boolean) = put(downloadsCbzKey, value)

    suspend fun setDownloadsRoot(path: String) = put(downloadsRootKey, path)

    suspend fun setAutoDownloadMode(value: AutoDownloadMode) = put(autoDownloadModeKey, value.ordinal)

    suspend fun setDeleteAfterRead(value: Boolean) = put(deleteAfterReadKey, value)

    /** Days between automatic backups; 0 = off. */
    val autoBackupDays: StateFlow<Int> = state { it[autoBackupDaysKey]?.takeIf { d -> d in AUTO_BACKUP_DAYS } ?: 0 }

    /** How many automatic backups are kept (older ones are deleted). */
    val autoBackupKeep: StateFlow<Int> = state { it[autoBackupKeepKey]?.takeIf { n -> n in AUTO_BACKUP_KEEP } ?: 5 }

    /** A folder picked with the file picker (tree URI) that automatic backups are copied to. */
    val autoBackupFolder: StateFlow<String?> = privateState { it[autoBackupFolderKey]?.takeIf(String::isNotBlank) }

    suspend fun setAutoBackup(days: Int, keep: Int) {
        context.appDataStore.edit {
            it[autoBackupDaysKey] = days
            it[autoBackupKeepKey] = keep
        }
    }

    /** Hours between scans of the watched folders; 0 = off. */
    val folderScanHours: StateFlow<Int> = state { it[folderScanHoursKey]?.takeIf { h -> h in FOLDER_SCAN_HOURS } ?: 0 }

    suspend fun setFolderScanHours(hours: Int) = put(folderScanHoursKey, hours)

    suspend fun setAutoBackupFolder(uri: String?) {
        context.privateDataStore.edit { if (uri.isNullOrBlank()) it.remove(autoBackupFolderKey) else it[autoBackupFolderKey] = uri }
    }

    /** Where the pack catalogue is read from; see `docs/packs/index.json`. */
    val packIndexUrl: StateFlow<String> = state { it[packIndexUrlKey]?.takeIf(String::isNotBlank) ?: DEFAULT_PACK_INDEX }

    suspend fun setLibraryLastCheck(time: Long, outcome: LibraryCheckOutcome) {
        context.appDataStore.edit {
            it[libraryLastCheckedKey] = time
            it[libraryLastCheckTotalKey] = outcome.checked
            it[libraryLastCheckFailedKey] = outcome.failed
        }
    }

    suspend fun setDefaultCategoryId(id: Long?) {
        context.appDataStore.edit {
            if (id == null) it.remove(defaultCategoryKey) else it[defaultCategoryKey] = id
        }
    }

    suspend fun setLibraryUpdateIntervalHours(hours: Int) = put(libraryIntervalKey, hours)

    suspend fun setLibraryUpdateWifiOnly(value: Boolean) = put(libraryWifiOnlyKey, value)

    suspend fun setLibraryUpdateRules(rules: UpdateRules) {
        context.appDataStore.edit {
            it[librarySkipCompletedKey] = rules.skipCompleted
            it[librarySkipNotStartedKey] = rules.skipNotStarted
            it[librarySkipUnreadKey] = rules.skipUnread
            it[libraryChargingOnlyKey] = rules.chargingOnly
            it[libraryUpdateCategoriesKey] = rules.categories.map(Long::toString).toSet()
        }
    }

    suspend fun setNotifyMutedCategories(ids: Set<Long>) = put(libraryNotifyMutedKey, ids.map(Long::toString).toSet())

    suspend fun setDownloadsWifiOnly(value: Boolean) = put(downloadsWifiOnlyKey, value)

    suspend fun setGlobalSearchNsfw(value: Boolean) = put(globalSearchNsfwKey, value)

    suspend fun setDarkTheme(value: Boolean) = put(darkThemeKey, value)

    suspend fun setActiveSourceId(id: String, kind: com.lume.app.domain.MediaKind = com.lume.app.domain.MediaKind.Comics) =
        put(activeSourceKey(kind), id)

    suspend fun setLibraryUpdatesEnabled(value: Boolean) = put(libraryUpdatesKey, value)

    suspend fun setAppLanguage(tag: String) = put(appLanguageKey, tag)

    companion object {
        val LIBRARY_INTERVALS = listOf(3, 6, 12, 24, 48)
        const val MAX_SIDE_PADDING = 30
        const val MAX_DIM = 0.8f
        const val MAX_SEARCH_HISTORY = 15
        const val MAX_PARALLEL_PAGES = 4
        val AUTO_BACKUP_DAYS = listOf(0, 1, 3, 7)
        val AUTO_BACKUP_KEEP = listOf(3, 5, 10)
        /** Hours between scans of the watched folders; 0 = only "Scan now". */
        val FOLDER_SCAN_HOURS = listOf(0, 6, 12, 24)
        /** Hours between background syncs; 0 = only "Sync now" and app start. */
        val SYNC_INTERVALS = listOf(0, 1, 6, 12, 24)
        const val DEFAULT_PACK_INDEX = "https://raw.githubusercontent.com/TheMiracleOfKein/lume/main/docs/packs/index.json"
    }
}
