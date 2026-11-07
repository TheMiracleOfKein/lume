package com.lume.app.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.lume.app.LocalAppContainer
import com.lume.app.data.page.PageLayout
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.page.PageFit
import com.lume.app.domain.page.PageRect
import com.lume.app.domain.page.PanelOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.ZoomFocalPoint
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import kotlin.math.min

// The paged modes (left to right, right to left): one page, half a wide page or a two-page
// spread per screen, cropped borders, and guided panel view.

/** Guided view: which panel of which strip item is shown; -1 = the whole page. */
private data class PanelStep(val stripIndex: Int, val panel: Int)

/** A panel fills this much of the screen, leaving a margin around it. */
private const val PANEL_FILL = 0.94f

@Composable
internal fun PagedReader(
    vm: ReaderViewModel,
    pageTurns: MutableSharedFlow<Int>,
    pageSeeks: MutableSharedFlow<Int>,
    colorFilter: ColorFilter?,
    onToggleChrome: () -> Unit,
    onUserScroll: () -> Unit,
    onEdge: (forward: Boolean) -> Unit,
) {
    val settings = vm.settings
    val analyzer = LocalAppContainer.current.pageAnalyzer
    val session = remember { vm.session }
    val pagerState = remember {
        PagerState(currentPage = stripIndexOf(vm.strip, vm.anchor).coerceAtLeast(0)) { vm.strip.size.coerceAtLeast(1) }
    }
    val rtl = settings.mode == ReaderMode.Rtl
    /** Zoom of each composed pager page, for guided panel view. */
    val zoomStates = remember { HashMap<Int, ZoomableState>() }
    val step = remember { arrayOfNulls<PanelStep>(1) }

    // Split pages or spreads changed under the pager: stay on the page that was on screen.
    LaunchedEffect(pagerState) {
        snapshotFlow { vm.strip }
            .distinctUntilChanged()
            .collect { strip ->
                val wanted = stripIndexOf(strip, vm.anchor)
                if (wanted >= 0 && wanted != pagerState.currentPage && !pagerState.isScrollInProgress) {
                    pagerState.scrollToPage(wanted)
                }
            }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to vm.strip }
            .distinctUntilChanged()
            .collect { (page, strip) ->
                if (step[0]?.stripIndex != page) step[0] = null
                val anchor = anchorAt(strip, page) ?: return@collect
                val finished = when (val item = strip.getOrNull(page)) {
                    is ReaderStripItem.Page ->
                        if (item.indexInChapter == item.chapterPageCount - 1 && (item.part == PagePart.Whole || item.secondPart)) {
                            setOf(item.chapter.id)
                        } else {
                            emptySet()
                        }
                    is ReaderStripItem.Spread ->
                        if (item.lastIndexInChapter == item.first.chapterPageCount - 1) setOf(item.chapter.id) else emptySet()
                    is ReaderStripItem.Divider -> setOf(item.previous.id)
                    null -> emptySet()
                }
                vm.onPosition(session, anchor, finished, page, page)
            }
    }
    val chromeHider = rememberChromeHider(settings.immersive, onUserScroll)
    LaunchedEffect(pagerState) {
        pageTurns.collect { delta ->
            onUserScroll()
            val current = pagerState.currentPage
            // Guided view: step through the panels of this page before turning it.
            if (vm.settings.guidedPanels) {
                val moved = stepPanel(vm, analyzer, current, delta, zoomStates[current], step, animate = !vm.settings.eInk)
                if (moved) return@collect
            }
            val wanted = current + delta
            val target = wanted.coerceIn(0, pagerState.pageCount - 1)
            if (target != wanted) onEdge(delta > 0)
            zoomStates[current]?.let { if (vm.settings.guidedPanels) it.resetZoom(withAnimation = false) }
            if (vm.settings.eInk) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        pageSeeks.collect { page ->
            val index = stripIndexOf(vm.strip, vm.currentChapterId, page)
            if (index >= 0) pagerState.scrollToPage(index)
        }
    }
    val edgeNotifier = rememberEdgeNotifier(
        horizontal = true,
        forwardIsNegative = !rtl,
        canForward = { pagerState.canScrollForward },
        canBackward = { pagerState.canScrollBackward },
        onEdge = onEdge,
    )
    val onPagerTap: (Float, Int) -> Unit = { x, width ->
        val third = width / 3f
        val zone = when {
            !vm.settings.tapZones -> 0
            x < third -> -1
            x > width - third -> 1
            else -> 0
        }
        // In RTL the left side is "forward".
        if (zone == 0) onToggleChrome() else pageTurns.tryEmit(if (rtl) -zone else zone)
    }
    val tap by rememberUpdatedState(onPagerTap)
    HorizontalPager(
        state = pagerState,
        reverseLayout = rtl,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeHider)
            .nestedScroll(edgeNotifier),
        key = { index -> vm.strip.getOrNull(index)?.key() ?: index },
    ) { index ->
        val zoom = rememberZoomableState(ZoomSpec(maxZoomFactor = PAGED_MAX_ZOOM))
        DisposableEffect(index, zoom) {
            zoomStates[index] = zoom
            onDispose { if (zoomStates[index] === zoom) zoomStates.remove(index) }
        }
        when (val item = vm.strip.getOrNull(index)) {
            is ReaderStripItem.Page -> {
                val aspect = vm.aspectOf(item.chapter.id, item.indexInChapter)
                val wantLayout = aspect != null && (settings.cropBorders || settings.guidedPanels)
                val layout by produceState(analyzer.cached(item.page.imageUrl), item.page.imageUrl, wantLayout) {
                    if (wantLayout && value == null) value = analyzer.analyze(item.page.imageUrl)
                }
                PagedPageImage(
                    page = item.page,
                    aspect = aspect,
                    onAspect = { vm.reportAspect(item.chapter.id, item.indexInChapter, it) },
                    region = pageRegion(item, layout, settings.cropBorders, settings.guidedPanels),
                    zoomEnabled = settings.doubleTapZoom,
                    guided = settings.guidedPanels,
                    zoomState = zoom,
                    onTap = { x, w -> tap(x, w) },
                    colorFilter = colorFilter,
                )
            }
            is ReaderStripItem.Spread -> SpreadPages(
                first = item.first.page,
                second = item.second?.page,
                rightToLeft = rtl,
                zoomEnabled = settings.doubleTapZoom,
                zoomState = zoom,
                onAspect = { page, aspect ->
                    val p = if (page == item.first.page) item.first else item.second ?: item.first
                    vm.reportAspect(p.chapter.id, p.indexInChapter, aspect)
                },
                onTap = { x, w -> tap(x, w) },
                colorFilter = colorFilter,
            )
            is ReaderStripItem.Divider -> Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { o -> tap(o.x, size.width) }) },
            ) {
                ChapterDividerBand(title = item.chapter.title, fillViewport = true)
            }
            null -> Box(Modifier.fillMaxSize())
        }
    }
}

