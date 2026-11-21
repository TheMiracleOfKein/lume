package com.lume.app.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.lume.app.readaloud.textLength
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lume.app.readaloud.ReadAloud
import com.lume.app.readaloud.ReadAloudProblem
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
import androidx.compose.ui.res.stringResource
import com.lume.app.R
import com.lume.app.translate.OriginalDialog
import com.lume.app.translate.TranslateSheet
import com.lume.app.translate.glossaryRoute
import com.lume.app.translate.TranslationProgress
import com.lume.app.translate.originalText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the list shows right now, for reporting the position. */
private data class Viewport(val index: Int, val offset: Int, val size: Int, val last: Int)

/** Fraction of the viewport a tap on the top or bottom third scrolls. */
private const val TAP_SCROLL_FRACTION = 0.9f

/** Room for the read aloud panel (its buttons and one line of messages) below the text. */
private val READ_ALOUD_PANEL_SPACE = 112.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var readAloudOpen by rememberSaveable { mutableStateOf(false) }
    var translateOpen by rememberSaveable { mutableStateOf(false) }
    /** The original shown for a long-pressed translated paragraph. */
    var originalText by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val readAloud = vm.readAloud

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
    val hideSystemBars = !chromeVisible && !settingsOpen && !chaptersOpen && !translationsOpen && !readAloudOpen && !translateOpen && !exiting
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
            onTranslate = { translateOpen = true }.takeIf { vm.translation.targets.isNotEmpty() },
            translated = vm.translation.target != null,
            translateStatus = (vm.translation.progress as? TranslationProgress.Working)?.let {
                stringResource(R.string.translate_progress, it.done, it.total)
            },
        )
    }
    val longPress = { chapterId: String, index: Int ->
        vm.originalOf(chapterId, index)?.originalText()?.let { originalText = it }
        Unit
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
                    onLongPress = longPress,
                )
                AnimatedVisibility(
                    visible = chromeVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = Motion.barEnterFromTop,
                    exit = Motion.barExitToTop,
                ) {
                    topBar(Modifier)
                }
                Column(Modifier.align(Alignment.BottomCenter)) {
                    // Read aloud's controls stay on screen when the bars hide.
                    if (readAloud != null) {
                        ReadAloudPanel(
                            state = readAloud,
                            following = vm.followVoice,
                            palette = palette,
                            onFollow = { vm.changeFollowVoice(true) },
                            onSettings = { readAloudOpen = true },
                            modifier = if (chromeVisible) {
                                Modifier
                            } else {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = chromeVisible,
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
                            onReadAloud = {
                                if (readAloud != null) readAloudOpen = true else vm.startReadAloud(context)
                            },
                        )
                    }
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
    if (readAloudOpen && readAloud != null) {
        val sheetState = rememberEasyDismissSheetState()
        val readAloudSettings by container.prefs.readAloudSettings.collectAsStateWithLifecycle()
        LumeBottomSheet(onDismissRequest = { readAloudOpen = false }, sheetState = sheetState) {
            ReadAloudSheet(
                state = readAloud,
                settings = readAloudSettings,
                onSettings = ReadAloud::updateSettings,
                onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { readAloudOpen = false } },
            )
        }
    }
    if (readAloud?.problem == ReadAloudProblem.NoEngine) {
        NoEngineDialog(onDismiss = ReadAloud::stop)
    }
    if (translateOpen) {
        val sheetState = rememberEasyDismissSheetState()
        LumeBottomSheet(onDismissRequest = { translateOpen = false }, sheetState = sheetState) {
            TranslateSheet(
                translation = vm.translation,
                onOpenGlossary = {
                    translateOpen = false
                    nav.navigate(glossaryRoute(sourceId, titleId, vm.titleName))
                },
                onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { translateOpen = false } },
            )
        }
    }
    originalText?.let { text ->
        OriginalDialog(text = text, onAddTerm = vm.translation::addTerm, onDismiss = { originalText = null })
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
    /** A block was long-pressed (chapter id, block index). */
    onLongPress: (String, Int) -> Unit = { _, _ -> },
) {
    val topPad = WindowInsets.statusBarsIgnoringVisibility.let { with(LocalDensity.current) { it.getTop(this).toDp() } }
    // While reading aloud its panel stays at the bottom: the text ends above it.
    val panelPad = if (vm.readAloud != null) READ_ALOUD_PANEL_SPACE else 0.dp
    val bottomPad = WindowInsets.navigationBarsIgnoringVisibility.let { with(LocalDensity.current) { it.getBottom(this).toDp() } } + panelPad
    if (vm.settings.paged) {
        NovelPagedText(vm, palette, topPad, bottomPad, onTapCentre = onTapCentre, onTurned = onScrolled, onLongPress = onLongPress)
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
    // Read aloud: the spoken sentence stays in view until the reader scrolls away by hand.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start && vm.readAloud != null) vm.changeFollowVoice(false)
        }
    }
    LaunchedEffect(session, listState) {
        snapshotFlow { vm.readAloud?.takeIf { restored && vm.followVoice }?.let { SpokenRange(it.chapterId, it.block, it.start, it.end) } }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { followSpoken(listState, vm.items, it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { at ->
                        // The list fills this box; item offsets start after its top padding
                        // (viewportStartOffset is minus that padding).
                        val info = listState.layoutInfo
                        val y = at.y.toInt() + info.viewportStartOffset
                        val row = info.visibleItemsInfo.firstOrNull { y in it.offset until it.offset + it.size }
                        (row?.let { vm.items.getOrNull(it.index) } as? NovelItem.Block)?.let { onLongPress(it.chapterId, it.index) }
                    },
                ) { tap ->
                    val third = size.height / 3f
                    val delta = listState.layoutInfo.viewportSize.height * TAP_SCROLL_FRACTION
                    val scrolls = tap.y < third || tap.y > 2 * third
                    if (scrolls && vm.readAloud != null) vm.changeFollowVoice(false)
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
            spoken = vm.spoken,
        )
    }
}

/**
 * Scrolls so the spoken sentence sits in the upper part of the screen; does nothing while it is
 * already well in view, so the text doesn't jump with every sentence.
 */
private suspend fun followSpoken(state: LazyListState, items: List<NovelItem>, spoken: SpokenRange) {
    val index = if (spoken.block < 0) {
        chapterStartIndex(items, spoken.chapterId)
    } else {
        items.indexOfFirst { it is NovelItem.Block && it.chapterId == spoken.chapterId && it.index == spoken.block }
    }
    if (index < 0) return
    val length = (items[index] as? NovelItem.Block)?.block?.textLength() ?: 0
    val fraction = if (length > 0) spoken.start.toFloat() / length else 0f
    fun sentenceY(): Float? {
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        return info.offset + fraction * info.size
    }
    val viewport = state.layoutInfo.viewportSize.height.toFloat()
    val y = sentenceY()
    if (y != null && y in viewport * 0.12f..viewport * 0.7f) return
    if (y == null) state.animateScrollToItem(index)
    val target = sentenceY() ?: return
    if (target !in viewport * 0.12f..viewport * 0.7f) state.animateScrollBy(target - viewport * 0.25f, Motion.medium())
}
