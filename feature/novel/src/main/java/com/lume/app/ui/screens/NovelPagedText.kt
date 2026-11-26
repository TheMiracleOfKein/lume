package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import com.lume.app.readaloud.textLength
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lume.app.R
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** A row as the paged reader draws it: its text and style, or a picture. */
private sealed interface PagedRow {
    data class Text(val text: AnnotatedString, val style: TextStyle, val top: Dp, val bottom: Dp, val heading: Boolean) : PagedRow
    data class Picture(val src: String) : PagedRow
}

private fun pagedRow(item: NovelItem, settings: NovelReaderSettings, palette: NovelPalette, body: TextStyle): PagedRow {
    val after = settings.paragraphSpacing.dp
    return when (item) {
        is NovelItem.Header -> PagedRow.Text(
            AnnotatedString(item.chapter.title),
            body.copy(
                fontSize = (settings.fontSize * 1.3f).sp,
                lineHeight = (settings.fontSize * 1.3f * 1.3f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            ),
            top = 28.dp, bottom = 20.dp, heading = true,
        )
        is NovelItem.Block -> when (val block = item.block) {
            is NovelBlock.Paragraph -> PagedRow.Text(block.spans.toAnnotatedString(), novelParagraphStyle(settings, body), 0.dp, after, heading = false)
            is NovelBlock.Heading -> {
                val scale = headingScale(block.level)
                PagedRow.Text(
                    block.spans.toAnnotatedString(),
                    body.copy(
                        fontSize = (settings.fontSize * scale).sp,
                        lineHeight = (settings.fontSize * scale * settings.lineHeight).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                    top = 12.dp, bottom = after, heading = true,
                )
            }
            is NovelBlock.Image -> PagedRow.Picture(block.src)
            NovelBlock.Divider -> PagedRow.Text(
                AnnotatedString("*   *   *"),
                body.copy(color = palette.textSecondary, textAlign = TextAlign.Center),
                top = 12.dp + after / 2, bottom = 12.dp + after / 2, heading = false,
            )
        }
    }
}

/** These settings with everything that doesn't change the text's layout set to its default. */
internal fun NovelReaderSettings.layoutOnly(): NovelReaderSettings {
    val d = NovelReaderSettings()
    return copy(
        brightnessSwipe = d.brightnessSwipe,
        brightness = d.brightness,
        volumeKeys = d.volumeKeys,
        statusLine = d.statusLine,
        autoScrollSpeed = d.autoScrollSpeed,
    )
}

/** True for a chapter's first row: it starts a new page. */
private fun startsChapter(items: List<NovelItem>, index: Int): Boolean {
    val item = items[index]
    if (item is NovelItem.Header || index == 0) return true
    val prev = items[index - 1]
    val prevChapter = when (prev) {
        is NovelItem.Header -> prev.chapter.id
        is NovelItem.Block -> prev.chapterId
    }
    return prevChapter != (item as NovelItem.Block).chapterId
}

/**
 * The text of the loaded chapters as pages turned sideways. Each row is measured with the reader's
 * own style and the pages are filled line by line ([paginate]), so a paragraph can go on over the
 * next page. Taps on the left and right thirds turn the page, the middle toggles the bars.
 * Restores [NovelReaderViewModel.anchor] once per session (and after the layout changes), then
 * reports the first row on the page like the scrolling list does.
 */
@Composable
internal fun NovelPagedText(
    vm: NovelReaderViewModel,
    palette: NovelPalette,
    topPad: Dp,
    bottomPad: Dp,
    onTapCentre: () -> Unit,
    onTurned: () -> Unit,
    /** A block was long-pressed (chapter id, block index, character offset in the block's text or null). */
    onLongPress: (String, Int, Int?) -> Unit = { _, _, _ -> },
    /** Page turns from the volume keys: +1 forward, -1 back. */
    pageTurns: Flow<Int> = emptyFlow(),
) {
    // Only what shapes the pages: brightness or the volume keys changing must not paginate again.
    val settings = vm.settings.layoutOnly()
    val items = vm.items
    val session = vm.session
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val body = novelBodyStyle(settings, palette)

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val widthPx = constraints.maxWidth
        val pageTop = topPad + 16.dp
        val pageBottom = bottomPad + 48.dp
        // A couple of pixels spare: gaps are drawn in dp, which can round up.
        val pageHeightPx = (with(density) { (maxHeight - pageTop - pageBottom).roundToPx() } - 2).coerceAtLeast(1)
        val sidePx = with(density) { settings.sideMargin.dp.roundToPx() }
        val textWidth = (widthPx - 2 * sidePx).coerceAtLeast(1)

        val rows = remember(items, settings, palette) { items.map { pagedRow(it, settings, palette, body) } }
        // Measuring is the slow part: a chapter appended keeps the rows measured before it.
        val cache = remember(settings, textWidth, density) { HashMap<String, PageMeasure>() }
        val measures = remember(rows, textWidth, density) {
            rows.mapIndexed { i, row ->
                val breakBefore = startsChapter(items, i)
                when (row) {
                    is PagedRow.Picture -> PageMeasure.Whole(breakBefore)
                    is PagedRow.Text -> {
                        // Keyed by the words too: a translation replaces a block's text in place.
                        val known = cache.getOrPut("${items[i].key}:${row.text.hashCode()}") {
                            val layout = measurer.measure(row.text, row.style, constraints = Constraints(minWidth = textWidth, maxWidth = textWidth), density = density)
                            PageMeasure.Lines(
                                top = with(density) { row.top.roundToPx() },
                                bottom = with(density) { row.bottom.roundToPx() },
                                lineTops = List(layout.lineCount) { layout.getLineTop(it).toInt() },
                                lineBottoms = List(layout.lineCount) { layout.getLineBottom(it).toInt() },
                            )
                        } as PageMeasure.Lines
                        known.copy(breakBefore = breakBefore)
                    }
                }
            }
        }
        val pages = remember(measures, pageHeightPx) { paginate(measures, pageHeightPx) }
        val currentPages by rememberUpdatedState(pages)
        val pagerState = rememberPagerState { currentPages.size }
        val currentMeasures by rememberUpdatedState(measures)
        val currentItems by rememberUpdatedState(items)
        val currentRows by rememberUpdatedState(rows)
        val currentTextWidth by rememberUpdatedState(textWidth)
        val currentSidePx by rememberUpdatedState(sidePx)
        var restored by remember(session) { mutableStateOf(false) }

        // A new chapter list, other text settings, another screen size or text translated in place:
        // back to the same spot.
        LaunchedEffect(session, settings, textWidth, pageHeightPx, vm.textVersion) {
            restored = false
            val target = anchorPage(currentItems, currentMeasures, currentPages, vm.anchor)
            if (target in 0 until pagerState.pageCount) pagerState.scrollToPage(target)
            restored = true
            // The restored page is not reported: its first slice sits at or before the real anchor,
            // so saving it would snap the position back at every relayout (e.g. on rotation).
            // Only report once the user has moved to another page.
            val restoredPage = pagerState.currentPage
            var moved = false
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (!shouldReportPage(moved, restoredPage, page)) return@collect
                    moved = true
                    val ps = currentPages
                    val slices = ps.getOrNull(page)?.slices ?: return@collect
                    val first = slices.first()
                    val last = slices.last()
                    val anchor = when (val item = currentItems.getOrNull(first.item)) {
                        is NovelItem.Header -> NovelAnchor(item.chapter.id, 0, 0f)
                        is NovelItem.Block -> {
                            val m = currentMeasures[first.item]
                            NovelAnchor(item.chapterId, item.index, if (m is PageMeasure.Lines) fractionOfLine(m, first.from) else 0f)
                        }
                        null -> return@collect
                    }
                    val lastMeasure = currentMeasures[last.item]
                    val lastWhole = lastMeasure !is PageMeasure.Lines || last.to >= lastMeasure.lineCount
                    vm.onPosition(session, anchor, if (lastWhole) last.item else last.item - 1)
                }
        }
        LaunchedEffect(session, pagerState) {
            snapshotFlow { restored && pagerState.isScrollInProgress }.collect { if (it) onTurned() }
        }
        // Read aloud: turn to the page of the spoken sentence until the reader turns pages by hand.
        LaunchedEffect(pagerState) {
            pagerState.interactionSource.interactions.collect {
                if (it is DragInteraction.Start && vm.readAloud != null) vm.changeFollowVoice(false)
            }
        }
        LaunchedEffect(session, pagerState) {
            snapshotFlow { vm.readAloud?.takeIf { restored && vm.followVoice }?.let { SpokenRange(it.chapterId, it.block, it.start, it.end) } }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { s ->
                    val target = spokenPage(currentItems, currentMeasures, currentPages, s) ?: return@collect
                    if (target != pagerState.currentPage) pagerState.animateScrollToPage(target, animationSpec = Motion.medium())
                }
        }

        LaunchedEffect(pagerState, pageTurns) {
            pageTurns.collect { delta ->
                if (vm.readAloud != null) vm.changeFollowVoice(false)
                turn(pagerState, delta)
            }
        }

        val spoken = vm.spoken
        val spokenColor = spokenColor(palette)
        val scope = rememberCoroutineScope()
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { page -> pages.getOrNull(page)?.slices?.firstOrNull()?.let { "${items.getOrNull(it.item)?.key}:${it.from}" } ?: page },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pagerState) {
                    detectTapGestures(
                        onLongPress = { at ->
                            val page = currentPages.getOrNull(pagerState.currentPage) ?: return@detectTapGestures
                            val hit = sliceHit(page, currentMeasures, at.y - pageTop.toPx()) ?: return@detectTapGestures
                            val block = currentItems.getOrNull(hit.item) as? NovelItem.Block ?: return@detectTapGestures
                            // The page measured this row at the text width; measuring it again the same way
                            // gives the layout the slice was cut from, so the press maps to the block's text.
                            val row = currentRows.getOrNull(hit.item) as? PagedRow.Text
                            val offset = row?.let {
                                val layout = measurer.measure(
                                    it.text, it.style,
                                    constraints = Constraints(minWidth = currentTextWidth, maxWidth = currentTextWidth),
                                    density = density,
                                )
                                characterAt(layout, at.x - currentSidePx, hit.yInText)
                            }
                            onLongPress(block.chapterId, block.index, offset)
                        },
                    ) { tap ->
                        val third = size.width / 3f
                        val turns = tap.x < third || tap.x > 2 * third
                        if (turns && vm.readAloud != null) vm.changeFollowVoice(false)
                        when {
                            tap.x < third -> scope.launch { turn(pagerState, -1) }
                            tap.x > 2 * third -> scope.launch { turn(pagerState, 1) }
                            else -> onTapCentre()
                        }
                    }
                },
        ) { page ->
            val slices = pages.getOrNull(page)?.slices.orEmpty()
            Column(Modifier.fillMaxSize().padding(top = pageTop, bottom = pageBottom)) {
                for (slice in slices) {
                    val row = rows.getOrNull(slice.item) ?: continue
                    val measure = measures.getOrNull(slice.item)
                    if (slice.gap > 0) Spacer(Modifier.height(with(density) { slice.gap.toDp() }))
                    when (row) {
                        is PagedRow.Picture -> AsyncImage(
                            model = row.src,
                            contentDescription = stringResource(R.string.novel_illustration),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        is PagedRow.Text -> {
                            val lines = measure as? PageMeasure.Lines ?: continue
                            // The highlight only adds a background, so the measured lines still fit.
                            val text = (items.getOrNull(slice.item) as? NovelItem.Block)
                                ?.let { row.text.withSpoken(spoken, it.chapterId, it.index, spokenColor) }
                                ?: row.text
                            TextSlice(row, text, lines, slice, settings.sideMargin.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Lines [PageSlice.from] until [PageSlice.to] of a text row: the whole text, shifted up and cut. */
@Composable
private fun TextSlice(row: PagedRow.Text, text: AnnotatedString, lines: PageMeasure.Lines, slice: PageSlice, side: Dp) {
    val top = lines.lineTops[slice.from]
    val height = lines.lineBottoms[slice.to - 1] - top
    Text(
        text = text,
        style = row.style,
        modifier = Modifier
            .padding(start = side, end = side)
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                layout(placeable.width, height) { placeable.place(0, -top) }
            }
            .then(if (row.heading && slice.from == 0) Modifier.semantics { heading() } else Modifier),
    )
}

private suspend fun turn(state: PagerState, delta: Int) {
    val target = (state.currentPage + delta).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
    if (target != state.currentPage) state.animateScrollToPage(target, animationSpec = Motion.medium())
}

/** The page with the spoken sentence's first line, or null when its row isn't loaded. */
private fun spokenPage(items: List<NovelItem>, measures: List<PageMeasure>, pages: List<NovelPage>, s: SpokenRange): Int? {
    if (pages.isEmpty()) return null
    val own = if (s.block < 0) -1 else items.indexOfFirst { it is NovelItem.Block && it.chapterId == s.chapterId && it.index == s.block }
    // A hidden title (or title heading) has no row of its own: the chapter's first page.
    val index = if (own >= 0) own else chapterStartIndex(items, s.chapterId)
    if (index < 0) return null
    val item = items[index]
    val m = measures.getOrNull(index)
    val length = if (own >= 0) (item as? NovelItem.Block)?.block?.textLength() ?: 0 else 0
    val line = if (m is PageMeasure.Lines && length > 0) lineAtFraction(m, s.start.toFloat() / length) else 0
    return pageOf(pages, index, line)
}

/** The page that shows [anchor]: its row, at the line its fraction points to. */
private fun anchorPage(items: List<NovelItem>, measures: List<PageMeasure>, pages: List<NovelPage>, anchor: NovelAnchor): Int {
    if (pages.isEmpty()) return 0
    val index = novelItemIndex(items, anchor)
    if (index < 0) return 0
    val m = measures.getOrNull(index)
    val line = if (m is PageMeasure.Lines && items[index] is NovelItem.Block) lineAtFraction(m, anchor.fraction) else 0
    return pageOf(pages, index, line)
}
