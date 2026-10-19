package com.lume.app.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.ui.components.SettingsSection
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.delay

/** The setting a search result opened this page for; its row is scrolled to and highlighted. */
val LocalSettingsFocus = staticCompositionLocalOf<String?> { null }

/** Rounded surface that groups related settings rows. */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { content() }
}

/**
 * Wraps the rows of one setting from [SettingsCatalog]. When a search opened the page for [id],
 * the rows scroll into view and flash once.
 */
@Composable
fun SettingItem(id: String, content: @Composable () -> Unit) {
    val focused = LocalSettingsFocus.current == id
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(Color.Transparent) }
    val highlight = AppColors.accentMuted
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        // Let the page lay out (and the enter transition settle) before scrolling.
        delay(FOCUS_DELAY_MS)
        requester.bringIntoView()
        glow.animateTo(highlight, tween(FLASH_IN_MS))
        delay(FLASH_HOLD_MS)
        glow.animateTo(Color.Transparent, tween(FLASH_OUT_MS))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .clip(RoundedCornerShape(12.dp))
            .background(glow.value),
    ) { content() }
}

private const val FOCUS_DELAY_MS = 350L
private const val FLASH_IN_MS = 250
private const val FLASH_HOLD_MS = 900L
private const val FLASH_OUT_MS = 900

/** Heading between groups on a settings page. */
@Composable
fun SettingsSectionTitle(@StringRes title: Int) {
    SettingsSection(stringResource(title))
}

/** Small grey label above chips and other controls that are not a single row. */
@Composable
fun SettingsLabel(text: String, top: Int = 8) {
    Text(
        text,
        color = AppColors.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = top.dp, bottom = 8.dp),
    )
}

/** Explanation under a control that has no row of its own. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = AppColors.textTertiary,
        fontSize = 12.sp,
        modifier = modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
    )
}

/** A row that opens a screen or runs an action, with an optional explanation. */
@Composable
fun SettingsLink(label: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (hint == null) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
            if (!hint.isNullOrBlank()) {
                Text(hint, color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.textSecondary,
        )
    }
}

@Composable
fun StorageRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.accent, unselectedColor = AppColors.textSecondary),
        )
        Column {
            Text(label, color = AppColors.textPrimary, fontSize = 15.sp)
            Text(detail, color = AppColors.textTertiary, fontSize = 12.sp)
        }
    }
}

/** Result of the last action on the page (backup, cache cleared …). */
@Composable
fun SettingsStatus(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
        exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
    ) {
        Text(
            message.orEmpty(),
            color = AppColors.accent,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
