package com.lume.app.ui.shell

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.VerticalDivider
import com.lume.app.ui.components.LocalUseRail
import com.lume.app.ui.screens.AboutScreen
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import com.lume.app.domain.MediaKind
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.filterNotNull
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.ui.components.CloudflareChallengeOverlay
import com.lume.app.ui.components.LocalNavExiting
import com.lume.app.ui.components.LocalShellBottomPadding
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.LumeLogo
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.CatalogScreen
import com.lume.app.ui.screens.CategoriesScreen
import com.lume.app.ui.screens.ChaptersScreen
import com.lume.app.ui.screens.DownloadsScreen
import com.lume.app.ui.screens.StatsScreen
import com.lume.app.ui.screens.HistoryScreen
import com.lume.app.ui.screens.HomeScreen
import com.lume.app.ui.screens.LibraryScreen
import com.lume.app.ui.screens.ReadingListScreen
import com.lume.app.ui.screens.SearchScreen
import com.lume.app.ui.screens.settings.SettingsPageScreen
import com.lume.app.ui.screens.settings.SettingsScreen
import com.lume.app.ui.screens.SourcesScreen
import com.lume.app.ui.screens.TitleScreen
import com.lume.app.ui.screens.UpdatesScreen
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import java.net.URI

private val MainTabRoutes = setOf(
    Routes.Library,
    Routes.Catalog,
    Routes.Home,
    Routes.Updates,
)

