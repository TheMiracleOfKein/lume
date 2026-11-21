package com.lume.app.translate

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.llm.LlmClient
import com.lume.app.llm.LlmConfig
import com.lume.app.llm.LlmModel
import com.lume.app.llm.LlmPreset
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Setup of AI translation: the service (OpenRouter and other OpenAI-compatible APIs, or any URL),
 * the model and the user's own key. Nothing is saved until "Save".
 */
@Composable
internal fun LlmSetup(onSave: (preset: String, baseUrl: String?, model: String?, key: String?) -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val savedPreset by container.prefs.llmPreset.collectAsStateWithLifecycle()
    val savedUrl by container.prefs.llmBaseUrl.collectAsStateWithLifecycle()
    val savedModel by container.prefs.llmModel.collectAsStateWithLifecycle()
    val keys by container.prefs.llmKeys.collectAsStateWithLifecycle()

    var preset by rememberSaveable(savedPreset) { mutableStateOf(LlmPreset.fromId(savedPreset).id) }
    val p = LlmPreset.fromId(preset)
    val samePreset = p == LlmPreset.fromId(savedPreset)
    var url by rememberSaveable(savedUrl) { mutableStateOf(savedUrl.orEmpty()) }
    var model by rememberSaveable(savedModel, preset) { mutableStateOf(if (samePreset) savedModel.orEmpty() else "") }
    var key by rememberSaveable(preset) { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }

    Hint(stringResource(R.string.translate_llm_about))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in LlmPreset.entries) {
            FilterChip(
                selected = option == p,
                onClick = { preset = option.id },
                label = { Text(if (option == LlmPreset.Custom) stringResource(R.string.translate_llm_custom) else option.label) },
            )
        }
    }
    if (p == LlmPreset.Custom) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.translate_llm_url)) },
            placeholder = { Text("http://192.168.1.10:11434/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text(stringResource(R.string.translate_llm_model)) },
            placeholder = { p.defaultModel?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.translate_llm_choose)) }
    }
    if (p.needsKey) {
        val saved = keys[p.id]
        Hint(stringResource(if (saved.isNullOrBlank()) R.string.translate_llm_key_none else R.string.translate_llm_key_saved))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.translate_llm_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (p == LlmPreset.OpenRouter) {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://openrouter.ai/keys".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.translate_llm_get_key)) }
        }
    }
    Button(onClick = {
        onSave(p.id, url.takeIf { p == LlmPreset.Custom }, model, key.takeIf { it.isNotBlank() })
        key = ""
    }) { Text(stringResource(R.string.action_save)) }

    val notSetUp = stringResource(R.string.translate_needs_llm)
    if (picking) {
        // The list needs the service and key: the typed key if there is one, else the saved one.
        val config = LlmConfig.from(
            p.id,
            url,
            model.ifBlank { p.defaultModel ?: "-" },
            keys + (key.takeIf { it.isNotBlank() }?.let { mapOf(p.id to it) } ?: emptyMap()),
        )
        ModelPicker(
            load = { config?.let { LlmClient(container.http.okHttp, it).models() } ?: error(notSetUp) },
            onPick = {
                model = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The title's style note for AI translation (tone, honorifics, how to address people). */
@Composable
internal fun StyleNote(saved: String?, onSave: (String) -> Unit) {
    var text by rememberSaveable(saved) { mutableStateOf(saved.orEmpty()) }
    Hint(stringResource(R.string.translate_style_hint))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(500) },
        label = { Text(stringResource(R.string.translate_style)) },
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(enabled = text.trim() != saved.orEmpty(), onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
}

/** The service's models with a search field; prices per million tokens where the service says them. */
@Composable
private fun ModelPicker(load: suspend () -> List<LlmModel>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
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
                                        .clickable { onPick(m.id) }
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
