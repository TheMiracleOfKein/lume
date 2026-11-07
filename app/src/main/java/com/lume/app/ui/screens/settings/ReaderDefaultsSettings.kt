package com.lume.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lume.app.R
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.screens.SettingsViewModel

/** Reader defaults; the reader's own settings sheet edits the same values. */
@Composable
fun ReaderDefaultsSettings(vm: SettingsViewModel) {
    val reader by vm.prefs.readerSettings.collectAsState()

    SettingsNote(stringResource(R.string.settings_reader_note))
    SettingsGroup {
        SettingItem("immersive") {
            SwitchRow(
                label = stringResource(R.string.reader_immersive),
                hint = stringResource(R.string.reader_immersive_hint),
                checked = reader.immersive,
                onCheckedChange = { vm.updateReader(reader.copy(immersive = it)) },
            )
        }
        SettingItem("keep_screen_on") {
            SwitchRow(
                label = stringResource(R.string.reader_keep_screen_on),
                hint = stringResource(R.string.reader_keep_screen_on_hint),
                checked = reader.keepScreenOn,
                onCheckedChange = { vm.updateReader(reader.copy(keepScreenOn = it)) },
            )
        }
        SettingItem("seamless") {
            SwitchRow(
                label = stringResource(R.string.reader_seamless),
                hint = stringResource(R.string.reader_seamless_hint),
                checked = reader.seamlessReading,
                onCheckedChange = { vm.updateReader(reader.copy(seamlessReading = it)) },
            )
        }
    }
    SettingsSectionTitle(R.string.settings_section_controls)
    SettingsGroup {
        SettingItem("tap_zones") {
            SwitchRow(
                label = stringResource(R.string.reader_tap_zones),
                hint = stringResource(R.string.reader_tap_zones_hint),
                checked = reader.tapZones,
                onCheckedChange = { vm.updateReader(reader.copy(tapZones = it)) },
            )
        }
        SettingItem("volume_keys") {
            SwitchRow(
                label = stringResource(R.string.reader_volume_keys),
                hint = stringResource(R.string.reader_volume_keys_hint),
                checked = reader.volumeKeys,
                onCheckedChange = { vm.updateReader(reader.copy(volumeKeys = it)) },
            )
        }
        SettingItem("double_tap_zoom") {
            SwitchRow(
                label = stringResource(R.string.reader_double_tap_zoom),
                hint = stringResource(R.string.reader_double_tap_zoom_hint),
                checked = reader.doubleTapZoom,
                onCheckedChange = { vm.updateReader(reader.copy(doubleTapZoom = it)) },
            )
        }
    }
    SettingsSectionTitle(R.string.settings_section_pages)
    SettingsGroup {
        SettingItem("guided_panels") {
            SwitchRow(
                label = stringResource(R.string.reader_guided_panels),
                hint = stringResource(R.string.reader_guided_panels_hint),
                checked = reader.guidedPanels,
                onCheckedChange = { vm.updateReader(reader.copy(guidedPanels = it)) },
            )
        }
        SettingItem("crop_borders") {
            SwitchRow(
                label = stringResource(R.string.reader_crop_borders),
                hint = stringResource(R.string.reader_crop_borders_hint),
                checked = reader.cropBorders,
                onCheckedChange = { vm.updateReader(reader.copy(cropBorders = it)) },
            )
        }
        SettingItem("split_wide") {
            SwitchRow(
                label = stringResource(R.string.reader_split_wide),
                hint = stringResource(R.string.reader_split_wide_hint),
                checked = reader.splitWide,
                onCheckedChange = { vm.updateReader(reader.copy(splitWide = it)) },
            )
        }
        SettingItem("spreads") {
            SettingsLabel(stringResource(R.string.reader_spreads))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ReaderSettings.SPREADS_OFF to R.string.reader_spreads_off,
                    ReaderSettings.SPREADS_AUTO to R.string.reader_spreads_auto,
                    ReaderSettings.SPREADS_ALWAYS to R.string.reader_spreads_always,
                ).forEach { (value, label) ->
                    ChoiceChip(
                        label = stringResource(label),
                        selected = reader.spreads == value,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.updateReader(reader.copy(spreads = value)) },
                    )
                }
            }
            SettingsNote(stringResource(R.string.settings_reader_paged_only), Modifier.padding(top = 8.dp))
        }
        SettingItem("preload") {
            SettingsLabel(pluralStringResource(R.plurals.reader_preload, reader.preloadPages, reader.preloadPages))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRELOAD_CHOICES.forEach { n ->
                    ChoiceChip(
                        label = n.toString(),
                        selected = reader.preloadPages == n,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.updateReader(reader.copy(preloadPages = n)) },
                    )
                }
            }
            SettingsNote(stringResource(R.string.reader_preload_hint), Modifier.padding(top = 8.dp))
        }
    }
}

private val PRELOAD_CHOICES = listOf(0, 2, 4, 6, 8, 10)
