package com.lume.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.domain.FilterList
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.updated
import com.lume.app.ui.theme.AppColors

/**
 * The catalogue filter panel (inside a bottom sheet). Edits a copy of [initial]; nothing reaches
 * the search until Apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    initial: FilterList,
    defaults: FilterList,
    onApply: (FilterList) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    fun change(id: String, transform: (SourceFilter) -> SourceFilter) {
        draft = draft.updated(id, transform)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.filters_title),
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            if (draft.isEmpty()) {
                Text(
                    stringResource(R.string.filters_none),
                    color = AppColors.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            draft.filters.forEach { filter -> FilterItem(filter, ::change) }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = { draft = defaults }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.filters_reset), color = AppColors.textPrimary)
            }
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
            ) {
                Text(stringResource(R.string.filters_apply))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterItem(filter: SourceFilter, change: (String, (SourceFilter) -> SourceFilter) -> Unit) {
    when (filter) {
        is SourceFilter.Header -> FilterLabel(filter.name)
        is SourceFilter.Group -> {
            FilterLabel(filter.name)
            filter.filters.forEach { FilterItem(it, change) }
        }
        is SourceFilter.Select -> {
            FilterLabel(filter.name)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filter.options.indices.forEach { i ->
                    ChoiceChip(
                        label = filter.labelAt(i),
                        selected = i == filter.selected,
                        onClick = { change(filter.id) { (it as SourceFilter.Select).copy(selected = i) } },
                    )
                }
            }
        }
        is SourceFilter.Sort -> {
            FilterLabel(filter.name)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filter.options.indices.forEach { i ->
                    ChoiceChip(
                        label = filter.labels.getOrElse(i) { filter.options[i] },
                        selected = i == filter.index,
                        onClick = { change(filter.id) { (it as SourceFilter.Sort).copy(index = i) } },
                    )
                }
            }
            CheckRow(stringResource(R.string.filter_ascending), filter.ascending) { value ->
                change(filter.id) { (it as SourceFilter.Sort).copy(ascending = value) }
            }
        }
        is SourceFilter.Check -> CheckRow(filter.name, filter.state) { value ->
            change(filter.id) { (it as SourceFilter.Check).copy(state = value) }
        }
        is SourceFilter.TriState -> {
            val state = when (filter.state) {
                1 -> ToggleableState.On
                2 -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            }
            val next = { change(filter.id) { (it as SourceFilter.TriState).copy(state = (filter.state + 1) % 3) } }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = next),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(state = state, onClick = next, colors = filterCheckboxColors())
                Text(filter.name, color = AppColors.textPrimary, fontSize = 14.sp)
            }
        }
        is SourceFilter.Text -> {
            FilterLabel(filter.name)
            TextField(
                value = filter.state,
                onValueChange = { value -> change(filter.id) { (it as SourceFilter.Text).copy(state = value) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.search,
                    unfocusedContainerColor = AppColors.search,
                    focusedTextColor = AppColors.textPrimary,
                    unfocusedTextColor = AppColors.textPrimary,
                    cursorColor = AppColors.accent,
                    focusedIndicatorColor = AppColors.accent,
                    unfocusedIndicatorColor = AppColors.divider,
                ),
            )
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = filterCheckboxColors())
        Text(label, color = AppColors.textPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun filterCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = AppColors.accent,
    uncheckedColor = AppColors.textSecondary,
    checkmarkColor = AppColors.onAccent,
)
