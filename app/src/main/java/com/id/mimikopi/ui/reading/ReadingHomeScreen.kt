package com.id.mimikopi.ui.reading

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.ui.components.*
import com.id.mimikopi.ui.theme.Tokens

@Composable
fun ReadingHomeScreen(onBack: () -> Unit, onSingle: () -> Unit, onMelody: (Int) -> Unit, onMidi: () -> Unit) {
    ReadingHomeContent(midiConnected(), onBack, onSingle, onMelody, onMidi)
}

@Composable
internal fun ReadingHomeContent(connected: Boolean, onBack: () -> Unit, onSingle: () -> Unit, onMelody: (Int) -> Unit, onMidi: () -> Unit) {
    var level by rememberSaveable { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState())) {
        LargeTitle(tr("ฝึกอ่านโน้ต", "Note reading", "読譜"), tr("ฝึกบนมือถือได้ หรือใช้เปียโนผ่าน MIDI", "Practise on the phone, or use a piano over MIDI", "スマホで練習、またはMIDIでピアノを使う"), onBack = onBack)
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (connected) tr("● ต่อ MIDI แล้ว · ใช้คีย์เปียโนตอบ", "● MIDI connected · answer with the piano keys", "● MIDI接続済み · ピアノの鍵盤で答える") else tr("● ใช้มือถือ · เริ่มฝึกได้เลย ไม่ต้องต่อเปียโน", "● Phone · start now, no piano needed", "● スマホ · すぐ始められます、ピアノ不要"), color = Tokens.Palette.InkMuted, fontSize = 14.sp)
            PageCard(title = tr("1 · อ่านทีละตัว", "Single notes", "1音ずつ")) {
                Text(tr("มองโน้ตบนบรรทัด 5 เส้น แล้วตอบทีละตัว เริ่มจากห้าโน้ต เลือกกุญแจซอลหรือฟาได้", "Read notes on the five-line staff and answer one at a time. Start with five notes; treble or bass clef", "五線の音符を読んで1つずつ答える。5つの音から始め、ト音記号かヘ音記号を選べます"), color = Tokens.Palette.InkMuted)
                PrimaryButton(tr("เริ่มอ่านทีละตัว", "Start single notes", "1音ずつ始める"), onSingle, modifier = Modifier.padding(top = 16.dp))
            }
            PageCard(title = tr("2 · อ่านทำนอง", "Melodies", "メロディ")) {
                Text(tr("อ่านโน้ตต่อกันเป็นท่อน เริ่มแบบรอให้กดถูกก่อน แล้วค่อยฝึกตามจังหวะเมื่อพร้อม", "Read notes in a row as a phrase. Start in wait mode until you press the right one, then practise in time when ready", "音符を続けてフレーズとして読む。正しく押すまで待つモードから始め、慣れたらリズムに合わせて"), color = Tokens.Palette.InkMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n -> FilterChip(selected = level == n, onClick = { level = n }, label = { Text("L$n") }) }
                }
                Text(when (level) { 1 -> tr("มือขวา · ทำนองง่าย", "Right hand · easy melodies", "右手 · やさしいメロディ"); 2 -> tr("มือซ้าย · กุญแจฟา", "Left hand · bass clef", "左手 · ヘ音記号"); 5 -> tr("สองมือ", "Both hands", "両手"); else -> tr("มือขวา · ทำนองยากขึ้น", "Right hand · harder melodies", "右手 · 難しめのメロディ") }, color = Tokens.Palette.InkMuted, fontSize = 13.sp)
                PrimaryButton(tr("เริ่มอ่านทำนอง", "Start melodies", "メロディを始める"), { onMelody(level) }, modifier = Modifier.padding(top = 16.dp))
            }
            TextButton(onClick = onMidi) { Text(tr("เชื่อมต่อหรือจัดการ MIDI", "Connect or manage MIDI", "MIDIを接続・管理")) }
            Text(tr("การฝึกช่วยให้คล่องขึ้น ระดับทักษะยังวัดจากแบบทดสอบแยกตามเดิม", "Practice makes you fluent; skill levels are still measured by separate tests", "練習で慣れていきます。スキルレベルは別のテストで測ります"), fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
    }
}
