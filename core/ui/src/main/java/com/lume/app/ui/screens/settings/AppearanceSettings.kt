package com.lume.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lume.app.R
import com.lume.app.data.locale.AppLanguage
import com.lume.app.data.locale.AppLocaleController
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.screens.SettingsViewModel
import com.lume.app.ui.theme.ThemeMode

@Composable
fun AppearanceSettings(vm: SettingsViewModel) {
    val prefs = vm.prefs
    val themeMode by prefs.themeMode.collectAsState()
    val pureBlack by prefs.pureBlack.collectAsState()
    val dynamicColor by prefs.dynamicColor.collectAsState()
    val translateLabels by prefs.translateChapterLabels.collectAsState()
    val appLanguageTag by prefs.appLanguage.collectAsState()
    val currentLanguage = AppLanguage.fromStored(appLanguageTag)

    SettingsGroup {
        SettingItem("theme") {
            SettingsLabel(stringResource(R.string.settings_theme))
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
        }
        SettingItem("pure_black") {
            SwitchRow(
                label = stringResource(R.string.settings_pure_black),
                hint = stringResource(R.string.settings_pure_black_hint),
                checked = pureBlack,
                onCheckedChange = vm::setPureBlack,
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SettingItem("dynamic_color") {
                SwitchRow(
                    label = stringResource(R.string.settings_dynamic_color),
                    hint = stringResource(R.string.settings_dynamic_color_hint),
                    checked = dynamicColor,
                    onCheckedChange = vm::setDynamicColor,
                )
            }
        }
    }
    SettingsSectionTitle(R.string.settings_language)
    SettingsGroup {
        SettingItem("language") {
            Row(Modifier.padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    ChoiceChip(
                        label = stringResource(lang.labelRes),
                        selected = currentLanguage == lang,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setAppLanguage(lang.tag) { AppLocaleController.apply(lang) } },
                    )
                }
            }
        }
        SettingItem("translate_labels") {
            SwitchRow(
                label = stringResource(R.string.settings_translate_labels),
                hint = stringResource(R.string.settings_translate_labels_hint),
                checked = translateLabels,
                onCheckedChange = vm::setTranslateLabels,
            )
        }
    }
}
