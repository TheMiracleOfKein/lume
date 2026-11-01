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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.lume.app.ui.nav.BackGestureCard
import com.lume.app.ui.nav.LocalBackGestureActive
import com.lume.app.ui.nav.rememberBackGestureActive
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.DisposableEffect
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.CatalogScreen
import com.lume.app.ui.screens.CategoriesScreen
import com.lume.app.ui.screens.ChaptersScreen
import com.lume.app.ui.screens.DownloadsScreen
import com.lume.app.ui.screens.HistoryScreen
import com.lume.app.ui.screens.HomeScreen
import com.lume.app.ui.screens.LibraryScreen
import com.lume.app.ui.screens.ReaderScreen
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
fun LumeRoot() {
    val nav = rememberNavController()
    var challengeUri by remember { mutableStateOf<URI?>(null) }
    val http = LocalAppContainer.current.http

    LaunchedEffect(Unit) {
        http.challengeRequests.collect { challengeUri = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppShell(nav)
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
fun AppShell(nav: NavHostController) {
    var menuOpen by remember { mutableStateOf(false) }
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
                nav.navigateToTab(target)
                AppLinks.consumed()
            }
        }
    }

    // Entries leaving by a pop (back button, back arrow or a committed gesture), recorded by the
    // pop transition itself; a forward exit takes the entry out again (e.g. after a cancelled gesture).
    val popping = remember { mutableStateMapOf<String, Unit>() }

    CompositionLocalProvider(
        LocalUseRail provides useRail,
        LocalBackGestureActive provides rememberBackGestureActive(),
        LocalPoppingIds provides popping,
    ) {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
        // No shell padding at all: each destination pads itself (see lumeComposable), so the
        // bottom bar sliding in or out never re-lays-out a screen mid-transition.
        NavHost(
            navController = nav,
            startDestination = Routes.Home,
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background),
            // Tabs fade through; drilling into a title / reader slides. Same specs everywhere.
            enterTransition = { if (isTabSwitch()) Motion.tabEnter else Motion.screenEnter },
            exitTransition = {
                popping.remove(initialState.id)
                if (isTabSwitch()) Motion.tabExit else Motion.screenExit
            },
            popEnterTransition = { if (isTabSwitch()) Motion.tabEnter else Motion.screenPopEnter },
            popExitTransition = {
                popping[initialState.id] = Unit
                if (isTabSwitch()) Motion.tabExit else Motion.screenPopExit
            },
        ) {
            lumeComposable(Routes.Home) { HomeScreen(nav) }
            lumeComposable(Routes.Catalog) { CatalogScreen(nav) }
            lumeComposable(Routes.Library) { LibraryScreen(nav) }
            lumeComposable(Routes.Updates) { UpdatesScreen(nav) }
            lumeComposable(Routes.History) { HistoryScreen(nav) }
            lumeComposable(Routes.Search) { SearchScreen(nav) }
            lumeComposable(
                route = Routes.SearchAll,
                arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                SearchScreen(nav, globalQuery = entry.arguments!!.getString("q"))
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
            lumeComposable(Routes.Categories) { CategoriesScreen(nav) }
            lumeComposable(Routes.About) { AboutScreen(nav) }
            lumeComposable(Routes.ReadingList) { ReadingListScreen(nav) }
            lumeComposable(
                route = Routes.Browse,
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
            ) { entry ->
                CatalogScreen(nav, browseSourceId = entry.arguments!!.getString("sourceId")!!)
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
            lumeComposable(
                route = Routes.Reader,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("titleId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("offset") { type = NavType.FloatType; defaultValue = -1f },
                ),
            ) { entry ->
                val args = entry.arguments!!
                ReaderScreen(
                    nav = nav,
                    sourceId = args.getString("sourceId")!!,
                    titleId = args.getString("titleId")!!,
                    chapterId = args.getString("chapterId")!!,
                    page = args.getInt("page").takeIf { it >= 0 },
                    offset = args.getFloat("offset").takeIf { it >= 0f },
                )
            }
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
            BottomNavBar(currentRoute = route, onSelect = onSelect)
        }
        AnimatedVisibility(
            visible = showBottomBar && useRail,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(Motion.medium()) { -it } + fadeIn(Motion.medium()),
            exit = slideOutHorizontally(Motion.medium()) { -it } + fadeOut(Motion.short()),
        ) {
            NavRail(currentRoute = route, onSelect = onSelect)
        }

        MainMenu(
            visible = menuOpen,
            onClose = { menuOpen = false },
            onNavigate = { target ->
                menuOpen = false
                when (target) {
                    in MainTabRoutes -> nav.navigateToTab(target)
                    else -> nav.navigate(target)
                }
            },
        )
    }
    }
}

private const val RAIL_MIN_WIDTH_DP = 600

private val LocalPoppingIds = compositionLocalOf<MutableMap<String, Unit>> { mutableMapOf() }

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

private fun NavGraphBuilder.lumeComposable(
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
        val popping = LocalPoppingIds.current
        val popped = exiting && entry.id in popping
        DisposableEffect(entry.id) { onDispose { popping.remove(entry.id) } }
        CompositionLocalProvider(
            LocalShellBottomPadding provides bottomPadding,
            LocalNavExiting provides exiting,
        ) {
            BackGestureCard(leaving = exiting && !isTab, popped = popped, background = AppColors.background) {
                if (isTab && rail) {
                    Box(Modifier.fillMaxSize().padding(start = LumeInsets.RailWidth)) { content(entry) }
                } else {
                    content(entry)
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onSelect: (String?) -> Unit,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelect(Routes.Home) },
                contentAlignment = Alignment.Center,
            ) {
                LumeLogo(selected = currentRoute == Routes.Home, size = 30.dp)
            }
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
private fun NavRail(currentRoute: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(LumeInsets.RailWidth - 1.dp)
                .background(AppColors.navBar)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val slot = Modifier.fillMaxWidth().height(72.dp)
            Box(slot.clickable { onSelect(Routes.Home) }, contentAlignment = Alignment.Center) {
                LumeLogo(selected = currentRoute == Routes.Home, size = 30.dp)
            }
            NavSlot(currentRoute == Routes.Library, stringResource(R.string.nav_library), Icons.Outlined.Bookmark, { onSelect(Routes.Library) }, slot)
            NavSlot(currentRoute == Routes.Catalog, stringResource(R.string.nav_catalog), Icons.Outlined.GridView, { onSelect(Routes.Catalog) }, slot)
            NavSlot(currentRoute == Routes.Updates, stringResource(R.string.nav_updates), Icons.Outlined.NewReleases, { onSelect(Routes.Updates) }, slot)
            NavSlot(false, stringResource(R.string.nav_menu), Icons.Outlined.Menu, { onSelect(null) }, slot)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
