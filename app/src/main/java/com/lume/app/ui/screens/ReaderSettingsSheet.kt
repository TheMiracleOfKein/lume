package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.components.lumeSliderColors
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlin.math.roundToInt

// Reader settings sheet: mode, theme, gaps, zoom and the other reader options.

@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onClose: () -> Unit,
    /** Live preview while a slider moves; not written anywhere yet. */
    onChange: (ReaderSettings) -> Unit,
    /** A finished edit: becomes the new default. */
    onCommit: (ReaderSettings) -> Unit,
    onSaveForTitle: (ReaderSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_title),
                color = AppColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, null, tint = AppColors.textPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reader_mode), color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(
                selected = settings.mode == ReaderMode.Rtl,
                icon = Icons.Outlined.SwipeLeft,
                label = ReaderMode.Rtl.label,
                onClick = { onCommit(settings.copy(mode = ReaderMode.Rtl)) },
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                selected = settings.mode == ReaderMode.Webtoon,
                icon = Icons.Outlined.SwipeVertical,
                label = ReaderMode.Webtoon.label,
                onClick = { onCommit(settings.copy(mode = ReaderMode.Webtoon)) },
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                selected = settings.mode == ReaderMode.Ltr,
                icon = Icons.Outlined.SwipeRight,
                label = ReaderMode.Ltr.label,
                onClick = { onCommit(settings.copy(mode = ReaderMode.Ltr)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.reader_theme), color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = stringResource(ReaderTheme.Light.labelRes),
                selected = settings.theme == ReaderTheme.Light,
                onClick = { onCommit(settings.copy(theme = ReaderTheme.Light)) },
                modifier = Modifier.weight(1f),
            )
            ChoiceChip(
                label = stringResource(ReaderTheme.Dark.labelRes),
                selected = settings.theme == ReaderTheme.Dark,
                onClick = { onCommit(settings.copy(theme = ReaderTheme.Dark)) },
                modifier = Modifier.weight(1f),
            )
            ChoiceChip(
                label = stringResource(ReaderTheme.System.labelRes),
                selected = settings.theme == ReaderTheme.System,
                onClick = { onCommit(settings.copy(theme = ReaderTheme.System)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.reader_gap, settings.gap.toInt()),
            color = AppColors.textSecondary,
            fontSize = 13.sp,
        )
        Slider(
            value = settings.gap,
            onValueChange = { onChange(settings.copy(gap = it)) },
            onValueChangeFinished = { onCommit(settings) },
            valueRange = 0f..32f,
            colors = lumeSliderColors(),
        )
        if (settings.mode == ReaderMode.Webtoon) {
            Text(
                stringResource(R.string.reader_side_padding, settings.sidePaddingPercent),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
            )
            Slider(
                value = settings.sidePaddingPercent.toFloat(),
                onValueChange = { onChange(settings.copy(sidePaddingPercent = it.roundToInt())) },
            onValueChangeFinished = { onCommit(settings) },
                valueRange = 0f..AppPreferences.MAX_SIDE_PADDING.toFloat(),
                steps = AppPreferences.MAX_SIDE_PADDING / 5 - 1,
                colors = lumeSliderColors(),
            )
        }
        Text(
            stringResource(R.string.reader_dim, (settings.dim * 100).roundToInt()),
            color = AppColors.textSecondary,
            fontSize = 13.sp,
        )
        Slider(
            value = settings.dim,
            onValueChange = { onChange(settings.copy(dim = it)) },
            onValueChangeFinished = { onCommit(settings) },
            valueRange = 0f..AppPreferences.MAX_DIM,
            colors = lumeSliderColors(),
        )
        SwitchRow(
            label = stringResource(R.string.reader_brightness_own),
            checked = settings.brightness >= 0f,
            onCheckedChange = { on -> onCommit(settings.copy(brightness = if (on) 0.5f else ReaderSettings.BRIGHTNESS_SYSTEM)) },
        )
        if (settings.brightness >= 0f) {
            Text(
                stringResource(R.string.reader_brightness, (settings.brightness * 100).roundToInt()),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
            )
            Slider(
                value = settings.brightness,
                onValueChange = { onChange(settings.copy(brightness = it)) },
                onValueChangeFinished = { onCommit(settings) },
                valueRange = 0.01f..1f,
                colors = lumeSliderColors(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.reader_color_filter), color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                ReaderSettings.FILTER_NONE to R.string.reader_filter_none,
                ReaderSettings.FILTER_SEPIA to R.string.reader_filter_sepia,
                ReaderSettings.FILTER_GREY to R.string.reader_filter_grey,
                ReaderSettings.FILTER_WARM to R.string.reader_filter_warm,
            ).forEach { (filter, label) ->
                ChoiceChip(
                    label = stringResource(label),
                    selected = settings.colorFilter == filter,
                    onClick = { onCommit(settings.copy(colorFilter = filter)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SwitchRow(
            label = stringResource(R.string.reader_eink),
            hint = stringResource(R.string.reader_eink_hint),
            checked = settings.eInk,
            onCheckedChange = { onCommit(settings.copy(eInk = it)) },
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.reader_orientation), color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ReaderSettings.ORIENTATION_AUTO to R.string.reader_orientation_auto,
                ReaderSettings.ORIENTATION_PORTRAIT to R.string.reader_orientation_portrait,
                ReaderSettings.ORIENTATION_LANDSCAPE to R.string.reader_orientation_landscape,
            ).forEach { (orientation, label) ->
                ChoiceChip(
                    label = stringResource(label),
                    selected = settings.orientation == orientation,
                    onClick = { onCommit(settings.copy(orientation = orientation)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (settings.mode == ReaderMode.Webtoon) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.reader_auto_scroll_speed, settings.autoScrollSpeed),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
            )
            Slider(
                value = settings.autoScrollSpeed.toFloat(),
                onValueChange = { onChange(settings.copy(autoScrollSpeed = it.roundToInt())) },
                onValueChangeFinished = { onCommit(settings) },
                valueRange = 1f..ReaderSettings.MAX_AUTO_SCROLL.toFloat(),
                steps = ReaderSettings.MAX_AUTO_SCROLL - 2,
                colors = lumeSliderColors(),
            )
            SwitchRow(
                label = stringResource(R.string.reader_webtoon_tap_zones),
                hint = stringResource(R.string.reader_webtoon_tap_zones_hint),
                checked = settings.webtoonTapZones,
                onCheckedChange = { onCommit(settings.copy(webtoonTapZones = it)) },
            )
        }
        SwitchRow(
            label = stringResource(R.string.reader_immersive),
            hint = stringResource(R.string.reader_immersive_hint),
            checked = settings.immersive,
            onCheckedChange = { onCommit(settings.copy(immersive = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.reader_keep_screen_on),
            checked = settings.keepScreenOn,
            onCheckedChange = { onCommit(settings.copy(keepScreenOn = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.reader_double_tap_zoom),
            checked = settings.doubleTapZoom,
            onCheckedChange = { onCommit(settings.copy(doubleTapZoom = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.reader_hide_page_number),
            checked = settings.hidePageNumber,
            onCheckedChange = { onCommit(settings.copy(hidePageNumber = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.reader_seamless),
            checked = settings.seamlessReading,
            onCheckedChange = { onCommit(settings.copy(seamlessReading = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.reader_volume_keys),
            checked = settings.volumeKeys,
            onCheckedChange = { onCommit(settings.copy(volumeKeys = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.reader_tap_zones),
            checked = settings.tapZones,
            onCheckedChange = { onCommit(settings.copy(tapZones = it)) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.reader_save_for_title),
            color = AppColors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSaveForTitle(settings) }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) AppColors.cardHigh else AppColors.card,
        Motion.short(),
        label = "modeBackground",
    )
    val border by animateColorAsState(
        if (selected) AppColors.accent else Color.Transparent,
        Motion.short(),
        label = "modeBorder",
    )
    Column(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = AppColors.textPrimary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) AppColors.accent else AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
