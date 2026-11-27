package com.lume.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.prefs.ReadAloudSettings
import com.lume.app.readaloud.ReadAloud
import com.lume.app.readaloud.ReadAloudProblem
import com.lume.app.readaloud.ReadAloudState
import com.lume.app.readaloud.ReadAloudStatus
import com.lume.app.readaloud.SleepTimer
import com.lume.app.readaloud.remainingMs
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.ConfirmDialog
import com.lume.app.ui.components.SliderRow
import com.lume.app.ui.components.SheetHeader
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Read aloud in the text reader: the highlight of the spoken sentence, the control panel, the
// settings sheet and the "no engine" dialog.

/** The sentence being read: [start] until [end] of block [block] of [chapterId]. */
internal data class SpokenRange(val chapterId: String, val block: Int, val start: Int, val end: Int)

/** The spoken sentence's background: the reader's accent, light enough for the text to stay readable. */
internal fun spokenColor(palette: NovelPalette): Color = palette.accent.copy(alpha = 0.25f)

/** This text with [range] (when it is this block's) marked as being read. */
internal fun AnnotatedString.withSpoken(range: SpokenRange?, chapterId: String, block: Int, color: Color): AnnotatedString {
    if (range == null || range.chapterId != chapterId || range.block != block) return this
    val end = range.end.coerceAtMost(length)
    if (range.start >= end) return this
    return buildAnnotatedString {
        append(this@withSpoken)
        addStyle(SpanStyle(background = color), range.start, end)
    }
}

/**
 * The read aloud controls above the reader's bottom bar: back / forward a paragraph, play or
 * pause, stop, and its settings. Shows what read aloud has to say (loading, no voice, the end)
 * and, when the reader has scrolled away from the voice, a way back to it.
 */
@Composable
internal fun ReadAloudPanel(
    state: ReadAloudState,
    following: Boolean,
    palette: NovelPalette,
    onFollow: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (val p = state.problem) {
        ReadAloudProblem.NoVoiceForLanguage -> stringResource(R.string.read_aloud_no_voice)
        is ReadAloudProblem.LoadFailed -> stringResource(R.string.read_aloud_load_failed, p.chapterTitle, p.message)
        ReadAloudProblem.Finished -> stringResource(R.string.read_aloud_finished)
        ReadAloudProblem.NoEngine, null -> if (state.status == ReadAloudStatus.Loading) stringResource(R.string.read_aloud_loading) else null
    }
    Column(modifier.fillMaxWidth().background(palette.bar)) {
        if (message != null || !following) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.orEmpty(),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (!following) {
                    TextButton(onClick = onFollow) { Text(stringResource(R.string.read_aloud_follow), color = palette.accent) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ReadAloud::previousParagraph) {
                Icon(Icons.Outlined.FastRewind, stringResource(R.string.cd_read_aloud_previous), tint = palette.text)
            }
            val paused = state.status == ReadAloudStatus.Paused
            IconButton(onClick = ReadAloud::togglePlay) {
                Icon(
                    if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    stringResource(if (paused) R.string.cd_read_aloud_play else R.string.cd_read_aloud_pause),
                    tint = palette.accent,
                )
            }
            IconButton(onClick = ReadAloud::nextParagraph) {
                Icon(Icons.Outlined.FastForward, stringResource(R.string.cd_read_aloud_next), tint = palette.text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.RecordVoiceOver, stringResource(R.string.cd_read_aloud_settings), tint = palette.text)
            }
            IconButton(onClick = ReadAloud::stop) {
                Icon(Icons.Outlined.Stop, stringResource(R.string.cd_read_aloud_stop), tint = palette.text)
            }
        }
    }
}

