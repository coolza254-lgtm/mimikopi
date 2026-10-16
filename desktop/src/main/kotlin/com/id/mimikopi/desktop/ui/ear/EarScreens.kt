package com.id.mimikopi.desktop.ui.ear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.ear.EarQuestion
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.lesson.LessonStatus
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.desktop.Services
import com.id.mimikopi.desktop.ui.BackHeader
import com.id.mimikopi.desktop.ui.BigCircle
import com.id.mimikopi.desktop.ui.BigNumber
import com.id.mimikopi.desktop.ui.DrillProgress
import com.id.mimikopi.desktop.ui.Hairline
import com.id.mimikopi.desktop.ui.MeterBar
import com.id.mimikopi.desktop.ui.NoKeyboardBanner
import com.id.mimikopi.desktop.ui.SmallCaps
import com.id.mimikopi.desktop.ui.Tokens
import java.time.LocalDate

private enum class Kind(val label: String) { CONFIRM("ยืนยันขั้นที่ผ่านเมื่อวาน"), REPEAT("ฝึกซ้ำขั้นเก่า"), STEP("ขั้นใหม่") }
private data class Item(val kind: Kind, val lesson: Lesson, val done: Boolean, val notYet: Boolean)

/**
 * The ear tab: the next rung's id in large mono with one button, today's numbered list (confirmations, one older
 * rung repeated, the new rung) and the ladder, as in the Android app.
 */
