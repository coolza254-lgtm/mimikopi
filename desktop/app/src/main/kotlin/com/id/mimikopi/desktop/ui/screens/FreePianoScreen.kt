package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.desktop.session.FreePianoSession
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.theme.Tokens

/** Playing freely: the program's piano for MIDI, the computer keyboard and the mouse, with a click when wanted. */
@Composable
fun FreePianoScreen() {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val vm = remember { FreePianoSession(s, scope) }
    val ui by vm.ui.collectAsState()
    val pressed by vm.pressed.collectAsState()
    val lowC by s.keysLowC.collectAsState()
    val audio by s.piano.status.collectAsState()
    DisposableEffect(vm) {
        vm.enter()
        app.keys.onSpace = { vm.setMetronome(!vm.ui.value.metronome) }
        onDispose {
            app.keys.onSpace = null
            vm.leave()
        }
    }
    Column(Modifier.fillMaxSize().background(Tokens.Palette.Paper).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LargeTitle(tr("เปียโน", "Piano", "ピアノ"), tr("เล่นอิสระ · ได้ยินเสียงโปรแกรมทั้ง MIDI แป้นพิมพ์ และเมาส์ · เล่นวันนี้ ${ui.minutesToday} นาที", "Free play · hear the app's sound from MIDI, keyboard and mouse · played today ${ui.minutesToday} min", "自由演奏 · MIDI、キーボード、マウスでアプリの音が鳴ります · 今日の演奏 ${ui.minutesToday}分"))
        if (ui.noSound) Text("เปิดเสียงไม่ได้: ${audio.error ?: tr("ตรวจอุปกรณ์เสียงที่หน้าตั้งค่า", "Check audio devices in Settings", "設定でオーディオ機器を確認")}", color = Tokens.Palette.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("ความดัง", "Volume", "音量"), modifier = Modifier.width(80.dp))
            Slider(ui.sound.volume, { vm.setSound(ui.sound.copy(volume = it), save = false) }, onValueChangeFinished = { vm.setSound(ui.sound, save = true) }, modifier = Modifier.width(260.dp))
            Text(tr("ห้อง", "Bars", "小節"), modifier = Modifier.width(50.dp))
            Slider(ui.sound.reverb, { vm.setSound(ui.sound.copy(reverb = it), save = false) }, onValueChangeFinished = { vm.setSound(ui.sound, save = true) }, modifier = Modifier.width(200.dp))
            Text(tr("นุ่ม", "Warmth", "やわらかさ"), modifier = Modifier.width(50.dp))
            Slider(ui.sound.warmth, { vm.setSound(ui.sound.copy(warmth = it), save = false) }, onValueChangeFinished = { vm.setSound(ui.sound, save = true) }, modifier = Modifier.width(200.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("น้ำหนักกด", "Touch", "タッチ"), modifier = Modifier.width(80.dp))
            Segmented(listOf(true, false), ui.velocity.on, { if (it) tr("ตามแรงกด", "By how hard you press", "押す強さで") else tr("เท่ากันทุกโน้ต", "Same for every note", "すべて同じ") }, { vm.setVelocity(if (it) VelocityMode(on = true) else VelocityMode(on = false)) }, Modifier.width(320.dp))
            Text(tr("ย้ายคีย์", "Transpose", "移調"), modifier = Modifier.padding(start = 16.dp))
            SecondaryButton("−", { vm.transpose(-1) })
            Text(if (ui.transpose == 0) "0" else "%+d".format(ui.transpose), fontSize = 16.sp, modifier = Modifier.width(40.dp))
            SecondaryButton("+", { vm.transpose(1) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("คลิกจังหวะ", "Click", "クリック"), modifier = Modifier.width(80.dp))
            SecondaryButton(if (ui.metronome) tr("■ หยุดคลิก (Space)", "■ Stop click (Space)", "■ クリック停止（Space）") else tr("▶ เริ่มคลิก (Space)", "▶ Start click (Space)", "▶ クリック開始（Space）"), { vm.setMetronome(!ui.metronome) })
            SecondaryButton("−5", { vm.setMetronome(ui.metronome, ui.bpm - 5) })
            Text("${ui.bpm} bpm", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            SecondaryButton("+5", { vm.setMetronome(ui.metronome, ui.bpm + 5) })
            SecondaryButton(tr("เครื่องเคาะเต็มรูปแบบ ›", "Full metronome ›", "メトロノーム ›"), { vm.setMetronome(false); app.nav.open(Screen.Metronome) })
        }
        Box(Modifier.weight(1f))
        val shown = pressed.sorted().joinToString("  ") { SpelledPitch.fromMidiSharp(it).name(NoteNameStyle.BOTH, withOctave = true) }
        Text(shown.ifEmpty { " " }, fontSize = 28.sp, color = Tokens.Palette.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(tr("‹ อ็อกเทฟ", "‹ Octave", "‹ オクターブ"), { vm.shiftOctave(-1) })
            Text(tr("คีย์บนจอ C${ui.lowNote / 12 - 1}–C${ui.lowNote / 12 + 2} · แป้นพิมพ์เริ่มที่ C${lowC / 12 - 1}", "On-screen keys C${ui.lowNote / 12 - 1}–C${ui.lowNote / 12 + 2} · keyboard starts at C${lowC / 12 - 1}", "画面の鍵盤 C${ui.lowNote / 12 - 1}–C${ui.lowNote / 12 + 2} · キーボードはC${lowC / 12 - 1}から"), color = Tokens.Palette.InkMuted)
            SecondaryButton(tr("อ็อกเทฟ ›", "Octave ›", "オクターブ ›"), { vm.shiftOctave(1) })
        }
        PianoKeys(ui.lowNote..ui.lowNote + 36, pressed, { n, down -> s.hub.dispatch(keyEvent(n, down)) }, labels = NoteNameStyle.LETTER,
            computerKeysLowC = lowC, height = 200.dp, modifier = Modifier.fillMaxWidth())
    }
}