/** Speed, sleep timer, voice and engine of read aloud, plus a way to Android's TTS settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadAloudSheet(
    state: ReadAloudState,
    settings: ReadAloudSettings,
    onSettings: (ReadAloudSettings) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var speed by remember(settings.speed) { mutableFloatStateOf(settings.speed) }
    // The lists come from the engine once it is ready; a new engine brings new ones.
    val engineKey = ReadAloud.enginePackage
    val voices = remember(engineKey, state.status) { ReadAloud.voices() }
    val engines = remember(engineKey, state.status) { ReadAloud.engines() }
    val language = ReadAloud.textLocale.language
    var allLanguages by rememberSaveable { mutableStateOf(false) }
    val shown = if (allLanguages) voices else voices.filter { it.locale.language == language || it.name == settings.voiceName }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sleep) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
    ) {
        SheetHeader(stringResource(R.string.read_aloud), onClose = onClose)
        Spacer(Modifier.height(8.dp))
        SliderRow(
            label = stringResource(R.string.read_aloud_speed),
            valueText = stringResource(R.string.read_aloud_speed_value, speed),
            value = speed,
            range = ReadAloudSettings.SPEED_RANGE,
            steps = 24,
            onChange = { speed = (it * 10).roundToInt() / 10f },
            onFinished = { onSettings(settings.copy(speed = speed)) },
        )

        Spacer(Modifier.height(10.dp))
        ReadAloudSectionLabel(stringResource(R.string.read_aloud_sleep))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(
                label = stringResource(R.string.read_aloud_sleep_off),
                selected = state.sleep == SleepTimer.Off,
                onClick = { ReadAloud.setSleep(SleepTimer.Off) },
            )
            ChoiceChip(
                label = stringResource(R.string.read_aloud_sleep_chapter),
                selected = state.sleep == SleepTimer.EndOfChapter,
                onClick = { ReadAloud.setSleep(SleepTimer.EndOfChapter) },
            )
            for (m in SleepTimer.MINUTES) {
                ChoiceChip(
                    label = stringResource(R.string.read_aloud_sleep_minutes, m),
                    selected = (state.sleep as? SleepTimer.At)?.minutes == m,
                    onClick = { ReadAloud.setSleep(SleepTimer.minutes(m, System.currentTimeMillis())) },
                )
            }
        }
        state.sleep.remainingMs(now)?.let { left ->
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.read_aloud_sleep_left, ((left + 59_999) / 60_000).toInt()),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        ReadAloudSectionLabel(stringResource(R.string.read_aloud_voice))
        Column(Modifier.selectableGroup()) {
            ChoiceRow(
                title = stringResource(R.string.read_aloud_voice_default),
                subtitle = null,
                selected = settings.voiceName == null,
                onClick = { onSettings(settings.copy(voiceName = null)) },
            )
            for (v in shown) {
                ChoiceRow(
                    title = v.locale.displayName,
                    subtitle = if (v.needsNetwork) "${v.name} · ${stringResource(R.string.read_aloud_voice_network)}" else v.name,
                    selected = settings.voiceName == v.name,
                    onClick = { onSettings(settings.copy(voiceName = v.name)) },
                )
            }
        }
        if (voices.isEmpty() && state.status != ReadAloudStatus.Loading) {
            Text(stringResource(R.string.read_aloud_no_voices), color = AppColors.textSecondary, fontSize = 13.sp)
        }
        SwitchRow(
            label = stringResource(R.string.read_aloud_all_languages),
            checked = allLanguages,
            onCheckedChange = { allLanguages = it },
        )

        if (engines.size > 1) {
            Spacer(Modifier.height(10.dp))
            ReadAloudSectionLabel(stringResource(R.string.read_aloud_engine))
            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    title = stringResource(R.string.read_aloud_engine_default),
                    subtitle = null,
                    selected = settings.engine == null,
                    onClick = { onSettings(settings.copy(engine = null, voiceName = null)) },
                )
                for (e in engines) {
                    ChoiceRow(
                        title = e.label,
                        subtitle = null,
                        selected = settings.engine == e.packageName,
                        // Voice names belong to one engine.
                        onClick = { onSettings(settings.copy(engine = e.packageName, voiceName = null)) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { openTtsSettings(context) }) {
            Text(stringResource(R.string.read_aloud_tts_settings), color = AppColors.accentText)
        }
        Text(stringResource(R.string.read_aloud_tts_settings_hint), color = AppColors.textSecondary, fontSize = 13.sp)
    }
}

/** No TTS engine works: say so, and offer Android's settings to install or turn one on. */
@Composable
internal fun NoEngineDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ConfirmDialog(
        title = stringResource(R.string.read_aloud_no_engine_title),
        text = stringResource(R.string.read_aloud_no_engine),
        confirmLabel = stringResource(R.string.read_aloud_tts_settings),
        dismissLabel = stringResource(R.string.action_close),
        confirmColor = Color.Unspecified,
        onConfirm = { openTtsSettings(context) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ReadAloudSectionLabel(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.textPrimary, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = AppColors.textSecondary, fontSize = 12.sp)
        }
    }
}

/** Android's text-to-speech settings (engines, voice data); the main settings when a phone hides them. */
private fun openTtsSettings(context: Context) {
    val tts = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(tts) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
