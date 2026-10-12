package com.lume.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion

/* Shared settings controls: one look for the Settings screen and the reader sheet. */

@Composable
fun lumeSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = AppColors.accent,
    checkedBorderColor = AppColors.accent,
    uncheckedThumbColor = AppColors.textSecondary,
    uncheckedTrackColor = AppColors.cardHigh,
    uncheckedBorderColor = AppColors.cardHigh,
)

@Composable
fun lumeSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = AppColors.accent,
    activeTrackColor = AppColors.accent,
    activeTickColor = Color.Black.copy(alpha = 0.35f),
    inactiveTrackColor = AppColors.cardHigh,
    inactiveTickColor = AppColors.textTertiary,
)

/** Section title used to group settings. */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        color = AppColors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** A whole-row toggle: tapping the label flips the switch too. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
            if (!hint.isNullOrBlank()) {
                Text(hint, color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = lumeSwitchColors())
    }
}

/** Label with live value above a slider. */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(label, color = AppColors.textSecondary, fontSize = 13.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = lumeSliderColors(),
        )
    }
}

/** Selectable chip / segment with animated selection (themes, intervals, languages…). */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) AppColors.accentMuted else AppColors.card,
        Motion.short(),
        label = "chipBackground",
    )
    val border by animateColorAsState(
        if (selected) AppColors.accent else Color.Transparent,
        Motion.short(),
        label = "chipBorder",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) AppColors.textPrimary else AppColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Text tab with an animated underline; used for Catalog tabs and Search modes. */
@Composable
fun SegmentTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        if (selected) AppColors.accent else AppColors.textSecondary,
        Motion.short(),
        label = "tabColor",
    )
    val indicator by animateDpAsState(if (selected) 2.dp else 0.dp, Motion.short(), label = "tabIndicator")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .height(indicator)
                .fillMaxWidth(0.4f)
                .clip(RoundedCornerShape(1.dp))
                .background(color),
        )
    }
}
