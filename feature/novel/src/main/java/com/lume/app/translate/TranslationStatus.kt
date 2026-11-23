package com.lume.app.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.delay

/** Seconds since [since], ticking once a second. */
@Composable
internal fun elapsedSeconds(since: Long): Int {
    var seconds by remember(since) { mutableIntStateOf(secondsSince(since)) }
    LaunchedEffect(since) {
        while (true) {
            seconds = secondsSince(since)
            delay(1_000)
        }
    }
    return seconds
}

private fun secondsSince(since: Long): Int = ((System.currentTimeMillis() - since) / 1_000).toInt().coerceAtLeast(0)

/** The short name of who translates: "OpenRouter", "Google Translate", "DeepL", "Lume Translate". */
@Composable
internal fun serviceShortName(s: TranslationService): String = when (s.engine) {
    TranslationEngine.Llm -> s.service ?: stringResource(R.string.translate_engine_llm)
    TranslationEngine.Google -> stringResource(R.string.translate_engine_google)
    TranslationEngine.DeepL -> stringResource(R.string.translate_engine_deepl)
    TranslationEngine.Offline -> stringResource(R.string.translate_service_offline)
}

/** The full name, with the model for AI: "OpenRouter · openrouter/auto". */
@Composable
internal fun serviceFullName(s: TranslationService): String {
    val short = serviceShortName(s)
    return if (s.engine == TranslationEngine.Llm && s.model != null) "$short · ${s.model}" else short
}

/** One line for the reader's top bar; null when there is nothing to say. */
@Composable
fun translationStatusLine(progress: TranslationProgress): String? = when (progress) {
    TranslationProgress.Idle -> null
    is TranslationProgress.Waiting -> stringResource(R.string.translate_status_waiting, serviceShortName(progress.service), elapsedSeconds(progress.since))
    is TranslationProgress.Working -> stringResource(R.string.translate_progress, progress.done, progress.total)
    is TranslationProgress.Failed -> stringResource(R.string.translate_status_failed)
}

/**
 * The translation's state in the sheet: who it was sent to and for how long it has waited, the
 * progress, or what went wrong, with Stop, Retry and a way to the settings.
 */
@Composable
internal fun TranslationStatusPanel(
    progress: TranslationProgress,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (progress) {
        TranslationProgress.Idle -> Unit
        is TranslationProgress.Waiting -> Column {
            val seconds = elapsedSeconds(progress.since)
            Hint(stringResource(R.string.translate_waiting, serviceFullName(progress.service), seconds))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (seconds >= SLOW_SECONDS) Hint(stringResource(R.string.translate_waiting_slow))
            TextButton(onClick = onStop) { Text(stringResource(R.string.translate_stop)) }
        }
        is TranslationProgress.Working -> Column {
            Hint(stringResource(R.string.translate_working_with, serviceFullName(progress.service), progress.done, progress.total))
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onStop) { Text(stringResource(R.string.translate_stop)) }
        }
        is TranslationProgress.Failed -> Column {
            Text(
                progress.service?.let { stringResource(R.string.translate_failed_with, serviceFullName(it)) }
                    ?: stringResource(R.string.translate_status_failed),
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Hint(progress.message)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                if (progress.fixInSettings) TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.ai_open_settings)) }
            }
        }
    }
}

/** After this long without a first answer, the sheet says that slow is normal for big models. */
private const val SLOW_SECONDS = 20
