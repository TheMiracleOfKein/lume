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
import androidx.compose.foundation.gestures.scrollBy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.lume.app.data.library.ReadingLists
import com.lume.app.ui.components.shareFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext
import java.io.File
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
import kotlinx.coroutines.isActive
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
    /** A page of the current chapter picked on the slider; only the latest one matters. */
    val pageSeeks = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    var autoScroll by rememberSaveable { mutableStateOf(false) }
    // Auto-scroll is a webtoon feature; switching to a paged mode turns it off.
    LaunchedEffect(settings.mode) { if (settings.mode != ReaderMode.Webtoon) autoScroll = false }
    val context = LocalContext.current
    val pageSaved = stringResource(R.string.reader_page_saved)
    val pageFailed = stringResource(R.string.reader_page_failed)
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
    val activity = LocalActivity.current
    ReaderWindowEffects(activity, settings)
    val window = activity?.window
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

    /** The current page's bytes, extension and file name; null (with a message) when it can't be read. */
    suspend fun currentPage(): Triple<ByteArray, String, String>? {
        val chapter = vm.currentChapter ?: return null
        val page = vm.loaded.firstOrNull { chapterIdsMatch(it.chapter.id, vm.currentChapterId) }?.pages?.getOrNull(vm.currentPageIndex)
            ?: return null
        return runCatching { readerPageBytes(context, page.imageUrl) }
            .onFailure { snackbar.showSnackbar(pageFailed, duration = SnackbarDuration.Short) }
            .getOrNull()
            ?.let { (bytes, ext) -> Triple(bytes, ext, pageFileName(vm.titleName, chapter.title, vm.currentPageIndex)) }
    }

    // Android 9 and older: saving goes through the file picker, so no storage permission is needed.
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val bytes = pendingSave
        pendingSave = null
        if (uri != null && bytes != null) {
            scope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("write")
                    }
                }.isSuccess
                snackbar.showSnackbar(if (ok) pageSaved else pageFailed, duration = SnackbarDuration.Short)
            }
        }
    }

    fun sharePage() {
        scope.launch {
            val (bytes, ext, name) = currentPage() ?: return@launch
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, ReadingLists.SHARED_DIR).apply { mkdirs() }
                    File(dir, "page.$ext").apply { writeBytes(bytes) }
                }
            }.getOrNull() ?: return@launch
            // The link lets whoever gets the page find the chapter.
            val link = vm.currentChapter?.url?.takeIf { it.startsWith("http") }
            shareFile(context, file, mimeOf(ext), name, text = listOfNotNull(name, link).joinToString("\n"))
        }
    }

    fun savePage() {
        scope.launch {
            val (bytes, ext, name) = currentPage() ?: return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ok = runCatching { savePageToPictures(context, bytes, ext, name) }.isSuccess
                snackbar.showSnackbar(if (ok) pageSaved else pageFailed, duration = SnackbarDuration.Short)
            } else {
                pendingSave = bytes
                saveAs.launch("$name.$ext")
            }
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
                    val colorFilter = remember(settings.colorFilter, settings.eInk) { readerColorFilter(settings) }
                    key(vm.session) {
                        when (settings.mode) {
                            ReaderMode.Webtoon -> WebtoonReader(
                                vm = vm,
                                pageTurns = pageTurns,
                                pageSeeks = pageSeeks,
                                colorFilter = colorFilter,
                                autoScroll = autoScroll,
                                onAutoScrollStop = { autoScroll = false },
                                onSingleTap = toggleChrome,
                                onUserScroll = hideChrome,
                            )
                            ReaderMode.Ltr, ReaderMode.Rtl -> PagedReader(
                                vm = vm,
                                pageTurns = pageTurns,
                                pageSeeks = pageSeeks,
                                colorFilter = colorFilter,
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
                            onSharePage = ::sharePage,
                            onSavePage = ::savePage,
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
                                pageIndex = vm.currentPageIndex,
                                pageCount = vm.currentChapterPageCount,
                                reversed = settings.mode == ReaderMode.Rtl,
                                onSeek = { pageSeeks.tryEmit(it) },
                                autoScroll = autoScroll.takeIf { settings.mode == ReaderMode.Webtoon },
                                onToggleAutoScroll = {
                                    autoScroll = !autoScroll
                                    if (autoScroll && settings.immersive) chromeVisible = false
                                },
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

/** Auto-scroll speed: dp per second for each step of the speed setting. */
private const val AUTO_SCROLL_DP_PER_STEP = 18

@Composable
private fun WebtoonReader(
    vm: ReaderViewModel,
    pageTurns: MutableSharedFlow<Int>,
    pageSeeks: MutableSharedFlow<Int>,
    colorFilter: ColorFilter?,
    autoScroll: Boolean,
    onAutoScrollStop: () -> Unit,
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
    val autoScrolling by rememberUpdatedState(autoScroll)
    LaunchedEffect(listState) {
        pageTurns.collect { delta ->
            onUserScroll()
            val viewport = listState.layoutInfo.viewportSize.height
            // E-ink panels smear animations: jump instead. So does auto-scroll, whose glide would
            // cancel an animated turn (and the turn the glide) over and over.
            if (vm.settings.eInk || autoScrolling) listState.scrollBy(delta * viewport * 0.85f)
            else listState.animateScrollBy(delta * viewport * 0.85f)
        }
    }
    LaunchedEffect(listState) {
        pageSeeks.collect { page ->
            val index = stripIndexOf(vm.strip, vm.currentChapterId, page)
            if (index >= 0) listState.scrollToItem(index)
        }
    }
    // Auto-scroll: a steady glide; a drag by hand or the end of the strip stops it.
    val currentStop by rememberUpdatedState(onAutoScrollStop)
    LaunchedEffect(listState, autoScroll) {
        if (!autoScroll) return@LaunchedEffect
        val glide = launch {
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                val seconds = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val pxPerSecond = with(density) { (AUTO_SCROLL_DP_PER_STEP * vm.settings.autoScrollSpeed).dp.toPx() }
                if (seconds > 0f) {
                    // A page turn (tap zone, volume key) cancels this scroll; the glide goes on.
                    try {
                        listState.scrollBy(pxPerSecond * seconds)
                    } catch (e: CancellationException) {
                        if (!isActive) throw e
                    }
                }
                if (!listState.canScrollForward) {
                    currentStop()
                    break
                }
            }
        }
        listState.interactionSource.interactions.first { it is DragInteraction.Start }
        glide.cancel()
        currentStop()
    }
    val chromeHider = rememberChromeHider(settings.immersive, onUserScroll)
    ReaderZoomFrame(
        zoomEnabled = settings.doubleTapZoom,
        resetKey = vm.session,
        onSingleTap = { y, height ->
            val third = height / 3f
            when {
                !vm.settings.webtoonTapZones -> onSingleTap()
                y < third -> pageTurns.tryEmit(-1)
                y > height - third -> pageTurns.tryEmit(1)
                else -> onSingleTap()
            }
        },
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
                        colorFilter = colorFilter,
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
    pageSeeks: MutableSharedFlow<Int>,
    colorFilter: ColorFilter?,
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
            if (vm.settings.eInk) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        pageSeeks.collect { page ->
            val index = stripIndexOf(vm.strip, vm.currentChapterId, page)
            if (index >= 0) pagerState.scrollToPage(index)
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
                    colorFilter = colorFilter,
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
