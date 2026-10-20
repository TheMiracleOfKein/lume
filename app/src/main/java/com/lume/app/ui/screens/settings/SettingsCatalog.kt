package com.lume.app.ui.screens.settings

import android.content.res.Resources
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import com.lume.app.R
import com.lume.app.domain.SearchableSetting

/** The sections of the settings screen; each but [About] has a page of its own. */
enum class SettingsPage(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector,
) {
    Appearance("appearance", R.string.settings_section_appearance, R.string.settings_summary_appearance, Icons.Outlined.Palette),
    Reader("reader", R.string.settings_section_reader, R.string.settings_summary_reader, Icons.AutoMirrored.Outlined.MenuBook),
    Library("library", R.string.settings_section_library_updates, R.string.settings_summary_library, Icons.AutoMirrored.Outlined.LibraryBooks),
    Downloads("downloads", R.string.settings_section_downloads, R.string.settings_summary_downloads, Icons.Outlined.Download),
    Sources("sources", R.string.settings_section_sources, R.string.settings_summary_sources, Icons.Outlined.Explore),
    Trackers("trackers", R.string.settings_trackers, R.string.settings_summary_trackers, Icons.Outlined.Sync),
    Backup("backup", R.string.settings_section_backup, R.string.settings_summary_backup, Icons.Outlined.Backup),
    Privacy("privacy", R.string.settings_section_privacy, R.string.settings_summary_privacy, Icons.Outlined.Shield),
    Network("network", R.string.settings_section_network, R.string.settings_summary_network, Icons.Outlined.Public),
    About("about", R.string.menu_about, R.string.settings_summary_about, Icons.Outlined.Info),
    ;

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
)

