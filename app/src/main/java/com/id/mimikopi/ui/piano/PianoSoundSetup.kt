package com.id.mimikopi.ui.piano

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.piano.PackException
import com.id.mimikopi.piano.PackProgress
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Installing the app's piano sound (the sampled grand piano pack) on the phone. */
sealed interface PackSetup {
    data object Idle : PackSetup
    data class Downloading(val fraction: Float) : PackSetup
    data class Decoding(val done: Int, val total: Int) : PackSetup
    data class Failed(val reason: String) : PackSetup
}

class PianoSoundSetupViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val _state = MutableStateFlow<PackSetup>(PackSetup.Idle)
    val state: StateFlow<PackSetup> = _state.asStateFlow()
    private var job: Job? = null

    /** Downloads only when tapped; afterwards the sound works offline. */
    fun install(onReady: () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = PackSetup.Downloading(0f)
            try {
                withContext(Dispatchers.IO) {
                    val d = services.samplePacks.fetchDescriptor()
                    services.samplePacks.install(d) { p ->
                        _state.value = when (p) {
                            is PackProgress.Downloading -> PackSetup.Downloading(p.fraction)
                            is PackProgress.Decoding -> PackSetup.Decoding(p.done, p.total)
                        }
                    }
                }
                services.piano.reset()
                _state.value = PackSetup.Idle
                onReady()
            } catch (e: PackException) {
                _state.value = PackSetup.Failed(
                    when (e.kind) {
                        PackException.Kind.NETWORK -> "ต่ออินเทอร์เน็ตไม่ได้ ลองใหม่เมื่อมีเน็ต"
                        PackException.Kind.NO_SPACE -> "พื้นที่ในเครื่องไม่พอ ต้องว่างราว 650 MB"
                        else -> "ติดตั้งไม่สำเร็จ (${e.kind.name.lowercase()}) ลองใหม่อีกครั้ง"
                    },
                )
            } catch (e: java.io.IOException) {
                _state.value = PackSetup.Failed("ติดตั้งไม่สำเร็จ: ${e.message ?: "อ่านเขียนไฟล์ไม่ได้"}")
            }
        }
    }
}

/** Shown on the piano page while the phone has no piano sound: what it is, its size, one button. */
@Composable
fun PianoSoundSetup(onReady: () -> Unit, vm: PianoSoundSetupViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Group {
        Column(Modifier.padding(20.dp)) {
            Text("ยังไม่มีเสียงเปียโนในเครื่อง", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text(
                "เสียงแกรนด์เปียโน (Salamander Grand Piano) ดาวน์โหลดครั้งเดียวประมาณ 79 MB ใช้พื้นที่ราว 440 MB " +
                    "ดาวน์โหลดเมื่อกดปุ่มนี้เท่านั้น ติดตั้งแล้วใช้ได้โดยไม่ต้องต่อเน็ต และใช้กับทุกหน้าในแอพ",
                fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is PackSetup.Downloading -> Text("กำลังดาวน์โหลด ${(s.fraction * 100).toInt()}%", fontSize = 15.sp, color = Tokens.Palette.Ink)
                is PackSetup.Decoding -> Text("กำลังเตรียมเสียง ${s.done}/${s.total}", fontSize = 15.sp, color = Tokens.Palette.Ink)
                is PackSetup.Failed -> {
                    Text(s.reason, fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 12.dp))
                    PrimaryButton("ลองอีกครั้ง", { vm.install(onReady) })
                }
                PackSetup.Idle -> PrimaryButton("ติดตั้งเสียงเปียโน", { vm.install(onReady) })
            }
        }
    }
}
