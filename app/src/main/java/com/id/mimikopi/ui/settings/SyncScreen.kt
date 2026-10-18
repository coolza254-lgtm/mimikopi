package com.id.mimikopi.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.sync.LanSync
import com.id.mimikopi.sync.SyncOutcome
import com.id.mimikopi.sync.SyncService
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.SecondaryButton
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.time.LocalDate

sealed interface SyncUi {
    data object Idle : SyncUi
    /** Waiting for the PC: the addresses to type, the port and the one-time code. */
    data class Waiting(val addresses: List<String>, val port: Int, val code: String) : SyncUi
    data object Working : SyncUi
    data class Done(val outcome: SyncOutcome) : SyncUi
    data object Exported : SyncUi
    data class Failed(val message: String) : SyncUi
}

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val sync = (app as MimikopiApp).sync
    private val _ui = MutableStateFlow<SyncUi>(SyncUi.Idle)
    val ui: StateFlow<SyncUi> = _ui.asStateFlow()
    private var server: ServerSocket? = null
    private var job: Job? = null

    /** Opens a listener and shows the address and code until the PC connects (or 3 minutes pass). */
    fun startWifi() {
        stop()
        job = viewModelScope.launch {
            val code = LanSync.newCode()
            val socket = runCatching { sync.open() }.getOrElse { _ui.value = SyncUi.Failed("เปิดการเชื่อมต่อไม่ได้: ${it.message}"); return@launch }
            server = socket
            _ui.value = SyncUi.Waiting(LanSync.localAddresses(), socket.localPort, code)
            _ui.value = try {
                SyncUi.Done(sync.serve(socket, code))
            } catch (e: Exception) {
                if (server == null) SyncUi.Idle else SyncUi.Failed(SyncService.explain(e))
            } finally {
                runCatching { socket.close() }
                server = null
            }
        }
    }

    /** Stops waiting (leaving the page or "ยกเลิก"). */
    fun stop() {
        val s = server
        server = null
        runCatching { s?.close() }
        job?.cancel()
        if (_ui.value is SyncUi.Waiting) _ui.value = SyncUi.Idle
    }

    fun export(uri: android.net.Uri) {
        viewModelScope.launch {
            _ui.value = runCatching { sync.exportTo(uri); SyncUi.Exported }.getOrElse { SyncUi.Failed("บันทึกไฟล์ไม่ได้: ${it.message}") }
        }
    }

    fun import(uri: android.net.Uri) {
        viewModelScope.launch {
            _ui.value = SyncUi.Working
            _ui.value = runCatching { SyncUi.Done(sync.importFrom(uri)) }.getOrElse { SyncUi.Failed(SyncService.explain(it)) }
        }
    }

    override fun onCleared() = stop()
}

/**
 * Phone-to-PC sync: one button opens a short wait with this phone's address and a one-time code for the PC to type;
 * a sync file is the fallback when the two are not on one Wi-Fi. Both merge, never replace.
 */
@Composable
fun SyncScreen(onBack: () -> Unit, vm: SyncViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    // The phone stays awake while it waits for the PC.
    val view = LocalView.current
    DisposableEffect(ui is SyncUi.Waiting) {
        view.keepScreenOn = ui is SyncUi.Waiting
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle("ซิงค์กับคอม", "รวมความคืบหน้าของมือถือกับคอมเข้าด้วยกัน ของทั้งสองเครื่องอยู่ครบ", onBack = onBack)
            Column(Modifier.padding(horizontal = PageMargin)) {
                GroupHeader("ผ่าน Wi-Fi บ้าน")
                Group {
                    Column(Modifier.padding(20.dp)) {
                        when (val s = ui) {
                            is SyncUi.Waiting -> {
                                if (s.addresses.isEmpty()) {
                                    Text("ไม่พบ Wi-Fi ต่อ Wi-Fi วงเดียวกับคอมก่อน", fontSize = 17.sp, color = Tokens.Palette.Ink)
                                } else {
                                    Text("ในโปรแกรมบนคอม ใส่ที่อยู่และรหัสนี้", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                                    s.addresses.take(2).forEach {
                                        Text("$it:${s.port}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 6.dp))
                                    }
                                    Text("รหัส", fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 14.dp))
                                    Text(s.code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Tokens.Palette.Ink)
                                    Text("รอคอมเชื่อมต่อ… เปิดหน้านี้ค้างไว้ (ภายใน 3 นาที)", fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 10.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                SecondaryButton("ยกเลิก", vm::stop)
                            }
                            SyncUi.Working -> Text("กำลังรวมข้อมูล…", fontSize = 17.sp, color = Tokens.Palette.Ink)
                            SyncUi.Exported -> {
                                Text("บันทึกไฟล์ซิงค์แล้ว ส่งไฟล์นี้ไปที่คอม แล้วในโปรแกรมบนคอมไปที่ ตั้งค่า → ซิงค์กับมือถือ → นำเข้าไฟล์ซิงค์",
                                    fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 16.dp))
                                PrimaryButton("เริ่มซิงค์ผ่าน Wi-Fi", vm::startWifi)
                            }
                            is SyncUi.Done -> {
                                Text("ซิงค์แล้ว", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                                Text(
                                    if (s.outcome.changed == 0) "ข้อมูลของ ${s.outcome.from} ตรงกับมือถืออยู่แล้ว"
                                    else "รับความคืบหน้าจาก ${s.outcome.from} แล้ว (${s.outcome.changed} รายการ) คอมก็ได้ของมือถือไปเช่นกัน",
                                    fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                                )
                                PrimaryButton("ซิงค์อีกครั้ง", vm::startWifi)
                            }
                            is SyncUi.Failed -> {
                                Text(s.message, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 16.dp))
                                PrimaryButton("เริ่มซิงค์", vm::startWifi)
                            }
                            SyncUi.Idle -> {
                                Text(
                                    "ให้มือถือกับคอมต่อ Wi-Fi วงเดียวกัน กดเริ่ม แล้วบนคอมไปที่ ตั้งค่า → ซิงค์กับมือถือ ใส่ที่อยู่และรหัสที่ขึ้นตรงนี้",
                                    fontSize = 15.sp, lineHeight = 21.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 16.dp),
                                )
                                PrimaryButton("เริ่มซิงค์", vm::startWifi)
                            }
                        }
                    }
                }
                GroupFooter("ส่งตรงระหว่างสองเครื่องใน Wi-Fi เข้ารหัส ไม่ผ่านอินเทอร์เน็ต รหัสใช้ได้ครั้งเดียว")

                GroupHeader("ผ่านไฟล์")
                Group {
                    GroupRow("ส่งออกไฟล์ซิงค์", subtitle = "ส่งไฟล์ไปที่คอมทาง LINE, Drive หรือสาย USB แล้วนำเข้าบนคอม",
                        onClick = { exporter.launch("mimikopi-sync-${LocalDate.now()}.json") })
                    GroupDivider()
                    GroupRow("นำเข้าไฟล์ซิงค์จากคอม", subtitle = "รวมกับข้อมูลในมือถือ ไม่ลบของเดิม",
                        onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) })
                }
                GroupFooter(
                    "ซิงค์เฉพาะความคืบหน้า: ขั้นที่ผ่าน ระดับทักษะและผลทดสอบ เวลาฝึก ผลฝึกฟัง เพลงที่ฝึก และโน้ตที่ทวน " +
                        "การตั้งค่าของแต่ละเครื่อง (MIDI ความหน่วง เสียง ธีม) ไม่ถูกเปลี่ยน ก่อนรวม แอพสำรองข้อมูลเดิมไว้ให้อัตโนมัติ",
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
