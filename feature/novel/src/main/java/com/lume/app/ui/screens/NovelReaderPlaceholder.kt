package com.lume.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.ui.components.BackButton
import com.lume.app.ui.components.LumeInsets
import com.lume.app.ui.theme.AppColors

/** Stands in for the text reader until it ships: a back arrow and a note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelReaderPlaceholder(onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onClick = onBack) },
                windowInsets = LumeInsets.topBar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.novel_reader_soon),
                color = AppColors.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
