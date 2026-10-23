package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.id.mimikopi.core.backup.BackupCheck
import com.id.mimikopi.core.input.PianoNames
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
import com.id.mimikopi.desktop.ui.components.ToyPiano
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
    val pianoNames by s.pianoNames.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) }
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

    renaming?.let { reported ->
        var name by remember(reported) { mutableStateOf(PianoNames.display(reported, pianoNames)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("ชื่อเปียโน", "Piano name", "ピアノの名前")) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(PianoNames.MAX_LENGTH) }, singleLine = true, label = { Text(tr("ชื่อที่แสดงในโปรแกรม", "Name shown in the app", "アプリでの表示名")) })
                    Text(tr("ชื่อที่เปียโนส่งมา: $reported", "Name the piano reports: $reported", "ピアノが送る名前：$reported"), fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { s.renamePiano(reported, name); renaming = null }) { Text(tr("บันทึก", "Save", "保存")) } },
            dismissButton = { TextButton(onClick = { s.renamePiano(reported, ""); renaming = null }) { Text(tr("ใช้ชื่อเดิม", "Use the original name", "元の名前を使う")) } },
        )
    }

    Page {
        LargeTitle(tr("ตั้งค่า", "Settings", "設定"), tr("เสียง · เปียโน MIDI · ข้อมูลในเครื่อง", "Sound · MIDI piano · local data", "音 · MIDIピアノ · 端末のデータ"))

        SyncButton()
        GroupHeader(tr("เสียงของโปรแกรม", "App sound", "アプリの音"))
        Group {
            GroupRow(tr("สถานะ", "Status", "状態"), when {
                audio.error != null -> audio.error
                audio.open -> "พร้อม · หน่วงราว ${audio.bufferMs?.let { "%.0f".format(it) } ?: "–"} ms"
                else -> tr("เปิดเมื่อหน้าที่ใช้เสียงเปิดอยู่", "On while a page that uses sound is open", "音を使うページを開いている間オン")
            })
            GroupDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("ความดัง", "Volume", "音量"), modifier = Modifier.width(140.dp))
                Slider(sound.volume, { sound = sound.copy(volume = it); s.setPianoSound(sound) }, modifier = Modifier.weight(1f))
            }
            GroupDivider()
            GroupRow(tr("เสียงเปียโน", "Piano sound", "ピアノ音"), when {
                packState != null -> packState
                audio.samples -> tr("เสียงเปียโนจริง (Salamander Grand, CC BY 3.0) · ${s.samples.installedBytes() / 1_000_000} MB", "Real piano sound (Salamander Grand, CC BY 3.0) · ${s.samples.installedBytes() / 1_000_000} MB", "本物のピアノ音（Salamander Grand、CC BY 3.0） · ${s.samples.installedBytes() / 1_000_000} MB")
                else -> tr("เสียงพื้นฐานในตัว · ติดตั้งเสียงเปียโนจริงได้ (ดาวน์โหลด 79 MB จาก GitHub ของโปรเจกต์เมื่อกดเท่านั้น)", "Built-in basic sound · a real piano sound can be installed (79 MB download from the project's GitHub, only when you press)", "内蔵の基本音 · 本物のピアノ音をインストールできます（押したときだけプロジェクトのGitHubから79 MBをダウンロード）")
            }, trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (packBusy) SecondaryButton(tr("ยกเลิก", "Cancel", "キャンセル"), onClick = { cancelPack = true })
                    else if (!audio.samples) {
                        SecondaryButton(tr("ดาวน์โหลดและติดตั้ง", "Download and install", "ダウンロードしてインストール"), onClick = {
                            packBusy = true; cancelPack = false
                            scope.launch {
                                packState = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val d = s.samples.fetchDescriptor()
                                        val pack = s.samples.install(d, { cancelPack }) { p ->
                                            packState = when (p) {
                                                is PackProgress.Downloading -> tr("ดาวน์โหลด ${(p.fraction * 100).toInt()}%", "Downloading ${(p.fraction * 100).toInt()}%", "ダウンロード中 ${(p.fraction * 100).toInt()}%")
                                                is PackProgress.Decoding -> tr("แปลงไฟล์เสียง ${p.done}/${p.total}", "Converting sound files ${p.done}/${p.total}", "音声ファイルを変換中 ${p.done}/${p.total}")
                                            }
                                        }
                                        s.samples.load(s.piano, pack)
                                    }
                                }.fold({ it ?: tr("ติดตั้งแล้ว", "Installed", "インストール済み") }, { tr("ไม่สำเร็จ: ${it.message} (ไม่ได้เปลี่ยนเสียงเดิม)", "Failed: ${it.message} (the current sound wasn't changed)", "失敗：${it.message}（今の音は変更していません）") })
                                packBusy = false
                            }
                        })
                        SecondaryButton(tr("จากไฟล์ ZIP", "From a ZIP file", "ZIPファイルから"), onClick = {
                            val f = chooseFile(tr("เลือกไฟล์ samples-v1.zip", "Choose samples-v1.zip", "samples-v1.zipを選ぶ"), save = false, extension = "zip") ?: return@SecondaryButton
                            packBusy = true; cancelPack = false
                            scope.launch {
                                packState = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val pack = s.samples.installFromFile(f, { cancelPack }) { p -> if (p is PackProgress.Decoding) packState = tr("แปลงไฟล์เสียง ${p.done}/${p.total}", "Converting sound files ${p.done}/${p.total}", "音声ファイルを変換中 ${p.done}/${p.total}") }
                                        s.samples.load(s.piano, pack)
                                    }
                                }.fold({ it ?: tr("ติดตั้งแล้ว", "Installed", "インストール済み") }, { tr("ไม่สำเร็จ: ${it.message}", "Failed: ${it.message}", "失敗：${it.message}") })
                                packBusy = false
                            }
                        })
                    } else SecondaryButton(tr("ลบ", "Delete", "削除"), onClick = { s.piano.unloadPack(); s.samples.uninstall(); packState = tr("ใช้เสียงพื้นฐาน", "Use the basic sound", "基本の音を使う") })
                }
            })
        }
        GroupFooter(tr("ตั้งค่าเสียงให้หน่วงน้อยที่สุดเองแล้ว ไม่ต้องวัดหรือปรับ · โปรแกรมอื่นยังมีเสียงได้ตามปกติ", "Audio is already set for the lowest latency; nothing to measure or adjust · other programs can still play sound", "音声は最小の遅延に自動設定済み。測定や調整は不要 · 他のソフトの音も通常どおり鳴ります"))

        GroupHeader(tr("เปียโน MIDI", "MIDI piano", "MIDIピアノ"))
        Group {
            if (ports.isEmpty()) GroupRow(tr("ไม่พบพอร์ต MIDI", "No MIDI port found", "MIDIポートが見つかりません"), tr("เสียบสาย USB แล้วรอสักครู่ โปรแกรมค้นหาทุก 1.5 วินาที", "Plug in the USB cable and wait a moment; the app searches every 1.5 seconds", "USBケーブルを挿して少し待ってください。1.5秒ごとに探します"))
            ports.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                GroupRow(
                    PianoNames.display(p.name, pianoNames), p.error ?: if (p.open) tr("เชื่อมต่อแล้ว · กดเพื่อเปลี่ยนชื่อ", "Connected · click to rename", "接続済み · クリックで名前を変更") else tr("กำลังเปิด", "Opening", "接続中"),
                    value = if (p.open) "●" else "○",
                    onClick = { renaming = p.name },
                    leading = if (p.open) ({ ToyPiano(40.dp) }) else null,
                )
            }
            GroupDivider()
            GroupRow(tr("ค้นหาใหม่", "Search again", "もう一度探す"), onClick = { s.midi.retryFailed() })
            GroupDivider()
            GroupRow(tr("เล่นเสียงจากโปรแกรมด้วยเมื่อกด MIDI", "Also play the app's sound for MIDI keys", "MIDIの鍵盤でアプリの音も鳴らす"), if (midiSound) tr("เปิด: ได้ยินทั้งเปียโนและโปรแกรม", "On: hear both the piano and the app", "オン：ピアノとアプリの両方が鳴ります") else tr("ปิด (แนะนำ): ใช้ลำโพงของเปียโนเอง ไม่ซ้อนเสียงที่หน่วงกว่า", "Off (recommended): use the piano's own speakers, without a later doubled sound", "オフ（おすすめ）：ピアノ本体のスピーカーを使い、遅れて重なる音を出しません"),
                trailing = { Toggle("", midiSound) { midiSound = it; s.setMidiSound(it) } })
        }
        GroupFooter(tr("ต่อ MIDI แล้ว หน้าฝึกจะซ่อนคีย์บนจอให้โน้ตใหญ่ขึ้น โจทย์ เฉลย ตัวอย่าง และคลิกยังออกจากลำโพงคอมพิวเตอร์", "With MIDI connected, practice pages hide the on-screen keys so the music is bigger. Questions, solutions, examples and the click still come from the computer's speakers", "MIDI接続中は練習ページで画面の鍵盤を隠して楽譜を大きくします。問題、答え、お手本、クリックはPCのスピーカーから鳴ります"))

        GroupHeader(tr("เล่นด้วยแป้นพิมพ์คอมพิวเตอร์", "Play with the computer keyboard", "PCのキーボードで弾く"))
        Group {
            GroupRow(tr("แถวล่าง Z X C V B N M , = คีย์ขาว · S D G H J = คีย์ดำ", "Bottom row Z X C V B N M , = white keys · S D G H J = black keys", "下の列 Z X C V B N M , = 白鍵 · S D G H J = 黒鍵"), tr("แถวบน Q W E R T Y U I O P ต่ออีกอ็อกเทฟ · 2 3 5 6 7 9 0 = คีย์ดำ", "Top row Q W E R T Y U I O P continues an octave up · 2 3 5 6 7 9 0 = black keys", "上の列 Q W E R T Y U I O P で1オクターブ上 · 2 3 5 6 7 9 0 = 黒鍵"))
            GroupDivider()
            GroupRow(tr("ระดับเสียงต่ำสุดของแถวล่าง", "Lowest note of the bottom row", "下の列の最低音"), tr("[ ลดอ็อกเทฟ · ] เพิ่มอ็อกเทฟ", "[ octave down · ] octave up", "[ オクターブ下 · ] オクターブ上"), value = "C${lowC / 12 - 1}",
                trailing = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton("−", onClick = { s.setKeysLowC(ComputerKeyboard.shiftOctave(lowC, false)) })
                    SecondaryButton("+", onClick = { s.setKeysLowC(ComputerKeyboard.shiftOctave(lowC, true)) })
                } })
        }
        GroupFooter(tr("Space เริ่ม/หยุด · Esc หยุดหรือกลับ · F11 เต็มจอ · ขณะพิมพ์ข้อความ แป้นพิมพ์ไม่เล่นโน้ต", "Space start/stop · Esc stop or back · F11 full screen · while typing text, the keyboard doesn't play notes", "Space 開始/停止 · Esc 停止または戻る · F11 全画面 · 文字入力中はキーボードで音を鳴らしません"))

        GroupHeader(com.id.mimikopi.core.i18n.tr("หน้าตา", "Display", "表示"))
        Group {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(com.id.mimikopi.core.i18n.tr("ภาษา", "Language", "言語"), modifier = Modifier.width(140.dp))
                Segmented(com.id.mimikopi.core.i18n.Lang.entries.toList(), com.id.mimikopi.core.i18n.L10n.lang, { it.label },
                    { com.id.mimikopi.desktop.ui.LangState.set(s, it) }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("เสียงตอบถูก/ผิด", "Answer sounds", "正解・不正解の音"), modifier = Modifier.width(140.dp))
                var fx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.db.get(com.id.mimikopi.core.fx.AnswerSound.KEY) != "0") }
                Segmented(listOf(true, false), fx, { if (it) tr("เปิด", "On", "オン") else tr("ปิด", "Off", "オフ") },
                    { fx = it; s.db.put(com.id.mimikopi.core.fx.AnswerSound.KEY, if (it) "1" else "0") }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("สี", "Colours", "色"), modifier = Modifier.width(140.dp))
                Segmented(listOf("LIGHT", "DARK", "SYSTEM"), Look.mode, { when (it) { "DARK" -> tr("มืด", "Dark", "ダーク"); "LIGHT" -> tr("สว่าง", "Light", "ライト"); else -> tr("ตามระบบ", "System", "システムに合わせる") } },
                    { Look.mode = it; s.db.put(SettingKeys.THEME_MODE, it) }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("ชื่อโน้ต", "Note names", "音名"), modifier = Modifier.width(140.dp))
                Segmented(NoteNameStyle.entries, nameStyle, { when (it) { NoteNameStyle.LETTER -> "C D E"; NoteNameStyle.SOLFEGE -> "ド レ ミ"; else -> tr("ทั้งสอง", "Both", "両方") } },
                    s::setNameStyle, Modifier.weight(1f))
            }
        }

        GroupHeader(tr("อัปเดตโปรแกรม", "App updates", "アプリの更新"))
        Group {
            GroupRow(tr("รุ่นที่ใช้อยู่ ${s.appVersion}", "Version in use ${s.appVersion}", "使用中のバージョン ${s.appVersion}"), updateText ?: tr("ตรวจหารุ่นใหม่เมื่อกดเท่านั้น", "Checks for a new version only when you press", "押したときだけ新しいバージョンを確認します"), trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val r = release
                    when {
                        updateBusy -> SecondaryButton(tr("ยกเลิก", "Cancel", "キャンセル"), onClick = { cancelUpdate = true })
                        r != null -> PrimaryButton(tr("ติดตั้งรุ่น ${r.version}", "Install version ${r.version}", "バージョン${r.version}をインストール"), onClick = {
                            updateBusy = true; cancelUpdate = false
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val msi = s.updater.download(r, { cancelUpdate }) { f -> updateText = tr("ดาวน์โหลด ${(f * 100).toInt()}%", "Downloading ${(f * 100).toInt()}%", "ダウンロード中 ${(f * 100).toInt()}%") }
                                        s.updater.install(msi)
                                    }
                                }
                                updateBusy = false
                                result.fold({ started ->
                                    if (started) { updateText = tr("กำลังติดตั้ง โปรแกรมจะปิดและเปิดใหม่เอง", "Installing; the app will close and reopen itself", "インストール中。アプリは自動で閉じて再起動します"); app.quit() }
                                    else updateText = tr("ติดตั้งอัตโนมัติได้บน Windows เท่านั้น", "Automatic install works on Windows only", "自動インストールはWindowsのみ")
                                }, { updateText = it.message })
                            }
                        }, modifier = Modifier.width(200.dp))
                        else -> SecondaryButton(tr("ตรวจหารุ่นใหม่", "Check for updates", "更新を確認"), onClick = {
                            updateBusy = true
                            updateText = tr("กำลังตรวจ…", "Checking…", "確認中…")
                            scope.launch {
                                val result = runCatching { withContext(Dispatchers.IO) { s.updater.check() } }
                                updateBusy = false
                                result.fold({ found ->
                                    release = found
                                    updateText = found?.let { tr("มีรุ่นใหม่ ${it.version}", "New version ${it.version}", "新しいバージョン ${it.version}") + it.notes.lineSequence().firstOrNull { l -> l.isNotBlank() }?.let { n -> " · $n" }.orEmpty() }
                                        ?: tr("ใช้รุ่นล่าสุดอยู่แล้ว", "You have the latest version", "最新バージョンです")
                                }, { updateText = it.message })
                            }
                        })
                    }
                }
            })
        }
        GroupFooter(tr("ดาวน์โหลดจาก GitHub ของโปรเจกต์ ตรวจค่า SHA-256 ก่อนติดตั้งทุกครั้ง · ข้อมูลการฝึกในเครื่องอยู่ครบ", "Downloads from the project's GitHub and checks the SHA-256 before every install · your practice data stays", "プロジェクトのGitHubからダウンロードし、毎回インストール前にSHA-256を確認 · 練習データはそのまま"))


        GroupHeader(tr("ข้อมูลในเครื่อง", "Local data", "端末のデータ"))
        Group {
            GroupRow(tr("โฟลเดอร์ข้อมูล", "Data folder", "データフォルダー"), s.dirs.root.absolutePath, onClick = { runCatching { java.awt.Desktop.getDesktop().open(s.dirs.root) } })
            GroupDivider()
            GroupRow(tr("ส่งออกไฟล์สำรองทั้งหมด", "Export a full backup", "すべてをバックアップ"), tr("รูปแบบเดียวกับมือถือ · นำไปกู้คืนบนมือถือได้", "Same format as the phone · can be restored on the phone", "スマホと同じ形式 · スマホで復元できます"), onClick = {
                val f = chooseFile(tr("บันทึกไฟล์สำรอง", "Save backup", "バックアップを保存"), save = true, suggested = "mimikopi-pc-${LocalDate.now()}.json") ?: return@GroupRow
                scope.launch {
                    dataReport = runCatching { withContext(Dispatchers.IO) { s.transfer.export(f) } }
                        .fold({ listOf(tr("บันทึกแล้ว $it แถว: ${f.absolutePath}", "Saved $it rows: ${f.absolutePath}", "${it}行を保存しました：${f.absolutePath}")) }, { listOf(tr("บันทึกไม่สำเร็จ: ${it.message}", "Save failed: ${it.message}", "保存に失敗：${it.message}")) })
                }
            })
            GroupDivider()
            GroupRow(tr("รวมข้อมูลจากไฟล์สำรองของมือถือ", "Merge data from a phone backup", "スマホのバックアップからデータを統合"), tr("เพิ่มประวัติรายบทและความคืบหน้า ไม่ลบของในเครื่องนี้ · นำเข้าซ้ำได้ไม่ซ้ำซ้อน", "Adds lesson history and progress without deleting anything here · importing again doesn't duplicate", "このPCのデータを消さずにレッスン履歴と進捗を追加 · 何度インポートしても重複しません"), onClick = {
                val f = chooseFile(tr("เลือกไฟล์สำรองจากมือถือ", "Choose a phone backup", "スマホのバックアップを選ぶ"), save = false) ?: return@GroupRow
                scope.launch {
                    dataReport = withContext(Dispatchers.IO) {
                        when (val c = s.transfer.read(f)) {
                            is BackupCheck.Rejected -> listOf(c.reason)
                            is BackupCheck.Ok -> runCatching { s.transfer.merge(c.backup).also { s.reloadAll() } }
                                .fold({ r -> r.lines + listOfNotNull(r.safetyCopy?.let { tr("สำเนาก่อนนำเข้า: ${it.name}", "Copy before importing: ${it.name}", "インポート前のコピー：${it.name}") }) }, { listOf(tr("นำเข้าไม่สำเร็จ ข้อมูลเดิมไม่เปลี่ยน: ${it.message}", "Import failed, existing data unchanged: ${it.message}", "インポート失敗、既存のデータは変更なし：${it.message}")) })
                        }
                    }
                }
            })
            GroupDivider()
            GroupRow(tr("แทนที่ด้วยไฟล์สำรอง (ย้ายเครื่อง)", "Replace with a backup (moving computers)", "バックアップで置き換え（PCの移行）"), tr("ลบข้อมูลในเครื่องนี้แล้วใช้ข้อมูลในไฟล์ · ทำสำเนาก่อนเสมอ", "Deletes the data on this computer and uses the file's data · a copy is always made first", "このPCのデータを消してファイルのデータを使います · 先に必ずコピーを作ります"), onClick = {
                val f = chooseFile(tr("เลือกไฟล์สำรองที่จะใช้แทน", "Choose the backup to use instead", "置き換えるバックアップを選ぶ"), save = false) ?: return@GroupRow
                when (val c = s.transfer.read(f)) {
                    is BackupCheck.Rejected -> dataReport = listOf(c.reason)
                    is BackupCheck.Ok -> { pendingRestore = c.backup; dataReport = listOf(tr("ไฟล์จาก ${c.backup.appVersionName} สร้างเมื่อ ${c.backup.createdAtUtc} · ${c.backup.data.totalRows} แถว · กดยืนยันด้านล่างเพื่อแทนที่", "File from ${c.backup.appVersionName}, made ${c.backup.createdAtUtc} · ${c.backup.data.totalRows} rows · confirm below to replace", "${c.backup.appVersionName}のファイル、作成 ${c.backup.createdAtUtc} · ${c.backup.data.totalRows}行 · 下で確認すると置き換えます")) }
                }
            })
            pendingRestore?.let { b ->
                GroupDivider()
                GroupRow(tr("ยืนยันแทนที่ข้อมูลทั้งหมด", "Confirm replacing all data", "すべてのデータの置き換えを確認"), tr("ข้อมูลปัจจุบันจะถูกเก็บเป็นสำเนาในโฟลเดอร์ backups ก่อน", "Current data is kept as a copy in the backups folder first", "今のデータは先にbackupsフォルダーにコピーされます"), onClick = {
                    pendingRestore = null
                    scope.launch {
                        dataReport = runCatching { withContext(Dispatchers.IO) { s.transfer.restore(b).also { s.reloadAll() } } }
                            .fold({ it.lines }, { listOf(tr("ไม่สำเร็จ ข้อมูลเดิมไม่เปลี่ยน: ${it.message}", "Failed, existing data unchanged: ${it.message}", "失敗、既存のデータは変更なし：${it.message}")) })
                    }
                }, titleColor = Tokens.Palette.Ink)
            }
            GroupDivider()
            GroupRow(tr("สำเนาอัตโนมัติ", "Automatic copies", "自動コピー"), tr("${s.transfer.autoBackups().size} ไฟล์ · ทำทุกวันที่เปิดโปรแกรม และก่อนนำเข้าทุกครั้ง", "${s.transfer.autoBackups().size} files · made every day the app opens and before every import", "${s.transfer.autoBackups().size}ファイル · アプリを開いた日ごとと、インポート前に毎回作成"))
        }
        dataReport.forEach { Text(it, fontSize = 14.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(horizontal = 16.dp)) }
        GroupFooter(tr("ข้อมูลอยู่ในเครื่องนี้ ไม่มีบัญชี · ความคืบหน้าซิงค์กับมือถือผ่าน repo ส่วนตัวเมื่อตั้งค่าไว้ · เพลงส่วนตัวไม่ถูกรวมอัตโนมัติ · เวอร์ชัน ${s.appVersion}", "Data stays on this computer, no account · progress syncs with the phone through your private repo when set up · private songs aren't merged automatically · version ${s.appVersion}", "データはこのPCに保存、アカウント不要 · 設定すれば個人リポジトリ経由でスマホと進捗を同期 · 個人曲は自動統合しません · バージョン ${s.appVersion}"))

        GroupHeader(tr("เกี่ยวกับและเครดิต", "About and credits", "アプリについて・クレジット"))
        Group {
            GroupRow(tr("ミミコピ (Mimikopi) สำหรับ Windows", "ミミコピ (Mimikopi) for Windows", "ミミコピ（Mimikopi）Windows版"), tr("รุ่น ${s.appVersion} · ข้อมูลอยู่ในเครื่องนี้ ไม่มีบัญชี · ซิงค์ผ่าน GitHub ของคุณเอง", "Version ${s.appVersion} · data stays on this computer, no account · syncs through your own GitHub", "バージョン ${s.appVersion} · データはこのPCに保存、アカウント不要 · 自分のGitHubで同期"))
            GroupDivider()
            GroupRow(tr("เสียงเปียโน: Salamander Grand Piano V3", "Piano sound: Salamander Grand Piano V3", "ピアノ音：Salamander Grand Piano V3"), tr("Alexander Holm · CC BY 3.0 · ตัดและแปลงไฟล์สำหรับโปรแกรมนี้", "Alexander Holm · CC BY 3.0 · trimmed and converted for this app", "Alexander Holm · CC BY 3.0 · このアプリ用にカット・変換"))
            GroupDivider()
            GroupRow(tr("ตัวโน้ต: Bravura (SMuFL)", "Notation: Bravura (SMuFL)", "記譜：Bravura（SMuFL）"), "Steinberg Media Technologies · SIL Open Font License 1.1")
            GroupDivider()
            GroupRow(tr("เสียงออกลำโพง: miniaudio", "Audio output: miniaudio", "音声出力：miniaudio"), "David Reid · Public domain / MIT No Attribution")
            GroupDivider()
            GroupRow("Kotlin, Compose Multiplatform, kotlinx, sqlite-jdbc", tr("JetBrains, Xerial และผู้ร่วมพัฒนา · Apache License 2.0", "JetBrains, Xerial and contributors · Apache License 2.0", "JetBrains、Xerialと貢献者 · Apache License 2.0"))
        }
    }
}

