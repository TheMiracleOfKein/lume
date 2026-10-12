package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

class AppPreferences(private val context: Context) {
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

    val darkTheme: Flow<Boolean> = context.appDataStore.data.map { it[darkThemeKey] ?: true }

    val activeSourceId: Flow<String?> = context.appDataStore.data.map { it[activeSourceKey] }

    val libraryUpdatesEnabled: Flow<Boolean> =
        context.appDataStore.data.map { it[libraryUpdatesKey] ?: false }

    /** "system" | "en" | "ru" | … — see [com.lume.app.data.locale.AppLanguage]. */
    val appLanguage: Flow<String> =
        context.appDataStore.data.map { it[appLanguageKey] ?: "system" }

    val anilistAccessToken: Flow<String?> = context.appDataStore.data.map { it[anilistTokenKey] }
    val anilistClientId: Flow<String?> = context.appDataStore.data.map { it[anilistClientIdKey] }
    val anilistClientSecret: Flow<String?> = context.appDataStore.data.map { it[anilistClientSecretKey] }
    val shikimoriAccessToken: Flow<String?> = context.appDataStore.data.map { it[shikiTokenKey] }
    val shikimoriUserId: Flow<String?> = context.appDataStore.data.map { it[shikiUserIdKey] }
    val shikimoriClientId: Flow<String?> = context.appDataStore.data.map { it[shikiClientIdKey] }
    val shikimoriClientSecret: Flow<String?> = context.appDataStore.data.map { it[shikiClientSecretKey] }

    val readerSettings: Flow<ReaderSettings> = context.appDataStore.data.map { prefs ->
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
    val libraryUpdateIntervalHours: Flow<Int> =
        context.appDataStore.data.map { it[libraryIntervalKey]?.takeIf { h -> h in LIBRARY_INTERVALS } ?: 6 }
    val libraryUpdateWifiOnly: Flow<Boolean> = context.appDataStore.data.map { it[libraryWifiOnlyKey] ?: false }
    val downloadsWifiOnly: Flow<Boolean> = context.appDataStore.data.map { it[downloadsWifiOnlyKey] ?: false }
    val globalSearchNsfw: Flow<Boolean> = context.appDataStore.data.map { it[globalSearchNsfwKey] ?: true }

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
    }
}
