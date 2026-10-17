package com.lume.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
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
import com.lume.app.ui.components.AnimatedLoadState
import com.lume.app.ui.components.CoverCard
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadState
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.QuickSearchBar
import com.lume.app.ui.components.SegmentTab
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.screens.SearchViewModel.Mode
import com.lume.app.ui.screens.SearchViewModel.SourceRow
import com.lume.app.ui.screens.SearchViewModel.SourceState
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion
import com.lume.app.ui.theme.animatedItem
import com.lume.app.ui.theme.fadingItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val networkEpoch by container.http.networkEpoch.collectAsState()

    // A solved Cloudflare check bumps the epoch: refill the sources it was blocking.
    LaunchedEffect(networkEpoch) { if (networkEpoch > 0) vm.retryChallenged() }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                SegmentTab(stringResource(R.string.search_mode_current), vm.mode == Mode.Current, Modifier.weight(1f)) {
                    if (vm.mode != Mode.Current) {
                        vm.mode = Mode.Current
                        vm.search()
                    }
                }
                SegmentTab(stringResource(R.string.search_mode_all), vm.mode == Mode.Global, Modifier.weight(1f)) {
                    if (vm.mode != Mode.Global) {
                        vm.mode = Mode.Global
                        vm.search()
                    }
                }
            }
            QuickSearchBar(
                value = vm.query,
                onValueChange = { vm.query = it },
                onSubmit = {
                    // Give the results the whole screen.
                    keyboard?.hide()
                    focusManager.clearFocus()
                    vm.search()
                },
                hint = stringResource(R.string.search_hint_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AnimatedContent(
                targetState = vm.mode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = Motion.contentSwap(),
                label = "searchMode",
            ) { pane ->
                if (pane == Mode.Current) {
                    CurrentSourceResults(vm, nav, onChallenge = { uri ->
                        scope.launch { container.http.requestChallenge(uri) }
                    })
                } else {
                    GlobalResults(vm, nav, onChallenge = { uri ->
                        scope.launch { container.http.requestChallenge(uri) }
                    })
                }
            }
        }
    }
}

@Composable
private fun CurrentSourceResults(
    vm: SearchViewModel,
    nav: NavHostController,
    onChallenge: (java.net.URI) -> Unit,
) {
    val state = vm.currentState
    val loadState = when (state) {
        null -> LoadState.Empty
        SourceState.Loading -> LoadState.Loading
        is SourceState.Failed -> LoadState.Error
        is SourceState.Done -> if (state.items.isEmpty()) LoadState.Empty else LoadState.Content
    }
    AnimatedLoadState(loadState, Modifier.fillMaxSize()) { target ->
        when (target) {
            LoadState.Loading -> LoadingBox()
            LoadState.Error -> {
                val failed = state as? SourceState.Failed
                ErrorRetryBox(
                    message = failed?.message.orEmpty(),
                    onRetry = { vm.search() },
                    onCloudflare = failed?.challenge?.let { uri -> { onChallenge(uri) } },
                )
            }
            LoadState.Empty -> if (vm.searched) EmptyBox(stringResource(R.string.search_empty)) else Box(Modifier.fillMaxSize())
            LoadState.Content -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items((state as? SourceState.Done)?.items.orEmpty(), key = { it.id }) { item ->
                    TitleListRow(
                        title = item.title,
                        coverUrl = item.coverUrl,
                        subtitle = item.latestChapter,
                        typeLabel = item.typeLabel,
                        modifier = animatedItem(),
                        onClick = { nav.navigate(Routes.title(vm.currentSourceId, item.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalResults(
    vm: SearchViewModel,
    nav: NavHostController,
    onChallenge: (java.net.URI) -> Unit,
) {
    if (!vm.searched) {
        Box(Modifier.fillMaxSize())
        return
    }
    val rows = vm.globalRows
    val finished = vm.globalPending == 0
    // Rows stay in one fixed order (active source first) while results stream in, so nothing
    // jumps around under your finger. Once every source answered, the empty ones fold into a
    // one-line footer.
    val emptyNames = remember(rows, finished) {
        if (!finished) emptyList()
        else rows.filter { (it.state as? SourceState.Done)?.items?.isEmpty() == true }.map { it.sourceName }
    }
    val shown = remember(rows, finished) {
        if (!finished) rows else rows.filterNot { (it.state as? SourceState.Done)?.items?.isEmpty() == true }
    }
    val total = rows.size
    val done = total - vm.globalPending
    Column(Modifier.fillMaxSize()) {
        // Overall progress stays visible while any source is still answering.
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (vm.globalPending > 0 && total > 0) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.accent,
                    trackColor = AppColors.surface,
                )
            }
        }
        if (vm.globalPending == 0 && rows.none { (it.state as? SourceState.Done)?.items?.isNotEmpty() == true }) {
            EmptyBox(stringResource(R.string.search_empty))
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(shown, key = { it.sourceId }) { row ->
                SourceResultRow(
                    row = row,
                    modifier = fadingItem(),
                    onOpen = { titleId -> nav.navigate(Routes.title(row.sourceId, titleId)) },
                    onRetry = {
                        (row.state as? SourceState.Failed)?.challenge?.let(onChallenge)
                        vm.retrySource(row.sourceId)
                    },
                )
            }
            if (emptyNames.isNotEmpty()) {
                item(key = "empty-sources") {
                    Text(
                        stringResource(R.string.search_no_results_in, emptyNames.joinToString()),
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = fadingItem().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceResultRow(
    row: SourceRow,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.sourceName,
                color = AppColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            when (val s = row.state) {
                SourceState.Loading -> CircularProgressIndicator(
                    color = AppColors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                )
                is SourceState.Done -> Text(
                    text = if (s.items.isEmpty()) {
                        stringResource(R.string.search_source_empty)
                    } else {
                        pluralStringResource(R.plurals.search_source_count, s.items.size, s.items.size)
                    },
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                is SourceState.Failed -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry), color = AppColors.accent, fontSize = 12.sp)
                }
            }
        }
        when (val s = row.state) {
            is SourceState.Done -> if (s.items.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        CoverCard(
                            title = item.title,
                            coverUrl = item.coverUrl,
                            badge = item.latestChapter,
                            width = 104.dp,
                            onClick = { onOpen(item.id) },
                        )
                    }
                }
            }
            is SourceState.Failed -> Text(
                text = s.message,
                color = AppColors.textTertiary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SourceState.Loading -> Unit
        }
    }
}