/**
 * The part of a page that fills the screen: half of a split page, and its content without the
 * border when cropping. Guided view shows whole pages uncropped, so panels line up with the screen.
 */
private fun pageRegion(item: ReaderStripItem.Page, layout: PageLayout?, crop: Boolean, guided: Boolean): PageRect? {
    val half = item.part.region()
    if (guided && half == null) return null
    val bounds = if (crop) layout?.contentBounds else null
    return when {
        half != null && bounds != null -> PageFit.intersect(half, bounds) ?: half
        else -> half ?: bounds
    }
}

/**
 * Moves guided view one panel by [delta] on strip item [index]. Each page goes whole page, then
 * its panels in reading order; true when it moved, false when the page should turn instead.
 */
private suspend fun stepPanel(
    vm: ReaderViewModel,
    analyzer: com.lume.app.data.page.PageAnalyzer,
    index: Int,
    delta: Int,
    zoom: ZoomableState?,
    step: Array<PanelStep?>,
    animate: Boolean,
): Boolean {
    val item = vm.strip.getOrNull(index) as? ReaderStripItem.Page ?: return false
    if (item.part != PagePart.Whole || zoom == null) return false
    val order = if (vm.settings.mode == ReaderMode.Rtl) PanelOrder.RightToLeft else PanelOrder.LeftToRight
    val panels = analyzer.cached(item.page.imageUrl)?.panels(order).orEmpty()
    // One panel (or none found yet): the page is its own panel.
    if (panels.size <= 1) return false
    val current = step[0]?.takeIf { it.stripIndex == index }?.panel ?: -1
    val next = current + delta
    return when {
        next in panels.indices -> {
            step[0] = PanelStep(index, next)
            zoom.focusPanel(panels[next], animate)
            true
        }
        next == -1 -> {
            step[0] = PanelStep(index, -1)
            if (animate) zoom.resetZoom() else zoom.resetZoom(withAnimation = false)
            true
        }
        else -> {
            step[0] = null
            false
        }
    }
}

/** Zooms so that [panel] (fractions of the page) fills the screen, centred. */
@OptIn(ExperimentalTelephotoApi::class)
private suspend fun ZoomableState.focusPanel(panel: PageRect, animate: Boolean) {
    val transformation = contentTransformation
    if (!transformation.isSpecified) return
    val content = transformation.contentSize
    val viewport = coordinateSystem.viewportSize
    if (content.width <= 0f || content.height <= 0f || viewport.width <= 0f || viewport.height <= 0f) return
    // Zoom relative to the page's own pixels: the panel's size against the screen's.
    val target = min(viewport.width / (panel.width * content.width), viewport.height / (panel.height * content.height)) * PANEL_FILL
    val centre = Offset((panel.left + panel.width / 2f) * content.width, (panel.top + panel.height / 2f) * content.height)
    val focal = ZoomFocalPoint.moveToViewportCenter(SpatialOffset(centre, CoordinateSpace.ZoomableContent))
    if (animate) {
        zoomTo(target, focal)
    } else {
        zoomTo(target, focal, animationSpec = androidx.compose.animation.core.snap())
    }
}
