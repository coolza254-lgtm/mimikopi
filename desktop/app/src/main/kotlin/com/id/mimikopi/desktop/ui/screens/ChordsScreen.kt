package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.exercise.ChordDrills
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.exercise.ChordRun
import com.id.mimikopi.core.keyboard.KeyboardLayout
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.ChordFingering
import com.id.mimikopi.core.theory.ChordQualities
import com.id.mimikopi.core.theory.HandShape
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.StageGenerators
import com.id.mimikopi.desktop.learning.SettingKeys
import com.id.mimikopi.desktop.session.DrillSource
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.Screen
import com.id.mimikopi.desktop.ui.components.GroupDivider
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.components.fallingKeyRange
import com.id.mimikopi.desktop.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chords (as the phone's chord tab): what to practise (chords by name, by roman numeral in a key, or changes through
 * a progression against the clock), then start; the last results; and how to hold each chord with each hand. Runs
 * use the lesson chord drills; nothing here unlocks or counts toward steps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordsScreen() {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(ChordPractice.parse(s.db.get(SettingKeys.CHORDS_SETTINGS))) }
    val runs = remember { ChordRun.parse(s.db.get(SettingKeys.CHORDS_RUNS)) }
    var book by remember { mutableStateOf(false) }
    fun change(next: ChordPractice) {
        p = next
        s.db.put(SettingKeys.CHORDS_SETTINGS, ChordPractice.encode(next))
    }
    fun play(notes: List<Int>) {
        scope.launch {
            if (!withContext(Dispatchers.IO) { s.piano.acquire() }) return@launch
            try {
                notes.forEach { s.piano.noteOn(it, 80) }
                delay(1400)
            } finally {
                notes.forEach { s.piano.noteOff(it) }
                withContext(Dispatchers.IO) { s.piano.release() }
            }
        }
    }

    Page {
        LargeTitle(tr("คอร์ด", "Chords", "コード"), tr("เห็นชื่อแล้วกดได้ทันที และเปลี่ยนคอร์ดได้ทันจังหวะ", "Press as soon as you see the name, and change chords in time", "名前を見たらすぐ押し、リズムに合わせてコードを替える"), onBack = app.nav::back)
        val tabs = listOf("NAME", "NUMERAL", "CHANGE", "BOOK")
        Segmented(tabs, if (book) "BOOK" else p.mode.name, {
            when (it) { "NAME" -> tr("ชื่อคอร์ด", "Chord names", "コード名"); "NUMERAL" -> tr("ตัวเลขโรมัน", "Roman numerals", "ローマ数字"); "CHANGE" -> tr("เปลี่ยนคอร์ด", "Chord changes", "コードチェンジ"); else -> tr("วิธีจับคอร์ด", "Voicings", "ボイシング") }
        }, { t -> if (t == "BOOK") book = true else { book = false; change(p.copy(mode = ChordPractice.Mode.valueOf(t))) } }, Modifier.widthIn(max = 640.dp))
        if (book) {
            ChordBook(::play)
            return@Page
        }
        Text(
            when (p.mode) {
                ChordPractice.Mode.NAME -> tr("โปรแกรมบอกชื่อคอร์ด (เช่น Am, G7) ให้กดโน้ตของคอร์ดนั้นพร้อมกัน กลับเสียงหรืออ็อกเทฟไหนก็ได้ ถ้าผิดจะชี้ตำแหน่งให้แล้วถามซ้ำท้ายรอบ", "The app names a chord (e.g. Am, G7); press its notes together in any inversion or octave. A wrong one is shown and asked again at the end", "アプリがコード名（例 Am、G7）を出すので、その音を同時に押します。転回形やオクターブは自由。間違えると位置を示し、最後にもう一度出題")
                ChordPractice.Mode.NUMERAL -> tr("โปรแกรมบอกตัวเลขโรมันในคีย์ (เช่น vi ในคีย์ G) ให้หาว่าเป็นคอร์ดอะไรแล้วกด ช่วยให้เปลี่ยนคีย์เพลงได้คล่อง", "The app gives a Roman numeral in a key (e.g. vi in G); find which chord it is and press it. Helps you transpose songs easily", "アプリがキーの中のローマ数字（例 Gのvi）を出すので、どのコードか考えて押します。曲の移調に強くなれます")
                ChordPractice.Mode.CHANGE -> tr("กดคอร์ดตามโปรเกรสชันวนไปจนหมดเวลา นับครั้งที่กดถูก ค้างโน้ตที่เหมือนกันระหว่างคอร์ดไว้ได้ แล้วขยับเฉพาะตัวที่ต่าง", "Press the chords of a progression round and round until time runs out, counting the right ones. Keep shared notes held and move only the others", "時間切れまでコード進行をくり返し押し、正解数を数えます。共通音は押さえたまま、違う音だけ動かしましょう")
            },
            fontSize = 15.sp, lineHeight = 22.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 14.dp),
        )
        when (p.mode) {
            ChordPractice.Mode.NAME -> {
                Label(tr("ชนิดคอร์ด (เลือกได้หลายแบบ)", "Chord types (choose several)", "コードの種類（複数選択可）"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChordPractice.GROUPS.forEach { g ->
                        FilterChip(selected = g.id in p.groups, onClick = { change(p.copy(groups = p.groups.toggle(g.id).ifEmpty { listOf(g.id) })) }, label = { Text(g.title) })
                    }
                }
                Label(tr("คีย์ (รากคอร์ดมาจากโน้ตในคีย์ที่เลือก)", "Key (roots come from notes in the chosen key)", "キー（ルートは選んだキーの音から）"))
                KeyChips(p.keys, multi = true) { change(p.copy(keys = it)) }
                Label(tr("จำนวนคอร์ด", "Number of chords", "コードの数"))
                ChoiceChips(ChordPractice.ITEMS, p.items, { "$it" }) { change(p.copy(items = it)) }
            }
            ChordPractice.Mode.NUMERAL -> {
                Label(tr("ชุดคอร์ด (เลือกได้หลายชุด)", "Chord sets (choose several)", "コードセット（複数選択可）"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChordPractice.NUMERAL_SETS.forEach { g ->
                        FilterChip(selected = g.id in p.numeralSets, onClick = { change(p.copy(numeralSets = p.numeralSets.toggle(g.id).ifEmpty { listOf(g.id) })) }, label = { Text(g.title) })
                    }
                }
                Label(tr("คีย์", "Key", "キー"))
                KeyChips(p.keys, multi = true) { change(p.copy(keys = it)) }
                Label(tr("จำนวนคอร์ด", "Number of chords", "コードの数"))
                ChoiceChips(ChordPractice.ITEMS, p.items, { "$it" }) { change(p.copy(items = it)) }
            }
            ChordPractice.Mode.CHANGE -> {
                Label(tr("โปรเกรสชัน", "Progression", "コード進行"))
                val key = p.keys.firstOrNull() ?: 0
                StageGenerators.PROGRESSIONS.forEach { prog ->
                    val selected = p.progression == prog.name
                    Column(Modifier.fillMaxWidth().clickable { change(p.copy(progression = prog.name)) }.padding(vertical = 10.dp)) {
                        Text(prog.name, fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Tokens.Palette.Accent else Tokens.Palette.InkMuted)
                        val chords = ChordDrills.progressionDegrees(prog.name).joinToString("  ") { StageGenerators.degreeChord(it, key).display() }
                        Text("${prog.degrees.joinToString(" ")}  ·  $chords", fontSize = 14.sp, color = if (selected) Tokens.Palette.Ink else Tokens.Palette.Dim)
                    }
                    GroupDivider(0.dp)
                }
                Label(tr("คีย์", "Key", "キー"))
                KeyChips(listOf(key), multi = false) { change(p.copy(keys = it)) }
                Label(tr("เวลา", "Time", "時間"))
                ChoiceChips(ChordPractice.SECONDS, p.seconds, { tr("$it วินาที", "$it s", "${it}秒") }) { change(p.copy(seconds = it)) }
            }
        }
        Text(p.summary(), fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 24.dp))
        PrimaryButton(tr("เริ่มฝึก", "Start", "開始"), onClick = { app.nav.open(Screen.Drill(DrillSource.Chords(false))) }, modifier = Modifier.padding(top = 12.dp).widthIn(max = 360.dp))
        if (runs.isNotEmpty()) {
            Label(tr("ผลล่าสุด", "Latest result", "最新の結果"))
            runs.take(10).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(r.day.drop(5), fontSize = 14.sp, color = Tokens.Palette.Dim, modifier = Modifier.width(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.result, fontSize = 15.sp, color = Tokens.Palette.Ink)
                        Text(r.summary, fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                    }
                }
                GroupDivider(0.dp)
            }
        }
    }
}

