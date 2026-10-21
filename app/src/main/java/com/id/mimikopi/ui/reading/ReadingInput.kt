package com.id.mimikopi.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.ui.components.TouchKeyboardSurface
import com.id.mimikopi.ui.theme.Tokens

/** MIDI uses physical keys. Phone players can split two octave banks for simultaneous two-hand notes. */
@Composable
fun ReadingKeyboard(
    connected: Boolean, range: IntRange, nameStyle: NoteNameStyle, showNames: Boolean,
    pressed: Set<Int>, onKey: (Int, Boolean) -> Unit,
    marked: Set<Int> = emptySet(), enabled: Boolean = true, twoHands: Boolean = false, performance: Boolean = false,
) {
    if (connected) {
        Text(if (enabled) "● ต่อ MIDI · เล่นโน้ตบนเปียโนได้เลย" else "● ต่อ MIDI · รอเสียงจบก่อนเล่น", color = Tokens.Palette.InkMuted, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (performance) 0.dp else 12.dp).testTag("reading-midi"))
        return
    }
    val pages = remember(range) { readingKeyPages(range) }
    val initial = pages.indexOfLast { it.first <= 60 }.coerceAtLeast(0)
    var high by rememberSaveable(range.first, range.last) { mutableIntStateOf(initial) }
    var low by rememberSaveable(range.first, range.last) { mutableIntStateOf(0) }
    var split by rememberSaveable(range.first, range.last, twoHands) { mutableStateOf(twoHands && pages.size > 1) }
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val compact = with(androidx.compose.ui.platform.LocalDensity.current) { window.height.toDp() < 500.dp }
    val latestKey by rememberUpdatedState(onKey)
    // Banks may share Middle C. Release a pitch only after its last finger/bank releases it.
    val holds = remember { mutableMapOf<Int, Int>() }
    val play = remember {
        { note: Int, down: Boolean ->
            val count = holds[note] ?: 0
            if (down) {
                holds[note] = count + 1
                if (count == 0) latestKey(note, true)
            } else if (count > 0) {
                if (count == 1) { holds.remove(note); latestKey(note, false) }
                else holds[note] = count - 1
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(Tokens.Palette.Paper).testTag("reading-phone")) {
        if (pages.size > 1 && !(compact && performance)) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (split) "คีย์สองช่วง · กดพร้อมกันได้" else "คีย์บนมือถือ", color = Tokens.Palette.InkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { split = !split }, enabled = enabled, modifier = Modifier.heightIn(min = if (compact) 32.dp else 40.dp).testTag("reading-split")) {
                Text(if (split) "ใช้ช่วงเดียว" else "แยกสองช่วง", fontSize = 12.sp)
            }
        }
        val upper: @Composable () -> Unit = {
            ReadingKeyBank(pages, high, { high = it }, "", nameStyle, showNames, marked, pressed, play, enabled, compact)
        }
        val lower: @Composable () -> Unit = {
            ReadingKeyBank(pages, low, { low = it }, "-low", nameStyle, showNames, marked, pressed, play, enabled, compact)
        }
        if (split && compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) { lower() }
            Column(Modifier.weight(1f)) { upper() }
        } else { upper(); if (split) lower() }
    }
}

@Composable
private fun ReadingKeyBank(
    pages: List<IntRange>, page: Int, onPage: (Int) -> Unit, bank: String,
    nameStyle: NoteNameStyle, showNames: Boolean, marked: Set<Int>, pressed: Set<Int>,
    onKey: (Int, Boolean) -> Unit, enabled: Boolean, compact: Boolean,
) {
    Column {
        if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { onPage(page - 1) }, enabled = page > 0 && enabled,
                contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = if (compact) 32.dp else 40.dp).testTag("reading-octave-down$bank")) { Text("ต่ำลง", fontSize = 12.sp) }
            Text("${SpelledPitch.fromMidiSharp(pages[page].first)}–${SpelledPitch.fromMidiSharp(pages[page].last)}", fontSize = 12.sp,
                color = Tokens.Palette.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).testTag("reading-octave$bank"))
            TextButton(onClick = { onPage(page + 1) }, enabled = page < pages.lastIndex && enabled,
                contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = if (compact) 32.dp else 40.dp).testTag("reading-octave-up$bank")) { Text("สูงขึ้น", fontSize = 12.sp) }
        }
        Box(Modifier.fillMaxWidth().height(if (compact) 76.dp else 112.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
            key(page, enabled) {
                TouchKeyboardSurface(pages[page], marked, showNames, nameStyle, pressed,
                    if (enabled) onKey else { _, _ -> },
                    Modifier.matchParentSize().then(if (enabled) Modifier else Modifier.semantics { disabled() }).testTag("reading-keyboard$bank"))
            }
            if (!enabled) Text("รอรับคำตอบ", color = Tokens.Palette.Ink,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).background(Tokens.Palette.Paper).padding(8.dp))
        }
    }
}

/** Every offered answer includes its octave; enharmonic names share a physical pitch. */
@Composable
internal fun ReadingNameAnswers(pitches: List<SpelledPitch>, enabled: Boolean, onAnswer: (Int) -> Unit) {
    val groups = remember(pitches) { pitches.distinct().groupBy { it.midi }.toSortedMap().values.toList() }
    Column(Modifier.fillMaxWidth().testTag("reading-name-answers"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { spellings ->
                    val p = spellings.first()
                    OutlinedButton(onClick = { onAnswer(p.midi) }, enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp).testTag("reading-answer-${p.midi}")) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(spellings.joinToString("/") { it.name(NoteNameStyle.LETTER, true) }, fontSize = 14.sp)
                            Text(p.name(NoteNameStyle.SOLFEGE), fontSize = 13.sp)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
