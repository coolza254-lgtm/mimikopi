package com.id.mimikopi.ui.reading

import com.id.mimikopi.core.i18n.tr
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
        Text(if (connected) tr("● ต่อ MIDI · เล่นจากเปียโน", "● MIDI connected · play from the piano", "● MIDI接続中 · ピアノで弾く") else tr("● มือถือ · มีเปียโนบนจอให้เล่น", "● Phone · an on-screen piano to play", "● スマホ · 画面のピアノで弾けます"), color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        Text(tr("อ่านทำนองทีละช่วง", "Read a melody section by section", "メロディを区間ごとに読む"), color = Tokens.Palette.Ink, fontSize = 24.sp)
        Text(tr("มองโน้ตที่เส้นชี้ แล้วกดเสียงให้ตรง เริ่มช้า ๆ ได้ ไม่ต้องต่อเปียโน", "Look at the note at the line and play the right pitch. Start slowly; no piano needed", "線のところの音符を見て正しい音を押す。ゆっくり始められます、ピアノ不要"), color = Tokens.Palette.InkMuted, fontSize = 14.sp)
        if (canSetPace) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.options.mode == PlayMode.WAIT, onClick = { onMode(PlayMode.WAIT) }, label = { Text(tr("รอให้กดถูก", "Wait for me", "待ちモード")) }, modifier = Modifier.testTag("reading-mode-wait"))
                FilterChip(selected = ui.options.mode == PlayMode.TEMPO, onClick = { onMode(PlayMode.TEMPO) }, label = { Text(tr("ตามจังหวะ", "In time", "リズムに合わせて")) }, modifier = Modifier.testTag("reading-mode-tempo"))
            }
            Text(if (ui.options.mode == PlayMode.WAIT) tr("โน้ตจะรอคุณ กดผิดแล้วลองใหม่ได้ · รอบนี้ไม่ตัดสินจังหวะ", "The notes wait for you; try again after a wrong one · timing isn't judged this round", "音符はあなたを待ちます。間違えても再挑戦できます · この回はリズムを判定しません") else tr("โน้ตเดินตามเวลา · นับเข้า $countInClicks จังหวะก่อนเริ่ม", "Notes move in time · $countInClicks count-in beats first", "音符が時間どおりに進みます · 始まる前に${countInClicks}拍のカウント"), color = Tokens.Palette.InkMuted, fontSize = 13.sp)
            if (ui.options.mode == PlayMode.TEMPO) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onBpm(-5) }, modifier = Modifier.testTag("reading-slower")) { Text("−5") }
                Text("${ui.bpm} bpm", color = Tokens.Palette.Ink, modifier = Modifier.padding(horizontal = 16.dp))
                OutlinedButton(onClick = { onBpm(5) }, modifier = Modifier.testTag("reading-faster")) { Text("+5") }
            }
        } else Text("${if (ui.options.mode == PlayMode.WAIT) tr("รอให้กดถูก", "Wait for me", "待ちモード") else tr("ตามจังหวะ", "In time", "リズムに合わせて")} · ${ui.bpm} bpm · ใช้เกณฑ์ของแบบฝึกนี้", color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        if (canSetHands) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HandChoice.RIGHT to tr("มือขวา", "Right hand", "右手"), HandChoice.LEFT to tr("มือซ้าย", "Left hand", "左手"), HandChoice.BOTH to tr("สองมือ", "Both hands", "両手")).forEach { (h, name) ->
                FilterChip(selected = ui.options.hands == h, onClick = { onHands(h) }, label = { Text(name) })
            }
        }
        rhythmHint?.let { Text(it, color = Tokens.Palette.InkMuted, fontSize = 13.sp) }
        ui.message?.let { Text(it, color = Tokens.Palette.Ink, fontSize = 14.sp) }
        ui.previewLeft?.let { Text(tr("ดูโน้ตก่อนเริ่ม · เหลือ $it วินาที", "Look before starting · $it s left", "始める前に見る · 残り${it}秒"), color = Tokens.Palette.Ink) }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("reading-score-start")) {
            Text(if (ui.options.mode == PlayMode.WAIT) tr("เริ่มอ่าน · รอให้กดถูก", "Start · wait mode", "開始 · 待ちモード") else tr("เริ่มอ่าน · ${ui.bpm} bpm", "Start · ${ui.bpm} bpm", "開始 · ${ui.bpm} bpm"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canListen) OutlinedButton(onClick = onListen, modifier = Modifier.weight(1f)) { Text(tr("ฟังตัวอย่าง", "Listen to an example", "お手本を聞く")) }
            OutlinedButton(onClick = onOptions, modifier = Modifier.weight(1f)) { Text(tr("ตั้งค่าเพิ่มเติม", "More settings", "その他の設定")) }
        }
        Text(tr("โน้ตที่จะฝึก", "Notes to practise", "練習する音符"), color = Tokens.Palette.InkMuted, fontSize = 13.sp)
        score(Modifier.fillMaxWidth().height(240.dp).testTag("reading-score-preview"))
        if (canSetHands) {
            Text(tr("แตะบาร์เพื่อเลือกช่วงฝึก · ดูตัวเลือกทั้งเพลงในการตั้งค่าเพิ่มเติม", "Tap bars to choose a section · whole-piece options are in More settings", "小節をタップして区間を選ぶ · 全曲の選択肢はその他の設定に"), color = Tokens.Palette.InkMuted, fontSize = 12.sp)
            TextButton(onClick = onFalling) { Text(tr("เปลี่ยนไปโหมดโน้ตไหล", "Switch to scrolling notes", "流れる音符モードへ")) }
        }
        if (ui.packMissing) Text(tr("ยังไม่มีชุดเสียงเปียโน ใช้เสียงพื้นฐานฝึกได้ และติดตั้งชุดเสียงได้ที่หน้าเปียโน", "No piano sound pack yet; practise with the basic sound and install a pack on the Piano page", "ピアノ音源パックがまだありません。基本の音で練習でき、ピアノページで音源をインストールできます"), color = Tokens.Palette.InkMuted, fontSize = 12.sp)
        TextButton(onClick = onMidi) { Text(if (connected) tr("จัดการการเชื่อมต่อ MIDI", "Manage the MIDI connection", "MIDI接続を管理") else tr("ต่อเปียโนผ่าน MIDI", "Connect a piano over MIDI", "MIDIでピアノを接続")) }
    }
}
