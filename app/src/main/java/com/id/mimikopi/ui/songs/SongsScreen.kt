package com.id.mimikopi.ui.songs

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import kotlinx.coroutines.launch

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MimikopiApp).pieces
    val progress = repo.progress
    init { viewModelScope.launch { repo.load() } }
}

@Composable
fun SongsScreen(onPlay: (String, String) -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit, onTarget: (String) -> Unit, vm: SongsViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TabHeader("เพลง", "1 เพลงเป้าหมาย", onOpenMidi, onOpenSettings)
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroicDashboard(progress, onTarget, onPlay, onOpenLibrary)
            }
        }
    }
}
