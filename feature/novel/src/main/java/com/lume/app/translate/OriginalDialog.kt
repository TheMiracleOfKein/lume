package com.lume.app.translate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.plainText

/** The plain text of a text block, or null for pictures and scene breaks. */
internal fun NovelBlock.originalText(): String? = when (this) {
    is NovelBlock.Paragraph -> spans.plainText
    is NovelBlock.Heading -> spans.plainText
    else -> null
}

/**
 * The original of a translated paragraph (long press). The text can be selected and copied, and a
 * name from it added to the title's glossary.
 */
@Composable
fun OriginalDialog(text: String, onAddTerm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translate_original)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text, fontSize = 16.sp) }
                if (adding) {
                    GlossaryAdd(onAdd = { s, t ->
                        onAddTerm(s, t)
                        adding = false
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            if (!adding) TextButton(onClick = { adding = true }) { Text(stringResource(R.string.translate_glossary_add_term)) }
        },
    )
}
