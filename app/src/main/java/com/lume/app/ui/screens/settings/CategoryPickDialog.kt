package com.lume.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.domain.UNCATEGORIZED_ID
import com.lume.app.ui.theme.AppColors

/** A category as the settings pickers list it. */
data class CategoryChoice(val id: Long, val name: String)

/** The library's categories followed by the "Uncategorized" pseudo-category (id 0). */
@Composable
fun categoryChoices(categories: List<CategoryEntity>): List<CategoryChoice> =
    categories.map { CategoryChoice(it.id, it.name) } +
        CategoryChoice(UNCATEGORIZED_ID, stringResource(R.string.category_uncategorized))

/** A multi-select of categories; [onSave] gets the ids that are ticked. */
@Composable
fun CategoryPickDialog(
    title: String,
    choices: List<CategoryChoice>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var ticked by rememberSaveable { mutableStateOf(selected.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    val on = choice.id in ticked
                    val toggle = { ticked = if (on) ticked - choice.id else ticked + choice.id }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = toggle),
                    ) {
                        Checkbox(checked = on, onCheckedChange = { toggle() })
                        Text(choice.name, color = AppColors.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ticked.toSet()) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
