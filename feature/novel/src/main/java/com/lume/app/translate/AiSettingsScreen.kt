package com.lume.app.translate

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lume.app.AppContainer
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.llm.AiFeature
import com.lume.app.llm.AiServices
import com.lume.app.llm.AiUsage
import com.lume.app.llm.LlmClient
import com.lume.app.llm.UsageTotals
import com.lume.app.llm.LlmConfig
import com.lume.app.llm.LlmPreset
import com.lume.app.llm.ServiceCheck
import com.lume.app.ui.screens.settings.SettingItem
import com.lume.app.ui.screens.settings.SettingsGroup
import com.lume.app.ui.screens.settings.SettingsLabel
import com.lume.app.ui.screens.settings.SettingsNote
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Settings → AI and translation: the keys of every service, whether each works, which one is
 * active and its model. The active service and model serve translation, the recap and codex
 * summaries alike; the reader's translate sheet can switch between set-up services, but keys and
 * addresses are only entered here.
 */
@Composable
internal fun AiSettingsScreen() {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val presetId by app.prefs.llmPreset.collectAsStateWithLifecycle()
    val keys by app.prefs.llmKeys.collectAsStateWithLifecycle()
    val url by app.prefs.llmBaseUrl.collectAsStateWithLifecycle()
    val models by app.prefs.llmModels.collectAsStateWithLifecycle()
    val checks by app.prefs.aiChecks.collectAsStateWithLifecycle()
    val deeplKey by app.prefs.deeplKey.collectAsStateWithLifecycle()
    val active = LlmPreset.fromId(presetId)
    /** The service (or DeepL) being checked right now. */
    var checking by remember { mutableStateOf<String?>(null) }

    fun runCheck(id: String, block: suspend () -> Unit) {
        scope.launch {
            checking = id
            try {
                block()
            } finally {
                checking = null
            }
        }
    }

    // A key not checked for a day is checked on opening, so the status says what is true now.
    LaunchedEffect(active, keys, url) {
        val last = AiServices.check(app, active.id)
        if (AiServices.hasSetup(app, active) && (last == null || System.currentTimeMillis() - last.at > RECHECK_MS)) {
            runCheck(active.id) { AiServices.check(app, active) }
        }
    }

    SettingsNote(stringResource(R.string.ai_settings_note))
    Spacer(Modifier.height(4.dp))

    // ---- Which service is active ----
    SettingsGroup {
        SettingItem("ai_service") {
            SettingsLabel(stringResource(R.string.ai_active_service))
            for (p in LlmPreset.entries) {
                val ready = remember(keys, url, p) { AiServices.hasSetup(app, p) }
                ServiceRow(
                    name = presetName(p),
                    status = if (checking == p.id) stringResource(R.string.ai_status_checking) else checkLine(ServiceCheck.decode(checks[p.id]), hasKey = ready),
                    selected = p == active,
                    onSelect = { app.appScope.launch { app.prefs.setLlmPreset(p.id) } },
                )
            }
            SettingsNote(stringResource(R.string.ai_active_service_hint), Modifier.padding(top = 4.dp))
        }
    }
    Spacer(Modifier.height(12.dp))

    // ---- The active service: key, address, check, model ----
    SettingsGroup {
        SettingsLabel(presetName(active))
        val ready = remember(keys, url, active) { AiServices.hasSetup(app, active) }
        val check = ServiceCheck.decode(checks[active.id])
        if (active == LlmPreset.Custom) {
            UrlField(saved = url) { value ->
                scope.launch {
                    app.prefs.setLlmBaseUrl(value)
                    app.prefs.setAiCheck(LlmPreset.Custom.id, null)
                    if (AiServices.hasSetup(app, LlmPreset.Custom)) runCheck(LlmPreset.Custom.id) { AiServices.check(app, LlmPreset.Custom) }
                }
            }
        }
        if (active.needsKey) {
            KeyEditor(
                saved = keys[active.id],
                onSave = { key -> runCheck(active.id) { AiServices.saveKey(app, active, key) } },
                onRemove = { app.appScope.launch { AiServices.saveKey(app, active, null) } },
            )
            active.keyUrl?.let { link -> KeyLink(link) }
        }
        CheckRow(
            status = if (checking == active.id) null else checkLine(check, hasKey = ready),
            enabled = ready && checking == null,
            onCheck = { runCheck(active.id) { AiServices.check(app, active) } },
        )
        Spacer(Modifier.height(8.dp))
        SettingItem("ai_model") {
            ModelChoice(
                app = app,
                preset = active,
                picked = models[active.id]?.takeIf { it.isNotBlank() },
                auto = AiServices.autoModel(app, active),
                canList = ready,
                onModel = { model ->
                    scope.launch {
                        app.prefs.setLlmModel(active.id, model)
                        // The check says whether the service offers the new model.
                        if (AiServices.hasSetup(app, active)) runCheck(active.id) { AiServices.check(app, active) }
                    }
                },
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    // ---- What AI requests used this month ----
    val usageText by app.prefs.aiUsage.collectAsStateWithLifecycle()
    val usage = remember(usageText) { AiUsage.month(usageText) }
    SettingsGroup {
        SettingItem("ai_usage") {
            SettingsLabel(stringResource(R.string.ai_usage))
            UsageLines(usage)
            SettingsNote(stringResource(R.string.ai_usage_hint), Modifier.padding(top = 4.dp))
        }
    }
    Spacer(Modifier.height(12.dp))

    // ---- DeepL ----
    SettingsGroup {
        SettingItem("deepl_key") {
            SettingsLabel(stringResource(R.string.translate_engine_deepl))
            SettingsNote(stringResource(R.string.ai_deepl_hint))
            KeyEditor(
                saved = deeplKey,
                onSave = { key -> runCheck(AiServices.DEEPL) { AiServices.saveDeepLKey(app, key) } },
                onRemove = { app.appScope.launch { AiServices.saveDeepLKey(app, null) } },
            )
            KeyLink("https://www.deepl.com/pro-api")
            CheckRow(
                status = if (checking == AiServices.DEEPL) null else checkLine(ServiceCheck.decode(checks[AiServices.DEEPL]), hasKey = !deeplKey.isNullOrBlank()),
                enabled = !deeplKey.isNullOrBlank() && checking == null,
                onCheck = { runCheck(AiServices.DEEPL) { AiServices.checkDeepL(app) } },
            )
        }
    }
    SettingsNote(stringResource(R.string.ai_keys_private), Modifier.padding(top = 12.dp))
}

/** A day: older checks run again when the page opens. */
private const val RECHECK_MS = 24 * 60 * 60_000L

/** This month's AI use per feature, and the cost where the service reported it. */
@Composable
private fun UsageLines(usage: Map<String, UsageTotals>) {
    val total = usage.values.fold(UsageTotals()) { a, b -> a + b }
    if (total.requests == 0L) {
        Text(stringResource(R.string.ai_usage_none), color = AppColors.textSecondary, fontSize = 13.sp)
        return
    }
    val names = listOf(
        AiFeature.Translation to R.string.ai_usage_translation,
        AiFeature.Recap to R.string.ai_usage_recap,
        AiFeature.Notes to R.string.ai_usage_notes,
        AiFeature.Summary to R.string.ai_usage_summary,
        AiFeature.Other to R.string.ai_usage_other,
    )
    for ((feature, label) in names) {
        val t = usage[feature.id] ?: continue
        Text(
            pluralStringResource(
                R.plurals.ai_usage_line, t.requests.toInt(),
                stringResource(label), t.requests.toInt(), formatTokens(t.promptTokens), formatTokens(t.completionTokens),
            ),
            color = AppColors.textPrimary, fontSize = 13.sp,
        )
    }
    if (total.costRequests > 0) {
        Text(
            if (total.costComplete) stringResource(R.string.ai_usage_cost, formatDollars(total.cost))
            else stringResource(R.string.ai_usage_cost_partial, formatDollars(total.cost), total.costRequests.toInt(), total.requests.toInt()),
            color = AppColors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 950, 12.3k, 1.25M. */
internal fun formatTokens(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1fk", n / 1_000.0)
    else -> String.format(java.util.Locale.getDefault(), "%.2fM", n / 1_000_000.0)
}

/** Dollars with cents, or more digits for amounts under a cent. */
internal fun formatDollars(d: Double): String =
    String.format(java.util.Locale.getDefault(), if (d in 0.0..0.01 && d != 0.0) "%.4f" else "%.2f", d)

@Composable
private fun ServiceRow(name: String, status: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(name, color = AppColors.textPrimary, fontSize = 15.sp)
            Text(status, color = AppColors.textSecondary, fontSize = 12.sp)
        }
    }
}

/** The saved key (its last characters only), a field to enter or replace it, and Remove. */
@Composable
private fun KeyEditor(saved: String?, onSave: (String) -> Unit, onRemove: () -> Unit) {
    var key by rememberSaveable(saved) { mutableStateOf("") }
    if (!saved.isNullOrBlank()) {
        Text(
            stringResource(R.string.ai_key_saved_masked, saved.takeLast(4)),
            color = AppColors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(if (saved.isNullOrBlank()) R.string.translate_llm_key else R.string.ai_key_replace)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        // Save sits inside the field, above the keyboard.
        trailingIcon = {
            IconButton(enabled = key.isNotBlank(), onClick = {
                onSave(key)
                key = ""
            }) {
                Icon(Icons.Outlined.Check, stringResource(R.string.action_save), tint = if (key.isNotBlank()) AppColors.accent else AppColors.textTertiary)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    if (!saved.isNullOrBlank()) TextButton(onClick = onRemove) { Text(stringResource(R.string.ai_key_remove)) }
}

@Composable
private fun KeyLink(url: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text(stringResource(R.string.ai_get_key, url.toUri().host.orEmpty().removePrefix("www."))) }
}

@Composable
private fun UrlField(saved: String?, onSave: (String) -> Unit) {
    var text by rememberSaveable(saved) { mutableStateOf(saved.orEmpty()) }
    val changed = text.trim() != saved.orEmpty()
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.translate_llm_url)) },
        placeholder = { Text("http://192.168.1.10:11434/v1") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        trailingIcon = {
            IconButton(enabled = changed, onClick = { onSave(text) }) {
                Icon(Icons.Outlined.Check, stringResource(R.string.action_save), tint = if (changed) AppColors.accent else AppColors.textTertiary)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    SettingsNote(stringResource(R.string.ai_custom_hint), Modifier.padding(top = 4.dp))
}

/** The last check's result ([status] null while checking) and "Check now". */
@Composable
private fun CheckRow(status: String?, enabled: Boolean, onCheck: () -> Unit) {
    if (status == null) {
        Text(stringResource(R.string.ai_status_checking), color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
    } else {
        Text(status, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
    OutlinedButton(onClick = onCheck, enabled = enabled, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.ai_check)) }
}

/** Automatic or a picked model, with a way back to automatic that says what it means. */
@Composable
private fun ModelChoice(app: AppContainer, preset: LlmPreset, picked: String?, auto: String?, canList: Boolean, onModel: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    SettingsLabel(stringResource(R.string.translate_llm_model))
    ServiceRow(
        name = auto?.let { stringResource(R.string.ai_model_auto_named, it) } ?: stringResource(R.string.ai_model_auto),
        status = autoHint(preset, auto),
        selected = picked == null,
        onSelect = { onModel(null) },
    )
    if (picked != null) {
        ServiceRow(name = picked, status = stringResource(R.string.ai_model_picked_hint), selected = true, onSelect = {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, enabled = canList) { Text(stringResource(R.string.ai_model_choose)) }
        if (picked != null) TextButton(onClick = { onModel(null) }) { Text(stringResource(R.string.ai_model_back_to_auto)) }
    }
    if (!canList) SettingsNote(stringResource(R.string.ai_model_needs_key), Modifier.padding(top = 4.dp))
    if (picking) {
        val notSetUp = stringResource(R.string.translate_needs_llm)
        ModelPicker(
            load = {
                LlmConfig.from(preset.id, app.prefs.llmBaseUrl.value, "-", app.prefs.llmKeys.value)
                    ?.let { LlmClient(app.http.okHttp, it).models() } ?: error(notSetUp)
            },
            onPick = {
                onModel(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
