package com.lume.app.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors

/** Keeps the screen on and asks for local network access (Android 17) while a nearby screen is open. */
@Composable
private fun NearbyScreenEffects(onPermissionDenied: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(LOCAL_NETWORK_PERMISSION)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyScaffold(title: String, nav: NavHostController, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(title, color = AppColors.textPrimary) },
                navigationIcon = { BackButton(onClick = { nav.popBackStack() }) },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(AppColors.background)) { content() }
    }
}

/** A centred column for the steps after choosing. */
@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun CodeText(code: String) {
    Text(
        code.chunked(3).joinToString(" "),
        color = AppColors.textPrimary,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun Body(text: String, color: androidx.compose.ui.graphics.Color = AppColors.textSecondary) {
    Text(text, color = color, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun Progress(done: Long, total: Long) {
    val context = LocalContext.current
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.accent,
        trackColor = AppColors.card,
    )
    Body(stringResource(R.string.nearby_progress, Formatter.formatShortFileSize(context, done), Formatter.formatShortFileSize(context, total)))
}

@Composable
private fun outcomeText(outcome: NearbyOutcome, sending: Boolean): String = when (outcome) {
    is NearbyOutcome.Done -> stringResource(if (sending) R.string.nearby_sent else R.string.nearby_received, outcome.chapters)
    NearbyOutcome.Declined -> stringResource(if (sending) R.string.nearby_declined else R.string.nearby_declined_by_sender)
    NearbyOutcome.Cancelled -> stringResource(R.string.nearby_cancelled)
    // The receiver can connect again and continue; the sender just says what broke.
    is NearbyOutcome.Failed -> outcome.message?.takeIf { sending }?.let { stringResource(R.string.nearby_failed, it) }
        ?: stringResource(R.string.nearby_failed_resume)
}

@Composable
fun NearbySendScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: NearbySendViewModel = viewModel { NearbySendViewModel(container) }
    var denied by remember { mutableStateOf(false) }
    NearbyScreenEffects { denied = true }
    NearbyScaffold(stringResource(R.string.nearby_send_title), nav) {
        when (val phase = vm.phase) {
            SendPhase.Choose -> SendChoose(vm)
            is SendPhase.Waiting -> StepColumn {
                CircularProgressIndicator(color = AppColors.accent)
                Text(stringResource(R.string.nearby_waiting), color = AppColors.textPrimary, fontSize = 18.sp)
                Body(stringResource(R.string.nearby_waiting_hint, phase.name))
                if (phase.addresses.isEmpty()) {
                    Body(stringResource(R.string.nearby_no_network), AppColors.danger)
                } else {
                    Body(stringResource(R.string.nearby_address_hint))
                    phase.addresses.forEach {
                        Text("$it:${phase.port}", color = AppColors.textPrimary, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (denied) Body(stringResource(R.string.nearby_permission_denied), AppColors.danger)
                Text(stringResource(R.string.nearby_listening_note), color = AppColors.textTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            is SendPhase.Confirm -> StepColumn {
                Text(stringResource(R.string.nearby_confirm_title, phase.peer), color = AppColors.textPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
                CodeText(phase.code)
                Body(stringResource(R.string.nearby_code_check))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.confirm(false) }) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = { vm.confirm(true) }) { Text(stringResource(R.string.nearby_send)) }
                }
            }
            is SendPhase.Sending -> StepColumn {
                Text(stringResource(R.string.nearby_sending, phase.peer), color = AppColors.textPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
                Progress(phase.sent, phase.total)
                OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
            }
            is SendPhase.Finished -> StepColumn {
                Text(outcomeText(phase.outcome, sending = true), color = AppColors.textPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::waitAgain) { Text(stringResource(R.string.nearby_wait_again)) }
                    Button(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.nearby_done)) }
                }
            }
        }
    }
}

@Composable
private fun SendChoose(vm: NearbySendViewModel) {
    val groups by vm.groups.collectAsState()
    val context = LocalContext.current
    if (groups.isEmpty()) {
        EmptyBox(stringResource(R.string.downloads_empty))
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.nearby_send_intro), color = AppColors.textSecondary, fontSize = 13.sp)
                    Row {
                        TextButton(onClick = vm::selectAll) { Text(stringResource(R.string.nearby_select_all), color = AppColors.accentText) }
                        if (vm.selection.isNotEmpty()) {
                            TextButton(onClick = vm::clearSelection) { Text(stringResource(R.string.nearby_select_none), color = AppColors.textSecondary) }
                        }
                    }
                }
            }
            items(groups, key = { "${it.sourceId}|${it.titleId}" }) { group ->
                val key = "${group.sourceId}|${group.titleId}"
                val chosen = vm.selection[key].orEmpty()
                val state = when (chosen.size) {
                    0 -> ToggleableState.Off
                    group.chapters.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                val open = key in vm.expanded
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleTitle(group) }.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(state = state, onClick = { vm.toggleTitle(group) })
                        CoverImage(url = group.coverUrl, width = 36.dp, height = 48.dp, radius = 4.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(group.titleName, color = AppColors.textPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val bytes = group.chapters.sumOf { vm.chapterBytes["$key|${it.chapterId}"] ?: 0L }
                            Text(
                                stringResource(R.string.chapters_count_short, group.chapters.size) + " · " + Formatter.formatShortFileSize(context, bytes),
                                color = AppColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { vm.toggleExpanded(key) }) {
                            Icon(
                                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                stringResource(if (open) R.string.action_collapse else R.string.action_expand),
                                tint = AppColors.textSecondary,
                            )
                        }
                    }
                    if (open) {
                        group.chapters.forEach { chapter ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.toggleChapter(group, chapter.chapterId) }.padding(start = 40.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = chapter.chapterId in chosen, onCheckedChange = { vm.toggleChapter(group, chapter.chapterId) })
                                Text(
                                    chapter.chapterName,
                                    color = AppColors.textPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Formatter.formatShortFileSize(context, vm.chapterBytes["$key|${chapter.chapterId}"] ?: 0L),
                                    color = AppColors.textTertiary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(AppColors.card).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nearby_read_marks), color = AppColors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = vm.readMarks, onCheckedChange = { vm.readMarks = it })
            }
            Button(
                onClick = vm::start,
                enabled = vm.selectedCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.nearby_start, vm.selectedCount, Formatter.formatShortFileSize(context, vm.selectedBytes)))
            }
        }
    }
}

