package com.lume.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MainMenu(
    visible: Boolean,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    BackHandler(enabled = visible, onBack = onClose)
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = Motion.scrimEnter, exit = Motion.scrimExit) {
            // Tap dimmed area (left of the panel) to close.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.54f))
                    .clickable(onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = Motion.drawerEnterFromEnd,
            exit = Motion.drawerExitToEnd,
        ) {
            MenuPanel(onClose = onClose, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun MenuPanel(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val darkTheme by LumeApp.instance.prefs.darkTheme.collectAsState(initial = true)
    val sources by LumeApp.instance.sources.sources.collectAsState()
    val activeId by LumeApp.instance.sources.activeSourceId.collectAsState(initial = null)
    val activeName = sources.firstOrNull { it.manifest.id == activeId }?.manifest?.name
        ?: sources.firstOrNull()?.manifest?.name
        ?: stringResource(R.string.none_short)

    // Animatable so a short swipe springs back smoothly instead of snapping.
    val dragOffset = remember { Animatable(0f) }
    val closeThresholdPx = with(density) { 72.dp.toPx() }

    Box {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.88f)
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragCancel = { scope.launch { dragOffset.animateTo(0f, Motion.medium()) } },
                        onDragEnd = {
                            // Swipe toward the right edge dismisses a right-side drawer.
                            if (dragOffset.value >= closeThresholdPx) onClose()
                            else scope.launch { dragOffset.animateTo(0f, Motion.medium()) }
                        },
                        onHorizontalDrag = { change, amount ->
                            if (amount > 0f || dragOffset.value > 0f) {
                                change.consume()
                                scope.launch { dragOffset.snapTo((dragOffset.value + amount).coerceAtLeast(0f)) }
                            }
                        },
                    )
                }
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(AppColors.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopChip(
                    icon = if (darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    label = stringResource(R.string.menu_theme),
                    onClick = {
                        scope.launch { LumeApp.instance.prefs.setDarkTheme(!darkTheme) }
                    },
                )
                Spacer(Modifier.weight(1f))
                TopChip(
                    icon = Icons.Outlined.Search,
                    label = stringResource(R.string.menu_search),
                    onClick = {
                        onClose()
                        onNavigate(Routes.Search)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.card),
            ) {
                MenuTile(
                    icon = Icons.Outlined.Public,
                    title = stringResource(R.string.menu_source),
                    trailingText = activeName,
                    onClick = {
                        onClose()
                        onNavigate(Routes.Sources)
                    },
                )
                MenuTile(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.menu_settings),
                    onClick = {
                        onClose()
                        onNavigate(Routes.Settings)
                    },
                )
                MenuTile(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.menu_downloads),
                    onClick = {
                        onClose()
                        onNavigate(Routes.Downloads)
                    },
                )
                MenuTile(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.menu_history),
                    onClick = {
                        onClose()
                        onNavigate(Routes.History)
                    },
                )
                MenuTile(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = stringResource(R.string.menu_library),
                    onClick = {
                        onClose()
                        onNavigate(Routes.Library)
                    },
                )
                MenuTile(
                    icon = Icons.Outlined.Home,
                    title = stringResource(R.string.menu_home),
                    onClick = {
                        onClose()
                        onNavigate(Routes.Home)
                    },
                )
                MenuTile(
                    icon = Icons.Outlined.GridView,
                    title = stringResource(R.string.menu_catalog),
                    onClick = {
                        onClose()
                        onNavigate(Routes.Catalog)
                    },
                )
            }
        }
    }
}

@Composable
private fun TopChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = AppColors.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.textSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .widthIn(max = 140.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForwardIos,
            null,
            tint = AppColors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}
