package com.id.mimikopi.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.id.mimikopi.desktop.Keys
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.ThemeMode
import com.id.mimikopi.desktop.audio.PackInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate

/** The app's version (set by the installer), or "dev". */
val appVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

/** MIDI keyboards: each device's state, a re-check, and what the app can and cannot tell. */
@Composable
fun MidiSection() {
    val devices by Services.midi.devices.collectAsState()
    PageCard(title = "คีย์บอร์ด MIDI") {
        if (devices.isEmpty()) Text("ยังไม่พบคีย์บอร์ด เสียบสาย USB จากคีย์บอร์ดเข้าคอม แล้วเปิดเครื่อง", style = MaterialTheme.typography.bodyMedium)
        devices.forEach { d ->
            val status = when {
                !d.open -> d.error ?: "กำลังเชื่อมต่อ…"
                d.silent -> "ยังเสียบอยู่แต่ไม่มีสัญญาณ (น่าจะปิดเครื่อง)"
                d.sendsSensing -> "เชื่อมต่อแล้ว · เปิดเครื่องอยู่"
                else -> "เชื่อมต่อแล้ว (คีย์บอร์ดนี้ไม่ส่งสัญญาณบอกสถานะ แอพจึงรู้ไม่ได้ว่าปิดเครื่องหรือยัง)"
            }
            Text("• ${d.name} · $status", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        OutlinedButton(onClick = { Services.midi.rescan() }, modifier = Modifier.padding(top = 12.dp)) { Text("ตรวจการเชื่อมต่อใหม่") }
        Text("ถ้าเสียบคีย์บอร์ดหลังเปิดโปรแกรมแล้วไม่ขึ้น ให้ปิดโปรแกรมแล้วเปิดใหม่ (Windows บางเครื่องไม่แจ้งอุปกรณ์ใหม่ให้โปรแกรม Java)",
            style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val mode by Services.themeMode.collectAsState()
    val sampled by Services.sampled.collectAsState()
    var soundThrough by remember { mutableStateOf(Services.hub.soundThrough) }
    var packState by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackHeader("ตั้งค่า", onBack)
        PageCard(title = "หน้าตา") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to "มืด (OLED)", ThemeMode.LIGHT to "สว่าง", ThemeMode.SYSTEM to "ตามเครื่อง").forEach { (m, label) ->
                    FilterChip(selected = mode == m, onClick = { Services.setThemeMode(m) }, label = { Text(label) })
                }
            }
        }
        MidiSection()
        PageCard(title = "เสียงเปียโน") {
            Text(if (sampled) "เปียโนแกรนด์แบบแซมเปิล (Salamander Grand Piano)" else "เสียงพื้นฐานของ Java (ยังไม่ได้ติดตั้งเสียงเปียโนจริง)", style = MaterialTheme.typography.bodyMedium)
            if (!sampled) {
                Text("ดาวน์โหลดครั้งเดียวประมาณ 79 MB แล้วแตกไฟล์ในเครื่อง (ใช้พื้นที่ราว 520 MB) เป็นไฟล์เดียวกับในแอพมือถือ",
                    style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
                Button(enabled = !installing, modifier = Modifier.padding(top = 12.dp), onClick = {
                    installing = true
                    scope.launch {
                        val error = withContext(Dispatchers.IO) {
                            runCatching {
                                Services.pack.install { p ->
                                    packState = when (p) {
                                        is PackInstaller.Progress.Downloading -> "กำลังดาวน์โหลด ${(p.fraction * 100).toInt()}%"
                                        is PackInstaller.Progress.Decoding -> "กำลังแตกไฟล์ ${p.done}/${p.total}"
                                    }
                                }
                                Services.loadSampledPiano()
                            }.exceptionOrNull()
                        }
                        packState = if (error == null) "ติดตั้งแล้ว" else "ติดตั้งไม่สำเร็จ: ${error.message}"
                        installing = false
                    }
                }) { Text(if (installing) "กำลังติดตั้ง…" else "ดาวน์โหลดเสียงเปียโนจริง") }
            }
            packState?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("เล่นเสียงของโปรแกรมเมื่อกดคีย์บอร์ด", style = MaterialTheme.typography.bodyMedium)
                    Text("เปิดเมื่อคีย์บอร์ดไม่มีลำโพงในตัว", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                }
                Switch(checked = soundThrough, onCheckedChange = {
                    soundThrough = it
                    Services.hub.soundThrough = it
                    Services.store.put(Keys.SOUND_THROUGH, it.toString())
                })
            }
        }
        PageCard(title = "ย้ายข้อมูลกับมือถือ") {
            Text("ใช้ไฟล์สำรองแบบเดียวกับแอพมือถือ: ในมือถือไปที่ ตั้งค่า → สำรองข้อมูล → ส่งออก แล้วนำไฟล์มาเปิดที่นี่ (ความคืบหน้าฝึกฟังและท่อนที่จดไว้จะตามมาด้วย)",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = {
                    val f = pickFile(load = true, "mimikopi-backup.json") ?: return@OutlinedButton
                    val error = runCatching { Services.store.importBackup(f.readText()) }.getOrElse { it.message }
                    if (error == null) { Services.learning.reload(); Services.sections.reload() }
                    backupMessage = error?.let { "นำเข้าไม่ได้: $it" } ?: "นำเข้าแล้ว"
                }) { Text("นำเข้าไฟล์สำรอง") }
                OutlinedButton(onClick = {
                    val f = pickFile(load = false, "mimikopi-backup-${LocalDate.now()}.json") ?: return@OutlinedButton
                    backupMessage = runCatching { f.writeText(Services.store.exportBackup(appVersion)); "บันทึกแล้ว: ${f.name}" }.getOrElse { "บันทึกไม่ได้: ${it.message}" }
                }) { Text("ส่งออกไฟล์สำรอง") }
            }
            backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
        PageCard(title = "เกี่ยวกับ") {
            Text("ミミコピ สำหรับ Windows รุ่น $appVersion", style = MaterialTheme.typography.bodyMedium)
            Text("ข้อมูลทั้งหมดอยู่ในเครื่องนี้ (โฟลเดอร์ ${Services.dir.path}) โปรแกรมใช้อินเทอร์เน็ตเฉพาะตอนดาวน์โหลดเสียงเปียโน",
                style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
            Text("รุ่นนี้มีฝึกฟังและฝึกท่อน ส่วนอ่านโน้ตและเพลงจะตามมาในรุ่นถัดไป · เสียงเปียโน: Salamander Grand Piano V3 (Alexander Holm, CC BY 3.0) · ฟอนต์ Noto Serif Thai, IBM Plex Sans Thai, IBM Plex Mono (SIL OFL 1.1)",
                style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp).width(640.dp))
        }
    }
}

private fun pickFile(load: Boolean, name: String): File? {
    val d = FileDialog(null as Frame?, if (load) "เลือกไฟล์สำรอง" else "บันทึกไฟล์สำรอง", if (load) FileDialog.LOAD else FileDialog.SAVE)
    d.file = name
    d.isVisible = true
    val file = d.file ?: return null
    return File(d.directory, file)
}
