package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.desktop.audio.PackProgress
import com.id.mimikopi.desktop.input.ComputerKeyboard
import com.id.mimikopi.desktop.learning.SettingKeys
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Look
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.GroupFooter
import com.id.mimikopi.desktop.ui.components.GroupHeader
import com.id.mimikopi.desktop.ui.components.GroupRow
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate

/** A native Windows open/save dialog; null when cancelled. */
fun chooseFile(title: String, save: Boolean, suggested: String? = null, extension: String = "json"): File? {
    val d = FileDialog(null as Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    suggested?.let { d.file = it }
    if (!save) d.setFilenameFilter { _, name -> name.lowercase().endsWith(".$extension") }
    d.isVisible = true
    val f = d.file ?: return null
    return File(d.directory, f)
}

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val audio by s.piano.status.collectAsState()
    val ports by s.midi.ports.collectAsState()
    val lowC by s.keysLowC.collectAsState()
    val nameStyle by s.nameStyle.collectAsState()
    var packState by remember { mutableStateOf<String?>(null) }
    var packBusy by remember { mutableStateOf(false) }
    var cancelPack by remember { mutableStateOf(false) }
    var dataReport by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingRestore by remember { mutableStateOf<com.id.mimikopi.core.backup.BackupFile?>(null) }
    var midiSound by remember { mutableStateOf(s.hub.soundThrough) }
    var sound by remember { mutableStateOf(s.piano.sound) }
    var release by remember { mutableStateOf<com.id.mimikopi.core.update.DesktopRelease?>(null) }
    var updateText by remember { mutableStateOf<String?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var cancelUpdate by remember { mutableStateOf(false) }

    Page {
        LargeTitle("ตั้งค่า", "เสียง · เปียโน MIDI · ข้อมูลในเครื่อง")

        GroupHeader("เสียงของโปรแกรม")
        Group {
            GroupRow("สถานะ", when {
                audio.error != null -> audio.error
                audio.open -> "พร้อม · หน่วงราว ${audio.bufferMs?.let { "%.0f".format(it) } ?: "–"} ms"
                else -> "เปิดเมื่อหน้าที่ใช้เสียงเปิดอยู่"
            })
            GroupDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ความดัง", modifier = Modifier.width(140.dp))
                Slider(sound.volume, { sound = sound.copy(volume = it); s.setPianoSound(sound) }, modifier = Modifier.weight(1f))
            }
            GroupDivider()
            GroupRow("เสียงเปียโน", when {
                packState != null -> packState
                audio.samples -> "เสียงเปียโนจริง (Salamander Grand, CC BY 3.0) · ${s.samples.installedBytes() / 1_000_000} MB"
                else -> "เสียงพื้นฐานในตัว · ติดตั้งเสียงเปียโนจริงได้ (ดาวน์โหลด 79 MB จาก GitHub ของโปรเจกต์เมื่อกดเท่านั้น)"
            }, trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (packBusy) SecondaryButton("ยกเลิก", onClick = { cancelPack = true })
                    else if (!audio.samples) {
                        SecondaryButton("ดาวน์โหลดและติดตั้ง", onClick = {
                            packBusy = true; cancelPack = false
                            scope.launch {
                                packState = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val d = s.samples.fetchDescriptor()
                                        val pack = s.samples.install(d, { cancelPack }) { p ->
                                            packState = when (p) {
                                                is PackProgress.Downloading -> "ดาวน์โหลด ${(p.fraction * 100).toInt()}%"
                                                is PackProgress.Decoding -> "แปลงไฟล์เสียง ${p.done}/${p.total}"
                                            }
                                        }
                                        s.samples.load(s.piano, pack)
                                    }
                                }.fold({ it ?: "ติดตั้งแล้ว" }, { "ไม่สำเร็จ: ${it.message} (ไม่ได้เปลี่ยนเสียงเดิม)" })
                                packBusy = false
                            }
                        })
                        SecondaryButton("จากไฟล์ ZIP", onClick = {
                            val f = chooseFile("เลือกไฟล์ samples-v1.zip", save = false, extension = "zip") ?: return@SecondaryButton
                            packBusy = true; cancelPack = false
                            scope.launch {
                                packState = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val pack = s.samples.installFromFile(f, { cancelPack }) { p -> if (p is PackProgress.Decoding) packState = "แปลงไฟล์เสียง ${p.done}/${p.total}" }
                                        s.samples.load(s.piano, pack)
                                    }
                                }.fold({ it ?: "ติดตั้งแล้ว" }, { "ไม่สำเร็จ: ${it.message}" })
                                packBusy = false
                            }
                        })
                    } else SecondaryButton("ลบ", onClick = { s.piano.unloadPack(); s.samples.uninstall(); packState = "ใช้เสียงพื้นฐาน" })
                }
            })
        }
        GroupFooter("ตั้งค่าเสียงให้หน่วงน้อยที่สุดเองแล้ว ไม่ต้องวัดหรือปรับ · โปรแกรมอื่นยังมีเสียงได้ตามปกติ")

        GroupHeader("เปียโน MIDI")
        Group {
            if (ports.isEmpty()) GroupRow("ไม่พบพอร์ต MIDI", "เสียบสาย USB แล้วรอสักครู่ โปรแกรมค้นหาทุก 1.5 วินาที")
            ports.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                GroupRow(p.name, p.error ?: if (p.open) "เชื่อมต่อแล้ว" else "กำลังเปิด", value = if (p.open) "●" else "○")
            }
            GroupDivider()
            GroupRow("ค้นหาใหม่", onClick = { s.midi.retryFailed() })
            GroupDivider()
            GroupRow("เล่นเสียงจากโปรแกรมด้วยเมื่อกด MIDI", if (midiSound) "เปิด: ได้ยินทั้งเปียโนและโปรแกรม" else "ปิด (แนะนำ): ใช้ลำโพงของเปียโนเอง ไม่ซ้อนเสียงที่หน่วงกว่า",
                trailing = { Toggle("", midiSound) { midiSound = it; s.setMidiSound(it) } })
        }
        GroupFooter("ต่อ MIDI แล้ว หน้าฝึกจะซ่อนคีย์บนจอให้โน้ตใหญ่ขึ้น โจทย์ เฉลย ตัวอย่าง และคลิกยังออกจากลำโพงคอมพิวเตอร์")

        GroupHeader("เล่นด้วยแป้นพิมพ์คอมพิวเตอร์")
        Group {
            GroupRow("แถวล่าง Z X C V B N M , = คีย์ขาว · S D G H J = คีย์ดำ", "แถวบน Q W E R T Y U I O P ต่ออีกอ็อกเทฟ · 2 3 5 6 7 9 0 = คีย์ดำ")
            GroupDivider()
            GroupRow("ระดับเสียงต่ำสุดของแถวล่าง", "[ ลดอ็อกเทฟ · ] เพิ่มอ็อกเทฟ", value = "C${lowC / 12 - 1}",
                trailing = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton("−", onClick = { s.setKeysLowC(ComputerKeyboard.shiftOctave(lowC, false)) })
                    SecondaryButton("+", onClick = { s.setKeysLowC(ComputerKeyboard.shiftOctave(lowC, true)) })
                } })
        }
        GroupFooter("Space เริ่ม/หยุด · Esc หยุดหรือกลับ · F11 เต็มจอ · ขณะพิมพ์ข้อความ แป้นพิมพ์ไม่เล่นโน้ต")

        GroupHeader("หน้าตา")
        Group {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("สี", modifier = Modifier.width(140.dp))
                Segmented(listOf("LIGHT", "DARK", "SYSTEM"), Look.mode, { when (it) { "DARK" -> "มืด"; "LIGHT" -> "สว่าง"; else -> "ตามระบบ" } },
                    { Look.mode = it; s.db.put(SettingKeys.THEME_MODE, it) }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ชื่อโน้ต", modifier = Modifier.width(140.dp))
                Segmented(NoteNameStyle.entries, nameStyle, { when (it) { NoteNameStyle.LETTER -> "C D E"; NoteNameStyle.SOLFEGE -> "ド レ ミ"; else -> "ทั้งสอง" } },
                    s::setNameStyle, Modifier.weight(1f))
            }
        }

        GroupHeader("อัปเดตโปรแกรม")
        Group {
            GroupRow("รุ่นที่ใช้อยู่ ${s.appVersion}", updateText ?: "ตรวจหารุ่นใหม่เมื่อกดเท่านั้น", trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val r = release
                    when {
                        updateBusy -> SecondaryButton("ยกเลิก", onClick = { cancelUpdate = true })
                        r != null -> PrimaryButton("ติดตั้งรุ่น ${r.version}", onClick = {
                            updateBusy = true; cancelUpdate = false
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val msi = s.updater.download(r, { cancelUpdate }) { f -> updateText = "ดาวน์โหลด ${(f * 100).toInt()}%" }
                                        s.updater.install(msi)
                                    }
                                }
                                updateBusy = false
                                result.fold({ started ->
                                    if (started) { updateText = "กำลังติดตั้ง โปรแกรมจะปิดและเปิดใหม่เอง"; app.quit() }
                                    else updateText = "ติดตั้งอัตโนมัติได้บน Windows เท่านั้น"
                                }, { updateText = it.message })
                            }
                        }, modifier = Modifier.width(200.dp))
                        else -> SecondaryButton("ตรวจหารุ่นใหม่", onClick = {
                            updateBusy = true
                            updateText = "กำลังตรวจ…"
                            scope.launch {
                                val result = runCatching { withContext(Dispatchers.IO) { s.updater.check() } }
                                updateBusy = false
                                result.fold({ found ->
                                    release = found
                                    updateText = found?.let { "มีรุ่นใหม่ ${it.version}" + it.notes.lineSequence().firstOrNull { l -> l.isNotBlank() }?.let { n -> " · $n" }.orEmpty() }
                                        ?: "ใช้รุ่นล่าสุดอยู่แล้ว"
                                }, { updateText = it.message })
                            }
                        })
                    }
                }
            })
        }
        GroupFooter("ดาวน์โหลดจาก GitHub ของโปรเจกต์ ตรวจค่า SHA-256 ก่อนติดตั้งทุกครั้ง · ข้อมูลการฝึกในเครื่องอยู่ครบ")

        GroupHeader("ข้อมูลในเครื่อง")
        Group {
            GroupRow("โฟลเดอร์ข้อมูล", s.dirs.root.absolutePath, onClick = { runCatching { java.awt.Desktop.getDesktop().open(s.dirs.root) } })
            GroupDivider()
            GroupRow("ส่งออกไฟล์สำรองทั้งหมด", "รูปแบบเดียวกับมือถือ · นำไปกู้คืนบนมือถือได้", onClick = {
                val f = chooseFile("บันทึกไฟล์สำรอง", save = true, suggested = "mimikopi-pc-${LocalDate.now()}.json") ?: return@GroupRow
                scope.launch {
                    dataReport = runCatching { withContext(Dispatchers.IO) { s.transfer.export(f) } }
                        .fold({ listOf("บันทึกแล้ว $it แถว: ${f.absolutePath}") }, { listOf("บันทึกไม่สำเร็จ: ${it.message}") })
                }
            })
            GroupDivider()
            GroupRow("รวมข้อมูลจากไฟล์สำรองของมือถือ", "เพิ่มประวัติรายบทและความคืบหน้า ไม่ลบของในเครื่องนี้ · นำเข้าซ้ำได้ไม่ซ้ำซ้อน", onClick = {
                val f = chooseFile("เลือกไฟล์สำรองจากมือถือ", save = false) ?: return@GroupRow
                scope.launch {
                    dataReport = withContext(Dispatchers.IO) {
                        when (val c = s.transfer.read(f)) {
                            is BackupCheck.Rejected -> listOf(c.reason)
                            is BackupCheck.Ok -> runCatching { s.transfer.merge(c.backup).also { s.reloadAll() } }
                                .fold({ r -> r.lines + listOfNotNull(r.safetyCopy?.let { "สำเนาก่อนนำเข้า: ${it.name}" }) }, { listOf("นำเข้าไม่สำเร็จ ข้อมูลเดิมไม่เปลี่ยน: ${it.message}") })
                        }
                    }
                }
            })
            GroupDivider()
            GroupRow("แทนที่ด้วยไฟล์สำรอง (ย้ายเครื่อง)", "ลบข้อมูลในเครื่องนี้แล้วใช้ข้อมูลในไฟล์ · ทำสำเนาก่อนเสมอ", onClick = {
                val f = chooseFile("เลือกไฟล์สำรองที่จะใช้แทน", save = false) ?: return@GroupRow
                when (val c = s.transfer.read(f)) {
                    is BackupCheck.Rejected -> dataReport = listOf(c.reason)
                    is BackupCheck.Ok -> { pendingRestore = c.backup; dataReport = listOf("ไฟล์จาก ${c.backup.appVersionName} สร้างเมื่อ ${c.backup.createdAtUtc} · ${c.backup.data.totalRows} แถว · กดยืนยันด้านล่างเพื่อแทนที่") }
                }
            })
            pendingRestore?.let { b ->
                GroupDivider()
                GroupRow("ยืนยันแทนที่ข้อมูลทั้งหมด", "ข้อมูลปัจจุบันจะถูกเก็บเป็นสำเนาในโฟลเดอร์ backups ก่อน", onClick = {
                    pendingRestore = null
                    scope.launch {
                        dataReport = runCatching { withContext(Dispatchers.IO) { s.transfer.restore(b).also { s.reloadAll() } } }
                            .fold({ it.lines }, { listOf("ไม่สำเร็จ ข้อมูลเดิมไม่เปลี่ยน: ${it.message}") })
                    }
                }, titleColor = Tokens.Palette.Ink)
            }
            GroupDivider()
            GroupRow("สำเนาอัตโนมัติ", "${s.transfer.autoBackups().size} ไฟล์ · ทำทุกวันที่เปิดโปรแกรม และก่อนนำเข้าทุกครั้ง")
        }
        dataReport.forEach { Text(it, fontSize = 14.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(horizontal = 16.dp)) }
        GroupFooter("ข้อมูลอยู่ในเครื่องนี้เท่านั้น ไม่มีบัญชี ไม่มีเซิร์ฟเวอร์ · เพลงส่วนตัวไม่ถูกรวมอัตโนมัติ · เวอร์ชัน ${s.appVersion}")
    }
}

fun sourceName(source: String): String = when (source) {
    "USB_MIDI" -> "เปียโน MIDI"
    "BLE_MIDI" -> "MIDI บลูทูธ"
    "COMPUTER_KEYS" -> "แป้นพิมพ์คอมพิวเตอร์"
    "TOUCH" -> "คลิกคีย์บนจอ"
    else -> source
}
