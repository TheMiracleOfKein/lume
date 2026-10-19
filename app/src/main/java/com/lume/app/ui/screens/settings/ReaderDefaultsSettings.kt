package com.lume.app.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.lume.app.R
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
}