@Composable
fun EarHome(onOpen: (Lesson) -> Unit, onOpenMidi: () -> Unit) {
    val learning = Services.learning
    remember { learning.reopenFailedConfirmations() }
    val records by learning.records.collectAsState()
    var showLadder by remember { mutableStateOf(false) }
    val t = LocalDate.now().toString()
    val rungs = learning.rungs

    val items = buildList {
        for (l in rungs) {
            val r = records[l.id] ?: continue
            val due = r.passDays.size in 1 until l.pass.days && r.passDays.all { it < t }
            val confirmedToday = r.passDays.size >= 2 && t in r.passDays && r.passDays.any { it < t }
            if (due || confirmedToday) add(Item(Kind.CONFIRM, l, confirmedToday, due && t in r.triedDays))
        }
        val older = rungs.filter { l -> (records[l.id]?.passDays?.size ?: 0) >= 2 && none { it.lesson.id == l.id } }
        if (older.size >= 3) older[Math.floorMod(LocalDate.now().toEpochDay().toInt(), older.size)].let { add(Item(Kind.REPEAT, it, t in (records[it.id]?.triedDays ?: emptySet()), false)) }
        rungs.filter { records[it.id]?.passDays == setOf(t) }.forEach { add(Item(Kind.STEP, it, true, false)) }
        ProgressRules.nextToLearn(rungs, records)?.let { add(Item(Kind.STEP, it, false, t in (records[it.id]?.triedDays ?: emptySet()))) }
    }
    val next = items.firstOrNull { !it.done }
    val passed = rungs.count { ProgressRules.isPassed(records[it.id], it) }

    Column {
        Text("ฝึกฟัง", style = MaterialTheme.typography.headlineMedium)
        Text("เป้าหมาย: ฟังแล้วแกะเพลงเองได้", style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted)
        NoKeyboardBanner(onOpenMidi, Modifier.padding(top = 18.dp))
        Spacer(Modifier.height(40.dp))
        if (next == null) {
            SmallCaps("วันนี้")
            Text(if (ProgressRules.nextToLearn(rungs, records) == null) "ครบทุกขั้นแล้ว" else "วันนี้ครบแล้ว", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text("พรุ่งนี้กลับมายืนยันขั้นที่ผ่านวันนี้", style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallCaps(next.kind.label)
                Box(Modifier.weight(1f).height(1.dp).background(Tokens.Palette.Line))
                SmallCaps("ด่าน ${next.lesson.stage}")
            }
            Spacer(Modifier.height(16.dp))
            BigNumber(next.lesson.id)
            Text(next.lesson.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp))
            Text(if (next.notYet) "วันนี้ลองแล้ว ยังไม่ผ่านเกณฑ์ ลองอีกได้" else next.lesson.explain.firstOrNull() ?: "",
                style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = { onOpen(next.lesson) }, contentPadding = PaddingValues(start = 28.dp, end = 10.dp),
                modifier = Modifier.padding(top = 26.dp).width(360.dp).heightIn(min = 60.dp)) {
                Text(if (next.kind == Kind.STEP) "เริ่มขั้นนี้" else "เริ่ม", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(Modifier.size(42.dp).background(Tokens.Palette.OnInk, CircleShape), contentAlignment = Alignment.Center) { Text("→", color = Tokens.Palette.Ink, fontSize = 18.sp) }
            }
        }
        Spacer(Modifier.height(36.dp))
        if (items.size > 1) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Box(Modifier.weight(1f)) { SmallCaps("วันนี้") }
                SmallCaps("${items.count { it.done }} / ${items.size}")
            }
            items.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(enabled = !item.done) { onOpen(item.lesson) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("%02d".format(i + 1), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = if (item.done) Tokens.Palette.Dim else Tokens.Palette.Ink)
                    Column(Modifier.weight(1f)) {
                        Text(item.lesson.title, style = MaterialTheme.typography.bodyLarge, color = if (item.done) Tokens.Palette.InkMuted else Tokens.Palette.Ink,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null)
                        Text(item.kind.label, style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
                    }
                    Text(if (item.done) "เสร็จ" else if (item == next) "ถัดไป" else "รอ", style = MaterialTheme.typography.labelMedium,
                        color = if (item == next) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                }
                Hairline()
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { showLadder = !showLadder }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ผ่านแล้ว $passed จาก ${rungs.size} ขั้น", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            MeterBar(if (rungs.isEmpty()) 0f else passed.toFloat() / rungs.size, Modifier.width(120.dp))
            Text(if (showLadder) "ซ่อนบันได" else "ดูบันได", style = MaterialTheme.typography.labelMedium, color = Tokens.Palette.InkMuted)
        }
        if (showLadder) {
            var lastStage = -1
            for (l in rungs) {
                if (l.stage != lastStage) { lastStage = l.stage; Spacer(Modifier.height(18.dp)); SmallCaps("ด่าน ${l.stage}"); Spacer(Modifier.height(4.dp)); Hairline() }
                val days = records[l.id]?.passDays?.size ?: 0
                val status = ProgressRules.status(l, records)
                val locked = status == LessonStatus.LOCKED
                val current = l.id == ProgressRules.nextToLearn(rungs, records)?.id
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !locked) { onOpen(l) }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(l.id, fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = if (locked) Tokens.Palette.Dim else Tokens.Palette.InkMuted, modifier = Modifier.width(40.dp))
                    Text(l.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, color = if (locked) Tokens.Palette.Dim else Tokens.Palette.Ink)
                    Text(when { days >= l.pass.days -> "ผ่านแล้ว"; days > 0 -> "รอยืนยัน"; current -> "ตอนนี้"; locked -> "ยังไม่ถึง"; else -> "เปิดแล้ว" },
                        style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
                }
                Hairline()
            }
        }
    }
}

