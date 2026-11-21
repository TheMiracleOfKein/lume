package com.lume.app.translate

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.update.UpdateState
import com.lume.app.ui.components.SettingsSection
import com.lume.app.ui.components.SheetHeader
import com.lume.app.ui.components.SwitchRow
import com.lume.app.ui.theme.AppColors
import com.lume.translate.ModelPair
import com.lume.translate.TranslateContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale

/** A language's name in the app's language, capitalised ("Russian", "Русский"). */
internal fun languageName(code: String): String {
    val locale = Locale.getDefault()
    return Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * Everything about translating this title, in one place: installing the add-on, downloading the
 * language pack, turning translation on and off, and the title's glossary.
 */
@Composable
fun TranslateSheet(translation: ReaderTranslation, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val addon by TranslateAddon.state.collectAsStateWithLifecycle()
    // Back from the system installer (or app info): look at the add-on again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { TranslateAddon.refresh(context) } }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        SheetHeader(stringResource(R.string.translate_title), onClose = onClose)
        when (val a = addon) {
            AddonState.Unknown -> Unit
            AddonState.NotInstalled -> AddonInstall(update = false)
            is AddonState.Incompatible -> AddonInstall(update = true)
            AddonState.WrongSignature -> {
                Hint(stringResource(R.string.translate_addon_wrong_signature))
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, "package:${TranslateContract.ADDON_PACKAGE}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.translate_addon_uninstall)) }
            }
            is AddonState.Ready -> {
                for (pair in translation.pairs) {
                    PairSection(pair, installed = pair.id in a.installedPairs, translation = translation)
                }
                if (translation.pair != null) Glossary(translation)
            }
        }
    }
    LaunchedEffect(Unit) { TranslateAddon.refresh(context) }
    // Keeps the add-on updater alive with the sheet.
    remember(container) { TranslateAddon.updater(container) }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
}

/** "Install Lume Translate": the add-on APK from Lume's latest release, through the system installer. */
@Composable
private fun AddonInstall(update: Boolean) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val updater = remember(container) { TranslateAddon.updater(container) }
    val state by updater.state.collectAsStateWithLifecycle()
    Hint(stringResource(if (update) R.string.translate_addon_outdated else R.string.translate_addon_about))
    when (val s = state) {
        UpdateState.Idle, UpdateState.UpToDate -> Button(onClick = { updater.check(manual = true) }) {
            Text(stringResource(if (update) R.string.translate_addon_update else R.string.translate_addon_install))
        }
        UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
        is UpdateState.Available -> {
            LaunchedEffect(s.release) {
                if (s.release.apkUrl != null) updater.download(s.release)
            }
            if (s.release.apkUrl == null) Hint(stringResource(R.string.translate_addon_not_in_release))
        }
        is UpdateState.Downloading -> {
            Hint(stringResource(R.string.translate_addon_downloading))
            val f = s.fraction
            if (f == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.ReadyToInstall -> {
            LaunchedEffect(s.apk) { updater.install(context, s.apk) }
            Button(onClick = { updater.install(context, s.apk) }) { Text(stringResource(R.string.translate_addon_open_installer)) }
        }
        is UpdateState.Failed -> {
            Hint(s.message)
            Button(onClick = { updater.check(manual = true) }) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** One language pair: download its pack, then switch translation on or off. */
@Composable
private fun PairSection(pair: ModelPair, installed: Boolean, translation: ReaderTranslation) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.translate_pair, languageName(pair.from), languageName(pair.to))
    SettingsSection(label)
    var downloading by remember { mutableStateOf<Job?>(null) }
    var fraction by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    if (!installed) {
        val size = Formatter.formatShortFileSize(context, pair.downloadSize)
        Hint(stringResource(R.string.translate_pack_about, size))
        failed?.let { Hint(stringResource(R.string.translate_pack_failed, it)) }
        if (downloading?.isActive == true) {
            val f = fraction
            if (f == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { downloading?.cancel() }) { Text(stringResource(R.string.action_cancel)) }
        } else {
            Button(onClick = {
                failed = null
                downloading = scope.launch {
                    TranslateAddon.download(context, pair)
                        .catch { e -> failed = e.message }
                        .collect { p ->
                            when (p) {
                                is PackDownload.Progress -> fraction = if (p.total > 0) p.done.toFloat() / p.total else null
                                PackDownload.Done -> {
                                    TranslateAddon.refresh(context)
                                    translation.enable(pair)
                                }
                            }
                        }
                }
            }) { Text(stringResource(R.string.translate_pack_download, size)) }
        }
        return
    }
    SwitchRow(
        label = stringResource(R.string.translate_switch, languageName(pair.to)),
        hint = stringResource(R.string.translate_switch_hint),
        checked = translation.target == pair.to,
        onCheckedChange = { on -> if (on) translation.enable(pair) else translation.disable() },
    )
    when (val p = translation.progress) {
        is TranslationProgress.Working -> {
            Hint(stringResource(R.string.translate_progress, p.done, p.total))
            LinearProgressIndicator(progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f }, modifier = Modifier.fillMaxWidth())
        }
        is TranslationProgress.Failed -> {
            Hint(stringResource(R.string.translate_failed, p.message))
            TextButton(onClick = translation::retry) { Text(stringResource(R.string.action_retry)) }
        }
        TranslationProgress.Idle -> Unit
    }
    TextButton(onClick = {
        scope.launch {
            if (translation.target == pair.to) translation.disable()
            runCatching { TranslateAddon.delete(context, pair) }
        }
    }) {
        Text(stringResource(R.string.translate_pack_delete, Formatter.formatShortFileSize(context, pair.installedSize)))
    }
}

/** The title's glossary: names and terms that must read the same in every chapter. */
@Composable
private fun Glossary(translation: ReaderTranslation) {
    SettingsSection(stringResource(R.string.translate_glossary))
    Hint(stringResource(R.string.translate_glossary_hint))
    for (term in translation.glossary) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.translate_glossary_term, term.source, term.target),
                color = AppColors.textPrimary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { translation.removeTerm(term.source) }) {
                Icon(Icons.Outlined.Close, stringResource(R.string.translate_glossary_remove, term.source), tint = AppColors.textSecondary)
            }
        }
    }
    GlossaryAdd(onAdd = translation::addTerm)
}

@Composable
internal fun GlossaryAdd(initialSource: String = "", onAdd: (String, String) -> Unit) {
    var source by rememberSaveable { mutableStateOf(initialSource) }
    var target by rememberSaveable { mutableStateOf("") }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text(stringResource(R.string.translate_glossary_source)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text(stringResource(R.string.translate_glossary_target)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(
        enabled = source.isNotBlank() && target.isNotBlank(),
        onClick = {
            onAdd(source, target)
            source = ""
            target = ""
        },
    ) { Text(stringResource(R.string.translate_glossary_add)) }
}
