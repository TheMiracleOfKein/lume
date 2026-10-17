package com.lume.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.data.db.ProgressEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(app: AppContainer) : ViewModel() {
    val history: StateFlow<List<ProgressEntity>> =
        app.dao.observeHistory().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
