package com.id.mimikopi.ui.reading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.playalong.PlayMode
import com.id.mimikopi.ui.reader.HandChoice
import com.id.mimikopi.ui.reader.ReaderUi
import com.id.mimikopi.ui.theme.Tokens

/** Setup is a readable page; the player keeps only music, input and Stop once a run begins. */
@Composable
internal fun ReadingScoreSetup(
    ui: ReaderUi, connected: Boolean, canSetPace: Boolean, canSetHands: Boolean,
    canListen: Boolean, onMode: (PlayMode) -> Unit, onHands: (HandChoice) -> Unit,
    onBpm: (Int) -> Unit, onStart: () -> Unit, onListen: () -> Unit, onOptions: () -> Unit,
    onMidi: () -> Unit, onFalling: () -> Unit, countInClicks: Int = 4, rhythmHint: String? = null, score: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag("reading-score-setup"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (connected) "● ต่อ MIDI · เล่นจากเปียโน" else "● มือถือ · มีเปียโนบนจอให้เล่น", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        Text("อ่านทำนองทีละช่วง", color = Tokens.Palette.Ink, fontSize = 24.sp)
        Text("มองโน้ตที่เส้นชี้ แล้วกดเสียงให้ตรง เริ่มช้า ๆ ได้ ไม่ต้องต่อเปียโน", color = Tokens.Palette.InkMuted, fontSize = 14.sp)
        if (canSetPace) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.options.mode == PlayMode.WAIT, onClick = { onMode(PlayMode.WAIT) }, label = { Text("รอให้กดถูก") }, modifier = Modifier.testTag("reading-mode-wait"))
                FilterChip(selected = ui.options.mode == PlayMode.TEMPO, onClick = { onMode(PlayMode.TEMPO) }, label = { Text("ตามจังหวะ") }, modifier = Modifier.testTag("reading-mode-tempo"))
            }
            Text(if (ui.options.mode == PlayMode.WAIT) "โน้ตจะรอคุณ กดผิดแล้วลองใหม่ได้ · รอบนี้ไม่ตัดสินจังหวะ" else "โน้ตเดินตามเวลา · นับเข้า $countInClicks จังหวะก่อนเริ่ม", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
            if (ui.options.mode == PlayMode.TEMPO) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onBpm(-5) }, modifier = Modifier.testTag("reading-slower")) { Text("−5") }
                Text("${ui.bpm} bpm", color = Tokens.Palette.Ink, modifier = Modifier.padding(horizontal = 16.dp))
                OutlinedButton(onClick = { onBpm(5) }, modifier = Modifier.testTag("reading-faster")) { Text("+5") }
            }
        } else Text("${if (ui.options.mode == PlayMode.WAIT) "รอให้กดถูก" else "ตามจังหวะ"} · ${ui.bpm} bpm · ใช้เกณฑ์ของแบบฝึกนี้", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        if (canSetHands) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HandChoice.RIGHT to "มือขวา", HandChoice.LEFT to "มือซ้าย", HandChoice.BOTH to "สองมือ").forEach { (h, name) ->
                FilterChip(selected = ui.options.hands == h, onClick = { onHands(h) }, label = { Text(name) })
            }
        }
        rhythmHint?.let { Text(it, color = Tokens.Palette.InkMuted, fontSize = 13.sp) }
        ui.message?.let { Text(it, color = Tokens.Palette.Ink, fontSize = 14.sp) }
        ui.previewLeft?.let { Text("ดูโน้ตก่อนเริ่ม · เหลือ $it วินาที", color = Tokens.Palette.Ink) }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("reading-score-start")) {
            Text(if (ui.options.mode == PlayMode.WAIT) "เริ่มอ่าน · รอให้กดถูก" else "เริ่มอ่าน · ${ui.bpm} bpm")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canListen) OutlinedButton(onClick = onListen, modifier = Modifier.weight(1f)) { Text("ฟังตัวอย่าง") }
            OutlinedButton(onClick = onOptions, modifier = Modifier.weight(1f)) { Text("ตั้งค่าเพิ่มเติม") }
        }
        Text("โน้ตที่จะฝึก", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        score(Modifier.fillMaxWidth().height(240.dp).testTag("reading-score-preview"))
        if (canSetHands) {
            Text("แตะบาร์เพื่อเลือกช่วงฝึก · ดูตัวเลือกทั้งเพลงในการตั้งค่าเพิ่มเติม", color = Tokens.Palette.InkMuted, fontSize = 12.sp)
            TextButton(onClick = onFalling) { Text("เปลี่ยนไปโหมดโน้ตไหล") }
        }
        if (ui.packMissing) Text("ยังไม่มีชุดเสียงเปียโน ใช้เสียงพื้นฐานฝึกได้ และติดตั้งชุดเสียงได้ที่หน้าเปียโน", color = Tokens.Palette.InkMuted, fontSize = 12.sp)
        TextButton(onClick = onMidi) { Text(if (connected) "จัดการการเชื่อมต่อ MIDI" else "ต่อเปียโนผ่าน MIDI") }
    }
}