@Composable
fun NearbyReceiveScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val vm: NearbyReceiveViewModel = viewModel { NearbyReceiveViewModel(container) }
    var denied by remember { mutableStateOf(false) }
    NearbyScreenEffects { denied = true }
    val context = LocalContext.current
    NearbyScaffold(stringResource(R.string.nearby_receive_title), nav) {
        when (val phase = vm.phase) {
            ReceivePhase.Find -> ReceiveFind(vm, denied)
            ReceivePhase.Connecting -> StepColumn {
                CircularProgressIndicator(color = AppColors.accent)
                Body(stringResource(R.string.nearby_connecting))
                OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
            }
            is ReceivePhase.Paired -> StepColumn {
                CodeText(phase.code)
                Body(stringResource(R.string.nearby_paired, phase.peer))
                CircularProgressIndicator(color = AppColors.accent)
                OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
            }
            is ReceivePhase.Review -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CodeText(phase.code)
                    Body(stringResource(R.string.nearby_code_check))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.nearby_offer_title, phase.peer), color = AppColors.textPrimary, fontSize = 17.sp)
                Text(
                    stringResource(R.string.nearby_offer_total, phase.offer.chapterCount, Formatter.formatShortFileSize(context, phase.offer.totalBytes)),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                phase.offer.titles.forEach { title ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(url = title.details.coverUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") },width = 36.dp, height = 48.dp, radius = 4.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(title.details.title, color = AppColors.textPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.chapters_count_short, title.chapters.size) + " · " +
                                    Formatter.formatShortFileSize(context, title.chapters.sumOf { it.bytes }),
                                color = AppColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (phase.plan.alreadyHere > 0) Note(stringResource(R.string.nearby_offer_have, phase.plan.alreadyHere))
                if (phase.plan.unsupportedTitles.isNotEmpty()) {
                    Note(stringResource(R.string.nearby_offer_unsupported, phase.plan.unsupportedTitles.joinToString(", ")))
                }
                if (phase.offer.readMarks) Note(stringResource(R.string.nearby_offer_read_marks))
                Note(stringResource(R.string.nearby_offer_note))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    OutlinedButton(onClick = { vm.accept(false) }) { Text(stringResource(R.string.nearby_decline)) }
                    Button(onClick = { vm.accept(true) }) { Text(stringResource(R.string.nearby_accept)) }
                }
            }
            is ReceivePhase.Receiving -> StepColumn {
                Text(stringResource(R.string.nearby_receiving, phase.peer), color = AppColors.textPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
                Progress(phase.received, phase.total)
                OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
            }
            is ReceivePhase.Finished -> StepColumn {
                Text(outcomeText(phase.outcome, sending = false), color = AppColors.textPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (phase.outcome is NearbyOutcome.Failed || phase.outcome is NearbyOutcome.Cancelled) {
                        OutlinedButton(onClick = vm::retry) { Text(stringResource(R.string.nearby_try_again)) }
                    } else {
                        OutlinedButton(onClick = vm::backToFind) { Text(stringResource(R.string.nearby_found)) }
                    }
                    Button(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.nearby_done)) }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = AppColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun ReceiveFind(vm: NearbyReceiveViewModel, denied: Boolean) {
    val peers by vm.peers.collectAsState()
    var invalid by remember { mutableStateOf(false) }
    val submit = { invalid = !vm.connectTyped() }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "found") {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nearby_found), color = AppColors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(R.string.nearby_looking),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (denied) {
                Text(
                    stringResource(R.string.nearby_permission_denied),
                    color = AppColors.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(peers, key = { "${it.name}@${it.host}:${it.port}" }) { peer ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.connect(peer.host, peer.port) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = AppColors.accentText)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(peer.name, color = AppColors.textPrimary, fontSize = 15.sp)
                    Text("${peer.host}:${peer.port}", color = AppColors.textTertiary, fontSize = 12.sp)
                }
            }
        }
        item(key = "manual") {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = vm.address,
                    onValueChange = { vm.address = it; invalid = false },
                    label = { Text(stringResource(R.string.nearby_enter_address)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.nearby_address_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { submit() }, enabled = vm.address.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.nearby_connect))
                }
            }
        }
    }
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
