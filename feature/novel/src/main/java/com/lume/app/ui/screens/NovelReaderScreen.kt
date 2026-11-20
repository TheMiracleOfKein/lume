package com.lume.app.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import com.lume.app.ui.components.LumeBottomSheet
import com.lume.app.ui.components.rememberEasyDismissSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.LocalNavExiting
import com.lume.app.ui.components.TranslationPickerSheet
import com.lume.app.ui.components.translationLabel
import com.lume.app.ui.nav.openTitleFromReader
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the list shows right now, for reporting the position. */
private data class Viewport(val index: Int, val offset: Int, val size: Int, val last: Int)

/** Fraction of the viewport a tap on the top or bottom third scrolls. */
private const val TAP_SCROLL_FRACTION = 0.9f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(
    nav: NavHostController,
    sourceId: String,
    titleId: String,
    chapterId: String,
    block: Int? = null,
    offset: Float? = null,
) {
    val container = LocalAppContainer.current
    val vm: NovelReaderViewModel = viewModel {
        NovelReaderViewModel(container, sourceId, titleId, chapterId, block, offset, createSavedStateHandle())
    }
    // Leaving the app or the screen: save now instead of waiting for the debounce.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    val leave = {
        vm.flush()
        nav.popBackStack()
        Unit
    }

    val settings = vm.settings
    val palette = novelPalette(settings.theme)
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var chaptersOpen by rememberSaveable { mutableStateOf(false) }
    var translationsOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Reading: the screen stays on, and the system bars hide with the reader's own bars.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val activity = LocalActivity.current
    val window = activity?.window
    val insetsController = remember(window, view) { window?.let { WindowCompat.getInsetsController(it, view) } }
    val exiting = LocalNavExiting.current
    val hideSystemBars = !chromeVisible && !settingsOpen && !chaptersOpen && !translationsOpen && !exiting
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
    // Status and navigation bar icons follow the reader's colours while reading.
    val darkReader = palette.dark
    DisposableEffect(insetsController, darkReader) {
        val previousStatus = insetsController?.isAppearanceLightStatusBars
        val previousNav = insetsController?.isAppearanceLightNavigationBars
        insetsController?.isAppearanceLightStatusBars = !darkReader
        insetsController?.isAppearanceLightNavigationBars = !darkReader
        onDispose {
            previousStatus?.let { insetsController?.isAppearanceLightStatusBars = it }
            previousNav?.let { insetsController?.isAppearanceLightNavigationBars = it }
        }
    }

    val openTitle = {
        vm.flush()
        nav.openTitleFromReader(sourceId, titleId)
    }
    val topBar = @Composable { modifier: Modifier ->
        NovelTopBar(
            titleName = vm.titleName,
            chapterTitle = vm.currentChapter?.title.orEmpty(),
            palette = palette,
            onBack = leave,
            onOpenTitle = openTitle,
            modifier = modifier,
            translation = vm.currentChapter?.takeIf { vm.translations.isNotEmpty() }?.let(::translationLabel),
            onTranslation = { translationsOpen = true },
        )
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        val error = vm.error
        when {
            vm.loading -> {
                LoadingBox()
                topBar(Modifier.align(Alignment.TopCenter))
            }
            error != null -> {
                ErrorRetryBox(message = error, onRetry = vm::retry)
                topBar(Modifier.align(Alignment.TopCenter))
            }
            else -> {
                NovelReaderContent(
                    vm = vm,
                    palette = palette,
                    onTapCentre = { chromeVisible = !chromeVisible },
                    onScrolled = { chromeVisible = false },
                )
                AnimatedVisibility(
                    visible = chromeVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = Motion.barEnterFromTop,
                    exit = Motion.barExitToTop,
                ) {
                    topBar(Modifier)
                }
                AnimatedVisibility(
                    visible = chromeVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = Motion.barEnterFromBottom,
                    exit = Motion.barExitToBottom,
                ) {
                    NovelBottomBar(
                        percent = vm.percent,
                        hasPrev = vm.neighbours.previous != null,
                        hasNext = vm.neighbours.next != null,
                        palette = palette,
                        onPrev = { vm.goChapter(-1) },
                        onNext = { vm.goChapter(1) },
                        onChapters = { chaptersOpen = true },
                        onSettings = { settingsOpen = true },
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        val sheetState = rememberEasyDismissSheetState()
        LumeBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = sheetState) {
            NovelReaderSettingsSheet(
                settings = settings,
                onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { settingsOpen = false } },
                onChange = { vm.updateSettings(it, commit = false) },
                onCommit = { vm.updateSettings(it, commit = true) },
                onReset = vm::resetSettings,
                saveForTitle = vm.saveForTitle,
                onSaveForTitleChange = vm::changeSaveForTitle,
            )
        }
    }
    if (chaptersOpen) {
        val sheetState = rememberEasyDismissSheetState()
        LumeBottomSheet(onDismissRequest = { chaptersOpen = false }, sheetState = sheetState) {
            NovelChapterList(
                chapters = vm.chapters,
                currentId = vm.currentChapterId,
                onPick = { picked ->
                    vm.openChapter(picked.id)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { chaptersOpen = false }
                },
                onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { chaptersOpen = false } },
            )
        }
    }
    if (translationsOpen) {
        TranslationPickerSheet(
            copies = vm.translations,
            currentId = vm.currentChapterId,
            onPick = vm::switchTranslation,
            onDismiss = { translationsOpen = false },
        )
    }
}

