package com.lume.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.lume.app.ui.theme.Motion
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.lume.app.domain.carryOver
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
    loading: Boolean = false,
    note: String? = null,
) {
    var draft by remember { mutableStateOf(initial) }
    // The site's lists arrive after the panel opened: add them, keep what was already set here.
    LaunchedEffect(initial) { draft = initial.carryOver(draft) }
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
        if (note != null) {
            Text(note, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (loading) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = AppColors.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.filters_loading), color = AppColors.textSecondary, fontSize = 13.sp)
            }
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            if (draft.isEmpty() && !loading) {
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
        is SourceFilter.Group ->
            if (filter.filters.all { it is SourceFilter.Check || it is SourceFilter.TriState }) {
                ChipGroup(filter, change)
            } else {
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
                colors = filterFieldColors(),
            )
        }
    }
}

/**
 * A group of check / include-exclude filters as chips. Long lists (a site's genres and tags) start
 * folded, with what is chosen in the header, and get a field to find an entry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(group: SourceFilter.Group, change: (String, (SourceFilter) -> SourceFilter) -> Unit) {
    val long = group.filters.size > FOLD_AT
    var open by rememberSaveable(group.id) { mutableStateOf(!long) }
    var find by rememberSaveable(group.id) { mutableStateOf("") }
    val chosen = group.filters.mapNotNull { f ->
        when {
            f is SourceFilter.Check && f.state -> f.name
            f is SourceFilter.TriState && f.state == 1 -> f.name
            f is SourceFilter.TriState && f.state == 2 -> "−" + f.name
            else -> null
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (long) Modifier.clickable { open = !open } else Modifier)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.name, color = AppColors.textSecondary, fontSize = 13.sp)
            if (long && chosen.isNotEmpty()) {
                Text(
                    chosen.joinToString(),
                    color = AppColors.accent,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (long) {
            Text(
                if (open) stringResource(R.string.filters_fold) else stringResource(R.string.filters_show_all, group.filters.size),
                color = AppColors.accent,
                fontSize = 13.sp,
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = AppColors.accent,
            )
        }
    }
    AnimatedVisibility(visible = open, enter = expandVertically(Motion.medium()), exit = shrinkVertically(Motion.medium())) {
        Column {
            if (group.filters.any { it is SourceFilter.TriState }) {
                Text(
                    stringResource(R.string.filters_tristate_hint),
                    color = AppColors.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (group.filters.size > FIND_AT) {
                TextField(
                    value = find,
                    onValueChange = { find = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.filters_find), color = AppColors.textTertiary) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = filterFieldColors(),
                )
            }
            val needle = find.trim()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.filters
                    .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
                    .forEach { f ->
                        when (f) {
                            is SourceFilter.Check -> ChoiceChip(
                                label = f.name,
                                selected = f.state,
                                onClick = { change(f.id) { (it as SourceFilter.Check).copy(state = !f.state) } },
                            )
                            is SourceFilter.TriState -> TriStateChip(f) {
                                change(f.id) { (it as SourceFilter.TriState).copy(state = (f.state + 1) % 3) }
                            }
                            else -> Unit
                        }
                    }
            }
        }
    }
}

/** Off, then include (accent), then exclude (struck through, danger colour), then off again. */
@Composable
private fun TriStateChip(filter: SourceFilter.TriState, onClick: () -> Unit) {
    val include = filter.state == 1
    val exclude = filter.state == 2
    val background by animateColorAsState(
        when {
            include -> AppColors.accentMuted
            exclude -> AppColors.danger.copy(alpha = 0.16f)
            else -> AppColors.card
        },
        Motion.short(),
        label = "triChipBackground",
    )
    val border = when {
        include -> AppColors.accent
        exclude -> AppColors.danger
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(10.dp)
    val spoken = when {
        include -> stringResource(R.string.filters_included)
        exclude -> stringResource(R.string.filters_excluded)
        else -> ""
    }
    Box(
        Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .semantics { if (spoken.isNotEmpty()) stateDescription = spoken }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            when {
                include -> "+ " + filter.name
                exclude -> "− " + filter.name
                else -> filter.name
            },
            color = when {
                include -> AppColors.textPrimary
                exclude -> AppColors.danger
                else -> AppColors.textSecondary
            },
            fontSize = 13.sp,
            fontWeight = if (include || exclude) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (exclude) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun filterFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AppColors.search,
    unfocusedContainerColor = AppColors.search,
    focusedTextColor = AppColors.textPrimary,
    unfocusedTextColor = AppColors.textPrimary,
    cursorColor = AppColors.accent,
    focusedIndicatorColor = AppColors.accent,
    unfocusedIndicatorColor = AppColors.divider,
)

/** Groups longer than this start folded. */
private const val FOLD_AT = 12

/** Groups longer than this get a field to find an entry. */
private const val FIND_AT = 30

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
