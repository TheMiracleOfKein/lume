package com.lume.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.lume.app.ui.components.LumeRefreshIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.tracker.TitleFormat
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.inReadingOrder
import com.lume.app.domain.dedupeChapters
import com.lume.app.data.db.TitleSourceEntity
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.components.openInBrowser
import com.lume.app.ui.components.shareLink
import com.lume.app.ui.components.PageBookmarkRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.nav.openReader
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(nav: NavHostController, sourceId: String, titleId: String) {
    // Large tablets and wide windows: details on the left, the chapter list beside them.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (windowWidth >= TWO_PANE_MIN_WIDTH) {
        Row(Modifier.fillMaxSize().background(AppColors.background)) {
            Box(Modifier.weight(0.45f).fillMaxHeight()) {
                TitleDetailsPane(nav, sourceId, titleId, showChaptersButton = false)
            }
            VerticalDivider(color = AppColors.divider)
            Box(Modifier.weight(0.55f).fillMaxHeight()) {
                ChaptersScreen(nav, sourceId, titleId, embedded = true)
            }
        }
    } else {
        TitleDetailsPane(nav, sourceId, titleId, showChaptersButton = true)
    }
}

private val TWO_PANE_MIN_WIDTH = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleDetailsPane(nav: NavHostController, sourceId: String, titleId: String, showChaptersButton: Boolean) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val vm: TitleViewModel = viewModel { TitleViewModel(container, sourceId, titleId) }
    val details = vm.details
    val chapters = vm.chapters
    val bookmarked by vm.inLibrary.collectAsState()
    val subscribed by vm.subscribed.collectAsState()
    val autoDownloadMode by vm.autoDownloadMode.collectAsState()
    val progressState by vm.progress.collectAsState()
    val links by vm.trackerLinks.collectAsState()
    val pageBookmarks by vm.pageBookmarks.collectAsState()
    val titleSources by vm.titleSources.collectAsState()
    var showTrackers by rememberSaveable { mutableStateOf(false) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val bookmarkScope = rememberCoroutineScope()
    val bookmarkDeleted = stringResource(R.string.bookmark_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    // One row per chapter, whatever number of teams or sources have it.
    val chapterCount = remember(chapters) { dedupeChapters(chapters).size }

    // Moved to another main source: that title replaces this screen.
    LaunchedEffect(vm.movedTo) {
        val (newSource, newTitle) = vm.movedTo ?: return@LaunchedEffect
        nav.navigate(Routes.title(newSource, newTitle)) {
            popUpTo(Routes.Title) { inclusive = true }
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        details?.title ?: stringResource(R.string.title_fallback),
                        color = AppColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BackButton(onClick = { nav.popBackStack() })
                },
                actions = {
                    val webUrl = remember(sourceId, titleId) {
                        container.sources.sourceById(sourceId)?.webUrl(titleId)
                    }
                    if (webUrl != null) {
                        IconButton(onClick = { openInBrowser(context, webUrl) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.cd_open_in_browser),
                                tint = AppColors.textPrimary,
                            )
                        }
                        IconButton(onClick = { shareLink(context, details?.title, webUrl) }) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.cd_share),
                                tint = AppColors.textPrimary,
                            )
                        }
                    }
                    // The bell: update checks look only at titles that have it.
                    if (bookmarked) {
                        IconButton(
                            onClick = {
                                vm.toggleSubscribed { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Icon(
                                if (subscribed) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                                contentDescription = stringResource(if (subscribed) R.string.cd_unsubscribe else R.string.cd_subscribe),
                                tint = if (subscribed) AppColors.accentText else AppColors.textPrimary,
                            )
                        }
                    }
                    IconButton(
                        onClick = { vm.toggleLibrary() },
                    ) {
                        Icon(
                            if (bookmarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(if (bookmarked) R.string.cd_remove_from_library else R.string.cd_add_to_library),
                            tint = if (bookmarked) AppColors.accentText else AppColors.textPrimary,
                        )
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val refreshState = rememberPullToRefreshState()
        AnimatedLoadState(
            state = loadStateOf(vm.loading, vm.error),
            modifier = Modifier.fillMaxSize(),
        ) { pane ->
            when (pane) {
                LoadState.Loading -> LoadingBox(Modifier.padding(padding))
                LoadState.Error -> ErrorRetryBox(
                    message = vm.error.orEmpty(),
                    onRetry = vm::refresh,
                    onCloudflare = vm.cloudflareUri?.let { { vm.solveChallenge() } },
                    modifier = Modifier.padding(padding),
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = vm::refresh,
                    state = refreshState,
                    indicator = {
                        LumeRefreshIndicator(refreshState, vm.refreshing, Modifier.align(Alignment.TopCenter))
                    },
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    val d = details ?: return@PullToRefreshBox
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            // Wide screens: keep the details at a readable width, centred.
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 840.dp)
                            .padding(16.dp),
                    ) {
                        TitleHeader(
                            d = d,
                            sourceId = sourceId,
                            hasProgress = progressState != null,
                            canRead = chapters.isNotEmpty(),
                            onRead = {
                                val p = progressState
                                if (p != null) {
                                    // The exact spot, even if that chapter's own row says "finished".
                                    nav.openReader(sourceId, titleId, p.chapterId, p.pageIndex, p.pageOffset)
                                } else {
                                    // No progress yet: start from the first chapter, not the newest one.
                                    chapters.inReadingOrder().firstOrNull()?.let {
                                        nav.openReader(sourceId, titleId, it.id)
                                    }
                                }
                            },
                        )
                        val suggestions = titleSources.filter { it.state == TitleSourceEntity.SUGGESTED }
                        AnimatedVisibility(
                            visible = suggestions.isNotEmpty(),
                            enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                            exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                        ) {
                            AlsoOnCard(
                                names = suggestions.map { container.sources.sourceById(it.linkSourceId)?.manifest?.name ?: it.linkSourceId }.distinct(),
                                enabled = !vm.busy,
                                onOpen = { showSources = true },
                                onLink = vm::linkAllSuggestions,
                                onDismiss = vm::dismissAllSuggestions,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        if (showChaptersButton) {
                            OutlinedButton(
                                onClick = { nav.navigate(Routes.chapters(sourceId, titleId)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.title_chapters_list, chapterCount),
                                    color = AppColors.textPrimary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { showSources = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val names = listOf(sourceId) + titleSources
                                .filter { it.state == TitleSourceEntity.LINKED }
                                .map { it.linkSourceId }
                            Text(
                                stringResource(
                                    R.string.title_sources,
                                    names.distinct().joinToString { container.sources.sourceById(it)?.manifest?.name ?: it },
                                ),
                                color = AppColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showTrackers = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val linked = if (links.isEmpty()) {
                                stringResource(R.string.none_short)
                            } else {
                                links.joinToString { it.tracker }
                            }
                            Text(
                                stringResource(R.string.title_trackers, linked),
                                color = AppColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (d.genres.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                // A tap opens this source's catalogue with the genre in its filters.
                                d.genres.forEach { genre ->
                                    Text(
                                        text = genre,
                                        color = AppColors.textSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AppColors.card)
                                            .clickable { nav.navigate(Routes.browse(sourceId, genre)) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        if (pageBookmarks.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.bookmarks_title),
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Column {
                                pageBookmarks.forEach { bookmark ->
                                    key(bookmark.id) {
                                        PageBookmarkRow(
                                            bookmark = bookmark,
                                            onOpen = {
                                                nav.openReader(
                                                    sourceId,
                                                    titleId,
                                                    bookmark.chapterId,
                                                    bookmark.pageIndex,
                                                    bookmark.pageOffset,
                                                )
                                            },
                                            onDelete = {
                                                vm.deletePageBookmark(bookmark.id)
                                                bookmarkScope.launch {
                                                    snackbar.currentSnackbarData?.dismiss()
                                                    val result = snackbar.showSnackbar(
                                                        bookmarkDeleted,
                                                        actionLabel = undoLabel,
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) vm.restorePageBookmark(bookmark)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        d.description?.let {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.title_description),
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        val similar = vm.similar
                        AnimatedVisibility(
                            visible = similar != null && similar.items.isNotEmpty(),
                            enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                            exit = shrinkVertically(Motion.medium()) + fadeOut(Motion.short()),
                        ) {
                            similar?.let { result ->
                                SimilarTitlesRow(result, lookingUp = vm.similarLookup, onOpen = vm::openSimilar)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSources) {
        TitleSourcesDialog(vm = vm, titleName = details?.title.orEmpty(), onDismiss = { showSources = false })
    }

    // "Similar titles": a finished lookup opens the title or the source's search, once.
    vm.similarRoute?.let { route ->
        LaunchedEffect(route) {
            vm.similarRouteTaken()
            nav.navigate(route)
        }
    }
    // Leaving the screen drops a lookup still running, so it can't navigate from elsewhere.
    DisposableEffect(vm) { onDispose { vm.cancelSimilar() } }
    vm.similarNotFound?.let { missing ->
        AlertDialog(
            onDismissRequest = vm::dismissSimilarNotFound,
            title = { Text(stringResource(R.string.similar_not_found_title, missing.sourceName)) },
            text = {
                Text(
                    stringResource(
                        if (missing.failed) R.string.similar_search_failed_text else R.string.similar_not_found_text,
                        missing.sourceName,
                        missing.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissSimilarNotFound()
                    nav.navigate(Routes.searchAll(missing.title))
                }) { Text(stringResource(R.string.similar_search_everywhere), color = AppColors.accentText) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissSimilarNotFound) {
                    Text(stringResource(R.string.action_cancel), color = AppColors.textSecondary)
                }
            },
            containerColor = AppColors.surface,
        )
    }

    if (showTrackers) {
        TrackersDialog(
            sourceId = sourceId,
            titleId = titleId,
            titleName = details?.title.orEmpty(),
            chapters = chapters,
            coverUrl = details?.coverUrl,
            typeLabel = details?.typeLabel,
            onDismiss = { showTrackers = false },
        )
    }

    vm.duplicate?.let { dup ->
        val linkedText = stringResource(R.string.title_duplicate_linked_done, dup.titleName)
        AlertDialog(
            onDismissRequest = vm::dismissDuplicate,
            title = { Text(stringResource(R.string.title_duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        if (dup.linked) R.string.title_duplicate_text_linked else R.string.title_duplicate_text,
                        dup.titleName,
                        dup.sourceName,
                    ),
                )
            },
            confirmButton = {
                if (dup.linked) {
                    TextButton(onClick = {
                        vm.dismissDuplicate()
                        nav.navigate(Routes.title(dup.sourceId, dup.titleId))
                    }) { Text(stringResource(R.string.title_duplicate_open)) }
                } else {
                    TextButton(onClick = {
                        vm.linkToDuplicate { Toast.makeText(context, linkedText, Toast.LENGTH_SHORT).show() }
                    }) { Text(stringResource(R.string.title_duplicate_link)) }
                }
            },
            dismissButton = {
                TextButton(onClick = vm::addDespiteDuplicate) { Text(stringResource(R.string.title_duplicate_add)) }
            },
        )
    }
}

/** The label of a tracker entry's format: "Light novel", "Manhwa" … */
internal fun titleFormatLabel(format: TitleFormat): Int = when (format) {
    TitleFormat.Manga -> R.string.type_manga
    TitleFormat.Manhwa -> R.string.type_manhwa
    TitleFormat.Manhua -> R.string.type_manhua
    TitleFormat.OneShot -> R.string.type_one_shot
    TitleFormat.Doujinshi -> R.string.type_doujinshi
    TitleFormat.Comic -> R.string.type_comic
    TitleFormat.LightNovel -> R.string.type_light_novel
    TitleFormat.Novel -> R.string.type_novel
    TitleFormat.WebNovel -> R.string.type_web_novel
}

/** The cover, names, facts and the Read / Continue button at the top of a title. */
@Composable
internal fun TitleHeader(
    d: TitleDetails,
    sourceId: String,
    hasProgress: Boolean,
    canRead: Boolean,
    onRead: () -> Unit,
) {
                        Row {
                            CoverImage(
                                url = d.coverUrl,
                                width = 120.dp,
                                height = 170.dp,
                                radius = 8.dp,
                                sourceId = sourceId,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    d.title,
                                    color = AppColors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                d.altTitle?.let {
                                    Text(
                                        it,
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                d.status?.let {
                                    Text(
                                        stringResource(R.string.title_status, it),
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                if (d.authors.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.title_by, d.authors.joinToString()),
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                val artOnly = d.artists.filter { it !in d.authors }
                                if (artOnly.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.title_art_by, artOnly.joinToString()),
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                // "Manhwa · 2018 · ★ 9.3"
                                val facts = listOfNotNull(
                                    d.typeLabel,
                                    d.year?.toString(),
                                    d.score?.let { stringResource(R.string.title_score, String.format(LocalConfiguration.current.locales[0], "%.1f", it)) },
                                )
                                if (facts.isNotEmpty()) {
                                    Text(facts.joinToString(" · "), color = AppColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onRead,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.accent,
                                        contentColor = AppColors.onAccent,
                                    ),
                                    enabled = canRead,
                                ) {
                                    Text(
                                        if (hasProgress) {
                                            stringResource(R.string.action_continue)
                                        } else {
                                            stringResource(R.string.action_read)
                                        },
                                    )
                                }
                            }
                        }
}
