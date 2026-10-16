package com.lume.app.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animate
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.isSpecified
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lume.app.ui.components.BookmarkNoteDialog
import com.lume.app.ui.components.LocalNavExiting
import com.lume.app.ui.components.PageBookmarkRow
import com.lume.app.ui.nav.openTitleFromReader
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lume.app.R
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.domain.ComicPage
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.ChoiceChip
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.components.lumeSliderColors
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    nav: NavHostController,
    sourceId: String,
    titleId: String,
    chapterId: String,
    page: Int? = null,
    offset: Float? = null,
) {
    val vm: ReaderViewModel = viewModel {
        ReaderViewModel(sourceId, titleId, chapterId, page, offset, createSavedStateHandle())
    }
    // Leaving the app or the screen: save now instead of waiting for the debounce.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    val settings = vm.settings
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var noteFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val inLibrary by vm.inLibrary.collectAsState()
    val pageBookmarks by vm.pageBookmarks.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    /** +1 = next page, -1 = previous; collected by whichever reader mode is active. */
    val pageTurns = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val bookmarkAdded = stringResource(R.string.reader_bookmark_added)
    val addNote = stringResource(R.string.reader_bookmark_add_note)
    DisposableEffect(view, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // Fullscreen: when the controls hide, the status and navigation bars hide with them
    // (a swipe from the edge peeks them; a tap on the page restores everything).
    val window = LocalActivity.current?.window
    val insetsController = remember(window, view) {
        window?.let { WindowCompat.getInsetsController(it, view) }
    }
    // Leaving (Back, or a back gesture in progress): bars come back before the next screen shows,
    // and since the shell no longer pads for them, nothing resizes either way.
    val exiting = LocalNavExiting.current
    val hideSystemBars = settings.immersive && !chromeVisible && !settingsOpen && !bookmarksOpen && !exiting
    LaunchedEffect(insetsController, hideSystemBars) {
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideSystemBars) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(insetsController) {
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val readerBg = when (settings.theme) {
        ReaderTheme.Light -> Color(0xFFF2F2F2)
        ReaderTheme.Dark -> Color.Black
        ReaderTheme.System -> if (systemDark) Color.Black else Color(0xFFF2F2F2)
    }

    fun bookmarkHere() {
        vm.addPageBookmark { id ->
            scope.launch {
                val result = snackbar.showSnackbar(bookmarkAdded, actionLabel = addNote, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) noteFor = id
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBg)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!settings.volumeKeys || settingsOpen) return@onPreviewKeyEvent false
                val delta = when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> -1
                    else -> return@onPreviewKeyEvent false
                }
                // Consume both down and up so the system volume does not change.
                if (event.type == KeyEventType.KeyDown) pageTurns.tryEmit(delta)
                true
            },
    ) {
        val loading = vm.loading || !vm.settingsReady
        LaunchedEffect(loading, settingsOpen) {
            if (!loading && !settingsOpen) runCatching { focusRequester.requestFocus() }
        }
        AnimatedLoadState(
            state = loadStateOf(loading, vm.error),
            modifier = Modifier.fillMaxSize(),
        ) { pane ->
            when (pane) {
                LoadState.Loading -> LoadingBox()
                LoadState.Error -> ErrorRetryBox(
                    message = vm.error.orEmpty(),
                    onRetry = vm::retry,
                    onCloudflare = vm.cloudflareUri?.let { { vm.solveChallenge() } },
                )
                else -> Box(Modifier.fillMaxSize()) {
                    val toggleChrome = { chromeVisible = !chromeVisible }
                    val hideChrome = { if (settings.immersive) chromeVisible = false }
                    key(vm.session) {
                        when (settings.mode) {
                            ReaderMode.Webtoon -> WebtoonReader(
                                vm = vm,
                                pageTurns = pageTurns,
                                onSingleTap = toggleChrome,
                                onUserScroll = hideChrome,
                            )
                            ReaderMode.Ltr, ReaderMode.Rtl -> PagedReader(
                                vm = vm,
                                pageTurns = pageTurns,
                                onToggleChrome = toggleChrome,
                                onUserScroll = hideChrome,
                            )
                        }
                    }

                    if (settings.dim > 0f) {
                        // Night dimming above the pages, below the controls; does not take touches.
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.dim)))
                    }
                    AnimatedVisibility(
                        visible = chromeVisible,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = Motion.barEnterFromTop,
                        exit = Motion.barExitToTop,
                    ) {
                        ReaderTopBar(
                            titleName = vm.titleName,
                            chapterTitle = vm.currentChapter?.title.orEmpty(),
                            inLibrary = inLibrary,
                            onBack = { nav.popBackStack() },
                            onOpenTitle = { nav.openTitleFromReader(sourceId, titleId) },
                            onToggleLibrary = vm::toggleLibrary,
                            onSettings = { settingsOpen = true },
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SnackbarHost(snackbar)
                        AnimatedVisibility(
                            visible = chromeVisible,
                            enter = Motion.barEnterFromBottom,
                            exit = Motion.barExitToBottom,
                        ) {
                            ReaderBottomBar(
                                pageLabel = vm.currentChapterPageCount.takeIf { it > 0 && !settings.hidePageNumber }
                                    ?.let { "${vm.currentPageIndex + 1} / $it" },
                                onPrev = { vm.goChapter(-1) },
                                onNext = { vm.goChapter(1) },
                                onChapters = { nav.navigate(Routes.chapters(sourceId, titleId)) },
                                onBookmark = ::bookmarkHere,
                                onBookmarkList = { bookmarksOpen = true },
                                onSettings = { settingsOpen = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
            containerColor = AppColors.surface,
            dragHandle = null,
        ) {
            ReaderSettingsSheet(
                settings = settings,
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { settingsOpen = false }
                },
                onChange = { next -> vm.updateSettings(next, commit = false) },
                onCommit = { next -> vm.updateSettings(next, commit = true) },
                onSaveForTitle = vm::saveSettingsForTitle,
            )
        }
    }

    if (bookmarksOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { bookmarksOpen = false },
            sheetState = sheetState,
            containerColor = AppColors.surface,
        ) {
            Text(
                stringResource(R.string.bookmarks_title),
                color = AppColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (pageBookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.bookmarks_empty),
                    color = AppColors.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                    itemsIndexed(pageBookmarks, key = { _, b -> b.id }) { _, bookmark ->
                        PageBookmarkRow(
                            bookmark = bookmark,
                            onOpen = {
                                scope.launch { sheetState.hide() }.invokeOnCompletion { bookmarksOpen = false }
                                vm.jumpTo(bookmark)
                            },
                            onDelete = { vm.deletePageBookmark(bookmark.id) },
                        )
                    }
                }
            }
        }
    }

    noteFor?.let { id ->
        BookmarkNoteDialog(
            initial = pageBookmarks.firstOrNull { it.id == id }?.note.orEmpty(),
            onDismiss = { noteFor = null },
            onSave = { text ->
                vm.setBookmarkNote(id, text)
                noteFor = null
            },
        )
    }
}

/** How long to wait for the anchor page's real height before giving up on the fine restore. */
private const val RESTORE_WAIT_MS = 4_000L

@Composable
private fun WebtoonReader(
    vm: ReaderViewModel,
    pageTurns: MutableSharedFlow<Int>,
    onSingleTap: () -> Unit,
    onUserScroll: () -> Unit,
) {
    val settings = vm.settings
    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val sideMarginPx = containerWidth * settings.sidePaddingPercent / 100
    val sideMargin = with(density) { sideMarginPx.toDp() }
    // Keyed to the reader session (and recreated with the mode), never saved on its own: the
    // ViewModel's anchor is the one source of truth, so the two can't fight.
    val session = remember { vm.session }
    val target = remember { vm.anchor }
    // Usually the anchor page's size is known from last time: then the exact spot is part of the
    // initial state and the very first frame is already right.
    val knownAspect = remember { vm.aspectOf(target.chapterId, target.pageIndex) }
    val listState = remember {
        val initialOffset = knownAspect?.let { (target.pageOffset * (containerWidth - 2 * sideMarginPx) / it).roundToInt() }
        LazyListState(
            firstVisibleItemIndex = stripIndexOf(vm.strip, target).coerceAtLeast(0),
            firstVisibleItemScrollOffset = initialOffset ?: 0,
        )
    }
    var restoring by remember { mutableStateOf(true) }

    // Otherwise, phase 2: once the anchor page reports its real height, scroll inside it.
    // Saving stays off until this finishes, or until the user drags (which cancels it).
    LaunchedEffect(listState) {
        val index = stripIndexOf(vm.strip, target)
        if (index >= 0 && target.pageOffset > 0f && knownAspect == null) {
            val restore = launch {
                val aspect = withTimeoutOrNull(RESTORE_WAIT_MS) {
                    snapshotFlow { vm.aspectOf(target.chapterId, target.pageIndex) }.filterNotNull().first()
                } ?: return@launch
                val itemWidth = (listState.layoutInfo.viewportSize.width.takeIf { it > 0 } ?: containerWidth) -
                    2 * sideMarginPx
                val height = itemWidth / aspect
                listState.scrollToItem(index, (target.pageOffset * height).roundToInt())
            }
            val cancelOnDrag = launch {
                listState.interactionSource.interactions.first { it is DragInteraction.Start }
                restore.cancel()
            }
            restore.join()
            cancelOnDrag.cancel()
        }
        restoring = false
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            if (restoring) return@snapshotFlow null
            val info = listState.layoutInfo
            val strip = vm.strip
            val visible = info.visibleItemsInfo.map { VisibleItem(it.index, it.offset, it.size) }
            val anchor = anchorFrom(visible, strip) ?: return@snapshotFlow null
            Triple(anchor, finishedChapters(visible, info.viewportEndOffset, strip), visible.lastOrNull()?.index ?: 0)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (anchor, finished, lastVisible) -> vm.onPosition(session, anchor, finished, lastVisible) }
    }
    LaunchedEffect(listState) {
        pageTurns.collect { delta ->
            onUserScroll()
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollBy(delta * viewport * 0.85f)
        }
    }
    val chromeHider = rememberChromeHider(settings.immersive, onUserScroll)
    ReaderZoomFrame(
        zoomEnabled = settings.doubleTapZoom,
        resetKey = vm.session,
        onSingleTap = onSingleTap,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeHider),
    ) { zoomModifier, isZoomed ->
        // Recomposes only when crossing 1x, not on every pinch frame.
        val scrollEnabled by remember { derivedStateOf { !isZoomed() } }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(zoomModifier),
            verticalArrangement = Arrangement.spacedBy(settings.gap.dp),
            contentPadding = PaddingValues(horizontal = sideMargin),
            userScrollEnabled = scrollEnabled,
        ) {
            itemsIndexed(vm.strip, key = { _, item -> item.key() }) { _, item ->
                when (item) {
                    is ReaderStripItem.Divider -> ChapterDividerBand(item.chapter.title)
                    is ReaderStripItem.Page -> ReaderPageImage(
                        page = item.page,
                        aspect = vm.aspectOf(item.chapter.id, item.indexInChapter),
                        onAspect = { vm.reportAspect(item.chapter.id, item.indexInChapter, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedReader(
    vm: ReaderViewModel,
    pageTurns: MutableSharedFlow<Int>,
    onToggleChrome: () -> Unit,
    onUserScroll: () -> Unit,
) {
    val settings = vm.settings
    val session = remember { vm.session }
    val pagerState = remember {
        PagerState(currentPage = stripIndexOf(vm.strip, vm.anchor).coerceAtLeast(0)) { vm.strip.size.coerceAtLeast(1) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to vm.strip }
            .distinctUntilChanged()
            .collect { (page, strip) ->
                val anchor = anchorAt(strip, page) ?: return@collect
                val finished = when (val item = strip.getOrNull(page)) {
                    is ReaderStripItem.Page ->
                        if (item.indexInChapter == item.chapterPageCount - 1) setOf(item.chapter.id) else emptySet()
                    is ReaderStripItem.Divider -> setOf(item.previous.id)
                    null -> emptySet()
                }
                vm.onPosition(session, anchor, finished, page)
            }
    }
    val chromeHider = rememberChromeHider(settings.immersive, onUserScroll)
    LaunchedEffect(pagerState) {
        pageTurns.collect { delta ->
            onUserScroll()
            val target = (pagerState.currentPage + delta).coerceIn(0, pagerState.pageCount - 1)
            pagerState.animateScrollToPage(target)
        }
    }
    val rtl = settings.mode == ReaderMode.Rtl
    val onPagerTap: (Float, Int) -> Unit = { x, width ->
        val third = width / 3f
        val zone = when {
            !settings.tapZones -> 0
            x < third -> -1
            x > width - third -> 1
            else -> 0
        }
        // In RTL the left side is "forward".
        if (zone == 0) onToggleChrome() else pageTurns.tryEmit(if (rtl) -zone else zone)
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = rtl,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeHider)
            .pointerInput(settings.tapZones, rtl) {
                detectTapGestures(onTap = { offset -> onPagerTap(offset.x, size.width) })
            },
        key = { index -> vm.strip.getOrNull(index)?.key() ?: index },
    ) { index ->
        when (val item = vm.strip.getOrNull(index)) {
            is ReaderStripItem.Page -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ReaderPageImage(
                    page = item.page,
                    aspect = vm.aspectOf(item.chapter.id, item.indexInChapter),
                    onAspect = { vm.reportAspect(item.chapter.id, item.indexInChapter, it) },
                    contentScale = ContentScale.Fit,
                    zoomEnabled = settings.doubleTapZoom,
                    fillViewport = true,
                    // The zoom gesture consumes taps, so forward single taps.
                    onTap = onPagerTap,
                )
            }
            is ReaderStripItem.Divider -> ChapterDividerBand(
                title = item.chapter.title,
                fillViewport = true,
            )
            null -> Box(Modifier.fillMaxSize())
        }
    }
}

private fun ReaderStripItem.key(): String = when (this) {
    is ReaderStripItem.Divider -> "div-${chapter.id}"
    is ReaderStripItem.Page -> "p-${chapter.id}-${page.index}-$indexInChapter"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderTopBar(
    titleName: String,
    chapterTitle: String,
    inLibrary: Boolean,
    onBack: () -> Unit,
    onOpenTitle: () -> Unit,
    onToggleLibrary: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.87f), Color.Transparent)))
            // Stable position whether or not the status bar is currently shown.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenTitle),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = titleName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = chapterTitle,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onToggleLibrary) {
                Icon(
                    if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (inLibrary) R.string.cd_remove_from_library else R.string.cd_add_to_library),
                    tint = if (inLibrary) AppColors.accent else Color.White,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.cd_settings), tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderBottomBar(
    pageLabel: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onBookmark: () -> Unit,
    onBookmarkList: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.87f))))
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pageLabel != null) {
            Text(
                text = pageLabel,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.cd_prev_chapter), tint = Color.White)
            }
            IconButton(onClick = onChapters) {
                Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.cd_chapters), tint = Color.White)
            }
            // Tap pins this spot; long-press lists this title's bookmarks.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onBookmark, onLongClick = onBookmarkList),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.cd_bookmark_spot), tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.cd_settings), tint = Color.White)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.SkipNext, stringResource(R.string.cd_next_chapter), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ChapterDividerBand(
    title: String,
    fillViewport: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxSize() else Modifier)
            .padding(vertical = if (fillViewport) 0.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(AppColors.accent.copy(alpha = 0.55f)),
            )
            Text(
                text = stringResource(R.string.reader_chapter_separator, title),
                color = AppColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(2f, fill = false),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(AppColors.accent.copy(alpha = 0.55f)),
            )
        }
    }
}

