package com.id.mimikopi.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.Keys
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.ThemeMode
import com.id.mimikopi.desktop.audio.PackInstaller
import com.id.mimikopi.desktop.update.DesktopUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate

/** The app's version (set by the installer), or "dev". */
val appVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

/** MIDI keyboards: each device's state and what arrives from it, a re-check, and a report for finding problems. */
@Composable
fun MidiSection() {
    val devices by Services.midi.devices.collectAsState()
    val notice by Services.midi.notice.collectAsState()
    var report by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    PageCard(title = "คีย์บอร์ด MIDI") {
        if (devices.isEmpty()) {
            // Just pulled out: say so, with which keyboard.
            val gone = notice?.takeIf { it.kind == com.id.mimikopi.core.input.MidiNotice.Kind.UNPLUGGED }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.size(28.dp).border(1.5.dp, Tokens.Palette.Dim, androidx.compose.foundation.shape.CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(if (gone != null) "ถอดสายแล้ว" else "ยังไม่ได้เชื่อมต่อ", style = MaterialTheme.typography.titleMedium)
                    gone?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Text("เปิดโปรแกรมนี้ก่อน แล้วค่อยเสียบสาย USB จากคีย์บอร์ดเข้าคอมและเปิดเครื่อง โปรแกรมจะขึ้นว่าเชื่อมต่อภายในวินาทีเดียว",
                style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
        }
        devices.forEach { d ->
            // Listed and open = connected, unless its Active Sensing or identity answers stopped.
            val connected = d.open && !d.silent
            val status = when {
                !d.open -> d.error ?: "กำลังเชื่อมต่อ…"
                d.silent -> "ไม่มีสัญญาณ · คีย์บอร์ดน่าจะปิดอยู่ เปิดเครื่องแล้วจะกลับเป็น Connected เอง"
                else -> "เชื่อมต่อแล้ว"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(28.dp).then(
                        if (connected) Modifier.background(Tokens.Palette.Positive, androidx.compose.foundation.shape.CircleShape)
                        else Modifier.border(1.5.dp, Tokens.Palette.Dim, androidx.compose.foundation.shape.CircleShape),
                    ),
                    contentAlignment = Alignment.Center,
                ) { if (connected) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp) }
                Column(Modifier.weight(1f)) {
                    if (connected) Text("Connected", style = MaterialTheme.typography.titleMedium, color = Tokens.Palette.Positive)
                    Text("${d.name} · $status", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (d.open) Text(
                if (d.messages == 0L) "   ยังไม่ได้รับโน้ต ลองกดคีย์สักตัว" else "   รับแล้ว ${d.messages} ครั้ง · ล่าสุด: ${d.lastMessage}",
                fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = Tokens.Palette.InkMuted,
            )
        }
        // Open but silent: Windows 11's new MIDI service can stop passing input to older programs (Java uses WinMM).
        if (devices.any { it.open && it.messages == 0L }) Text(
            "ถ้ากดคีย์แล้วตัวเลขด้านบนไม่ขึ้นเลย แปลว่า Windows ให้เปิดคีย์บอร์ดได้แต่ไม่ส่งโน้ตมา (Windows 11 รุ่นใหม่มีปัญหานี้กับบริการ MIDI): " +
                "1) ถอดสายคีย์บอร์ด แล้วเสียบใหม่ตอนโปรแกรมนี้เปิดอยู่ (ได้ผลกับหลายเครื่อง)  2) ปิดเว็บหรือโปรแกรมอื่นที่ใช้ MIDI  3) รีสตาร์ทคอม  " +
                "ถ้ายังไม่ได้ ให้ทำ Windows Update แล้วกดรายงานการเชื่อมต่อส่งมา",
            style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            OutlinedButton(onClick = { Services.midi.rescan(force = true) }) { Text("ตรวจการเชื่อมต่อใหม่") }
            OutlinedButton(onClick = { report = Services.midi.report(); copied = false }) { Text("รายงานการเชื่อมต่อ") }
        }
        report?.let { r ->
            Text(r, fontFamily = Tokens.Fonts.mono, fontSize = 11.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 10.dp))
            TextButton(onClick = {
                runCatching {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(r), null)
                    copied = true
                }
            }) { Text(if (copied) "คัดลอกแล้ว" else "คัดลอกรายงาน") }
        }
        Text("ถ้าเสียบแล้วไม่ขึ้นภายในไม่กี่วินาที ให้กดตรวจการเชื่อมต่อใหม่ ถ้ายังไม่ขึ้นให้ปิดโปรแกรมแล้วเปิดใหม่",
            style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenMetronome: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val mode by Services.themeMode.collectAsState()
    val sampled by Services.sampled.collectAsState()
    var soundThrough by remember { mutableStateOf(Services.hub.soundThrough) }
    var velocity by remember { mutableStateOf(Services.hub.velocity) }
    fun setVelocity(m: com.id.mimikopi.core.input.VelocityMode) {
        velocity = m
        Services.hub.velocity = m
        Services.store.put(Keys.VELOCITY_MODE, m.encode())
    }
    var packState by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackHeader("ตั้งค่า", onBack)
        PageCard(title = "เมโทรนอม") {
            Text("เมโทรนอมสำหรับซ้อมเอง: ความเร็ว จังหวะ เน้นจังหวะแรก และเสียงย่อย", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenMetronome, modifier = Modifier.padding(top = 12.dp)) { Text("เปิดเมโทรนอม") }
        }
        val timing by Services.learning.timingMode.collectAsState()
        PageCard(title = "ความเข้มงวดของจังหวะ") {
            Text("ช่วงที่ยอมให้คลาดจากจังหวะในโหมดตามจังหวะ (การวัดผลใช้แบบปกติเสมอ)", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                listOf(
                    com.id.mimikopi.core.practice.TimingMode.STRICT to "เข้ม",
                    com.id.mimikopi.core.practice.TimingMode.NORMAL to "ปกติ",
                    com.id.mimikopi.core.practice.TimingMode.FORGIVING to "ผ่อน",
                ).forEach { (mode, label) ->
                    FilterChip(selected = timing == mode, onClick = { Services.learning.setTimingMode(mode) },
                        label = { Text("$label (±${Services.learning.exampleMs(mode)} ms)") })
                }
            }
        }
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
            // Key strength: off at the start (every note at one level), on later to practise touch.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("น้ำหนักคีย์ (Velocity)", style = MaterialTheme.typography.bodyMedium)
                    Text(if (velocity.on) "เปิดอยู่: กดแรงเสียงดัง กดเบาเสียงเบา ใช้ตอนฝึกน้ำหนักมือ" else "ปิดอยู่: ทุกโน้ตดังเท่ากัน กดเบาหรือแรงก็เหมือนกัน เหมาะกับช่วงแรก",
                        style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                }
                Switch(checked = velocity.on, onCheckedChange = { setVelocity(velocity.copy(on = it)) })
            }
            if (!velocity.on) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(com.id.mimikopi.core.input.VelocityMode.SOFT to "เบา", com.id.mimikopi.core.input.VelocityMode.MEDIUM to "กลาง", com.id.mimikopi.core.input.VelocityMode.LOUD to "ดัง").forEach { (level, label) ->
                    FilterChip(selected = velocity.fixed == level, onClick = { setVelocity(velocity.copy(fixed = level)) }, label = { Text(label) })
                }
            }
        }
        SyncSection()
        PageCard(title = "สำรองข้อมูล") {
            Text("ไฟล์สำรองทั้งเครื่อง (แบบเดียวกับแอพมือถือ) นำเข้าแล้วจะแทนข้อมูลเดิมทั้งหมด ใช้กู้คืนเครื่องนี้ ถ้าจะรวมกับมือถือให้ใช้ \"ซิงค์กับมือถือ\" ด้านบน",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = {
                    val f = pickFile(load = true, "mimikopi-backup.json") ?: return@OutlinedButton
                    val error = runCatching { Services.store.importBackup(f.readText()) }.getOrElse { it.message }
                    if (error == null) {
                        Services.learning.reload(); Services.pieces.reload()
                        Services.scope.launch { com.id.mimikopi.desktop.port.MimikopiApp.progress.reload() }
                    }
                    backupMessage = error?.let { "นำเข้าไม่ได้: $it" } ?: "นำเข้าแล้ว"
                }) { Text("นำเข้าไฟล์สำรอง") }
                OutlinedButton(onClick = {
                    val f = pickFile(load = false, "mimikopi-backup-${LocalDate.now()}.json") ?: return@OutlinedButton
                    backupMessage = runCatching { f.writeText(Services.store.exportBackup(appVersion)); "บันทึกแล้ว: ${f.name}" }.getOrElse { "บันทึกไม่ได้: ${it.message}" }
                }) { Text("ส่งออกไฟล์สำรอง") }
            }
            backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
        UpdateSection()
        PageCard(title = "เกี่ยวกับ") {
            Text("ミミコピ สำหรับ Windows รุ่น $appVersion", style = MaterialTheme.typography.bodyMedium)
            Text("ข้อมูลทั้งหมดอยู่ในเครื่องนี้ (โฟลเดอร์ ${Services.dir.path}) โปรแกรมใช้อินเทอร์เน็ตเฉพาะตอนตรวจรุ่นใหม่ (วันละครั้ง) และตอนดาวน์โหลดเสียงเปียโน",
                style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
            Text("เสียงเปียโน: Salamander Grand Piano V3 (Alexander Holm, CC BY 3.0) · ฟอนต์ตัวโน้ต Bravura (SIL OFL 1.1) · ตัวหนังสือใช้ฟอนต์ของ Windows",
                style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp).width(640.dp))
        }
    }
}