fun sourceName(source: String): String = when (source) {
    "USB_MIDI" -> tr("เปียโน MIDI", "MIDI piano", "MIDIピアノ")
    "BLE_MIDI" -> tr("MIDI บลูทูธ", "Bluetooth MIDI", "Bluetooth MIDI")
    "COMPUTER_KEYS" -> tr("แป้นพิมพ์คอมพิวเตอร์", "Computer keyboard", "PCキーボード")
    "TOUCH" -> tr("คลิกคีย์บนจอ", "Click on-screen keys", "画面の鍵盤をクリック")
    else -> source
}

/**
 * Sync with the phone: one button (the user asked for nothing else). The repository and branch are built in; the first
 * press asks once for the GitHub token, which can't ship inside a public app, and keeps it on this computer only.
 */
@Composable
private fun SyncButton() {
    val s = LocalApp.current.services
    val scope = rememberCoroutineScope()
    val status by s.sync.status.collectAsState()
    var asking by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(if (status.running) tr("กำลังซิงค์…", "Syncing…", "同期中…") else "Sync", enabled = !status.running, onClick = {
            if (s.sync.config().ready) scope.launch(Dispatchers.IO) { s.sync.sync() } else asking = true
        })
        status.line?.let { Text(it, fontSize = 14.sp, color = if (status.failed) Tokens.Palette.Ink else Tokens.Palette.InkMuted) }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(tr("เชื่อมต่อครั้งแรก", "First connection", "初回接続")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("ใส่ GitHub token ครั้งเดียว เก็บในเครื่องนี้เท่านั้น", "Enter a GitHub token once; it stays on this device only", "GitHubトークンを一度だけ入力。この端末にのみ保存されます"))
                OutlinedTextField(token, { token = it.trim().take(200) }, singleLine = true, label = { Text("token") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                s.sync.saveConfig(com.id.mimikopi.core.sync.GitHubSyncConfig(token = token))
                asking = false
                scope.launch(Dispatchers.IO) { s.sync.sync() }
            }, enabled = token.isNotBlank()) { Text("Sync") }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("ยกเลิก", "Cancel", "キャンセル")) } },
    )
}