private class ReaderZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(raw: Offset, s: Float = scale): Offset {
        if (s <= 1.01f || viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val maxX = viewport.width * (s - 1f) / 2f
        val maxY = viewport.height * (s - 1f) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    fun zoomBy(zoomChange: Float, focal: Offset) {
        // Slight amplification so pinch feels responsive without jumping.
        val amplified = 1f + (zoomChange - 1f) * 1.35f
        val old = scale
        val next = (old * amplified).coerceIn(1f, 5f)
        if (next <= 1.01f) {
            reset()
            return
        }
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        offset = if (old <= 1.01f) {
            (center - focal) * (next - 1f)
        } else {
            val ratio = next / old
            offset * ratio + (focal - center) * (1f - ratio)
        }
        scale = next
        offset = clamp(offset, next)
    }

    /** Read through this (not [scale]) in composition: it changes only when crossing 1x. */
    val isZoomed: Boolean by derivedStateOf { scale > 1.01f }

    /** Double tap: glide to [target] around [focal] (or back to 1x) instead of jumping. */
    suspend fun animateZoomTo(target: Float, focal: Offset) {
        val fromScale = scale
        val fromOffset = offset
        val toScale = target.coerceIn(1f, 5f)
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val toOffset = when {
            toScale <= 1.01f -> Offset.Zero
            fromScale <= 1.01f -> clamp((center - focal) * (toScale - 1f), toScale)
            else -> {
                val ratio = toScale / fromScale
                clamp(fromOffset * ratio + (focal - center) * (1f - ratio), toScale)
            }
        }
        animate(0f, 1f, animationSpec = Motion.medium()) { t, _ ->
            scale = fromScale + (toScale - fromScale) * t
            offset = fromOffset + (toOffset - fromOffset) * t
        }
        if (toScale <= 1.01f) reset()
    }

    suspend fun toggleZoom(focal: Offset, target: Float = 2.5f) {
        animateZoomTo(if (scale > 1.1f) 1f else target, focal)
    }
}

@Composable
private fun ReaderZoomFrame(
    zoomEnabled: Boolean,
    resetKey: Any?,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (zoomModifier: Modifier, isZoomed: () -> Boolean) -> Unit,
) {
    val state = remember { ReaderZoomState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resetKey) { state.reset() }

    Box(
        modifier = modifier
            .onSizeChanged { state.viewport = it }
            // Keys must NOT include scale — restarting mid-pinch makes zoom weak/jerky.
            .pointerInput(zoomEnabled) {
                if (zoomEnabled) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { pos -> scope.launch { state.toggleZoom(pos) } },
                    )
                } else {
                    // Without a double-tap handler a single tap fires at once (no ~300 ms wait).
                    detectTapGestures(onTap = { onSingleTap() })
                }
            }
            .pointerInput(zoomEnabled) {
                if (!zoomEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                                    state.zoomBy(zoomChange, centroid)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            pressed.size == 1 && state.scale > 1.01f -> {
                                val change = pressed[0]
                                val pan = change.positionChange()
                                if (pan != Offset.Zero) {
                                    state.offset = state.clamp(state.offset + pan)
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        // Scale and offset are read only inside graphicsLayer, so pinching redraws the layer
        // without recomposing the list underneath.
        content(
            Modifier.graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x
                translationY = state.offset.y
                transformOrigin = TransformOrigin.Center
            },
            { state.isZoomed },
        )
    }
}

/**
 * Hides the reader chrome after real scrolling (about 24 dp), not on every touch that starts a
 * drag; a sloppy tap or a tiny nudge leaves the controls alone.
 */
@Composable
private fun rememberChromeHider(enabled: Boolean, onHide: () -> Unit): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val currentOnHide by rememberUpdatedState(onHide)
    val currentEnabled by rememberUpdatedState(enabled)
    return remember(thresholdPx) {
        object : NestedScrollConnection {
            private var travelled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && currentEnabled) {
                    travelled += kotlin.math.abs(consumed.x) + kotlin.math.abs(consumed.y)
                    if (travelled >= thresholdPx) {
                        travelled = 0f
                        currentOnHide()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                travelled = 0f
                return Velocity.Zero
            }
        }
    }
}

@Composable
private fun ReaderPageImage(
    page: ComicPage,
    /** Width / height when known (from this session or saved progress); sizes the placeholder. */
    aspect: Float?,
    onAspect: (Float) -> Unit,
    contentScale: ContentScale = ContentScale.FillWidth,
    zoomEnabled: Boolean = false,
    fillViewport: Boolean = false,
    /** Single tap at (x, width); needed because the double-tap detector consumes taps. */
    onTap: ((Float, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    var attempt by remember(page.imageUrl) { mutableIntStateOf(0) }
    var failed by remember(page.imageUrl) { mutableStateOf(false) }
    val model = remember(page.imageUrl, attempt) {
        ImageRequest.Builder(context)
            .data(if (page.imageUrl.startsWith("http")) page.imageUrl else File(page.imageUrl))
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // A new parameter makes Coil treat a retry as a new request.
            .setParameter("attempt", attempt, memoryCacheKey = null)
            .build()
    }
    val state = remember(page.imageUrl) { ReaderZoomState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .then(
                when {
                    fillViewport -> Modifier.fillMaxSize()
                    // Real height before the bitmap arrives: nothing above the reading spot moves.
                    aspect != null -> Modifier.fillMaxWidth().aspectRatio(aspect)
                    // Never seen this page: reserve a typical height (also kept on error).
                    else -> Modifier.fillMaxWidth().heightIn(min = 420.dp)
                },
            )
            .background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            onSuccess = { success ->
                failed = false
                val size = success.painter.intrinsicSize
                if (size.isSpecified && size.width > 0f && size.height > 0f) onAspect(size.width / size.height)
            },
            onError = { failed = true },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillViewport || aspect != null) Modifier.fillMaxSize() else Modifier)
                .onSizeChanged { state.viewport = it }
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (zoomEnabled) {
                        Modifier
                            .pointerInput(page.imageUrl, onTap) {
                                detectTapGestures(
                                    onTap = onTap?.let { tap -> { pos -> tap(pos.x, size.width) } },
                                    onDoubleTap = { pos -> scope.launch { state.toggleZoom(pos) } },
                                )
                            }
                            .pointerInput(page.imageUrl) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        when {
                                            pressed.size >= 2 -> {
                                                val zoomChange = event.calculateZoom()
                                                val centroid = event.calculateCentroid(useCurrent = true)
                                                if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                                                    state.zoomBy(zoomChange, centroid)
                                                }
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            }
                                            pressed.size == 1 && state.scale > 1.01f -> {
                                                val change = pressed[0]
                                                val pan = change.positionChange()
                                                if (pan != Offset.Zero) {
                                                    state.offset = state.clamp(state.offset + pan)
                                                    change.consume()
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
            contentScale = contentScale,
        )
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.page_load_failed), color = AppColors.textSecondary, fontSize = 13.sp)
                TextButton(onClick = { failed = false; attempt++ }) {
                    Text(stringResource(R.string.action_retry), color = AppColors.accent)
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
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

