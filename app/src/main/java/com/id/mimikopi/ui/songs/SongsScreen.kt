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
    onOpenLibrary: () -> Unit, onOpenPrivate: () -> Unit, onTarget: (String) -> Unit, vm: SongsViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TabHeader("เพลง", "1 เพลงเป้าหมาย", onOpenMidi, onOpenSettings)
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.id.mimikopi.ui.components.PageCard(title = "เพลงส่วนตัว · ฝึกจำจาก YouTube", onClick = onOpenPrivate) {
                    androidx.compose.material3.Text("แบ่งช่วง → หยุดวิดีโอแล้วลองเอง → เช็กจุดติด → ทวนข้ามวัน", color = com.id.mimikopi.ui.theme.Tokens.Palette.Ink)
                    androidx.compose.material3.Text("เก็บในเครื่อง · เริ่มได้โดยยังไม่มีโน้ตทั้งเพลง", color = com.id.mimikopi.ui.theme.Tokens.Palette.InkMuted)
                }
                HeroicDashboard(progress, onTarget, onPlay, onOpenLibrary)
            }
        }
    }
}
