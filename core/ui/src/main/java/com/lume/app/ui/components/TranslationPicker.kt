package com.lume.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.domain.Chapter
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/** The team that translated [chapter], for the reader's top bar and the picker. */
fun translationLabel(chapter: Chapter): String = chapter.branchName?.takeIf { it.isNotBlank() } ?: chapter.branchId.orEmpty()

/**
 * The other translations of the chapter being read (one row per team, the current one checked).
 * Picking a row calls [onPick] and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationPickerSheet(
    copies: List<Chapter>,
    currentId: String,
    onPick: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberEasyDismissSheetState()
    val scope = rememberCoroutineScope()
    val close = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }; Unit }
    LumeBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SheetHeader(stringResource(R.string.reader_translation), onClose = close)
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                items(copies, key = { it.id }) { copy ->
                    val current = copy.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                if (!current) onPick(copy)
                                close()
                            }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 12.dp)
                            .semantics { selected = current },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = translationLabel(copy),
                            color = if (current) AppColors.accentText else AppColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (current) {
                            Icon(Icons.Outlined.Check, null, tint = AppColors.accentText, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
