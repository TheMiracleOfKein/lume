package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lume.app.domain.LibraryDisplay
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.LibrarySort
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import com.lume.app.domain.decodeLibraryFilters
import com.lume.app.domain.encodeLibraryFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val Context.appDataStore by preferencesDataStore("lume_prefs")

data class ReaderSettings(
    val mode: ReaderMode = ReaderMode.Webtoon,
    val theme: ReaderTheme = ReaderTheme.Dark,
    val gap: Float = 0f,
    val doubleTapZoom: Boolean = true,
    val hidePageNumber: Boolean = false,
    /** Continuous scroll across chapters with a divider; next chapter is prefetched. */
    val seamlessReading: Boolean = true,
    /** Volume down / up turn to the next / previous page (consumes the key in the reader). */
    val volumeKeys: Boolean = false,
    /** Paged modes: tap the left / right third to turn pages; the middle toggles controls. */
    val tapZones: Boolean = true,
    /**
     * Fullscreen reading: scrolling hides the reader controls *and* the status / navigation bars;
     * a tap without scrolling brings them back.
     */
    val immersive: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Webtoon mode: empty margin on each side, in percent of the screen width (tablets/landscape). */
    val sidePaddingPercent: Int = 0,
    /** Extra darkening over pages for night reading, 0 (off) … 0.8. */
    val dim: Float = 0f,
)

/**
 * Every preference is a [StateFlow] that already holds the stored value: the file is read once,
 * synchronously, when the app starts. Screens read `.value` on their first frame, so switches,
 * expanded sections and the theme never start from a default and then animate to the real value.
 */