/**
 * The text, as pages when that is the setting ([NovelPagedText]), else scrolling with tap zones:
 * the middle toggles the bars, the top and bottom thirds scroll by a screen. Restores the saved position once per [NovelReaderViewModel.session], then reports
 * the position of the first visible row and how far it is scrolled.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NovelReaderContent(
    vm: NovelReaderViewModel,
    palette: NovelPalette,
    onTapCentre: () -> Unit,
    onScrolled: () -> Unit,
) {
    val topPad = WindowInsets.statusBarsIgnoringVisibility.let { with(LocalDensity.current) { it.getTop(this).toDp() } }
    val bottomPad = WindowInsets.navigationBarsIgnoringVisibility.let { with(LocalDensity.current) { it.getBottom(this).toDp() } }
    if (vm.settings.paged) {
        NovelPagedText(vm, palette, topPad, bottomPad, onTapCentre = onTapCentre, onTurned = onScrolled)
        return
    }
    val session = vm.session
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var restored by remember(session) { mutableStateOf(false) }

    LaunchedEffect(session, listState) {
        // Restore: the row first, then (once its height is known) the offset inside it.
        val items = vm.items
        val anchor = vm.anchor
        val index = novelItemIndex(items, anchor).coerceAtLeast(0)
        listState.scrollToItem(index)
        if (anchor.fraction > 0f) {
            val size = snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size }
                .filterNotNull()
                .first()
            listState.scrollToItem(index, restoreOffsetPx(anchor.fraction, size))
        }
        restored = true
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }
            if (first == null) {
                null
            } else {
                Viewport(first.index, listState.firstVisibleItemScrollOffset, first.size, info.visibleItemsInfo.last().index)
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { v ->
                novelAnchorAt(vm.items, v.index, v.offset, v.size)?.let { vm.onPosition(session, it, v.last) }
            }
    }
    // Reading on (a real scroll, not the restore) puts the bars away.
    LaunchedEffect(session, listState) {
        snapshotFlow { restored && listState.isScrollInProgress }.filter { it }.collect { onScrolled() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val third = size.height / 3f
                    val delta = listState.layoutInfo.viewportSize.height * TAP_SCROLL_FRACTION
                    when {
                        tap.y < third -> scope.launch { listState.animateScrollBy(-delta, Motion.medium()) }
                        tap.y > 2 * third -> scope.launch { listState.animateScrollBy(delta, Motion.medium()) }
                        else -> onTapCentre()
                    }
                }
            },
    ) {
        NovelTextList(
            items = vm.items,
            settings = vm.settings,
            palette = palette,
            listState = listState,
            contentPadding = PaddingValues(top = topPad + 16.dp, bottom = bottomPad + 48.dp),
        )
    }
}
