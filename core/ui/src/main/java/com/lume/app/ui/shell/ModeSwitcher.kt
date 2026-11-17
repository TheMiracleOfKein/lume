package com.lume.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.source.ContentModule
import com.lume.app.domain.MediaKind
import com.lume.app.ui.components.LocalShellBottomPadding
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion

/** The icon a reading mode shows in the switcher and its empty state. */
fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.Comics -> Icons.Outlined.AutoStories
    MediaKind.Novel -> Icons.AutoMirrored.Outlined.MenuBook
    MediaKind.Video -> Icons.Outlined.Movie
}

/** The mode can be switched only when the build ships more than one. */
fun canSwitchModes(modules: List<ContentModule>): Boolean = modules.map { it.kind }.distinct().size > 1

/**
 * True when [active]'s module is a preview and no loaded source serves it yet: its source-based
 * screens show [ModeComingSoon] instead of an error.
 */
fun showsComingSoon(modules: List<ContentModule>, active: MediaKind, sourceKinds: Collection<MediaKind>): Boolean {
    val module = modules.firstOrNull { it.kind == active } ?: return false
    return module.preview && active !in sourceKinds
}

/** The sources that serve [mode]. */
fun <T> sourcesOfMode(sources: List<T>, mode: MediaKind, kindOf: (T) -> MediaKind): List<T> =
    sources.filter { kindOf(it) == mode }

/**
 * True when an item saved from [sourceId] belongs to [mode]. A source that is no longer loaded
 * (removed pack) counts as comics, the only kind that could have been removed before modes existed.
 */
fun belongsToMode(sourceId: String, kinds: Map<String, MediaKind>, mode: MediaKind): Boolean =
    (kinds[sourceId] ?: MediaKind.Comics) == mode

/**
 * The reading-mode popup, opened by a long press on the logo or from the menu. It sits above the
 * bottom bar (next to the rail on wide screens); a tap outside or Back closes it.
 */
@Composable
internal fun ModeSwitcher(
    visible: Boolean,
    modules: List<ContentModule>,
    active: MediaKind,
    useRail: Boolean,
    onSelect: (MediaKind) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(Motion.short()), exit = fadeOut(Motion.short())) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        onClickLabel = stringResource(R.string.action_close),
                        interactionSource = null,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            val place = if (useRail) {
                Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(LumeInsets.sides.only(WindowInsetsSides.Start))
                    .padding(start = LumeInsets.RailWidth + 8.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = LumeInsets.BottomBarHeight + 8.dp)
            }
            ModeSwitcherPanel(modules, active, onSelect, place)
        }
    }
}

/** The list of modes: icon, name, a "Preview" badge and a check on the active one. */
@Composable
fun ModeSwitcherPanel(
    modules: List<ContentModule>,
    active: MediaKind,
    onSelect: (MediaKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.mode_switcher_title)
    Column(
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .semantics { paneTitle = title }
            .padding(vertical = 8.dp),
    ) {
        Text(
            title,
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            modules.forEach { module ->
                ModeRow(module, selected = module.kind == active, onClick = { onSelect(module.kind) })
            }
        }
    }
}

@Composable
private fun ModeRow(module: ContentModule, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            module.kind.icon(),
            contentDescription = null,
            tint = if (selected) AppColors.accent else AppColors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(stringResource(module.labelRes), color = AppColors.textPrimary, fontSize = 15.sp)
        if (module.preview) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.mode_preview),
                color = AppColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .border(1.dp, AppColors.divider, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

/** Shows [content], or [ModeComingSoon] while the active preview mode has no sources. */
@Composable
internal fun ModeGate(content: @Composable () -> Unit) {
    val container = LocalAppContainer.current
    val active by container.prefs.activeMode.collectAsState()
    val sources by container.sources.sources.collectAsState()
    val module = container.installedModules.firstOrNull { it.kind == active }
    if (module != null && showsComingSoon(container.installedModules, active, sources.map { it.manifest.kind })) {
        ModeComingSoon(module)
    } else {
        content()
    }
}

/** The empty state of a mode that has no sources yet. */
@Composable
fun ModeComingSoon(module: ContentModule, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = LocalShellBottomPadding.current)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(module.kind.icon(), contentDescription = null, tint = AppColors.textSecondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.mode_coming_title, stringResource(module.labelRes)),
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.mode_coming_body),
            color = AppColors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
