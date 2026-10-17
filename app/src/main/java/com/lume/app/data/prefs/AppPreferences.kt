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

    private val initial: Preferences = runBlocking { context.appDataStore.data.first() }
    private val current: StateFlow<Preferences> =
        context.appDataStore.data.stateIn(scope, SharingStarted.Eagerly, initial)

    private fun <T> state(read: (Preferences) -> T): StateFlow<T> =
        current.map(read).stateIn(scope, SharingStarted.Eagerly, read(initial))

    val darkTheme: StateFlow<Boolean> = state { it[darkThemeKey] ?: true }

    val activeSourceId: StateFlow<String?> = state { it[activeSourceKey] }

    val libraryUpdatesEnabled: StateFlow<Boolean> = state { it[libraryUpdatesKey] ?: false }

    /** "system" | "en" | "ru" | … — see [com.lume.app.data.locale.AppLanguage]. */
    val appLanguage: StateFlow<String> = state { it[appLanguageKey] ?: "system" }

    val anilistAccessToken: StateFlow<String?> = state { it[anilistTokenKey] }
    val anilistClientId: StateFlow<String?> = state { it[anilistClientIdKey] }
    val anilistClientSecret: StateFlow<String?> = state { it[anilistClientSecretKey] }
    val shikimoriAccessToken: StateFlow<String?> = state { it[shikiTokenKey] }
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
        const val DEFAULT_PACK_INDEX = "https://raw.githubusercontent.com/TheMiracleOfKein/lume/main/docs/packs/index.json"
        private val prefsJson = Json { ignoreUnknownKeys = true }
    }
}
