package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.prefs.NovelFont
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.data.prefs.NovelTheme
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.ResetReaderSettingsButton
import com.lume.app.ui.components.SheetHeader
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.components.SliderRow
import com.lume.app.ui.theme.AppColors
import kotlin.math.roundToInt

// Reader settings sheet: text layout and theme, with a live preview in the reader's own colours.

@Composable
internal fun NovelReaderSettingsSheet(
    settings: NovelReaderSettings,
    onClose: () -> Unit,
    /** Live preview while a slider moves; not written anywhere yet. */
    onChange: (NovelReaderSettings) -> Unit,
    /** A finished edit: becomes the new default. */
    onCommit: (NovelReaderSettings) -> Unit,
    onReset: () -> Unit,
    saveForTitle: Boolean,
    onSaveForTitleChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
    ) {
        SheetHeader(stringResource(R.string.settings_title), onClose = onClose)
        Spacer(Modifier.height(8.dp))
        NovelReaderSettingsControls(settings, onChange, onCommit)
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = stringResource(R.string.reader_save_for_title),
            hint = stringResource(R.string.novel_save_for_title_hint),
            checked = saveForTitle,
            onCheckedChange = onSaveForTitleChange,
        )
        ResetReaderSettingsButton(onConfirm = onReset)
    }
}

