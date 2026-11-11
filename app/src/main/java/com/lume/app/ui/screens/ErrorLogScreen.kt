package com.lume.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.ErrorEntry
import com.lume.app.data.ErrorLog
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors

/** The full text of recent errors: read, copy, share or clear them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogScreen(nav: NavHostController) {
    val log = LocalAppContainer.current.errorLog
    val entries by log.entries.collectAsState()
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.error_log_title), color = AppColors.textPrimary) },
                navigationIcon = { BackButton(onClick = { nav.popBackStack() }) },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { copyText(context, log.asText(entries)) }) {
                            Icon(Icons.Outlined.ContentCopy, stringResource(R.string.error_log_copy_all), tint = AppColors.textSecondary)
                        }
                        IconButton(onClick = { shareText(context, log.asText(entries)) }) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.error_log_share), tint = AppColors.textSecondary)
                        }
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.error_log_clear), tint = AppColors.textSecondary)
                        }
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyBox(stringResource(R.string.error_log_empty), Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().background(AppColors.background),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.error_log_hint),
                        color = AppColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(entries, key = { i, e -> "${e.at}-$i" }) { _, entry ->
                    ErrorRow(entry, onCopy = { copyText(context, ErrorLog.format(entry)) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.error_log_clear_title)) },
            confirmButton = {
                TextButton(onClick = {
                    log.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.error_log_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ErrorRow(entry: ErrorEntry, onCopy: () -> Unit) {
    var expanded by rememberSaveable(entry.at, entry.message) { mutableStateOf(false) }
    val ago = DateUtils.getRelativeTimeSpanString(entry.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    val time = DateUtils.formatDateTime(
        LocalContext.current,
        entry.at,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_NUMERIC_DATE,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .clickable(role = Role.Button, enabled = entry.details.isNotBlank()) { expanded = !expanded }
            .animateContentSize()
            .padding(start = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.where, color = AppColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$time · $ago", color = AppColors.textTertiary, fontSize = 11.sp)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, stringResource(R.string.error_log_copy), tint = AppColors.textSecondary)
            }
        }
        SelectionContainer(Modifier.padding(end = 12.dp)) {
            Text(entry.message, color = AppColors.danger, fontSize = 13.sp)
        }
        if (entry.details.isNotBlank()) {
            if (expanded) {
                SelectionContainer(Modifier.padding(top = 6.dp, end = 12.dp)) {
                    Text(entry.details, color = AppColors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                Text(
                    stringResource(R.string.error_log_show_details),
                    color = AppColors.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private const val MAX_SHARED_CHARS = 100_000

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Lume", text.take(MAX_SHARED_CHARS)))
    // Android 13+ shows its own confirmation.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.error_log_copied, Toast.LENGTH_SHORT).show()
}

internal fun shareText(context: Context, text: String, subject: String = context.getString(R.string.error_log_title)) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
        // An intent extra must stay well under the binder limit.
        .putExtra(android.content.Intent.EXTRA_TEXT, text.take(MAX_SHARED_CHARS))
    try {
        context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        // Nothing can handle a share.
    }
}
