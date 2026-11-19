package com.lume.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.ui.theme.AppColors

/** "Reset to defaults" for a reader's settings: asks first, then calls [onConfirm]. */
@Composable
fun ResetReaderSettingsButton(onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var asking by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reader_reset_defaults),
            color = AppColors.accentText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.reader_reset_title)) },
            text = { Text(stringResource(R.string.reader_reset_text)) },
            confirmButton = {
                TextButton(onClick = { asking = false; onConfirm() }) { Text(stringResource(R.string.reader_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
