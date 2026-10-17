package com.lume.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DrawerValue { Open, Closed }

/**
 * Right-side drawer. The panel follows the finger ([anchoredDraggable]): a fling closes it with
 * its velocity, a short drag springs back, and the scrim fades with the panel's position.
 */
@Composable
fun MainMenu(
    visible: Boolean,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    // Read up here, before the panel exists, so its labels are right on its first frame.
    val container = LocalAppContainer.current
    val darkTheme by container.prefs.darkTheme.collectAsState()
    val sources by container.sources.sources.collectAsState()
    val activeId by container.prefs.activeSourceId.collectAsState()
    val activeName = sources.firstOrNull { it.manifest.id == activeId }?.manifest?.name
        ?: sources.firstOrNull()?.manifest?.name
        ?: stringResource(R.string.none_short)

    val state = remember { AnchoredDraggableState(DrawerValue.Closed) }
    var panelWidth by remember { mutableFloatStateOf(0f) }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentVisible by rememberUpdatedState(visible)

    BackHandler(enabled = visible, onBack = onClose)
    LaunchedEffect(visible, panelWidth) {
        if (panelWidth <= 0f) return@LaunchedEffect
        state.animateTo(
            if (visible) DrawerValue.Open else DrawerValue.Closed,
            if (visible) Motion.long() else Motion.medium(),
        )
    }
    // Flung or dragged shut by the user.
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }
            .drop(1)
            .collect { if (it == DrawerValue.Closed && currentVisible) currentOnClose() }
    }

    val offset = state.offset
    val openFraction = when {
        panelWidth <= 0f || offset.isNaN() -> 0f
        else -> (1f - offset / panelWidth).coerceIn(0f, 1f)
    }
    // Fully closed: nothing composed, nothing to catch touches.
    if (!visible && openFraction <= 0f) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Tap the dimmed area (left of the panel) to close.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = openFraction }
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(enabled = visible, interactionSource = null, indication = null, onClick = onClose),
        )
        MenuPanel(
            darkTheme = darkTheme,
            activeName = activeName,
            onClose = onClose,
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .onSizeChanged { size ->
                    val w = size.width.toFloat()
                    if (w != panelWidth) {
                        panelWidth = w
                        state.updateAnchors(
                            DraggableAnchors {
                                DrawerValue.Open at 0f
                                DrawerValue.Closed at w
                            },
                        )
                    }
                }
                .offset { IntOffset((if (offset.isNaN()) panelWidth else offset).roundToInt(), 0) }
                .graphicsLayer { alpha = if (offset.isNaN()) 0f else 1f }
                .anchoredDraggable(
                    state = state,
                    reverseDirection = false,
                    orientation = Orientation.Horizontal,
                    enabled = visible,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = state,
                        positionalThreshold = { distance -> distance * 0.35f },
                        animationSpec = Motion.medium(),
                    ),
                ),
        )
    }
}

@Composable
private fun MenuPanel(
    darkTheme: Boolean,
    activeName: String,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val prefs = LocalAppContainer.current.prefs
    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 400.dp)
            .fillMaxWidth(0.88f)
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
                    scope.launch { prefs.setDarkTheme(!darkTheme) }
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