/**
 * The preview and every control of the text reader's settings: the reader's sheet and
 * Settings → Reader (Ranobe mode) both show these. [item] wraps each control; Settings uses it
 * to scroll to and highlight the one a search opened the page for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelReaderSettingsControls(
    settings: NovelReaderSettings,
    onChange: (NovelReaderSettings) -> Unit,
    onCommit: (NovelReaderSettings) -> Unit,
    item: @Composable (id: String, content: @Composable () -> Unit) -> Unit = { _, content -> content() },
) {
    // A tap on a slider fires "change" and "finished" in the same frame, before [settings]
    // catches up; "finished" saves the latest edit kept here, not the value from before it.
    val latest = remember { arrayOf(settings) }
    latest[0] = settings
    val change: (NovelReaderSettings) -> Unit = { latest[0] = it; onChange(it) }
    val commitLatest: () -> Unit = { onCommit(latest[0]) }
    val palette = novelPalette(settings.theme)
    Column(Modifier.fillMaxWidth()) {
        // Preview: the same text style the reader uses.
        Text(
            text = stringResource(R.string.novel_preview_text),
            style = TextStyle(
                color = palette.text,
                fontFamily = settings.font.family(),
                fontSize = settings.fontSize.sp,
                lineHeight = (settings.fontSize * settings.lineHeight).sp,
                textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
                textIndent = if (settings.paragraphIndent > 0) TextIndent(firstLine = settings.paragraphIndent.em) else null,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.background)
                .padding(horizontal = settings.sideMargin.dp.coerceAtMost(24.dp), vertical = 14.dp),
        )
        Spacer(Modifier.height(14.dp))

        item("novel_font_size") {
            SliderRow(
                label = stringResource(R.string.novel_font_size),
                valueText = "${settings.fontSize} sp",
                value = settings.fontSize.toFloat(),
                range = NovelReaderSettings.FONT_SIZE_RANGE.first.toFloat()..NovelReaderSettings.FONT_SIZE_RANGE.last.toFloat(),
                steps = NovelReaderSettings.FONT_SIZE_RANGE.last - NovelReaderSettings.FONT_SIZE_RANGE.first - 1,
                onChange = { change(latest[0].copy(fontSize = it.roundToInt())) },
                onFinished = commitLatest,
            )
        }
        item("novel_line_height") {
            SliderRow(
                label = stringResource(R.string.novel_line_height),
                valueText = "%.2f".format(settings.lineHeight),
                value = settings.lineHeight,
                range = NovelReaderSettings.LINE_HEIGHT_RANGE,
                steps = 15,
                onChange = { change(latest[0].copy(lineHeight = it)) },
                onFinished = commitLatest,
            )
        }
        item("novel_side_margin") {
            SliderRow(
                label = stringResource(R.string.novel_side_margin),
                valueText = "${settings.sideMargin} dp",
                value = settings.sideMargin.toFloat(),
                range = NovelReaderSettings.SIDE_MARGIN_RANGE.first.toFloat()..NovelReaderSettings.SIDE_MARGIN_RANGE.last.toFloat(),
                steps = (NovelReaderSettings.SIDE_MARGIN_RANGE.last - NovelReaderSettings.SIDE_MARGIN_RANGE.first) / 4 - 1,
                onChange = { change(latest[0].copy(sideMargin = it.roundToInt())) },
                onFinished = commitLatest,
            )
        }
        item("novel_paragraph_spacing") {
            SliderRow(
                label = stringResource(R.string.novel_paragraph_spacing),
                valueText = "${settings.paragraphSpacing} dp",
                value = settings.paragraphSpacing.toFloat(),
                range = NovelReaderSettings.PARAGRAPH_SPACING_RANGE.first.toFloat()..NovelReaderSettings.PARAGRAPH_SPACING_RANGE.last.toFloat(),
                steps = NovelReaderSettings.PARAGRAPH_SPACING_RANGE.last / 2 - 1,
                onChange = { change(latest[0].copy(paragraphSpacing = it.roundToInt())) },
                onFinished = commitLatest,
            )
        }

        Spacer(Modifier.height(6.dp))
        item("novel_font") {
            Text(stringResource(R.string.novel_font), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    label = stringResource(R.string.novel_font_serif),
                    selected = settings.font == NovelFont.Serif,
                    onClick = { onCommit(settings.copy(font = NovelFont.Serif)) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceChip(
                    label = stringResource(R.string.novel_font_sans),
                    selected = settings.font == NovelFont.Sans,
                    onClick = { onCommit(settings.copy(font = NovelFont.Sans)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        item("novel_justify") {
            SwitchRow(
                label = stringResource(R.string.novel_justify),
                checked = settings.justify,
                onCheckedChange = { onCommit(settings.copy(justify = it)) },
            )
        }
        item("novel_paragraph_indent") {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.novel_paragraph_indent), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (indent in NovelReaderSettings.PARAGRAPH_INDENT_RANGE) {
                    ChoiceChip(
                        label = if (indent == 0) stringResource(R.string.novel_paragraph_indent_off) else "$indent em",
                        selected = settings.paragraphIndent == indent,
                        onClick = { onCommit(settings.copy(paragraphIndent = indent)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        item("novel_show_chapter_title") {
            SwitchRow(
                label = stringResource(R.string.novel_show_chapter_title),
                hint = stringResource(R.string.novel_show_chapter_title_summary),
                checked = settings.showChapterTitle,
                onCheckedChange = { onCommit(settings.copy(showChapterTitle = it)) },
            )
        }
        item("novel_paged") {
            SwitchRow(
                label = stringResource(R.string.novel_paged),
                hint = stringResource(R.string.novel_paged_summary),
                checked = settings.paged,
                onCheckedChange = { onCommit(settings.copy(paged = it)) },
            )
        }
        item("novel_auto_scroll_speed") {
            SliderRow(
                label = stringResource(R.string.novel_auto_scroll_speed),
                valueText = "${settings.autoScrollSpeed}",
                value = settings.autoScrollSpeed.toFloat(),
                range = NovelReaderSettings.AUTO_SCROLL_SPEED_RANGE.first.toFloat()..NovelReaderSettings.AUTO_SCROLL_SPEED_RANGE.last.toFloat(),
                steps = NovelReaderSettings.AUTO_SCROLL_SPEED_RANGE.last - NovelReaderSettings.AUTO_SCROLL_SPEED_RANGE.first - 1,
                onChange = { change(latest[0].copy(autoScrollSpeed = it.roundToInt())) },
                onFinished = commitLatest,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.novel_controls), color = AppColors.textSecondary, fontSize = 13.sp)
        item("novel_brightness_swipe") {
            SwitchRow(
                label = stringResource(R.string.novel_brightness_swipe),
                hint = stringResource(R.string.novel_brightness_swipe_summary),
                checked = settings.brightnessSwipe,
                onCheckedChange = { onCommit(settings.copy(brightnessSwipe = it)) },
            )
            if (settings.brightness >= 0f) {
                TextButton(onClick = { onCommit(settings.copy(brightness = NovelReaderSettings.BRIGHTNESS_SYSTEM)) }) {
                    Text(
                        stringResource(R.string.novel_brightness_system, (settings.brightness * 100).roundToInt()),
                        color = AppColors.accent,
                    )
                }
            }
        }
        item("novel_volume_keys") {
            SwitchRow(
                label = stringResource(R.string.novel_volume_keys),
                hint = stringResource(R.string.novel_volume_keys_summary),
                checked = settings.volumeKeys,
                onCheckedChange = { onCommit(settings.copy(volumeKeys = it)) },
            )
        }
        item("novel_status_line") {
            SwitchRow(
                label = stringResource(R.string.novel_status_line),
                hint = stringResource(R.string.novel_status_line_summary),
                checked = settings.statusLine,
                onCheckedChange = { onCommit(settings.copy(statusLine = it)) },
            )
        }

        Spacer(Modifier.height(10.dp))
        item("novel_theme") {
            Text(stringResource(R.string.novel_theme), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (theme in NovelTheme.entries) {
                    ChoiceChip(
                        label = stringResource(themeLabel(theme)),
                        selected = settings.theme == theme,
                        onClick = { onCommit(settings.copy(theme = theme)) },
                    )
                }
            }
        }
    }
}

private fun themeLabel(theme: NovelTheme): Int = when (theme) {
    NovelTheme.App -> R.string.novel_theme_app
    NovelTheme.Light -> R.string.novel_theme_light
    NovelTheme.Sepia -> R.string.novel_theme_sepia
    NovelTheme.Dark -> R.string.novel_theme_dark
    NovelTheme.Black -> R.string.novel_theme_black
}