/** One rung's questions, laid out like the Android ear screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarDrillScreen(lesson: Lesson, onBack: () -> Unit, onOpenMidi: () -> Unit) {
    val model = remember(lesson.id) { EarDrillModel(lesson) }
    DisposableEffect(model) { onDispose { model.dispose() } }
    val ui by model.ui.collectAsState()

    when (val s = ui) {
        is EarUi.Ready -> Column {
            BackHeader(lesson.id, onBack)
            Text(s.title, style = MaterialTheme.typography.headlineMedium)
            lesson.explain.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
            Text("${s.count} ข้อ · ${lesson.passText}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            NoKeyboardBanner(onOpenMidi, Modifier.padding(top = 18.dp))
            Button(onClick = model::start, modifier = Modifier.padding(top = 26.dp).width(360.dp).heightIn(min = 60.dp)) { Text("เริ่ม", style = MaterialTheme.typography.titleMedium) }
        }
        is EarUi.Asking -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("←", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp))
                DrillProgress(s.index, s.total)
            }
            Text(s.prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(top = 28.dp))
            s.rhythmState?.let { Text(it, fontFamily = Tokens.Fonts.mono, fontSize = 40.sp, modifier = Modifier.padding(top = 20.dp)) }
            Spacer(Modifier.height(30.dp))
            if (s.q is EarQuestion.Rhythm) {
                if (s.correct == null && s.rhythmState == null) BigCircle("เริ่มฟังจังหวะ", model::startRhythm)
            } else BigCircle(if (s.correct == null) "ฟังอีกครั้ง" else "ฟังเฉลย", model::play)
            Spacer(Modifier.height(30.dp))
            val melody = (s.q as? EarQuestion.Melody)?.notes
            if (melody != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    melody.indices.forEach { i -> NoteSlot(if (i == 0) melody.first() else s.entered.getOrNull(i - 1), filled = i == 0) }
                }
                if (s.correct == false) {
                    SmallCaps("เฉลย")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) { melody.forEach { NoteSlot(it, filled = true) } }
                }
                if (s.entered.isNotEmpty() && s.correct == null) TextButton(onClick = model::retryEntry) { Text("ลบแล้วเล่นใหม่") }
            } else if (s.correct == null && s.choices.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.choices.forEach { c -> OutlinedButton(onClick = { model.answer(c.value) }, modifier = Modifier.heightIn(min = 52.dp)) { Text(c.label, style = MaterialTheme.typography.labelLarge) } }
                }
            }
            s.correct?.let { ok ->
                Text(if (ok) "ถูก" else "ผิด", style = MaterialTheme.typography.headlineSmall, color = if (ok) Tokens.Palette.Ink else Tokens.Palette.OnInk,
                    modifier = Modifier.padding(top = 24.dp).then(if (ok) Modifier else Modifier.background(Tokens.Palette.Ink, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 2.dp)))
            }
            if (melody == null) s.correction?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
            if (s.correct != null) Button(onClick = model::next, modifier = Modifier.padding(top = 26.dp).width(360.dp).heightIn(min = 60.dp)) { Text("ข้อต่อไป", style = MaterialTheme.typography.titleMedium) }
            NoKeyboardBanner(onOpenMidi, Modifier.padding(top = 24.dp))
        }
        is EarUi.Done -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(64.dp).then(if (s.met) Modifier.background(Tokens.Palette.Ink, CircleShape) else Modifier.border(1.5.dp, Tokens.Palette.Ink, CircleShape)),
                contentAlignment = Alignment.Center) { Text(if (s.met) "✓" else "↻", fontSize = 26.sp, color = if (s.met) Tokens.Palette.OnInk else Tokens.Palette.Ink) }
            Text(s.headline, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
            s.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 28.dp)) {
                OutlinedButton(onClick = model::start, modifier = Modifier.heightIn(min = 56.dp).width(180.dp)) { Text("อีกรอบ") }
                Button(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp).width(180.dp)) { Text("เสร็จ") }
            }
        }
    }
}

@Composable
private fun NoteSlot(note: Int?, filled: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(64.dp, 82.dp).clip(shape).then(
            when {
                filled -> Modifier.background(Tokens.Palette.Ink)
                note != null -> Modifier.border(1.5.dp, Tokens.Palette.Ink, shape)
                else -> Modifier.border(1.dp, Tokens.Palette.Faint, shape)
            },
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (note != null) {
            val p = SpelledPitch.fromMidiSharp(note)
            val c = if (filled) Tokens.Palette.OnInk else Tokens.Palette.Ink
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(p.letter.name + SpelledPitch.accidentalSymbol(p.accidental), fontFamily = Tokens.Fonts.mono, fontWeight = FontWeight.Medium, fontSize = 26.sp, color = c)
                Text("${p.octave}", fontFamily = Tokens.Fonts.mono, fontSize = 12.sp, color = c, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
