package com.lume.app.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.PaddingValues
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.components.listBottomPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import com.lume.app.ui.components.ChoiceChip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.lume.app.ui.components.FilterPanel
import com.lume.app.ui.components.QuickSearchBar
import com.lume.app.ui.components.BackButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.SegmentTab
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.components.loadStateOf
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem

/**
 * [browseSourceId]: browse that source (opened from Sources) instead of the active one.
 * [genre]: opened from a genre on a title screen; the first chip lists the titles with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(nav: NavHostController, browseSourceId: String? = null, genre: String? = null) {
    val container = LocalAppContainer.current
    val vm: CatalogViewModel = viewModel { CatalogViewModel(container, browseSourceId, genre) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val browseName = remember(browseSourceId) {
        browseSourceId?.let { id -> container.sources.sourceById(id)?.manifest?.name ?: id }
    }
    val bookmarks by container.dao.observeBookmarks().collectAsState(initial = emptyList())
    // One list state per tab and section: switching keeps each list's scroll position.
    val popularList = rememberLazyListState()
    val latestList = rememberLazyListState()
    val sectionLists = remember { mutableMapOf<String, LazyListState>() }
    // null: the popular list; else one of the source's browse sections.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    // True while the genre chip (only there when opened from a genre) is the chosen one.
    var genreOpen by rememberSaveable { mutableStateOf(vm.genreFeed != null) }
    val sections = vm.sections
    LaunchedEffect(sections) {
        if (section != null && sections.none { it.id == section }) section = null
    }
    val genreFeed = vm.genreFeed
    val genreList = rememberLazyListState()
    val popularFeed = when {
        genreOpen && genreFeed != null -> genreFeed
        else -> section?.let { vm.sectionFeed(it) } ?: vm.popular
    }

    LaunchedEffect(tab, section, genreOpen, vm.sectionsEpoch) {
        when (tab) {
            0 -> vm.ensureLoaded(popularFeed)
            1 -> vm.ensureLoaded(vm.latest)
        }
    }
    val searchList = rememberLazyListState()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    if (filtersOpen) {
        ModalBottomSheet(
            onDismissRequest = { filtersOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.surface,
        ) {
            FilterPanel(
                initial = vm.filters,
                defaults = vm.defaultFilters,
                loading = vm.filtersLoading,
                sourceId = vm.filtersSourceId,
                note = stringResource(R.string.filters_no_query).takeIf { vm.filtersNeedEmptyQuery && vm.query.isNotBlank() },
                onApply = { next ->
                    filtersOpen = false
                    keyboard?.hide()
                    focusManager.clearFocus()
                    vm.applyFilters(next)
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(LumeInsets.statusBar),
    ) {
        if (browseName != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = { nav.popBackStack() })
                Text(
                    browseName,
                    color = AppColors.textPrimary,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (tab != 2 || vm.searchActive) {
            CatalogSearchRow(vm, onOpenFilters = {
                vm.prepareFilters()
                filtersOpen = true
            })
        }
        if (vm.searchActive) {
            BackHandler { vm.clearSearch() }
            CatalogFeedList(
                vm = vm,
                feed = vm.searchFeed,
                listState = searchList,
                onOpen = { id -> nav.navigate(Routes.title(vm.sourceId, id)) },
            )
            return@Column
        }
        Row(modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 4.dp, vertical = 8.dp)) {
            SegmentTab(stringResource(R.string.catalog_tab_popular), selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            SegmentTab(stringResource(R.string.catalog_tab_updates), selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
            // A browsed source has no library tab: the library is the same for every source.
            if (browseName == null) {
                SegmentTab(stringResource(R.string.catalog_tab_library), selected = tab == 2, modifier = Modifier.weight(1f)) { tab = 2 }
            }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = Motion.contentSwap(),
            label = "catalogTab",
        ) { tabPane ->
            when (tabPane) {
                0 -> Column(Modifier.fillMaxSize()) {
                    if (sections.isNotEmpty() || genreFeed != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (genreFeed != null && vm.genre != null) {
                                ChoiceChip(vm.genre, selected = genreOpen, onClick = { genreOpen = true })
                            }
                            ChoiceChip(
                                stringResource(R.string.catalog_tab_popular),
                                selected = !genreOpen && section == null,
                                onClick = { genreOpen = false; section = null },
                            )
                            sections.forEach { s ->
                                ChoiceChip(s.name, selected = !genreOpen && section == s.id, onClick = { genreOpen = false; section = s.id })
                            }
                        }
                    }
                    CatalogFeedList(
                        vm = vm,
                        feed = popularFeed,
                        listState = when {
                            genreOpen && genreFeed != null -> genreList
                            else -> section?.let { id -> sectionLists.getOrPut(id) { LazyListState() } } ?: popularList
                        },
                        onOpen = { id -> nav.navigate(Routes.title(vm.sourceId, id)) },
                    )
                }
                1 -> CatalogFeedList(
                    vm = vm,
                    feed = vm.latest,
                    listState = latestList,
                    onOpen = { id -> nav.navigate(Routes.title(vm.sourceId, id)) },
                )
                else -> {
                    if (bookmarks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.catalog_library_empty), color = AppColors.textSecondary)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = listBottomPadding())) {
                            items(bookmarks, key = { "${it.sourceId}:${it.titleId}" }) { item ->
                                TitleListRow(
                                    modifier = animatedItem(),
                                    title = item.titleName,
                                    coverUrl = item.coverUrl,
                                    typeLabel = item.typeLabel,
                                    onClick = { nav.navigate(Routes.title(item.sourceId, item.titleId)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Search and filters inside the source's own catalogue (not across sources). */