/**
 * How to hold a chord: pick the root and the kind, then see it on the keyboard with the finger numbers, for both
 * hands together, the right hand (root position and inversions) and the left hand; each picture can be heard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordBook(onPlay: (List<Int>) -> Unit) {
    var root by remember { mutableStateOf(ChordFingering.ROOTS.first()) }
    var quality by remember { mutableStateOf("") }
    val chord = Chord(root, quality)
    Label(tr("รากคอร์ด", "Root", "ルート"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordFingering.ROOTS.forEach { r -> FilterChip(selected = r == root, onClick = { root = r }, label = { Text(r.display()) }) }
    }
    Label(tr("ชนิดคอร์ด", "Chord type", "コードの種類"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordFingering.QUALITIES.forEach { q -> FilterChip(selected = q == quality, onClick = { quality = q }, label = { Text(Chord(root, q).display()) }) }
    }
    Column(Modifier.padding(top = 24.dp)) {
        Text(chord.display(), fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = Tokens.Palette.Ink)
        Text((ChordQualities.NAMES[quality] ?: quality) + "  ·  " + ChordFingering.spelled(chord).joinToString("  ") { "${it.display()} (${it.letter.solfege}${it.display().drop(1)})" },
            fontSize = 15.sp, color = Tokens.Palette.InkMuted)
        Text(tr("ตัวเลขบนคีย์คือนิ้ว: 1 นิ้วโป้ง 2 ชี้ 3 กลาง 4 นาง 5 ก้อย", "Numbers on keys are fingers: 1 thumb 2 index 3 middle 4 ring 5 little", "鍵盤の数字は指番号：1親指 2人差し指 3中指 4薬指 5小指"), fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
    }
    val (left, right) = ChordFingering.twoHands(chord)
    Section(tr("สองมือ (แบบที่ใช้บ่อยที่สุด)", "Both hands (the most common way)", "両手（最もよく使う形）"), tr("มือซ้ายกดรากคอร์ดด้วยนิ้วก้อย มือขวากดคอร์ด", "Left hand plays the root with the little finger, right hand plays the chord", "左手は小指でルート、右手でコード"))
    ShapeRow(HandShape(left.notes + right.notes, left.fingers + right.fingers, left.title + " · " + right.title), splitAt = left.notes.size, onPlay = onPlay)
    Section(tr("มือขวา", "Right hand", "右手"), tr("เริ่มจากตำแหน่งพื้นฐาน พอคล่องแล้วฝึกพลิกคอร์ด จะเปลี่ยนคอร์ดได้โดยไม่ต้องย้ายมือไกล", "Start in root position; once fluent, practise inversions so you can change chords without moving far", "基本形から始め、慣れたら転回形を練習。手を大きく動かさずにコードを替えられます"))
    ChordFingering.rightHand(chord).forEach { ShapeRow(it, onPlay = onPlay) }
    Section(tr("มือซ้าย", "Left hand", "左手"), tr("นิ้วก้อยอยู่ล่างสุด นิ้วโป้งอยู่บนสุด", "Little finger at the bottom, thumb at the top", "小指が一番下、親指が一番上"))
    ChordFingering.leftHand(chord).forEach { ShapeRow(it, onPlay = onPlay) }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

@Composable
private fun Section(title: String, body: String) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 28.dp))
    Text(body, fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun ShapeRow(shape: HandShape, splitAt: Int = 0, onPlay: (List<Int>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row {
            Text(shape.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f).padding(top = 10.dp))
            SecondaryButton(tr("ฟัง", "Listen", "聞く"), onClick = { onPlay(shape.notes) })
        }
        ChordKeys(shape, splitAt, Modifier.padding(top = 8.dp).widthIn(max = 640.dp).fillMaxWidth().height(120.dp))
    }
    GroupDivider(0.dp)
}

/** The chord's keys marked with a disc holding the finger number; left-hand fingers get a ring. */
@Composable
private fun ChordKeys(shape: HandShape, splitAt: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val range = remember(shape.notes) { fallingKeyRange(shape.notes.min(), shape.notes.max(), minOctaves = 1) }
    val whites = remember(range) { KeyboardLayout(range.first, range.last, 1f, 1f).whiteKeys.size }
    val whiteKey = if (Tokens.dark) Color(0xFFE6E6E6) else Color(0xFFFFFFFF)
    val accent = Tokens.Palette.Accent
    Canvas(modifier) {
        val layout = KeyboardLayout(range.first, range.last, size.width / whites, size.height)
        val finger = shape.notes.zip(shape.fingers).toMap()
        val leftNotes = shape.notes.take(splitAt).toSet()
        fun disc(center: Offset, radius: Float, n: Int, left: Boolean) {
            drawCircle(accent, radius, center)
            if (left) drawCircle(Color.White, radius * 0.82f, center, style = Stroke(radius * 0.12f))
            val t = measurer.measure("$n", TextStyle(fontSize = (radius * 1.1f).toSp(), fontWeight = FontWeight.SemiBold, color = Color.White))
            drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - t.size.height / 2f))
        }
        for (k in layout.whiteKeys) {
            drawRoundRect(whiteKey, Offset(k.left + 1f, 0f), Size(k.width - 2f, k.bottom), CornerRadius(k.width * 0.12f))
            drawRoundRect(Color(0xFF9E9E9E), Offset(k.left + 1f, 0f), Size(k.width - 2f, k.bottom), CornerRadius(k.width * 0.12f), style = Stroke(1.5f))
            finger[k.note]?.let { disc(Offset(k.left + k.width / 2, k.bottom - k.width * 0.55f), (k.width * 0.36f).coerceAtMost(15.dp.toPx()), it, k.note in leftNotes) }
        }
        for (k in layout.blackKeys) {
            drawRoundRect(Color(0xFF151515), Offset(k.left, 0f), Size(k.width, k.bottom), CornerRadius(k.width * 0.15f))
            finger[k.note]?.let { disc(Offset(k.left + k.width / 2, k.bottom - k.width * 0.75f), (k.width * 0.42f).coerceAtMost(13.dp.toPx()), it, k.note in leftNotes) }
        }
    }
}

/** Keys as chips: several at once, or one ([multi] off). At least one stays chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyChips(selected: List<Int>, multi: Boolean, onChange: (List<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordPractice.KEYS.forEach { k ->
            FilterChip(selected = k in selected, onClick = { onChange(if (multi) selected.toggle(k).ifEmpty { listOf(k) } else listOf(k)) }, label = { Text(Keys.name(k)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onChange: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onChange(o) }, label = { Text(label(o)) }) }
    }
}

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item