object SettingsCatalog {
    val all: List<SettingInfo> = listOf(
        SettingInfo("theme", SettingsPage.Appearance, R.string.settings_theme, keywordsRes = R.string.settings_kw_theme),
        SettingInfo("pure_black", SettingsPage.Appearance, R.string.settings_pure_black, R.string.settings_pure_black_hint, R.string.settings_kw_pure_black),
        SettingInfo("dynamic_color", SettingsPage.Appearance, R.string.settings_dynamic_color, R.string.settings_dynamic_color_hint, R.string.settings_kw_dynamic_color, minSdk = 31),
        SettingInfo("language", SettingsPage.Appearance, R.string.settings_language, keywordsRes = R.string.settings_kw_language),
        SettingInfo("translate_labels", SettingsPage.Appearance, R.string.settings_translate_labels, R.string.settings_translate_labels_hint, R.string.settings_kw_translate_labels),

        SettingInfo("immersive", SettingsPage.Reader, R.string.reader_immersive, R.string.reader_immersive_hint, R.string.settings_kw_immersive),
        SettingInfo("keep_screen_on", SettingsPage.Reader, R.string.reader_keep_screen_on, R.string.reader_keep_screen_on_hint, R.string.settings_kw_keep_screen_on),
        SettingInfo("seamless", SettingsPage.Reader, R.string.reader_seamless, R.string.reader_seamless_hint, R.string.settings_kw_seamless),
        SettingInfo("tap_zones", SettingsPage.Reader, R.string.reader_tap_zones, R.string.reader_tap_zones_hint, R.string.settings_kw_tap_zones),
        SettingInfo("volume_keys", SettingsPage.Reader, R.string.reader_volume_keys, R.string.reader_volume_keys_hint, R.string.settings_kw_volume_keys),
        SettingInfo("double_tap_zoom", SettingsPage.Reader, R.string.reader_double_tap_zoom, R.string.reader_double_tap_zoom_hint, R.string.settings_kw_double_tap_zoom),

        SettingInfo("library_check", SettingsPage.Library, R.string.settings_library_auto_check, R.string.settings_library_auto_check_hint, R.string.settings_kw_library_check),
        SettingInfo("library_interval", SettingsPage.Library, R.string.settings_library_interval, keywordsRes = R.string.settings_kw_interval),
        SettingInfo("library_wifi", SettingsPage.Library, R.string.settings_wifi_only, R.string.settings_wifi_only_hint, R.string.settings_kw_wifi),
        SettingInfo("find_sources", SettingsPage.Library, R.string.settings_find_other_sources, R.string.settings_find_other_sources_hint, R.string.settings_kw_find_sources),
        SettingInfo("categories", SettingsPage.Library, R.string.categories_title, R.string.settings_categories_hint, R.string.settings_kw_categories),
        SettingInfo("local_import", SettingsPage.Library, R.string.local_import_folder, keywordsRes = R.string.settings_kw_local),
        SettingInfo("local_open", SettingsPage.Library, R.string.local_open_library, keywordsRes = R.string.settings_kw_local),
        SettingInfo("local_clean", SettingsPage.Library, R.string.local_clean_temp, R.string.local_clean_temp_hint),

        SettingInfo("downloads_wifi", SettingsPage.Downloads, R.string.settings_downloads_wifi_only, R.string.settings_downloads_wifi_only_hint, R.string.settings_kw_wifi),
        SettingInfo("auto_download", SettingsPage.Downloads, R.string.settings_downloads_auto_new, R.string.settings_downloads_auto_new_hint, R.string.settings_kw_auto_download),
        SettingInfo("delete_read", SettingsPage.Downloads, R.string.settings_downloads_delete_read, R.string.settings_downloads_delete_read_hint, R.string.settings_kw_delete_read),
        SettingInfo("cbz", SettingsPage.Downloads, R.string.settings_downloads_cbz, R.string.settings_downloads_cbz_hint, R.string.settings_kw_cbz),
        SettingInfo("parallel", SettingsPage.Downloads, R.string.settings_downloads_parallel, R.string.settings_downloads_parallel_hint, R.string.settings_kw_parallel),
        SettingInfo("storage", SettingsPage.Downloads, R.string.settings_download_folder, R.string.settings_storage_note, R.string.settings_kw_storage),
        SettingInfo("downloads_manage", SettingsPage.Downloads, R.string.settings_downloads, R.string.settings_downloads_hint, R.string.settings_kw_downloads),
        SettingInfo("restore_downloads", SettingsPage.Downloads, R.string.settings_restore_downloads, R.string.settings_restore_downloads_hint),

        SettingInfo("nsfw", SettingsPage.Sources, R.string.settings_search_nsfw, R.string.settings_search_nsfw_hint, R.string.settings_kw_nsfw),
        SettingInfo("similar_titles", SettingsPage.Sources, R.string.settings_similar_titles, R.string.settings_similar_titles_hint, R.string.settings_kw_similar_titles),
        SettingInfo("sources", SettingsPage.Sources, R.string.settings_sources, R.string.settings_sources_hint, R.string.settings_kw_sources),

        SettingInfo("tracker_accounts", SettingsPage.Trackers, R.string.settings_group_accounts, R.string.settings_trackers_note, R.string.settings_kw_trackers),
        SettingInfo("auto_link", SettingsPage.Trackers, R.string.tracker_auto_link, R.string.tracker_auto_link_hint, R.string.settings_kw_auto_link),

        SettingInfo("backup_create", SettingsPage.Backup, R.string.settings_create_backup, R.string.backup_create_hint, R.string.settings_kw_backup),
        SettingInfo("backup_save", SettingsPage.Backup, R.string.backup_save_to, R.string.backup_save_to_hint, R.string.settings_kw_backup),
        SettingInfo("backup_restore", SettingsPage.Backup, R.string.settings_restore_backup, R.string.backup_restore_hint, R.string.settings_kw_restore),
        SettingInfo("backup_restore_file", SettingsPage.Backup, R.string.backup_restore_file, R.string.backup_restore_file_hint, R.string.settings_kw_restore),
        SettingInfo("reading_list_open", SettingsPage.Backup, R.string.reading_list_open, R.string.reading_list_open_hint, R.string.settings_kw_reading_list),
        SettingInfo("auto_backup", SettingsPage.Backup, R.string.backup_auto, R.string.backup_auto_hint, R.string.settings_kw_auto_backup),

        SettingInfo("incognito", SettingsPage.Privacy, R.string.settings_incognito, R.string.settings_incognito_hint, R.string.settings_kw_incognito),
        SettingInfo("app_lock", SettingsPage.Privacy, R.string.settings_app_lock, R.string.settings_app_lock_hint, R.string.settings_kw_app_lock),
        SettingInfo("secure_screen", SettingsPage.Privacy, R.string.settings_secure_screen, R.string.settings_secure_screen_hint, R.string.settings_kw_secure_screen),

        SettingInfo("doh", SettingsPage.Network, R.string.settings_doh, R.string.settings_doh_hint, R.string.settings_kw_doh),
        SettingInfo("clear_cache", SettingsPage.Network, R.string.settings_clear_cache, R.string.settings_clear_cache_hint, R.string.settings_kw_cache),
        SettingInfo("clear_cookies", SettingsPage.Network, R.string.settings_clear_cookies, R.string.settings_clear_cookies_hint, R.string.settings_kw_cookies),

        SettingInfo("about", SettingsPage.About, R.string.menu_about, keywordsRes = R.string.settings_kw_about),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): SettingInfo? = byId[id]

    /** The settings this device shows, as the search sees them, in the current app language. */
    fun searchable(resources: Resources): List<SearchableSetting> = all
        .filter { Build.VERSION.SDK_INT >= it.minSdk }
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
