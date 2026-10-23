package com.id.mimikopi.ui.piano

import com.id.mimikopi.core.i18n.tr
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
                        PackException.Kind.NETWORK -> tr("ต่ออินเทอร์เน็ตไม่ได้ ลองใหม่เมื่อมีเน็ต", "No internet connection; try again when online", "インターネットに接続できません。接続してから再試行してください")
                        PackException.Kind.NO_SPACE -> tr("พื้นที่ในเครื่องไม่พอ ต้องว่างราว 650 MB", "Not enough storage; about 650 MB must be free", "空き容量が足りません。約650 MB必要です")
                        else -> tr("ติดตั้งไม่สำเร็จ (${e.kind.name.lowercase()}) ลองใหม่อีกครั้ง", "Install failed (${e.kind.name.lowercase()}), please try again", "インストールに失敗しました（${e.kind.name.lowercase()}）。もう一度お試しください")
                    },
                )
            } catch (e: java.io.IOException) {
                _state.value = PackSetup.Failed("ติดตั้งไม่สำเร็จ: ${e.message ?: tr("อ่านเขียนไฟล์ไม่ได้", "Couldn't read or write files", "ファイルを読み書きできません")}")
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
            Text(tr("ยังไม่มีเสียงเปียโนในเครื่อง", "No piano sound on this device yet", "この端末にはまだピアノ音がありません"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            Text(
                tr("เสียงแกรนด์เปียโน (Salamander Grand Piano) ดาวน์โหลดครั้งเดียวประมาณ 79 MB ใช้พื้นที่ราว 440 MB ", "Grand piano sound (Salamander Grand Piano): a one-time download of about 79 MB, using about 440 MB. ", "グランドピアノ音（Salamander Grand Piano）：約79 MBを一度だけダウンロード、使用容量は約440 MB。") +
                    tr("ดาวน์โหลดเมื่อกดปุ่มนี้เท่านั้น ติดตั้งแล้วใช้ได้โดยไม่ต้องต่อเน็ต และใช้กับทุกหน้าในแอพ", "It only downloads when you press this button. Once installed it works offline and on every page of the app", "このボタンを押したときだけダウンロードします。インストール後はオフラインでもアプリ全体で使えます"),
                fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is PackSetup.Downloading -> Text(tr("กำลังดาวน์โหลด ${(s.fraction * 100).toInt()}%", "Downloading ${(s.fraction * 100).toInt()}%", "ダウンロード中 ${(s.fraction * 100).toInt()}%"), fontSize = 15.sp, color = Tokens.Palette.Ink)
                is PackSetup.Decoding -> Text(tr("กำลังเตรียมเสียง ${s.done}/${s.total}", "Preparing sounds ${s.done}/${s.total}", "音を準備中 ${s.done}/${s.total}"), fontSize = 15.sp, color = Tokens.Palette.Ink)
                is PackSetup.Failed -> {
                    Text(s.reason, fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 12.dp))
                    PrimaryButton(tr("ลองอีกครั้ง", "Try again", "もう一度"), { vm.install(onReady) })
                }
                PackSetup.Idle -> PrimaryButton(tr("ติดตั้งเสียงเปียโน", "Install piano sound", "ピアノ音をインストール"), { vm.install(onReady) })
            }
        }
    }
}
