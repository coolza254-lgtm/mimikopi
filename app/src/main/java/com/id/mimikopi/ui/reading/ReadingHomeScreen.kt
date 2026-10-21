package com.id.mimikopi.ui.reading

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
        LargeTitle("ฝึกอ่านโน้ต", "ฝึกบนมือถือได้ หรือใช้เปียโนผ่าน MIDI", onBack = onBack)
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (connected) "● ต่อ MIDI แล้ว · ใช้คีย์เปียโนตอบ" else "● ใช้มือถือ · เริ่มฝึกได้เลย ไม่ต้องต่อเปียโน", color = Tokens.Palette.InkMuted, fontSize = 14.sp)
            PageCard(title = "1 · อ่านทีละตัว") {
                Text("มองโน้ตบนบรรทัด 5 เส้น แล้วตอบทีละตัว เริ่มจากห้าโน้ต เลือกกุญแจซอลหรือฟาได้", color = Tokens.Palette.InkMuted)
                PrimaryButton("เริ่มอ่านทีละตัว", onSingle, modifier = Modifier.padding(top = 16.dp))
            }
            PageCard(title = "2 · อ่านทำนอง") {
                Text("อ่านโน้ตต่อกันเป็นท่อน เริ่มแบบรอให้กดถูกก่อน แล้วค่อยฝึกตามจังหวะเมื่อพร้อม", color = Tokens.Palette.InkMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n -> FilterChip(selected = level == n, onClick = { level = n }, label = { Text("L$n") }) }
                }
                Text(when (level) { 1 -> "มือขวา · ทำนองง่าย"; 2 -> "มือซ้าย · กุญแจฟา"; 5 -> "สองมือ"; else -> "มือขวา · ทำนองยากขึ้น" }, color = Tokens.Palette.InkMuted, fontSize = 13.sp)
                PrimaryButton("เริ่มอ่านทำนอง", { onMelody(level) }, modifier = Modifier.padding(top = 16.dp))
            }
            TextButton(onClick = onMidi) { Text("เชื่อมต่อหรือจัดการ MIDI") }
            Text("การฝึกช่วยให้คล่องขึ้น ระดับทักษะยังวัดจากแบบทดสอบแยกตามเดิม", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
        }
    }
}
