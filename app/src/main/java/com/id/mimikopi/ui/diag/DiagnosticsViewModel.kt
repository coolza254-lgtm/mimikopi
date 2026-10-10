package com.id.mimikopi.ui.diag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.diag.DiagnosticsReport
import com.id.mimikopi.diag.DiagnosticsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {
    private val collector = (app as MimikopiApp).let { DiagnosticsCollector(it, it.database, it.piano, it.samplePacks) }

    private val _report = MutableStateFlow<DiagnosticsReport?>(null)
    val report: StateFlow<DiagnosticsReport?> = _report.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _report.value = withContext(Dispatchers.IO) { collector.collect() }
        }
    }
}
