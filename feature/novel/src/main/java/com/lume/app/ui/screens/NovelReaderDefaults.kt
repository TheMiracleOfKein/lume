package com.lume.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lume.app.AppContainer
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.db.TextIndexSize
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.ConfirmDialog
import com.lume.app.ui.components.ResetReaderSettingsButton
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.screens.settings.SettingItem
import com.lume.app.ui.screens.settings.SettingsGroup
import com.lume.app.ui.screens.settings.SettingsLabel
import com.lume.app.ui.screens.settings.SettingsLink
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
    StorySettings(vm)
    ResetReaderSettingsButton(onConfirm = vm::reset)
}

/** Story index (name lookup) and the "Previously…" recap: their switches and the index's size. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorySettings(vm: NovelReaderDefaultsViewModel) {
    val context = LocalContext.current
    val storyIndex by vm.storyIndex.collectAsState()
    val recapHours by vm.recapAfterHours.collectAsState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshIndexSize() }
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        SettingItem("story_index") {
            SwitchRow(
                label = stringResource(R.string.settings_story_index),
                hint = stringResource(R.string.settings_story_index_hint),
                checked = storyIndex,
                onCheckedChange = vm::setStoryIndex,
            )
        }
        SettingItem("story_recap") {
            SettingsLabel(stringResource(R.string.settings_story_recap))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RECAP_CHOICES.forEach { (hours, label) ->
                    ChoiceChip(
                        label = stringResource(label),
                        selected = hours == recapHours,
                        onClick = { vm.setRecapAfterHours(hours) },
                    )
                }
            }
            SettingsNote(stringResource(R.string.settings_story_recap_hint), Modifier.padding(top = 8.dp))
        }
        SettingItem("story_index_clear") {
            vm.indexSize?.let { size ->
                SettingsNote(
                    stringResource(
                        R.string.settings_story_index_size,
                        size.blocks,
                        android.text.format.Formatter.formatShortFileSize(context, size.chars * BYTES_PER_CHAR),
                    ),
                )
            }
            SettingsLink(stringResource(R.string.settings_story_index_clear), stringResource(R.string.settings_story_index_clear_hint)) {
                confirmClear = true
            }
            if (confirmClear) {
                ConfirmDialog(
                    title = stringResource(R.string.settings_story_index_clear),
                    text = stringResource(R.string.settings_story_index_clear_hint),
                    confirmLabel = stringResource(R.string.action_clear),
                    onConfirm = {
                        vm.clearIndex()
                        Toast.makeText(context, R.string.settings_story_index_cleared, Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { confirmClear = false },
                )
            }
        }
    }
}

/** Hours after which the recap shows, and the label of each choice. */
private val RECAP_CHOICES = listOf(
    0 to R.string.settings_story_recap_off,
    6 to R.string.settings_story_recap_6h,
    24 to R.string.settings_story_recap_1d,
    72 to R.string.settings_story_recap_3d,
)

/** Text is stored as UTF-16 in the database file's estimate: about two bytes a character. */
private const val BYTES_PER_CHAR = 2L

internal class NovelReaderDefaultsViewModel(private val app: AppContainer) : ViewModel() {
    val storyIndex = app.prefs.storyIndex
    val recapAfterHours = app.prefs.recapAfterHours

    /** What the story index holds; null until read. */
    var indexSize by mutableStateOf<TextIndexSize?>(null)
        private set

    fun refreshIndexSize() {
        viewModelScope.launch { indexSize = app.dao.textIndexSize() }
    }

    fun setStoryIndex(on: Boolean) {
        viewModelScope.launch { app.prefs.setStoryIndex(on) }
    }

    fun setRecapAfterHours(hours: Int) {
        viewModelScope.launch { app.prefs.setRecapAfterHours(hours) }
    }

    fun clearIndex() {
        viewModelScope.launch {
            app.dao.deleteAllText()
            indexSize = app.dao.textIndexSize()
        }
    }

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
