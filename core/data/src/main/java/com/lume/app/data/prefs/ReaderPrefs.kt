package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lume.app.data.security.SecretSealer
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

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
    /** [ORIENTATION_AUTO], [ORIENTATION_PORTRAIT] or [ORIENTATION_LANDSCAPE] while reading. */
    val orientation: Int = ORIENTATION_AUTO,
    /** [FILTER_NONE], [FILTER_SEPIA], [FILTER_GREY] or [FILTER_WARM] over the pages. */
    val colorFilter: Int = FILTER_NONE,
    /** E-ink screens: grey, high-contrast pages and page turns without animation. */
    val eInk: Boolean = false,
    /** Screen brightness while reading, 0.01 … 1; [BRIGHTNESS_SYSTEM] keeps the system's. */
    val brightness: Float = BRIGHTNESS_SYSTEM,
    /** Webtoon auto-scroll speed, 1 (slow) … [MAX_AUTO_SCROLL]. */
    val autoScrollSpeed: Int = 3,
    /** Webtoon mode: tap the top / bottom third to scroll back / on; the middle toggles controls. */
    val webtoonTapZones: Boolean = false,
    /** Pages loaded ahead of the one on screen, 0 … [MAX_PRELOAD]. */
    val preloadPages: Int = 4,
    /** Whole chapters after the current one loaded ahead while reading, 0 … [MAX_PRELOAD_CHAPTERS]. */
    val preloadChapters: Int = 0,
    /** Paged modes: plain white or black borders are cut off, so the drawing fills the screen. */
    val cropBorders: Boolean = false,
    /** Paged modes in portrait: a double page is shown as two halves, one after the other. */
    val splitWide: Boolean = false,
    /** Paged modes: [SPREADS_OFF], [SPREADS_AUTO] (landscape and wide screens) or [SPREADS_ALWAYS]. */
    val spreads: Int = SPREADS_AUTO,
    /** Spreads: the chapter's first page stands alone, so the pairs match a printed book. */
    val spreadCoverAlone: Boolean = true,
    /** Paged modes: a tap or page turn moves from panel to panel, zooming in on each. */
    val guidedPanels: Boolean = false,
    /**
     * Guided panel view: [PANELS_RTL] (manga) or [PANELS_LTR] (western comics) within a row of
     * panels. Separate from the page-turn direction ([mode]).
     */
    val panelOrder: Int = PANELS_RTL,
) {
    companion object {
        const val PANELS_RTL = 0
        const val PANELS_LTR = 1
        const val SPREADS_OFF = 0
        const val SPREADS_AUTO = 1
        const val SPREADS_ALWAYS = 2
        const val MAX_PRELOAD = 10
        const val MAX_PRELOAD_CHAPTERS = 2
        const val ORIENTATION_AUTO = 0
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val FILTER_NONE = 0
        const val FILTER_SEPIA = 1
        const val FILTER_GREY = 2
        const val FILTER_WARM = 3
        const val BRIGHTNESS_SYSTEM = -1f
        const val MAX_AUTO_SCROLL = 10
    }
}

