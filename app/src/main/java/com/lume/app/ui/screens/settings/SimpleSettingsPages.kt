package com.lume.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lume.app.R
import com.lume.app.data.network.DohProvider
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.SettingsViewModel

/* The short sections: sources and search, privacy, network and storage. */

@Composable
fun SourcesSettings(nav: NavHostController, vm: SettingsViewModel) {
    val searchNsfw by vm.prefs.globalSearchNsfw.collectAsState()
    val similarTitles by vm.prefs.similarTitles.collectAsState()
    SettingsGroup {
        SettingItem("sources") {
            SettingsLink(stringResource(R.string.settings_sources), stringResource(R.string.settings_sources_hint)) {
                nav.navigate(Routes.Sources)
            }
        }
        SettingItem("nsfw") {
            SwitchRow(
                label = stringResource(R.string.settings_search_nsfw),
                hint = stringResource(R.string.settings_search_nsfw_hint),
                checked = searchNsfw,
                onCheckedChange = vm::setSearchNsfw,
            )
        }
        SettingItem("similar_titles") {
            SwitchRow(
                label = stringResource(R.string.settings_similar_titles),
                hint = stringResource(R.string.settings_similar_titles_hint),
                checked = similarTitles,
                onCheckedChange = vm::setSimilarTitles,
            )
        }
    }
}

@Composable
fun PrivacySettings(vm: SettingsViewModel) {
    val prefs = vm.prefs
    val incognito by prefs.incognito.collectAsState()
    val appLock by prefs.appLock.collectAsState()
    val secureScreen by prefs.secureScreen.collectAsState()
    SettingsGroup {
        SettingItem("incognito") {
            SwitchRow(
                label = stringResource(R.string.settings_incognito),
                hint = stringResource(R.string.settings_incognito_hint),
                checked = incognito,
                onCheckedChange = vm::setIncognito,
            )
        }
        SettingItem("app_lock") {
            SwitchRow(
                label = stringResource(R.string.settings_app_lock),
                hint = stringResource(R.string.settings_app_lock_hint),
                checked = appLock,
                onCheckedChange = vm::setAppLock,
            )
        }
        SettingItem("secure_screen") {
            SwitchRow(
                label = stringResource(R.string.settings_secure_screen),
                hint = stringResource(R.string.settings_secure_screen_hint),
                checked = secureScreen,
                onCheckedChange = vm::setSecureScreen,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkSettings(vm: SettingsViewModel) {
    val doh by vm.prefs.dnsOverHttps.collectAsState()
    SettingsGroup {
        SettingItem("doh") {
            SettingsLabel(stringResource(R.string.settings_doh))
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
            SettingsNote(stringResource(R.string.settings_doh_hint), Modifier.padding(top = 8.dp))
        }
    }
    SettingsSectionTitle(R.string.settings_group_clean)
    SettingsGroup {
        SettingItem("clear_cache") {
            SettingsLink(stringResource(R.string.settings_clear_cache), stringResource(R.string.settings_clear_cache_hint)) {
                vm.clearCache()
            }
        }
        SettingItem("clear_cookies") {
            SettingsLink(stringResource(R.string.settings_clear_cookies), stringResource(R.string.settings_clear_cookies_hint)) {
                vm.clearCookies()
            }
        }
    }
}
