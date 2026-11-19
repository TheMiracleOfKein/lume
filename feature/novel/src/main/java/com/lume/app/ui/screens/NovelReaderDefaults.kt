package com.lume.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lume.app.AppContainer
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.ui.components.ResetReaderSettingsButton
import com.lume.app.ui.screens.settings.SettingItem
import com.lume.app.ui.screens.settings.SettingsGroup
import com.lume.app.ui.screens.settings.SettingsNote
import kotlinx.coroutines.launch

/** Settings → Reader in the Ranobe mode: the text reader's defaults, the same values its sheet edits. */
@Composable
internal fun NovelReaderDefaults() {
    val container = LocalAppContainer.current
    val vm: NovelReaderDefaultsViewModel = viewModel { NovelReaderDefaultsViewModel(container) }
    SettingsNote(stringResource(R.string.settings_reader_note))
    Spacer(Modifier.height(4.dp))
    SettingsGroup {
        Spacer(Modifier.height(6.dp))
        NovelReaderSettingsControls(
            settings = vm.settings,
            onChange = { vm.update(it, commit = false) },
            onCommit = { vm.update(it, commit = true) },
            item = { id, content -> SettingItem(id, content) },
        )
        Spacer(Modifier.height(6.dp))
    }
    ResetReaderSettingsButton(onConfirm = vm::reset)
}

internal class NovelReaderDefaultsViewModel(private val app: AppContainer) : ViewModel() {
    /** Read once; after that only this page changes it, so a DataStore round trip can't snap a slider back. */
    var settings by mutableStateOf(app.prefs.novelReaderSettings.value)
        private set

    /** Live edit (slider drag); [commit] also saves it. */
    fun update(next: NovelReaderSettings, commit: Boolean) {
        val clean = next.clamped()
        settings = clean
        if (commit) viewModelScope.launch { app.prefs.setNovelReaderSettings(clean) }
    }

    fun reset() = update(NovelReaderSettings(), commit = true)
}