/** The manga reader, the novel reader and read aloud. */
abstract class ReaderPrefs(context: Context, scope: CoroutineScope, secrets: SecretSealer) :
    SyncPrefs(context, scope, secrets) {
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
    private val readerOrientationKey = intPreferencesKey("reader_orientation")
    private val readerColorFilterKey = intPreferencesKey("reader_color_filter")
    private val readerEInkKey = booleanPreferencesKey("reader_eink")
    private val readerBrightnessKey = doublePreferencesKey("reader_brightness")
    private val readerAutoScrollKey = intPreferencesKey("reader_auto_scroll_speed")
    private val readerWebtoonTapZonesKey = booleanPreferencesKey("reader_webtoon_tap_zones")
    private val readerPreloadKey = intPreferencesKey("reader_preload_pages")
    private val readerPreloadChaptersKey = intPreferencesKey("reader_preload_chapters")
    private val readerCropBordersKey = booleanPreferencesKey("reader_crop_borders")
    private val readerSplitWideKey = booleanPreferencesKey("reader_split_wide")
    private val readerSpreadsKey = intPreferencesKey("reader_spreads")
    private val readerSpreadCoverKey = booleanPreferencesKey("reader_spread_cover_alone")
    private val readerGuidedPanelsKey = booleanPreferencesKey("reader_guided_panels")
    private val readerPanelOrderKey = intPreferencesKey("reader_panel_order")
    private val novelFontSizeKey = intPreferencesKey("novel_font_size")
    private val novelLineHeightKey = doublePreferencesKey("novel_line_height")
    private val novelFontKey = intPreferencesKey("novel_font")
    private val novelSideMarginKey = intPreferencesKey("novel_side_margin")
    private val novelParagraphSpacingKey = intPreferencesKey("novel_paragraph_spacing")
    private val novelJustifyKey = booleanPreferencesKey("novel_justify")
    private val novelThemeKey = intPreferencesKey("novel_theme")
    private val novelPagedKey = booleanPreferencesKey("novel_paged")
    private val novelShowChapterTitleKey = booleanPreferencesKey("novel_show_chapter_title")
    private val novelBrightnessSwipeKey = booleanPreferencesKey("novel_brightness_swipe")
    private val novelBrightnessKey = doublePreferencesKey("novel_brightness")
    private val novelVolumeKeysKey = booleanPreferencesKey("novel_volume_keys")
    private val novelStatusLineKey = booleanPreferencesKey("novel_status_line")
    private val novelParagraphIndentKey = intPreferencesKey("novel_paragraph_indent")
    private val novelAutoScrollSpeedKey = intPreferencesKey("novel_auto_scroll_speed")
    private val readAloudSpeedKey = doublePreferencesKey("read_aloud_speed")
    private val readAloudVoiceKey = stringPreferencesKey("read_aloud_voice")
    private val readAloudEngineKey = stringPreferencesKey("read_aloud_engine")

    /** Text layout and colours of the novel reader. */
    val novelReaderSettings: StateFlow<NovelReaderSettings> = state { prefs ->
        val defaults = NovelReaderSettings()
        NovelReaderSettings(
            fontSize = prefs[novelFontSizeKey] ?: defaults.fontSize,
            lineHeight = (prefs[novelLineHeightKey] ?: defaults.lineHeight.toDouble()).toFloat(),
            font = NovelFont.entries.getOrElse(prefs[novelFontKey] ?: -1) { defaults.font },
            sideMargin = prefs[novelSideMarginKey] ?: defaults.sideMargin,
            paragraphSpacing = prefs[novelParagraphSpacingKey] ?: defaults.paragraphSpacing,
            justify = prefs[novelJustifyKey] ?: defaults.justify,
            theme = NovelTheme.entries.getOrElse(prefs[novelThemeKey] ?: -1) { defaults.theme },
            paged = prefs[novelPagedKey] ?: defaults.paged,
            showChapterTitle = prefs[novelShowChapterTitleKey] ?: defaults.showChapterTitle,
            brightnessSwipe = prefs[novelBrightnessSwipeKey] ?: defaults.brightnessSwipe,
            brightness = (prefs[novelBrightnessKey] ?: defaults.brightness.toDouble()).toFloat(),
            volumeKeys = prefs[novelVolumeKeysKey] ?: defaults.volumeKeys,
            statusLine = prefs[novelStatusLineKey] ?: defaults.statusLine,
            paragraphIndent = prefs[novelParagraphIndentKey] ?: defaults.paragraphIndent,
            autoScrollSpeed = prefs[novelAutoScrollSpeedKey] ?: defaults.autoScrollSpeed,
        ).clamped()
    }

    /** Speed, voice and engine of read aloud in the text reader. */
    val readAloudSettings: StateFlow<ReadAloudSettings> = state { prefs ->
        ReadAloudSettings(
            speed = (prefs[readAloudSpeedKey] ?: 1.0).toFloat(),
            voiceName = prefs[readAloudVoiceKey],
            engine = prefs[readAloudEngineKey],
        ).clamped()
    }

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
            sidePaddingPercent = (prefs[readerSidePaddingKey] ?: 0).coerceIn(0, AppPreferences.MAX_SIDE_PADDING),
            dim = (prefs[readerDimKey] ?: 0.0).toFloat().coerceIn(0f, AppPreferences.MAX_DIM),
            orientation = (prefs[readerOrientationKey] ?: ReaderSettings.ORIENTATION_AUTO).coerceIn(0, 2),
            colorFilter = (prefs[readerColorFilterKey] ?: ReaderSettings.FILTER_NONE).coerceIn(0, 3),
            eInk = prefs[readerEInkKey] ?: false,
            brightness = (prefs[readerBrightnessKey] ?: ReaderSettings.BRIGHTNESS_SYSTEM.toDouble()).toFloat()
                .let { if (it < 0f) ReaderSettings.BRIGHTNESS_SYSTEM else it.coerceIn(0.01f, 1f) },
            autoScrollSpeed = (prefs[readerAutoScrollKey] ?: 3).coerceIn(1, ReaderSettings.MAX_AUTO_SCROLL),
            webtoonTapZones = prefs[readerWebtoonTapZonesKey] ?: false,
            preloadPages = (prefs[readerPreloadKey] ?: 4).coerceIn(0, ReaderSettings.MAX_PRELOAD),
            preloadChapters = (prefs[readerPreloadChaptersKey] ?: 0).coerceIn(0, ReaderSettings.MAX_PRELOAD_CHAPTERS),
            cropBorders = prefs[readerCropBordersKey] ?: false,
            splitWide = prefs[readerSplitWideKey] ?: false,
            spreads = (prefs[readerSpreadsKey] ?: ReaderSettings.SPREADS_AUTO).coerceIn(0, 2),
            spreadCoverAlone = prefs[readerSpreadCoverKey] ?: true,
            guidedPanels = prefs[readerGuidedPanelsKey] ?: false,
            panelOrder = (prefs[readerPanelOrderKey] ?: ReaderSettings.PANELS_RTL).coerceIn(0, 1),
        )
    }

    suspend fun setNovelReaderSettings(settings: NovelReaderSettings) {
        val s = settings.clamped()
        context.appDataStore.edit {
            it[novelFontSizeKey] = s.fontSize
            it[novelLineHeightKey] = s.lineHeight.toDouble()
            it[novelFontKey] = s.font.ordinal
            it[novelSideMarginKey] = s.sideMargin
            it[novelParagraphSpacingKey] = s.paragraphSpacing
            it[novelJustifyKey] = s.justify
            it[novelThemeKey] = s.theme.ordinal
            it[novelPagedKey] = s.paged
            it[novelShowChapterTitleKey] = s.showChapterTitle
            it[novelBrightnessSwipeKey] = s.brightnessSwipe
            it[novelBrightnessKey] = s.brightness.toDouble()
            it[novelVolumeKeysKey] = s.volumeKeys
            it[novelStatusLineKey] = s.statusLine
            it[novelParagraphIndentKey] = s.paragraphIndent
            it[novelAutoScrollSpeedKey] = s.autoScrollSpeed
        }
    }

    suspend fun setReadAloudSettings(settings: ReadAloudSettings) {
        val s = settings.clamped()
        context.appDataStore.edit {
            it[readAloudSpeedKey] = s.speed.toDouble()
            if (s.voiceName != null) it[readAloudVoiceKey] = s.voiceName else it.remove(readAloudVoiceKey)
            if (s.engine != null) it[readAloudEngineKey] = s.engine else it.remove(readAloudEngineKey)
        }
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
            it[readerOrientationKey] = settings.orientation
            it[readerColorFilterKey] = settings.colorFilter
            it[readerEInkKey] = settings.eInk
            it[readerBrightnessKey] = settings.brightness.toDouble()
            it[readerAutoScrollKey] = settings.autoScrollSpeed
            it[readerWebtoonTapZonesKey] = settings.webtoonTapZones
            it[readerPreloadKey] = settings.preloadPages
            it[readerPreloadChaptersKey] = settings.preloadChapters
            it[readerCropBordersKey] = settings.cropBorders
            it[readerSplitWideKey] = settings.splitWide
            it[readerSpreadsKey] = settings.spreads
            it[readerSpreadCoverKey] = settings.spreadCoverAlone
            it[readerGuidedPanelsKey] = settings.guidedPanels
            it[readerPanelOrderKey] = settings.panelOrder
        }
    }
}