@Composable
fun LumeRoot(contentScreens: List<ContentScreens> = emptyList()) {
    val nav = rememberNavController()
    var challengeUri by remember { mutableStateOf<URI?>(null) }
    val http = LocalAppContainer.current.http

    LaunchedEffect(Unit) {
        http.challengeRequests.collect { challengeUri = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppShell(nav, contentScreens)
        com.lume.app.ui.components.UpdatePrompt()
        challengeUri?.let { uri ->
            CloudflareChallengeOverlay(
                uri = uri,
                onDismiss = { challengeUri = null },
                onSolved = { challengeUri = null },
            )
        }
    }
}

@Composable
fun AppShell(nav: NavHostController, contentScreens: List<ContentScreens> = emptyList()) {
    var menuOpen by remember { mutableStateOf(false) }
    // Reading modes: only builds with more than one module can switch (long press on the logo).
    val container = LocalAppContainer.current
    val modules = container.installedModules
    val activeMode by container.prefs.activeMode.collectAsState()
    var modesOpen by remember { mutableStateOf(false) }
    val openModes: (() -> Unit)? = if (canSwitchModes(modules)) ({ modesOpen = true }) else null
    val scope = rememberCoroutineScope()
    val switchMode: (MediaKind) -> Unit = { kind ->
        modesOpen = false
        if (kind != activeMode) {
            scope.launch { container.prefs.setActiveMode(kind) }
            nav.resetToHome()
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route in MainTabRoutes
    // Tablets and wide windows: a navigation rail on the left instead of the bottom bar.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val useRail = windowWidth.value >= RAIL_MIN_WIDTH_DP

    // Only the window on screen takes a route. Sharing a file or link to Lume can open a second
    // Lume window; the one left in the background must not take the route away from it.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AppLinks.pendingRoute.filterNotNull().collect { target ->
                nav.openLink(target)
                AppLinks.consumed()
            }
        }
    }

    CompositionLocalProvider(LocalUseRail provides useRail) {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
    // While the menu is open, TalkBack and other accessibility services see only the menu: the
    // screen and the bars behind the scrim leave the semantics tree (they come back on close).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (menuOpen || modesOpen) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        // No shell padding at all: each destination pads itself (see lumeComposable), so the
        // bottom bar sliding in or out never re-lays-out a screen mid-transition.
        NavHost(
            navController = nav,
            startDestination = Routes.Home,
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background),
            // Tabs fade through; drilling into a title / reader slides. Going back (button, arrow
            // or gesture) is instant on every screen: no pop animation for a gesture to scrub.
            enterTransition = { if (isTabSwitch()) Motion.tabEnter else Motion.screenEnter },
            exitTransition = { if (isTabSwitch()) Motion.tabExit else Motion.screenExit },
            popEnterTransition = { Motion.screenPopEnter },
            popExitTransition = { Motion.screenPopExit },
        ) {
            lumeComposable(Routes.Home) { ModeGate { HomeScreen(nav) } }
            lumeComposable(Routes.Catalog) { ModeGate { CatalogScreen(nav) } }
            lumeComposable(Routes.Library) { LibraryScreen(nav) }
            lumeComposable(Routes.Updates) { ModeGate { UpdatesScreen(nav) } }
            lumeComposable(Routes.History) { HistoryScreen(nav) }
            lumeComposable(Routes.Search) { ModeGate { SearchScreen(nav) } }
            lumeComposable(
                route = Routes.SearchAll,
                arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                ModeGate { SearchScreen(nav, globalQuery = entry.arguments!!.getString("q")) }
            }
            lumeComposable(Routes.Sources) { SourcesScreen(nav) }
            lumeComposable(Routes.Settings) { SettingsScreen(nav) }
            lumeComposable(
                route = Routes.SettingsPage,
                arguments = listOf(
                    navArgument("page") { type = NavType.StringType },
                    navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                SettingsPageScreen(
                    nav = nav,
                    pageKey = entry.arguments!!.getString("page")!!,
                    focus = entry.arguments!!.getString("focus"),
                )
            }
            lumeComposable(Routes.Downloads) { DownloadsScreen(nav) }
            lumeComposable(Routes.NearbySend) { com.lume.app.ui.screens.NearbySendScreen(nav) }
            lumeComposable(Routes.NearbyReceive) { com.lume.app.ui.screens.NearbyReceiveScreen(nav) }
            lumeComposable(Routes.Stats) { StatsScreen(nav) }
            lumeComposable(Routes.Categories) { CategoriesScreen(nav) }
            lumeComposable(Routes.About) { AboutScreen(nav) }
            lumeComposable(Routes.ErrorLog) { com.lume.app.ui.screens.ErrorLogScreen(nav) }
            lumeComposable(Routes.ReadingList) { ReadingListScreen(nav) }
            lumeComposable(
                route = Routes.Browse,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("genre") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                CatalogScreen(
                    nav,
                    browseSourceId = entry.arguments!!.getString("sourceId")!!,
                    genre = entry.arguments!!.getString("genre"),
                )
            }
            lumeComposable(
                route = Routes.Title,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("titleId") { type = NavType.StringType },
                ),
            ) { entry ->
                TitleScreen(
                    nav = nav,
                    sourceId = entry.arguments!!.getString("sourceId")!!,
                    titleId = entry.arguments!!.getString("titleId")!!,
                )
            }
            lumeComposable(
                route = Routes.Chapters,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("titleId") { type = NavType.StringType },
                ),
            ) { entry ->
                ChaptersScreen(
                    nav = nav,
                    sourceId = entry.arguments!!.getString("sourceId")!!,
                    titleId = entry.arguments!!.getString("titleId")!!,
                )
            }
            // Readers come from the installed content modules.
            val graph = ShellGraph(this)
            contentScreens.forEach { it.register(graph, nav) }
        }

        val onSelect: (String?) -> Unit = { target ->
            if (target == null) {
                menuOpen = true
            } else {
                nav.navigateToTab(target)
            }
        }
        AnimatedVisibility(
            visible = showBottomBar && !useRail,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = Motion.barEnterFromBottom,
            exit = Motion.barExitToBottom,
        ) {
            BottomNavBar(currentRoute = route, onSelect = onSelect, onHomeLongClick = openModes)
        }
        AnimatedVisibility(
            visible = showBottomBar && useRail,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(Motion.medium()) { -it } + fadeIn(Motion.medium()),
            exit = slideOutHorizontally(Motion.medium()) { -it } + fadeOut(Motion.short()),
        ) {
            NavRail(currentRoute = route, onSelect = onSelect, onHomeLongClick = openModes)
        }
    }

        MainMenu(
            visible = menuOpen,
            onClose = { menuOpen = false },
            onNavigate = { target ->
                menuOpen = false
                when (target) {
                    in MainTabRoutes -> nav.navigateToTab(target)
                    else -> nav.navigate(target) { launchSingleTop = true }
                }
            },
            onOpenModes = openModes?.let { open -> { menuOpen = false; open() } },
        )
        ModeSwitcher(
            visible = modesOpen,
            modules = modules,
            active = activeMode,
            useRail = useRail,
            onSelect = switchMode,
            onDismiss = { modesOpen = false },
        )
    }
    }
}

private const val RAIL_MIN_WIDTH_DP = 600

/**
 * A route from a notification, widget or shared link. Tabs switch as usual; anything else is
 * pushed on top of what the user has open, so Back returns to where they were. A reader link
 * replaces an open reader instead of stacking a second one.
 */
private fun NavHostController.openLink(route: String) {
    when {
        route in MainTabRoutes -> navigateToTab(route)
        route.startsWith("read/") -> navigate(route) {
            popUpTo(Routes.Reader) { inclusive = true }
        }
        else -> navigate(route) { launchSingleTop = true }
    }
}

