package com.id.mimikopi.desktop.ui.screens

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
        LargeTitle("เปียโน", "เล่นอิสระ · ได้ยินเสียงโปรแกรมทั้ง MIDI แป้นพิมพ์ และเมาส์ · เล่นวันนี้ ${ui.minutesToday} นาที")
        if (ui.noSound) Text("เปิดเสียงไม่ได้: ${audio.error ?: "ตรวจอุปกรณ์เสียงที่หน้าตั้งค่า"}", color = Tokens.Palette.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ความดัง", modifier = Modifier.width(80.dp))
            Slider(ui.sound.volume, { vm.setSound(ui.sound.copy(volume = it), save = false) }, onValueChangeFinished = { vm.setSound(ui.sound, save = true) }, modifier = Modifier.width(260.dp))
            Text("ห้อง", modifier = Modifier.width(50.dp))
            Slider(ui.sound.reverb, { vm.setSound(ui.sound.copy(reverb = it), save = false) }, onValueChangeFinished = { vm.setSound(ui.sound, save = true) }, modifier = Modifier.width(200.dp))
            Text("นุ่ม", modifier = Modifier.width(50.dp))
            Slider(ui.sound.warmth, { vm.setSound(ui.sound.copy(warmth = it), save = false) }, onValueChangeFinished = { vm.setSound(ui.sound, save = true) }, modifier = Modifier.width(200.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("น้ำหนักกด", modifier = Modifier.width(80.dp))
            Segmented(listOf(true, false), ui.velocity.on, { if (it) "ตามแรงกด" else "เท่ากันทุกโน้ต" }, { vm.setVelocity(if (it) VelocityMode(on = true) else VelocityMode(on = false)) }, Modifier.width(320.dp))
            Text("ย้ายคีย์", modifier = Modifier.padding(start = 16.dp))
            SecondaryButton("−", { vm.transpose(-1) })
            Text(if (ui.transpose == 0) "0" else "%+d".format(ui.transpose), fontSize = 16.sp, modifier = Modifier.width(40.dp))
            SecondaryButton("+", { vm.transpose(1) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("คลิกจังหวะ", modifier = Modifier.width(80.dp))
            SecondaryButton(if (ui.metronome) "■ หยุดคลิก (Space)" else "▶ เริ่มคลิก (Space)", { vm.setMetronome(!ui.metronome) })
            SecondaryButton("−5", { vm.setMetronome(ui.metronome, ui.bpm - 5) })
            Text("${ui.bpm} bpm", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            SecondaryButton("+5", { vm.setMetronome(ui.metronome, ui.bpm + 5) })
        }
        Box(Modifier.weight(1f))
        val shown = pressed.sorted().joinToString("  ") { SpelledPitch.fromMidiSharp(it).name(NoteNameStyle.BOTH, withOctave = true) }
        Text(shown.ifEmpty { " " }, fontSize = 28.sp, color = Tokens.Palette.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("‹ อ็อกเทฟ", { vm.shiftOctave(-1) })
            Text("คีย์บนจอ C${ui.lowNote / 12 - 1}–C${ui.lowNote / 12 + 2} · แป้นพิมพ์เริ่มที่ C${lowC / 12 - 1}", color = Tokens.Palette.InkMuted)
            SecondaryButton("อ็อกเทฟ ›", { vm.shiftOctave(1) })
        }
        PianoKeys(ui.lowNote..ui.lowNote + 36, pressed, { n, down -> s.hub.dispatch(keyEvent(n, down)) }, labels = NoteNameStyle.LETTER,
            computerKeysLowC = lowC, height = 200.dp, modifier = Modifier.fillMaxWidth())
    }
}