@Composable
private fun CatalogSearchRow(vm: CatalogViewModel, onOpenFilters: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickSearchBar(
            value = vm.query,
            onValueChange = { vm.query = it },
            onSubmit = {
                keyboard?.hide()
                focusManager.clearFocus()
                vm.submitSearch()
            },
            hint = stringResource(R.string.catalog_search_hint),
            modifier = Modifier.weight(1f),
        )
        if (vm.searchActive || vm.query.isNotEmpty()) {
            IconButton(onClick = {
                keyboard?.hide()
                focusManager.clearFocus()
                vm.clearSearch()
            }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.catalog_search_clear), tint = AppColors.textSecondary)
            }
        }
        IconButton(onClick = onOpenFilters) {
            val filtersOn = stringResource(R.string.cd_filters_active)
            Box(if (vm.filtersActive) Modifier.semantics { stateDescription = filtersOn } else Modifier) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.filters_title),
                    tint = if (vm.filtersActive) AppColors.accentText else AppColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CatalogFeedList(
    vm: CatalogViewModel,
    feed: CatalogFeed,
    listState: LazyListState,
    onOpen: (String) -> Unit,
) {
    val shouldLoadMore by remember(feed, listState) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            feed.hasNext && !feed.loadingMore && !feed.loading && !feed.moreError && last >= feed.items.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore(feed)
    }
    AnimatedLoadState(
        state = loadStateOf(feed.loading && feed.items.isEmpty(), feed.error.takeIf { feed.items.isEmpty() }),
        modifier = Modifier.fillMaxSize(),
    ) { pane ->
        when (pane) {
            LoadState.Loading -> LoadingBox()
            LoadState.Error -> ErrorRetryBox(
                message = feed.error.orEmpty(),
                onRetry = { vm.ensureLoaded(feed, force = true) },
                onCloudflare = feed.cloudflareUri?.let { { vm.solveChallenge(feed) } },
            )
            else -> if (catalogFeedIsEmpty(feed.loadedOnce, feed.loading, feed.error, feed.items.size)) {
                EmptyBox(stringResource(R.string.catalog_feed_empty))
            } else LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = listBottomPadding()),
            ) {
                items(feed.items, key = { it.id }) { item ->
                    TitleListRow(
                        modifier = animatedItem(),
                        title = item.title,
                        coverUrl = item.coverUrl,
                        subtitle = item.latestChapter ?: item.updatedLabel,
                        typeLabel = item.typeLabel,
                        onClick = { onOpen(item.id) },
                    )
                }
                if (feed.moreError) {
                    item(key = "more-error") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.catalog_more_failed),
                                color = AppColors.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.loadMore(feed) }) {
                                Text(stringResource(R.string.action_retry), color = AppColors.accentText)
                            }
                        }
                    }
                }
                if (feed.loadingMore) {
                    item(key = "loading-more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AppColors.accent)
                        }
                    }
                }
            }
        }
    }
}