/** After a mode switch: Home of the new mode, with no screen of the old mode to go back to. */
private fun NavHostController.resetToHome() {
    navigate(Routes.Home) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in MainTabRoutes && targetState.destination.route in MainTabRoutes

internal fun NavGraphBuilder.lumeComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val rail = LocalUseRail.current
        val isTab = route in MainTabRoutes
        val bottomPadding = when {
            !isTab -> 0.dp
            rail -> navBars
            else -> LumeInsets.BottomBarHeight + navBars
        }
        // Flips as soon as this screen starts leaving (also while a back gesture is in progress),
        // so the reader can bring the system bars back before the next screen is visible.
        val exiting = transition.targetState == EnterExitState.PostExit
        CompositionLocalProvider(
            LocalShellBottomPadding provides bottomPadding,
            LocalNavExiting provides exiting,
        ) {
            if (isTab && rail) {
                // Clear the rail (plus any cutout or side bar next to it) and the opposite side inset.
                val dir = LocalLayoutDirection.current
                val sides = LumeInsets.sides.asPaddingValues()
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = LumeInsets.RailWidth + if (dir == LayoutDirection.Ltr) sides.calculateLeftPadding(dir) else sides.calculateRightPadding(dir),
                            end = if (dir == LayoutDirection.Ltr) sides.calculateRightPadding(dir) else sides.calculateLeftPadding(dir),
                        ),
                ) { content(entry) }
            } else {
                content(entry)
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onSelect: (String?) -> Unit,
    onHomeLongClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.navBar)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = AppColors.divider, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSlot(
                selected = currentRoute == Routes.Library,
                label = stringResource(R.string.nav_library),
                icon = Icons.Outlined.Bookmark,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Routes.Library) },
            )
            NavSlot(
                selected = currentRoute == Routes.Catalog,
                label = stringResource(R.string.nav_catalog),
                icon = Icons.Outlined.GridView,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Routes.Catalog) },
            )
            HomeSlot(
                selected = currentRoute == Routes.Home,
                onClick = { onSelect(Routes.Home) },
                onLongClick = onHomeLongClick,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            NavSlot(
                selected = currentRoute == Routes.Updates,
                label = stringResource(R.string.nav_updates),
                icon = Icons.Outlined.NewReleases,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Routes.Updates) },
            )
            NavSlot(
                selected = false,
                label = stringResource(R.string.nav_menu),
                icon = Icons.Outlined.Menu,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(null) },
            )
        }
    }
}

/** The bottom bar's destinations, stacked on the left for wide screens. */
@Composable
private fun NavRail(currentRoute: String?, onSelect: (String?) -> Unit, onHomeLongClick: (() -> Unit)?) {
    Row(Modifier.fillMaxHeight()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .background(AppColors.navBar)
                .windowInsetsPadding(LumeInsets.sides.only(WindowInsetsSides.Start))
                .width(LumeInsets.RailWidth - 1.dp)
                .windowInsetsPadding(
                    WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Short landscape screens: smaller slots, and the column scrolls if it still doesn't fit.
            val slotHeight = if (maxHeight < 480.dp) 56.dp else 72.dp
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val slot = Modifier.fillMaxWidth().height(slotHeight)
                HomeSlot(currentRoute == Routes.Home, { onSelect(Routes.Home) }, slot, onHomeLongClick)
                NavSlot(currentRoute == Routes.Library, stringResource(R.string.nav_library), Icons.Outlined.Bookmark, { onSelect(Routes.Library) }, slot)
                NavSlot(currentRoute == Routes.Catalog, stringResource(R.string.nav_catalog), Icons.Outlined.GridView, { onSelect(Routes.Catalog) }, slot)
                NavSlot(currentRoute == Routes.Updates, stringResource(R.string.nav_updates), Icons.Outlined.NewReleases, { onSelect(Routes.Updates) }, slot)
                NavSlot(false, stringResource(R.string.nav_menu), Icons.Outlined.Menu, { onSelect(null) }, slot)
            }
        }
        VerticalDivider(color = AppColors.divider, thickness = 1.dp)
    }
}

@Composable
private fun NavSlot(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        if (selected) AppColors.textPrimary else AppColors.textSecondary,
        Motion.short(),
        label = "navSlot",
    )
    val pill by animateColorAsState(
        if (selected) AppColors.selection else Color.Transparent,
        Motion.short(),
        label = "navPill",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .background(pill, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(1.dp))
        Text(label, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The centre Home destination: the app logo, announced as "Home". With [onLongClick] (builds with
 * more than one reading mode) a long press opens the mode switcher, with haptic feedback and a
 * TalkBack action label.
 */
@Composable
private fun HomeSlot(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val label = stringResource(R.string.nav_home)
    val haptics = LocalHapticFeedback.current
    val press = if (onLongClick == null) {
        Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    } else {
        Modifier
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.mode_switch_action),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics { this.selected = selected }
    }
    Box(
        modifier = modifier
            .then(press)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LumeLogo(selected = selected, size = 30.dp, contentDescription = null, ringed = true)
    }
}
