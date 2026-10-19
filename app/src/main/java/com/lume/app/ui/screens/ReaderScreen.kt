package com.lume.app.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val container = LocalAppContainer.current
    val vm: ReaderViewModel = viewModel {
        ReaderViewModel(container, sourceId, titleId, chapterId, page, offset, createSavedStateHandle())
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

    // A chapter whose own source failed came from another one: say which, once.
    val fallbackNotice = vm.fallbackNotice
    LaunchedEffect(fallbackNotice) {
        if (fallbackNotice != null) {
            snackbar.showSnackbar(fallbackNotice, duration = SnackbarDuration.Short)
            vm.clearFallbackNotice()
        }
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