/** The program's own updates: the result of the daily check, a button to check now, and download + install. */
@Composable
private fun UpdateSection() {
    val updater = Services.updater
    val state by updater.state.collectAsState()
    PageCard(title = "อัปเดตโปรแกรม") {
        if (!updater.supported) {
            Text("อัปเดตจากในโปรแกรมใช้ได้กับโปรแกรมที่ติดตั้งบน Windows", style = MaterialTheme.typography.bodyMedium)
            return@PageCard
        }
        val busy = state is DesktopUpdater.State.Checking || state is DesktopUpdater.State.Downloading || state is DesktopUpdater.State.Installing
        Text(
            when (val s = state) {
                DesktopUpdater.State.Idle -> "รุ่นที่ใช้อยู่ $appVersion"
                DesktopUpdater.State.Checking -> "กำลังตรวจรุ่นใหม่…"
                is DesktopUpdater.State.UpToDate -> "ใช้รุ่นล่าสุดอยู่แล้ว ($appVersion)"
                is DesktopUpdater.State.Available -> "มีรุ่นใหม่ ${s.manifest.versionName} (ใช้อยู่ $appVersion) · ${s.manifest.msiSize / 1_000_000} MB"
                is DesktopUpdater.State.Downloading -> "กำลังดาวน์โหลด ${(s.fraction * 100).toInt()}%"
                DesktopUpdater.State.Installing -> "กำลังติดตั้ง โปรแกรมจะปิดแล้วเปิดใหม่เอง"
                is DesktopUpdater.State.Failed -> s.message
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        (state as? DesktopUpdater.State.Available)?.manifest?.notesTh?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            val available = state as? DesktopUpdater.State.Available
            if (available != null) Button(enabled = !busy, onClick = {
                Thread {
                    if (updater.downloadAndInstall(available.manifest)) {
                        // The installer replaces this program's files: close it.
                        Services.midi.stop()
                        kotlin.system.exitProcess(0)
                    }
                }.apply { isDaemon = true; start() }
            }) { Text("ดาวน์โหลดและติดตั้ง") }
            OutlinedButton(enabled = !busy, onClick = { Thread { updater.check(force = true) }.apply { isDaemon = true; start() } }) { Text("ตรวจรุ่นใหม่") }
        }
        Text("ข้อมูลและความคืบหน้าอยู่ครบหลังอัปเดต", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun pickFile(load: Boolean, name: String): File? {
    val d = FileDialog(null as Frame?, if (load) "เลือกไฟล์สำรอง" else "บันทึกไฟล์สำรอง", if (load) FileDialog.LOAD else FileDialog.SAVE)
    d.file = name
    d.isVisible = true
    val file = d.file ?: return null
    return File(d.directory, file)
}

/**
 * Phone-to-PC sync: the address and one-time code the phone shows, or a sync file the phone exported. Merges both
 * ways (nothing replaced); runs off the UI thread.
 */
@Composable
private fun SyncSection() {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun outcome(o: com.id.mimikopi.desktop.sync.SyncOutcome) =
        if (o.changed == 0) "ข้อมูลตรงกับ ${o.from} อยู่แล้ว" else "ซิงค์แล้ว: รับความคืบหน้าจาก ${o.from} (${o.changed} รายการ) และส่งของคอมไปให้แล้ว"
    fun start(work: () -> com.id.mimikopi.desktop.sync.SyncOutcome) {
        busy = true
        message = "กำลังซิงค์…"
        scope.launch {
            message = withContext(Dispatchers.IO) { runCatching { outcome(work()) }.getOrElse { com.id.mimikopi.desktop.sync.DesktopSync.explain(it) } }
            busy = false
        }
    }
    PageCard(title = "ซิงค์กับมือถือ") {
        Text("บนมือถือไปที่ ตั้งค่า → ซิงค์กับคอม → เริ่มซิงค์ แล้วใส่ที่อยู่และรหัสที่ขึ้นบนมือถือ (มือถือกับคอมต้องอยู่ Wi-Fi วงเดียวกัน) ข้อมูลของทั้งสองเครื่องรวมกัน ไม่มีอะไรหาย การตั้งค่าของแต่ละเครื่องไม่เปลี่ยน",
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            androidx.compose.material3.OutlinedTextField(address, { address = it.trim() }, label = { Text("ที่อยู่ (เช่น 192.168.1.23:48123)") }, singleLine = true, modifier = Modifier.width(300.dp))
            androidx.compose.material3.OutlinedTextField(code, { v -> code = v.filter { it.isDigit() }.take(6) }, label = { Text("รหัส 6 หลัก") }, singleLine = true, modifier = Modifier.width(150.dp))
            Button(onClick = { start { com.id.mimikopi.desktop.sync.DesktopSync.connect(address, code) } },
                enabled = !busy && code.length == 6 && com.id.mimikopi.core.sync.LanSync.parseAddress(address) != null) { Text("ซิงค์") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            OutlinedButton(onClick = {
                val f = pickFile(load = true, "mimikopi-sync.json") ?: return@OutlinedButton
                start { com.id.mimikopi.desktop.sync.DesktopSync.importFrom(f) }
            }, enabled = !busy) { Text("นำเข้าไฟล์ซิงค์จากมือถือ") }
            OutlinedButton(onClick = {
                val f = pickFile(load = false, "mimikopi-sync-${LocalDate.now()}.json") ?: return@OutlinedButton
                message = runCatching { com.id.mimikopi.desktop.sync.DesktopSync.exportTo(f); "บันทึกไฟล์ซิงค์แล้ว: ${f.name} ส่งไปมือถือแล้วนำเข้าที่ ตั้งค่า → ซิงค์กับคอม" }
                    .getOrElse { "บันทึกไม่ได้: ${it.message}" }
            }, enabled = !busy) { Text("ส่งออกไฟล์ซิงค์") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).width(640.dp)) }
        Text("ส่งตรงระหว่างสองเครื่องใน Wi-Fi เข้ารหัส ไม่ผ่านอินเทอร์เน็ต ก่อนรวม โปรแกรมเก็บสำเนาข้อมูลเดิมไว้ (data.before-sync.json)",
            style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp).width(640.dp))
    }
}
