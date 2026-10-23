package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.editor.Recording
import com.id.mimikopi.core.i18n.tr
import com.id.mimikopi.core.score.Hand
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.desktop.session.ReaderArgs
import com.id.mimikopi.desktop.session.RecordSession
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.Chip
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.GroupRow
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PageCard
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.theme.Tokens

/** Bought sheets entered from the piano (learning-system.md 2.12, step 3): the list and a new one. */
@Composable
fun ScoresScreen() {
    val app = LocalApp.current
    val scores by app.services.privateScores.scores.collectAsState()
    var title by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(0) }
    var time by remember { mutableStateOf(listOf(4, 4)) }
    var tempo by remember { mutableStateOf("80") }
    Page {
        LargeTitle(tr("โน้ตที่ซื้อ", "Bought sheets", "購入した楽譜"),
            tr("อัดจากเปียโนทีละช่วงทีละมือ เก็บในเครื่องนี้เท่านั้น ไม่ขึ้น GitHub", "Play them in from the piano, a few bars and one hand at a time · kept on this computer only, never on GitHub", "ピアノで数小節ずつ片手ずつ入力 · このPCにのみ保存、GitHubには上げません"), onBack = app.nav::back)
        if (scores.isNotEmpty()) Group {
            scores.forEachIndexed { i, f ->
                if (i > 0) GroupDivider()
                GroupRow(f.title, "${Keys.name(f.key)} · ${f.time[0]}/${f.time[1]} · ♩ = ${f.tempo} · " + tr("${f.rh.size} ห้อง", "${f.rh.size} bars", "${f.rh.size}小節"),
                    onClick = { app.nav.open(Screen.Record(f.id)) },
                    trailing = { TextButton(onClick = { app.nav.open(Screen.Reader(ReaderArgs(pieceId = f.id))) }) { Text(tr("ฝึก", "Practise", "練習")) } })
            }
        }
        PageCard(title = tr("เพิ่มโน้ตเพลงใหม่", "Add a new sheet", "新しい楽譜を追加")) {
            OutlinedTextField(title, { title = it }, label = { Text(tr("ชื่อเพลง", "Song name", "曲名")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(tr("คีย์ตามโน้ต", "Key on the sheet", "楽譜のキー"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Segmented((-5..5).toList(), key, { Keys.name(it) }, { key = it })
            Text(tr("อัตราจังหวะ", "Time signature", "拍子"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Segmented(listOf(listOf(4, 4), listOf(3, 4), listOf(2, 4), listOf(6, 8)), time, { "${it[0]}/${it[1]}" }, { time = it })
            OutlinedTextField(tempo, { tempo = it.filter(Char::isDigit).take(3) }, label = { Text(tr("ความเร็วเพลง (♩)", "Song tempo (♩)", "曲のテンポ（♩）")) }, singleLine = true, modifier = Modifier.padding(top = 12.dp))
            PrimaryButton(tr("สร้างแล้วเริ่มอัด", "Create and start recording", "作成して入力開始"), onClick = {
                val id = app.services.privateScores.create(title, key, time, (tempo.toIntOrNull() ?: 80).coerceIn(30, 240), 8)
                title = ""
                app.nav.open(Screen.Record(id))
            }, modifier = Modifier.padding(top = 16.dp), enabled = title.isNotBlank())
        }
    }
}

/** Recording one private score: a few bars of one hand along with the metronome, then fix and keep. */
@Composable
fun RecordScreen(id: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val vm = remember(id) { RecordSession(app.services, scope, id) }
    val u by vm.ui.collectAsState()
    DisposableEffect(vm) {
        vm.enter()
        app.keys.onSpace = { if (u.countIn != null || u.bar != null) vm.stop() else vm.record() }
        app.keys.onEscape = { if (u.countIn != null || u.bar != null) { vm.stop(); true } else false }
        onDispose { app.keys.onSpace = null; app.keys.onEscape = null; vm.leave() }
    }
    val t = u.timeline
    Page {
        LargeTitle(t?.meta?.title ?: "", tr("เลือกมือและห้อง เล่นตามเมโทรนอมหลังนับเข้าหนึ่งห้อง แล้วแก้โน้ตที่ผิดก่อนเก็บ", "Choose the hand and bars, play with the metronome after a one-bar count-in, then fix wrong notes before keeping", "手と小節を選び、1小節のカウントのあとメトロノームに合わせて弾き、間違いを直してから保存"), onBack = app.nav::back)
        if (t == null) return@Page
        PageCard {
            Segmented(listOf(Hand.RIGHT, Hand.LEFT), u.hand, { if (it == Hand.RIGHT) tr("มือขวา", "Right hand", "右手") else tr("มือซ้าย", "Left hand", "左手") }, vm::setHand)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("ห้อง ${u.from + 1}–${u.to + 1}", "Bars ${u.from + 1}–${u.to + 1}", "${u.from + 1}–${u.to + 1}小節"), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SecondaryButton("‹", { vm.setRange(u.from - 1, u.to - 1) })
                SecondaryButton("›", { vm.setRange(u.from + 1, u.to + 1) })
                SecondaryButton("−", { vm.setRange(u.from, u.to - 1) })
                SecondaryButton("+", { vm.setRange(u.from, u.to + 1) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("♩ = ${u.bpm}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                listOf(-10, -2, 2, 10).forEach { d -> SecondaryButton(if (d > 0) "+$d" else "$d", { vm.setBpm(u.bpm + d) }) }
            }
            Text(tr("โน้ตที่สั้นที่สุด", "Shortest note", "最も短い音符"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Segmented(Recording.GRIDS, u.grid, { when (it) { 4 -> tr("ตัวดำ ♩", "Crotchet ♩", "四分 ♩"); 2 -> tr("เขบ็ต 1 ชั้น ♪", "Quaver ♪", "8分 ♪"); else -> tr("เขบ็ต 2 ชั้น ♬", "Semiquaver ♬", "16分 ♬") } }, vm::setGrid)
            when {
                u.countIn != null -> Text(tr("นับเข้า ${u.countIn}", "Count-in ${u.countIn}", "カウント ${u.countIn}"), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                u.bar != null -> Text(tr("กำลังอัด · ห้อง ${u.bar + 1}", "Recording · bar ${u.bar + 1}", "入力中 · ${u.bar + 1}小節"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Accent, modifier = Modifier.padding(top = 16.dp))
            }
            if (u.countIn != null || u.bar != null) SecondaryButton(tr("หยุด (Esc)", "Stop (Esc)", "停止（Esc）"), vm::stop, Modifier.padding(top = 12.dp))
            else if (u.take == null) PrimaryButton(tr("อัด (Space)", "Record (Space)", "入力（Space）"), vm::record, Modifier.padding(top = 16.dp))
            u.message?.let { Text(it, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
        }
        u.take?.let { take ->
            PageCard(title = tr("ตรวจโน้ตที่อัด", "Check the take", "入力した音を確認")) {
                Text(tr("แตะโน้ตที่ผิด แล้วกดคีย์ที่ถูกบนเปียโน", "Tap a wrong note, then press the right key on the piano", "間違った音をタップして、ピアノで正しい鍵盤を押す"), color = Tokens.Palette.InkMuted)
                val per = t.measureLength
                take.groupBy { it.start / per }.toSortedMap().forEach { (m, notes) ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("ห้อง ${u.from + m + 1}", "Bar ${u.from + m + 1}", "${u.from + m + 1}小節"), color = Tokens.Palette.InkMuted, modifier = Modifier.padding(end = 6.dp))
                        notes.forEach { n ->
                            Chip(n.pitches.joinToString("+") { it.toString().replace("b", "♭").replace("#", "♯") }, selected = u.fixing == n.start) { vm.fix(if (u.fixing == n.start) null else n.start) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(tr("เก็บแล้วไปห้องถัดไป", "Keep and go to the next bars", "保存して次の小節へ"), vm::keepTake, Modifier.weight(1f))
                    SecondaryButton(tr("อัดใหม่", "Record again", "やり直す"), vm::discardTake)
                    u.fixing?.let { f -> SecondaryButton(tr("ลบโน้ตนี้", "Delete this note", "この音を削除"), { vm.deleteTakeNote(f); vm.fix(null) }) }
                }
            }
        }
        PageCard {
            Text(tr("${t.measures} ห้อง · มือขวา ${t.rh.size} โน้ต · มือซ้าย ${t.lh.size} โน้ต", "${t.measures} bars · right hand ${t.rh.size} notes · left hand ${t.lh.size} notes", "${t.measures}小節 · 右手${t.rh.size}音 · 左手${t.lh.size}音"))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(tr("เพิ่ม 4 ห้อง", "Add 4 bars", "4小節追加"), { vm.addBars(4) })
                SecondaryButton(tr("ฝึกเพลงนี้", "Practise this song", "この曲を練習"), { app.nav.open(Screen.Reader(ReaderArgs(pieceId = id))) })
            }
        }
    }
}
