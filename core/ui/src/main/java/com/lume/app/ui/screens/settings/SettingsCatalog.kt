package com.lume.app.ui.screens.settings

import android.content.res.Resources
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.graphics.vector.ImageVector
import com.lume.app.R
import com.lume.app.domain.MediaKind
import com.lume.app.domain.SearchableSetting

/** Settings that only mean something for comics: downloads, trackers, the page reader. */
private val COMICS_ONLY = setOf(MediaKind.Comics)
private val NOVEL_ONLY = setOf(MediaKind.Novel)

/**
 * The sections of the settings screen; each but [About] has a page of its own. [modes] are the
 * app modes the section is shown in (null: every mode).
 */
enum class SettingsPage(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector,
    val modes: Set<MediaKind>? = null,
    /** The summary in the Ranobe mode, when it differs. */
    @StringRes val novelSummaryRes: Int? = null,
) {
    Appearance("appearance", R.string.settings_section_appearance, R.string.settings_summary_appearance, Icons.Outlined.Palette),
    Reader("reader", R.string.settings_section_reader, R.string.settings_summary_reader, Icons.AutoMirrored.Outlined.MenuBook, novelSummaryRes = R.string.settings_summary_reader_novel),
    Ai("ai", R.string.settings_section_ai, R.string.settings_summary_ai, Icons.Outlined.AutoAwesome, NOVEL_ONLY),
    Library("library", R.string.settings_section_library_updates, R.string.settings_summary_library, Icons.AutoMirrored.Outlined.LibraryBooks),
    Downloads("downloads", R.string.settings_section_downloads, R.string.settings_summary_downloads, Icons.Outlined.Download, COMICS_ONLY),
    Sources("sources", R.string.settings_section_sources, R.string.settings_summary_sources, Icons.Outlined.Explore),
    Trackers("trackers", R.string.settings_trackers, R.string.settings_summary_trackers, Icons.Outlined.Link),
    Sync("sync", R.string.settings_section_sync, R.string.settings_summary_sync, Icons.Outlined.CloudSync),
    Backup("backup", R.string.settings_section_backup, R.string.settings_summary_backup, Icons.Outlined.Backup),
    Privacy("privacy", R.string.settings_section_privacy, R.string.settings_summary_privacy, Icons.Outlined.Shield),
    Network("network", R.string.settings_section_network, R.string.settings_summary_network, Icons.Outlined.Public),
    About("about", R.string.menu_about, R.string.settings_summary_about, Icons.Outlined.Info),
    ;

    fun shownIn(mode: MediaKind): Boolean = modes == null || mode in modes

    @StringRes
    fun summaryIn(mode: MediaKind): Int = if (mode == MediaKind.Novel) novelSummaryRes ?: summaryRes else summaryRes

    companion object {
        fun fromKey(key: String?): SettingsPage? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One searchable setting. [id] is what the page passes to [SettingItem], so a search result can
 * open the page and highlight the row.
 */
data class SettingInfo(
    val id: String,
    val page: SettingsPage,
    @StringRes val titleRes: Int,
    @StringRes val hintRes: Int? = null,
    @StringRes val keywordsRes: Int? = null,
    val minSdk: Int = 0,
    /** The app modes the setting applies to (null: every mode its page is shown in). */
    val modes: Set<MediaKind>? = null,
) {
    fun shownIn(mode: MediaKind): Boolean = page.shownIn(mode) && (modes == null || mode in modes)
}

object SettingsCatalog {
    val all: List<SettingInfo> = listOf(
        SettingInfo("theme", SettingsPage.Appearance, R.string.settings_theme, keywordsRes = R.string.settings_kw_theme),
        SettingInfo("pure_black", SettingsPage.Appearance, R.string.settings_pure_black, R.string.settings_pure_black_hint, R.string.settings_kw_pure_black),
        SettingInfo("dynamic_color", SettingsPage.Appearance, R.string.settings_dynamic_color, R.string.settings_dynamic_color_hint, R.string.settings_kw_dynamic_color, minSdk = 31),
        SettingInfo("language", SettingsPage.Appearance, R.string.settings_language, keywordsRes = R.string.settings_kw_language),
        SettingInfo("translate_labels", SettingsPage.Appearance, R.string.settings_translate_labels, R.string.settings_translate_labels_hint, R.string.settings_kw_translate_labels),

        SettingInfo("immersive", SettingsPage.Reader, R.string.reader_immersive, R.string.reader_immersive_hint, R.string.settings_kw_immersive, modes = COMICS_ONLY),
        SettingInfo("keep_screen_on", SettingsPage.Reader, R.string.reader_keep_screen_on, R.string.reader_keep_screen_on_hint, R.string.settings_kw_keep_screen_on, modes = COMICS_ONLY),
        SettingInfo("seamless", SettingsPage.Reader, R.string.reader_seamless, R.string.reader_seamless_hint, R.string.settings_kw_seamless, modes = COMICS_ONLY),
        SettingInfo("tap_zones", SettingsPage.Reader, R.string.reader_tap_zones, R.string.reader_tap_zones_hint, R.string.settings_kw_tap_zones, modes = COMICS_ONLY),
        SettingInfo("volume_keys", SettingsPage.Reader, R.string.reader_volume_keys, R.string.reader_volume_keys_hint, R.string.settings_kw_volume_keys, modes = COMICS_ONLY),
        SettingInfo("double_tap_zoom", SettingsPage.Reader, R.string.reader_double_tap_zoom, R.string.reader_double_tap_zoom_hint, R.string.settings_kw_double_tap_zoom, modes = COMICS_ONLY),
        SettingInfo("guided_panels", SettingsPage.Reader, R.string.reader_guided_panels, R.string.reader_guided_panels_hint, R.string.settings_kw_guided_panels, modes = COMICS_ONLY),
        SettingInfo("panel_order", SettingsPage.Reader, R.string.reader_panel_order, R.string.reader_panel_order_hint, R.string.settings_kw_panel_order, modes = COMICS_ONLY),
        SettingInfo("crop_borders", SettingsPage.Reader, R.string.reader_crop_borders, R.string.reader_crop_borders_hint, R.string.settings_kw_crop_borders, modes = COMICS_ONLY),
        SettingInfo("split_wide", SettingsPage.Reader, R.string.reader_split_wide, R.string.reader_split_wide_hint, R.string.settings_kw_split_wide, modes = COMICS_ONLY),
        SettingInfo("spreads", SettingsPage.Reader, R.string.reader_spreads, R.string.settings_reader_paged_only, R.string.settings_kw_spreads, modes = COMICS_ONLY),
        SettingInfo("preload", SettingsPage.Reader, R.string.settings_reader_preload, R.string.reader_preload_hint, R.string.settings_kw_preload, modes = COMICS_ONLY),
        SettingInfo("preload_chapters", SettingsPage.Reader, R.string.reader_preload_chapters, R.string.reader_preload_chapters_hint, R.string.settings_kw_preload, modes = COMICS_ONLY),

        // The text reader's defaults; the Ranobe mode shows them on the Reader page.
        SettingInfo("novel_font_size", SettingsPage.Reader, R.string.novel_font_size, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_line_height", SettingsPage.Reader, R.string.novel_line_height, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_side_margin", SettingsPage.Reader, R.string.novel_side_margin, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_paragraph_spacing", SettingsPage.Reader, R.string.novel_paragraph_spacing, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_font", SettingsPage.Reader, R.string.novel_font, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_justify", SettingsPage.Reader, R.string.novel_justify, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_paragraph_indent", SettingsPage.Reader, R.string.novel_paragraph_indent, keywordsRes = R.string.settings_kw_novel_text, modes = NOVEL_ONLY),
        SettingInfo("novel_show_chapter_title", SettingsPage.Reader, R.string.novel_show_chapter_title, R.string.novel_show_chapter_title_summary, R.string.settings_kw_novel_title, modes = NOVEL_ONLY),
        SettingInfo("novel_paged", SettingsPage.Reader, R.string.novel_paged, keywordsRes = R.string.settings_kw_novel_paged, modes = NOVEL_ONLY),
        SettingInfo("novel_auto_scroll_speed", SettingsPage.Reader, R.string.novel_auto_scroll_speed, keywordsRes = R.string.settings_kw_novel_auto_scroll, modes = NOVEL_ONLY),
        SettingInfo("novel_brightness_swipe", SettingsPage.Reader, R.string.novel_brightness_swipe, R.string.novel_brightness_swipe_summary, R.string.settings_kw_novel_brightness, modes = NOVEL_ONLY),
        SettingInfo("novel_volume_keys", SettingsPage.Reader, R.string.novel_volume_keys, R.string.novel_volume_keys_summary, R.string.settings_kw_novel_volume, modes = NOVEL_ONLY),
        SettingInfo("novel_status_line", SettingsPage.Reader, R.string.novel_status_line, R.string.novel_status_line_summary, R.string.settings_kw_novel_status, modes = NOVEL_ONLY),
        SettingInfo("novel_theme", SettingsPage.Reader, R.string.novel_theme, keywordsRes = R.string.settings_kw_novel_theme, modes = NOVEL_ONLY),
        SettingInfo("story_index", SettingsPage.Reader, R.string.settings_story_index, R.string.settings_story_index_hint, R.string.settings_kw_story, modes = NOVEL_ONLY),
        SettingInfo("story_recap", SettingsPage.Reader, R.string.settings_story_recap, R.string.settings_story_recap_hint, R.string.settings_kw_story, modes = NOVEL_ONLY),
        SettingInfo("story_ai_recap", SettingsPage.Reader, R.string.settings_story_ai_recap, R.string.settings_story_ai_recap_hint, R.string.settings_kw_story, modes = NOVEL_ONLY),
        SettingInfo("story_index_clear", SettingsPage.Reader, R.string.settings_story_index_clear, R.string.settings_story_index_clear_hint, R.string.settings_kw_story, modes = NOVEL_ONLY),

        SettingInfo("ai_service", SettingsPage.Ai, R.string.ai_active_service, R.string.ai_active_service_hint, R.string.settings_kw_ai),
        SettingInfo("ai_model", SettingsPage.Ai, R.string.translate_llm_model, R.string.ai_model_settings_hint, R.string.settings_kw_ai),
        SettingInfo("deepl_key", SettingsPage.Ai, R.string.translate_deepl_key, R.string.ai_deepl_hint, R.string.settings_kw_ai),

        SettingInfo("library_check", SettingsPage.Library, R.string.settings_library_auto_check, R.string.settings_library_auto_check_hint, R.string.settings_kw_library_check),
        SettingInfo("library_interval", SettingsPage.Library, R.string.settings_library_interval, keywordsRes = R.string.settings_kw_interval),
        SettingInfo("library_wifi", SettingsPage.Library, R.string.settings_wifi_only, R.string.settings_wifi_only_hint, R.string.settings_kw_wifi),
        SettingInfo("library_charging", SettingsPage.Library, R.string.settings_library_charging_only, R.string.settings_library_charging_only_hint, R.string.settings_kw_library_rules),
        SettingInfo("library_notify_categories", SettingsPage.Library, R.string.settings_notify_categories, R.string.settings_notify_categories_hint, R.string.settings_kw_notify_categories),
        SettingInfo("library_update_categories", SettingsPage.Library, R.string.settings_update_categories, R.string.settings_update_categories_hint, R.string.settings_kw_library_rules),
        SettingInfo("library_skip_completed", SettingsPage.Library, R.string.settings_library_skip_completed, keywordsRes = R.string.settings_kw_library_rules),
        SettingInfo("library_skip_not_started", SettingsPage.Library, R.string.settings_library_skip_not_started, keywordsRes = R.string.settings_kw_library_rules),
        SettingInfo("library_skip_unread", SettingsPage.Library, R.string.settings_library_skip_unread, R.string.settings_library_skip_unread_hint, R.string.settings_kw_library_rules),
        SettingInfo("find_sources", SettingsPage.Library, R.string.settings_find_other_sources, R.string.settings_find_other_sources_hint, R.string.settings_kw_find_sources, modes = COMICS_ONLY),
        SettingInfo("reading_stats", SettingsPage.Library, R.string.stats_title, R.string.settings_stats_hint, R.string.settings_kw_stats),
        SettingInfo("categories", SettingsPage.Library, R.string.categories_title, R.string.settings_categories_hint, R.string.settings_kw_categories),
        SettingInfo("local_import", SettingsPage.Library, R.string.local_import_folder, keywordsRes = R.string.settings_kw_local),
        SettingInfo("local_import_books", SettingsPage.Library, R.string.local_import_books, R.string.local_import_books_hint, R.string.settings_kw_local_books, modes = NOVEL_ONLY),
        SettingInfo("local_open", SettingsPage.Library, R.string.local_open_library, keywordsRes = R.string.settings_kw_local),
        SettingInfo("local_clean", SettingsPage.Library, R.string.local_clean_temp, R.string.local_clean_temp_hint),
        SettingInfo("watched_add", SettingsPage.Library, R.string.watched_add, R.string.watched_add_hint, R.string.settings_kw_watched),
        SettingInfo("watched_scan_now", SettingsPage.Library, R.string.watched_scan_now, keywordsRes = R.string.settings_kw_watched),
        SettingInfo("watched_interval", SettingsPage.Library, R.string.watched_interval, keywordsRes = R.string.settings_kw_watched),

        SettingInfo("downloads_wifi", SettingsPage.Downloads, R.string.settings_downloads_wifi_only, R.string.settings_downloads_wifi_only_hint, R.string.settings_kw_wifi),
        SettingInfo("auto_download", SettingsPage.Downloads, R.string.settings_downloads_auto_new, R.string.settings_downloads_auto_new_hint, R.string.settings_kw_auto_download),
        SettingInfo("delete_read", SettingsPage.Downloads, R.string.settings_downloads_delete_read, R.string.settings_downloads_delete_read_hint, R.string.settings_kw_delete_read),
        SettingInfo("cbz", SettingsPage.Downloads, R.string.settings_downloads_cbz, R.string.settings_downloads_cbz_hint, R.string.settings_kw_cbz),
        SettingInfo("parallel", SettingsPage.Downloads, R.string.settings_downloads_parallel, R.string.settings_downloads_parallel_hint, R.string.settings_kw_parallel),
        SettingInfo("storage", SettingsPage.Downloads, R.string.settings_download_folder, R.string.settings_storage_note, R.string.settings_kw_storage),
        SettingInfo("downloads_manage", SettingsPage.Downloads, R.string.settings_downloads, R.string.settings_downloads_hint, R.string.settings_kw_downloads),
        SettingInfo("restore_downloads", SettingsPage.Downloads, R.string.settings_restore_downloads, R.string.settings_restore_downloads_hint),

        SettingInfo("nsfw", SettingsPage.Sources, R.string.settings_search_nsfw, R.string.settings_search_nsfw_hint, R.string.settings_kw_nsfw),
        SettingInfo("similar_titles", SettingsPage.Sources, R.string.settings_similar_titles, R.string.settings_similar_titles_hint, R.string.settings_kw_similar_titles, modes = COMICS_ONLY),
        SettingInfo("sources", SettingsPage.Sources, R.string.settings_sources, R.string.settings_sources_hint, R.string.settings_kw_sources),

        SettingInfo("tracker_accounts", SettingsPage.Trackers, R.string.settings_group_accounts, R.string.settings_trackers_note, R.string.settings_kw_trackers),
        SettingInfo("auto_link", SettingsPage.Trackers, R.string.tracker_auto_link, R.string.tracker_auto_link_hint, R.string.settings_kw_auto_link),

        SettingInfo("sync_place", SettingsPage.Sync, R.string.sync_place, R.string.sync_intro, R.string.settings_kw_sync),
        SettingInfo("sync_passphrase", SettingsPage.Sync, R.string.sync_passphrase, R.string.sync_passphrase_hint, R.string.settings_kw_sync_passphrase),
        SettingInfo("sync_interval", SettingsPage.Sync, R.string.sync_interval, keywordsRes = R.string.settings_kw_sync),
        SettingInfo("sync_now", SettingsPage.Sync, R.string.sync_now, keywordsRes = R.string.settings_kw_sync),

        SettingInfo("backup_create", SettingsPage.Backup, R.string.settings_create_backup, R.string.backup_create_hint, R.string.settings_kw_backup),
        SettingInfo("backup_save", SettingsPage.Backup, R.string.backup_save_to, R.string.backup_save_to_hint, R.string.settings_kw_backup),
        SettingInfo("backup_restore", SettingsPage.Backup, R.string.settings_restore_backup, R.string.backup_restore_hint, R.string.settings_kw_restore),
        SettingInfo("backup_restore_file", SettingsPage.Backup, R.string.backup_restore_file, R.string.backup_restore_file_hint, R.string.settings_kw_restore),
        SettingInfo("reading_list_open", SettingsPage.Backup, R.string.reading_list_open, R.string.reading_list_open_hint, R.string.settings_kw_reading_list),
        SettingInfo("auto_backup", SettingsPage.Backup, R.string.backup_auto, R.string.backup_auto_hint, R.string.settings_kw_auto_backup),

        SettingInfo("incognito", SettingsPage.Privacy, R.string.settings_incognito, R.string.settings_incognito_hint, R.string.settings_kw_incognito),
        SettingInfo("app_lock", SettingsPage.Privacy, R.string.settings_app_lock, R.string.settings_app_lock_hint, R.string.settings_kw_app_lock),
        SettingInfo("secure_screen", SettingsPage.Privacy, R.string.settings_secure_screen, R.string.settings_secure_screen_hint, R.string.settings_kw_secure_screen),

        SettingInfo("remote_hosts", SettingsPage.Network, R.string.settings_remote_hosts, R.string.settings_remote_hosts_hint, R.string.settings_kw_remote_hosts),
        SettingInfo("doh", SettingsPage.Network, R.string.settings_doh, R.string.settings_doh_hint, R.string.settings_kw_doh),
        SettingInfo("clear_cache", SettingsPage.Network, R.string.settings_clear_cache, R.string.settings_clear_cache_hint, R.string.settings_kw_cache),
        SettingInfo("clear_cookies", SettingsPage.Network, R.string.settings_clear_cookies, R.string.settings_clear_cookies_hint, R.string.settings_kw_cookies),

        SettingInfo("about", SettingsPage.About, R.string.menu_about, keywordsRes = R.string.settings_kw_about),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): SettingInfo? = byId[id]

    /** The settings this device shows in [mode], as the search sees them, in the current app language. */
    fun searchable(resources: Resources, mode: MediaKind = MediaKind.Comics): List<SearchableSetting> = all
        .filter { Build.VERSION.SDK_INT >= it.minSdk && it.shownIn(mode) }
        .map { info ->
            SearchableSetting(
                id = info.id,
                title = resources.getString(info.titleRes),
                section = resources.getString(info.page.titleRes),
                hint = info.hintRes?.let(resources::getString).orEmpty(),
                keywords = info.keywordsRes?.let(resources::getString).orEmpty(),
            )
        }
}
