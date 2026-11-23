package com.lume.app.translate

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.llm.AiServices
import com.lume.app.llm.CheckStatus
import com.lume.app.llm.LlmClient
import com.lume.app.llm.LlmConfig
import com.lume.app.llm.LlmModel
import com.lume.app.llm.LlmPreset
import com.lume.app.llm.ServiceCheck
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import java.util.Locale

/** A service's name for the user: the brand, or "Own server" for a custom address. */
@Composable
internal fun presetName(p: LlmPreset): String = if (p == LlmPreset.Custom) stringResource(R.string.translate_llm_custom) else p.label

/**
 * The AI choice in the reader's translate sheet: which of the set-up services is active and its
 * model (automatic or picked). Keys and addresses live in Settings → AI and translation; this
 * links there. Changing the service or model here changes it for the recap and codex too.
 */
@Composable
internal fun LlmQuickChoice(onPreset: (LlmPreset) -> Unit, onModel: (String?) -> Unit, onOpenSettings: () -> Unit) {
    val app = LocalAppContainer.current
    val presetId by app.prefs.llmPreset.collectAsStateWithLifecycle()
    val keys by app.prefs.llmKeys.collectAsStateWithLifecycle()
    val url by app.prefs.llmBaseUrl.collectAsStateWithLifecycle()
    val models by app.prefs.llmModels.collectAsStateWithLifecycle()
    val checks by app.prefs.aiChecks.collectAsStateWithLifecycle()
    val active = LlmPreset.fromId(presetId)
    val ready = remember(keys, url) { LlmPreset.entries.filter { AiServices.hasSetup(app, it) } }
    var picking by remember { mutableStateOf(false) }

    if (ready.isEmpty()) {
        Hint(stringResource(R.string.ai_none_set_up))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_set_up)) }
        return
    }
    Text(stringResource(R.string.ai_service), color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ready.forEach { p -> FilterChip(selected = p == active, onClick = { onPreset(p) }, label = { Text(presetName(p)) }) }
    }
    if (active !in ready) {
        Hint(stringResource(R.string.ai_active_not_set_up, presetName(active)))
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.ai_open_settings)) }
        return
    }
    val check = ServiceCheck.decode(checks[active.id])
    val picked = models[active.id]?.takeIf { it.isNotBlank() }
    val auto = check?.autoModel ?: active.defaultModel
    Text(stringResource(R.string.translate_llm_model), color = AppColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = picked == null,
            onClick = { onModel(null) },
            label = { Text(auto?.let { stringResource(R.string.ai_model_auto_named, it) } ?: stringResource(R.string.ai_model_auto)) },
        )
        if (picked != null) FilterChip(selected = true, onClick = {}, label = { Text(picked) })
        FilterChip(selected = false, onClick = { picking = true }, label = { Text(stringResource(R.string.ai_model_other)) })
    }
    Hint(if (picked == null) autoHint(active, auto) else stringResource(R.string.ai_model_picked_hint))
    Hint(checkLine(check, hasKey = true))
    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.ai_open_settings)) }

    if (picking) {
        val notSetUp = stringResource(R.string.translate_needs_llm)
        ModelPicker(
            load = {
                LlmConfig.from(active.id, url, "-", keys)?.let { LlmClient(app.http.okHttp, it).models() } ?: error(notSetUp)
            },
            onPick = {
                onModel(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** What "Automatic" does for [p]. */
@Composable
internal fun autoHint(p: LlmPreset, auto: String?): String = when {
    p == LlmPreset.OpenRouter -> stringResource(R.string.ai_model_auto_openrouter)
    auto != null -> stringResource(R.string.ai_model_auto_hint, auto)
    else -> stringResource(R.string.ai_model_auto_custom)
}

/** The status of a key from its last check, for the settings and the sheet. */
@Composable
internal fun checkLine(check: ServiceCheck?, hasKey: Boolean): String {
    if (!hasKey) return stringResource(R.string.ai_status_no_key)
    if (check == null) return stringResource(R.string.ai_status_saved)
    val whenText = DateUtils.getRelativeTimeSpanString(check.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    return when (check.status) {
        CheckStatus.Ok -> when {
            check.limit != null && check.used != null -> {
                val nf = NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale)
                stringResource(R.string.ai_status_ok_quota, nf.format(check.used), nf.format(check.limit), whenText)
            }
            check.modelCount != null -> stringResource(R.string.ai_status_ok, check.modelCount, whenText)
            else -> stringResource(R.string.ai_status_ok_plain, whenText)
        }
        CheckStatus.ModelMissing -> stringResource(R.string.ai_status_model_missing, check.detail.orEmpty(), whenText)
        CheckStatus.Refused -> stringResource(R.string.ai_status_refused, whenText)
        CheckStatus.NoCredits -> stringResource(R.string.ai_status_credits, whenText)
        CheckStatus.Unreachable -> stringResource(R.string.ai_status_unreachable, whenText)
        CheckStatus.Failed -> stringResource(R.string.ai_status_failed, check.detail.orEmpty(), whenText)
    }
}

/** The title's style note for AI translation (tone, honorifics, how to address people). */
@Composable
internal fun StyleNote(saved: String?, onSave: (String) -> Unit) {
    var text by rememberSaveable(saved) { mutableStateOf(saved.orEmpty()) }
    Hint(stringResource(R.string.translate_style_hint))
    // Save sits inside the field: a button below it would be under the keyboard while typing.
    val changed = text.trim() != saved.orEmpty()
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(500) },
        label = { Text(stringResource(R.string.translate_style)) },
        minLines = 2,
        maxLines = 5,
        trailingIcon = {
            IconButton(enabled = changed, onClick = { onSave(text) }) {
                Icon(Icons.Outlined.Check, stringResource(R.string.action_save), tint = if (changed) AppColors.accent else AppColors.textTertiary)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The service's models with a search field; prices per million tokens where the service says them. */
@Composable
internal fun ModelPicker(load: suspend () -> List<LlmModel>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var models by remember { mutableStateOf<List<LlmModel>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            models = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = e.message ?: e.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translate_llm_choose_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = models
                when {
                    failed != null -> Hint(failed!!)
                    list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    else -> {
                        val shown = list.filter { query.isBlank() || it.id.contains(query, true) || it.name.contains(query, true) }
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(shown, key = { it.id }) { m ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(m.id.removePrefix("models/")) }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(m.id, color = AppColors.textPrimary, fontSize = 14.sp)
                                    priceLine(m)?.let { Text(it, color = AppColors.textSecondary, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun priceLine(m: LlmModel): String? {
    val input = m.promptPrice ?: return null
    val output = m.completionPrice ?: return null
    if (input == 0.0 && output == 0.0) return stringResource(R.string.translate_llm_free)
    return stringResource(R.string.translate_llm_price, money(input), money(output))
}

private fun money(v: Double): String = String.format(Locale.US, if (v < 1) "%.2f" else "%.1f", v)
