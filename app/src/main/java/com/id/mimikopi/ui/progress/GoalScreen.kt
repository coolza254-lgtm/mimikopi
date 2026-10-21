package com.id.mimikopi.ui.progress

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.songs.HeroicDashboard
import com.id.mimikopi.ui.songs.SongsViewModel

@Composable
fun GoalScreen(onBack: () -> Unit, onTarget: (String) -> Unit, onFree: (String, String) -> Unit,
    onSource: () -> Unit, vm: SongsViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    ScreenScaffold("เป้าหมาย · Heroic Polonaise", onBack) {
        HeroicDashboard(progress, onTarget, onFree, onSource)
    }
}
