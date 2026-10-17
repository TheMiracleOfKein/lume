package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.lume.app.ui.components.LumeInsets
import com.lume.app.LocalAppContainer
import com.lume.app.R
import com.lume.app.data.source.SourceEngines
import com.lume.app.domain.CatalogSource
import com.lume.app.ui.source.SourceLoginActivity
import com.lume.app.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(nav: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = LocalAppContainer.current
    val vm: SourcesViewModel = viewModel { SourcesViewModel(container) }
    val sources by vm.sources.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val busy = vm.busy
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }

    val grouped = remember(sources) {
        sources
            .sortedBy { it.manifest.name.lowercase() }
            .groupBy { languageGroupKey(it.manifest.language) }
            .toList()
            .sortedBy { languageGroupSort(it.first) }
    }

    Scaffold(
        containerColor = AppColors.background,
        contentWindowInsets = LumeInsets.scaffold,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sources_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.sources_engines_hint),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sources_import_hint),
                color = AppColors.textPrimary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = vm.importUrl,
                onValueChange = { vm.importUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("https://…/madara-site.json", color = AppColors.textTertiary)
                },
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
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::import,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (busy) stringResource(R.string.sources_importing)
                    else stringResource(R.string.sources_import_by_url),
                )
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (langKey, list) ->
                    item(key = "lang-$langKey") {
                        SectionLabel(languageGroupLabel(langKey))
                    }
                    items(list, key = { "${langKey}-${it.manifest.id}" }) { source ->
                        val isLib = source.manifest.engine == SourceEngines.LIBSOCIAL
                        val token by vm.loginToken(source.manifest.id)
                            .collectAsState(initial = null)
                        SourceRow(
                            source = source,
                            selected = source.manifest.id == activeId,
                            removable = vm.isRemovable(source.manifest.id),
                            loggedIn = token != null,
                            showLogin = isLib,
                            onSelect = { vm.setActive(source.manifest.id) },
                            onRemove = { vm.remove(source.manifest.id) },
                            onLogin = {
                                SourceLoginActivity.start(context, source.manifest.id)
                            },
                            onLogout = { vm.logout(source.manifest.id) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private fun languageGroupKey(language: String?): String {
    val tag = language?.trim()?.lowercase().orEmpty()
    return when {
        tag.isEmpty() || tag == "multi" || tag.contains(",") -> "multi"
        tag.startsWith("ru") -> "ru"
        tag.startsWith("en") -> "en"
        else -> tag.take(8)
    }
}

private fun languageGroupSort(key: String): Int = when (key) {
    "ru" -> 0
    "en" -> 1
    "multi" -> 2
    else -> 3
}

@Composable
private fun languageGroupLabel(key: String): String = when (key) {
    "ru" -> stringResource(R.string.sources_lang_ru)
    "en" -> stringResource(R.string.sources_lang_en)
    "multi" -> stringResource(R.string.sources_lang_multi)
    else -> key.uppercase()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AppColors.textTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SourceRow(
    source: CatalogSource,
    selected: Boolean,
    removable: Boolean,
    loggedIn: Boolean,
    showLogin: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val engine = source.manifest.engine
    val langBadge = languageBadge(source.manifest.language)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppColors.accent,
                unselectedColor = AppColors.textSecondary,
            ),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.manifest.name, color = AppColors.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    langBadge,
                    color = AppColors.accent,
                    fontSize = 11.sp,
                )
            }
            Text(
                buildString {
                    append(SourceEngines.displayName(engine))
                    append(" · ")
                    append(SourceEngines.kindLabel(context, engine))
                    if (showLogin) {
                        append(" · ")
                        append(
                            if (loggedIn) resources.getString(R.string.source_auth_on)
                            else resources.getString(R.string.source_auth_off),
                        )
                    }
                },
                color = AppColors.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                source.manifest.baseUrl,
                color = AppColors.textTertiary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        if (showLogin) {
            IconButton(onClick = { if (loggedIn) onLogout() else onLogin() }) {
                Icon(
                    if (loggedIn) Icons.AutoMirrored.Outlined.Logout else Icons.AutoMirrored.Outlined.Login,
                    contentDescription = null,
                    tint = if (loggedIn) AppColors.accent else AppColors.textSecondary,
                )
            }
        }
        if (removable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, null, tint = AppColors.textSecondary)
            }
        }
    }
}

@Composable
private fun languageBadge(language: String?): String {
    val key = languageGroupKey(language)
    return when (key) {
        "ru" -> "RU"
        "en" -> "EN"
        "multi" -> stringResource(R.string.sources_lang_badge_multi)
        else -> key.uppercase()
    }
}