class AppPreferences(private val context: Context, private val scope: CoroutineScope) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val activeSourceKey = stringPreferencesKey("active_source_id")
    private val readerModeKey = intPreferencesKey("reader_mode")
    private val readerThemeKey = intPreferencesKey("reader_theme")
    private val readerGapKey = doublePreferencesKey("reader_gap")
    private val readerZoomKey = booleanPreferencesKey("reader_zoom")
    private val readerHidePageKey = booleanPreferencesKey("reader_hide_page")
    private val readerSeamlessKey = booleanPreferencesKey("reader_seamless")
    private val readerVolumeKeysKey = booleanPreferencesKey("reader_volume_keys")
    private val readerTapZonesKey = booleanPreferencesKey("reader_tap_zones")
    private val readerImmersiveKey = booleanPreferencesKey("reader_immersive")
    private val readerKeepScreenOnKey = booleanPreferencesKey("reader_keep_screen_on")
    private val readerSidePaddingKey = intPreferencesKey("reader_side_padding")
    private val readerDimKey = doublePreferencesKey("reader_dim")
    private val libraryIntervalKey = intPreferencesKey("library_update_interval_hours")
    private val libraryWifiOnlyKey = booleanPreferencesKey("library_update_wifi_only")
    private val downloadsWifiOnlyKey = booleanPreferencesKey("downloads_wifi_only")
    private val globalSearchNsfwKey = booleanPreferencesKey("global_search_nsfw")
    private val libraryUpdatesKey = booleanPreferencesKey("library_updates_enabled")
    private val appLanguageKey = stringPreferencesKey("app_language")

    private val anilistTokenKey = stringPreferencesKey("anilist_access_token")
    private val anilistClientIdKey = stringPreferencesKey("anilist_client_id")
    private val anilistClientSecretKey = stringPreferencesKey("anilist_client_secret")
    private val shikiTokenKey = stringPreferencesKey("shikimori_access_token")
    private val shikiRefreshKey = stringPreferencesKey("shikimori_refresh_token")
    private val shikiUserIdKey = stringPreferencesKey("shikimori_user_id")
    private val shikiClientIdKey = stringPreferencesKey("shikimori_client_id")
    private val shikiClientSecretKey = stringPreferencesKey("shikimori_client_secret")

    private val libraryDisplayKey = stringPreferencesKey("library_display")
    private val librarySortKey = stringPreferencesKey("library_sort")
    private val librarySortAscKey = booleanPreferencesKey("library_sort_asc")
    private val libraryFiltersKey = stringPreferencesKey("library_filters")
    private val libraryUnreadBadgeKey = booleanPreferencesKey("library_badge_unread")
    private val libraryDownloadedBadgeKey = booleanPreferencesKey("library_badge_downloaded")
    private val defaultCategoryKey = longPreferencesKey("default_category_id")
    private val libraryLastCheckedKey = longPreferencesKey("library_last_checked_at")

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
    private val deleteAfterReadKey = booleanPreferencesKey("downloads_delete_after_read")

    private val trackerAutoLinkKey = booleanPreferencesKey("tracker_auto_link_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val pureBlackKey = booleanPreferencesKey("theme_pure_black")
    private val dynamicColorKey = booleanPreferencesKey("theme_dynamic_color")
    private val incognitoKey = booleanPreferencesKey("incognito")
    private val appLockKey = booleanPreferencesKey("app_lock")
    private val secureScreenKey = booleanPreferencesKey("secure_screen")
    private val dohKey = stringPreferencesKey("dns_over_https")
    private val translateLabelsKey = booleanPreferencesKey("translate_chapter_labels")
    private val autoBackupDaysKey = intPreferencesKey("auto_backup_days")
    private val autoBackupKeepKey = intPreferencesKey("auto_backup_keep")
    private val autoBackupFolderKey = stringPreferencesKey("auto_backup_folder")

    private val initial: Preferences = runBlocking { context.appDataStore.data.first() }
    private val current: StateFlow<Preferences> =
        context.appDataStore.data.stateIn(scope, SharingStarted.Eagerly, initial)

    private fun <T> state(read: (Preferences) -> T): StateFlow<T> =
        current.map(read).stateIn(scope, SharingStarted.Eagerly, read(initial))

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

    suspend fun setPureBlack(value: Boolean) {
        context.appDataStore.edit { it[pureBlackKey] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.appDataStore.edit { it[dynamicColorKey] = value }
    }

    /** Incognito: reading leaves no history, progress or read marks. */
    val incognito: StateFlow<Boolean> = state { it[incognitoKey] ?: false }

    /** Ask for the screen lock (fingerprint, PIN …) when the app opens. */
    val appLock: StateFlow<Boolean> = state { it[appLockKey] ?: false }

    /** Hide the app's content in the recent-apps screen and block screenshots. */
    val secureScreen: StateFlow<Boolean> = state { it[secureScreenKey] ?: false }

    /** DNS over HTTPS provider id ("" = the system resolver); see `data/network/Doh.kt`. */
    val dnsOverHttps: StateFlow<String> = state { it[dohKey].orEmpty() }

    /** Russian-only chapter labels ("Том 1 Глава 5") are shown in the app language. */
    val translateChapterLabels: StateFlow<Boolean> = state { it[translateLabelsKey] ?: true }

    suspend fun setIncognito(value: Boolean) {
        context.appDataStore.edit { it[incognitoKey] = value }
    }

    suspend fun setAppLock(value: Boolean) {
        context.appDataStore.edit { it[appLockKey] = value }
    }

    suspend fun setSecureScreen(value: Boolean) {
        context.appDataStore.edit { it[secureScreenKey] = value }
    }

    suspend fun setDnsOverHttps(value: String) {
        context.appDataStore.edit { it[dohKey] = value }
    }

    suspend fun setTranslateChapterLabels(value: Boolean) {
        context.appDataStore.edit { it[translateLabelsKey] = value }
    }

    val activeSourceId: StateFlow<String?> = state { it[activeSourceKey] }

    val libraryUpdatesEnabled: StateFlow<Boolean> = state { it[libraryUpdatesKey] ?: false }

    /** "system" | "en" | "ru" | … — see [com.lume.app.data.locale.AppLanguage]. */
    val appLanguage: StateFlow<String> = state { it[appLanguageKey] ?: "system" }

    val anilistAccessToken: StateFlow<String?> = state { it[anilistTokenKey] }
    val anilistClientId: StateFlow<String?> = state { it[anilistClientIdKey] }
    val anilistClientSecret: StateFlow<String?> = state { it[anilistClientSecretKey] }
    val shikimoriAccessToken: StateFlow<String?> = state { it[shikiTokenKey] }
    val shikimoriRefreshToken: StateFlow<String?> = state { it[shikiRefreshKey] }

    /**
     * Values of the trackers added in v0.5 (MyAnimeList, Kitsu, MangaUpdates), stored as
     * `tracker_<id>_<name>`. Never part of backups (see [isPrivateKey]).
     */
    val trackerValues: StateFlow<Map<String, String>> = state { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (k.name.startsWith(TRACKER_PREFIX) && v is String) k.name.removePrefix(TRACKER_PREFIX) to v else null
        }.toMap()
    }

    fun trackerValue(trackerId: String, name: String): String? = trackerValues.value["${trackerId}_$name"]

    suspend fun setTrackerValue(trackerId: String, name: String, value: String?) {
        val key = stringPreferencesKey("$TRACKER_PREFIX${trackerId}_$name")
        context.appDataStore.edit { if (value.isNullOrBlank()) it.remove(key) else it[key] = value }
    }

    /** Link titles to trackers by an exact title match when they are added to the library. */
    val trackerAutoLink: StateFlow<Boolean> = state { it[trackerAutoLinkKey] ?: true }

    suspend fun setTrackerAutoLink(value: Boolean) {
        context.appDataStore.edit { it[trackerAutoLinkKey] = value }
    }
    val shikimoriUserId: StateFlow<String?> = state { it[shikiUserIdKey] }
    val shikimoriClientId: StateFlow<String?> = state { it[shikiClientIdKey] }
    val shikimoriClientSecret: StateFlow<String?> = state { it[shikiClientSecretKey] }

    val readerSettings: StateFlow<ReaderSettings> = state { prefs ->
        val modeIndex = prefs[readerModeKey] ?: ReaderMode.Webtoon.prefsIndex
        val themeIndex = prefs[readerThemeKey] ?: ReaderTheme.Dark.ordinal
        ReaderSettings(
            mode = ReaderMode.fromPrefs(modeIndex),
            theme = ReaderTheme.entries.getOrElse(themeIndex) { ReaderTheme.Dark },
            gap = (prefs[readerGapKey] ?: 0.0).toFloat().coerceIn(0f, 32f),
            doubleTapZoom = prefs[readerZoomKey] ?: true,
            hidePageNumber = prefs[readerHidePageKey] ?: false,
            seamlessReading = prefs[readerSeamlessKey] ?: true,
            volumeKeys = prefs[readerVolumeKeysKey] ?: false,
            tapZones = prefs[readerTapZonesKey] ?: true,
            immersive = prefs[readerImmersiveKey] ?: true,
            keepScreenOn = prefs[readerKeepScreenOnKey] ?: true,
            sidePaddingPercent = (prefs[readerSidePaddingKey] ?: 0).coerceIn(0, MAX_SIDE_PADDING),
            dim = (prefs[readerDimKey] ?: 0.0).toFloat().coerceIn(0f, MAX_DIM),
        )
    }

    /** Hours between background library checks (one of [LIBRARY_INTERVALS]). */
    val libraryUpdateIntervalHours: StateFlow<Int> = state { it[libraryIntervalKey]?.takeIf { h -> h in LIBRARY_INTERVALS } ?: 6 }
    val libraryUpdateWifiOnly: StateFlow<Boolean> = state { it[libraryWifiOnlyKey] ?: false }
    val downloadsWifiOnly: StateFlow<Boolean> = state { it[downloadsWifiOnlyKey] ?: false }
    val globalSearchNsfw: StateFlow<Boolean> = state { it[globalSearchNsfwKey] ?: true }

    val librarySettings: StateFlow<LibrarySettings> = state { prefs ->
        LibrarySettings(
            display = LibraryDisplay.entries.firstOrNull { it.name == prefs[libraryDisplayKey] } ?: LibraryDisplay.Grid,
            sort = LibrarySort.entries.firstOrNull { it.name == prefs[librarySortKey] } ?: LibrarySort.Title,
            ascending = prefs[librarySortAscKey] ?: true,
            filters = decodeLibraryFilters(prefs[libraryFiltersKey]),
            unreadBadge = prefs[libraryUnreadBadgeKey] ?: true,
            downloadedBadge = prefs[libraryDownloadedBadgeKey] ?: true,
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
        }
    }

    /** When the last library check finished (0 = never). */
    val libraryLastCheckedAt: StateFlow<Long> = state { it[libraryLastCheckedKey] ?: 0L }

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

    suspend fun setSearchHistory(items: List<String>) {
        context.appDataStore.edit { it[searchHistoryKey] = prefsJson.encodeToString(items.take(MAX_SEARCH_HISTORY)) }
    }

    /** The download queue is on hold (the running chapter goes back to the queue). */
    val downloadsPaused: StateFlow<Boolean> = state { it[downloadsPausedKey] ?: false }

    /** Pages of one chapter fetched at the same time (1 … [MAX_PARALLEL_PAGES]). */
    val downloadParallelPages: StateFlow<Int> = state { (it[downloadPagesKey] ?: 2).coerceIn(1, MAX_PARALLEL_PAGES) }

    /** New downloads are packed into one `.cbz` per chapter. */
    val downloadsAsCbz: StateFlow<Boolean> = state { it[downloadsCbzKey] ?: false }

    /** Folder that holds `downloads/`; empty = the app's internal storage. */
    val downloadsRoot: StateFlow<String> = state { it[downloadsRootKey].orEmpty() }

    /** Library checks queue new chapters for download. */
    val autoDownloadNew: StateFlow<Boolean> = state { it[autoDownloadNewKey] ?: false }

    /** Chapters finished in the reader lose their download when the reader closes. */
    val deleteAfterRead: StateFlow<Boolean> = state { it[deleteAfterReadKey] ?: false }

    suspend fun setDownloadsPaused(value: Boolean) {
        context.appDataStore.edit { it[downloadsPausedKey] = value }
    }

    suspend fun setDownloadParallelPages(value: Int) {
        context.appDataStore.edit { it[downloadPagesKey] = value.coerceIn(1, MAX_PARALLEL_PAGES) }
    }

    suspend fun setDownloadsAsCbz(value: Boolean) {
        context.appDataStore.edit { it[downloadsCbzKey] = value }
    }

    suspend fun setDownloadsRoot(path: String) {
        context.appDataStore.edit { it[downloadsRootKey] = path }
    }

    suspend fun setAutoDownloadNew(value: Boolean) {
        context.appDataStore.edit { it[autoDownloadNewKey] = value }
    }

    suspend fun setDeleteAfterRead(value: Boolean) {
        context.appDataStore.edit { it[deleteAfterReadKey] = value }
    }

    /** Days between automatic backups; 0 = off. */
    val autoBackupDays: StateFlow<Int> = state { it[autoBackupDaysKey]?.takeIf { d -> d in AUTO_BACKUP_DAYS } ?: 0 }

    /** How many automatic backups are kept (older ones are deleted). */
    val autoBackupKeep: StateFlow<Int> = state { it[autoBackupKeepKey]?.takeIf { n -> n in AUTO_BACKUP_KEEP } ?: 5 }

    /** A folder picked with the file picker (tree URI) that automatic backups are copied to. */
    val autoBackupFolder: StateFlow<String?> = state { it[autoBackupFolderKey]?.takeIf(String::isNotBlank) }

    suspend fun setAutoBackup(days: Int, keep: Int) {
        context.appDataStore.edit {
            it[autoBackupDaysKey] = days
            it[autoBackupKeepKey] = keep
        }
    }

    suspend fun setAutoBackupFolder(uri: String?) {
        context.appDataStore.edit { if (uri.isNullOrBlank()) it.remove(autoBackupFolderKey) else it[autoBackupFolderKey] = uri }
    }

    /**
     * Every stored preference as (key, type, value) for backups. Account tokens, app keys and
     * the backup folder (a permission that only this install holds) stay out.
     */
    fun exportSettings(): List<Triple<String, String, String>> = current.value.asMap().mapNotNull { (key, value) ->
        if (isPrivateKey(key.name)) return@mapNotNull null
        when (value) {
            is Boolean -> Triple(key.name, "bool", value.toString())
            is Int -> Triple(key.name, "int", value.toString())
            is Long -> Triple(key.name, "long", value.toString())
            is Double -> Triple(key.name, "double", value.toString())
            is Float -> Triple(key.name, "float", value.toString())
            is String -> Triple(key.name, "string", value)
            is Set<*> -> Triple(key.name, "set", prefsJson.encodeToString(value.map { it.toString() }))
            else -> null
        }
    }

    /** Writes settings from a backup back; unknown types and private keys are skipped. */
    suspend fun importSettings(values: List<Triple<String, String, String>>) {
        context.appDataStore.edit { prefs ->
            for ((name, type, raw) in values) {
                if (isPrivateKey(name)) continue
                runCatching {
                    when (type) {
                        "bool" -> prefs[booleanPreferencesKey(name)] = raw.toBooleanStrict()
                        "int" -> prefs[intPreferencesKey(name)] = raw.toInt()
                        "long" -> prefs[longPreferencesKey(name)] = raw.toLong()
                        "double" -> prefs[doublePreferencesKey(name)] = raw.toDouble()
                        "float" -> prefs[androidx.datastore.preferences.core.floatPreferencesKey(name)] = raw.toFloat()
                        "string" -> prefs[stringPreferencesKey(name)] = raw
                        "set" -> prefs[stringSetPreferencesKey(name)] = prefsJson.decodeFromString<List<String>>(raw).toSet()
                    }
                }
            }
        }
    }

    private fun isPrivateKey(name: String): Boolean =
        PRIVATE_KEY_PARTS.any { name.contains(it) } || name == "auto_backup_folder" ||
            (name.startsWith(TRACKER_PREFIX) && name != "tracker_auto_link_enabled")

    /** Where the pack catalogue is read from; see `docs/packs/index.json`. */
    val packIndexUrl: StateFlow<String> = state { it[packIndexUrlKey]?.takeIf(String::isNotBlank) ?: DEFAULT_PACK_INDEX }

    suspend fun setLibraryLastCheckedAt(time: Long) {
        context.appDataStore.edit { it[libraryLastCheckedKey] = time }
    }

    suspend fun setDefaultCategoryId(id: Long?) {
        context.appDataStore.edit {
            if (id == null) it.remove(defaultCategoryKey) else it[defaultCategoryKey] = id
        }
    }

    suspend fun setLibraryUpdateIntervalHours(hours: Int) {
        context.appDataStore.edit { it[libraryIntervalKey] = hours }
    }

    suspend fun setLibraryUpdateWifiOnly(value: Boolean) {
        context.appDataStore.edit { it[libraryWifiOnlyKey] = value }
    }

    suspend fun setDownloadsWifiOnly(value: Boolean) {
        context.appDataStore.edit { it[downloadsWifiOnlyKey] = value }
    }

    suspend fun setGlobalSearchNsfw(value: Boolean) {
        context.appDataStore.edit { it[globalSearchNsfwKey] = value }
    }

    suspend fun setDarkTheme(value: Boolean) {
        context.appDataStore.edit { it[darkThemeKey] = value }
    }

    suspend fun setActiveSourceId(id: String) {
        context.appDataStore.edit { it[activeSourceKey] = id }
    }

    suspend fun setLibraryUpdatesEnabled(value: Boolean) {
        context.appDataStore.edit { it[libraryUpdatesKey] = value }
    }

    suspend fun setAppLanguage(tag: String) {
        context.appDataStore.edit { it[appLanguageKey] = tag }
    }

    suspend fun setReaderSettings(settings: ReaderSettings) {
        context.appDataStore.edit {
            it[readerModeKey] = settings.mode.prefsIndex
            it[readerThemeKey] = settings.theme.ordinal
            it[readerGapKey] = settings.gap.toDouble()
            it[readerZoomKey] = settings.doubleTapZoom
            it[readerHidePageKey] = settings.hidePageNumber
            it[readerSeamlessKey] = settings.seamlessReading
            it[readerVolumeKeysKey] = settings.volumeKeys
            it[readerTapZonesKey] = settings.tapZones
            it[readerImmersiveKey] = settings.immersive
            it[readerKeepScreenOnKey] = settings.keepScreenOn
            it[readerSidePaddingKey] = settings.sidePaddingPercent
            it[readerDimKey] = settings.dim.toDouble()
        }
    }

    suspend fun setAniListAccessToken(token: String?) {
        context.appDataStore.edit {
            if (token.isNullOrBlank()) it.remove(anilistTokenKey) else it[anilistTokenKey] = token
        }
    }

    @Suppress("unused")
    suspend fun setAniListClient(clientId: String, clientSecret: String) {
        context.appDataStore.edit {
            it[anilistClientIdKey] = clientId
            it[anilistClientSecretKey] = clientSecret
        }
    }

    suspend fun setShikimoriTokens(access: String?, refresh: String?) {
        context.appDataStore.edit {
            if (access.isNullOrBlank()) {
                it.remove(shikiTokenKey)
                it.remove(shikiUserIdKey)
            } else {
                it[shikiTokenKey] = access
            }
            if (refresh.isNullOrBlank()) it.remove(shikiRefreshKey) else it[shikiRefreshKey] = refresh
        }
    }

    suspend fun setShikimoriUserId(userId: String?) {
        context.appDataStore.edit {
            if (userId.isNullOrBlank()) it.remove(shikiUserIdKey) else it[shikiUserIdKey] = userId
        }
    }

    @Suppress("unused")
    suspend fun setShikimoriClient(clientId: String, clientSecret: String) {
        context.appDataStore.edit {
            it[shikiClientIdKey] = clientId
            it[shikiClientSecretKey] = clientSecret
        }
    }

    companion object {
        val LIBRARY_INTERVALS = listOf(3, 6, 12, 24, 48)
        const val MAX_SIDE_PADDING = 30
        const val MAX_DIM = 0.8f
        const val MAX_SEARCH_HISTORY = 15
        const val MAX_PARALLEL_PAGES = 4
        val AUTO_BACKUP_DAYS = listOf(0, 1, 3, 7)
        val AUTO_BACKUP_KEEP = listOf(3, 5, 10)
        private val PRIVATE_KEY_PARTS = listOf("token", "secret", "client_id", "user_id", "password", "verifier")
        private const val TRACKER_PREFIX = "tracker_"
        const val DEFAULT_PACK_INDEX = "https://raw.githubusercontent.com/TheMiracleOfKein/lume/main/docs/packs/index.json"
        private val prefsJson = Json { ignoreUnknownKeys = true }
    }
}
