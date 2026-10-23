package com.id.mimikopi.ui.chords

import com.id.mimikopi.core.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.id.mimikopi.core.keyboard.KeyboardLayout
import com.id.mimikopi.core.theory.Chord
import com.id.mimikopi.core.theory.ChordFingering
import com.id.mimikopi.core.theory.ChordQualities
import com.id.mimikopi.core.theory.HandShape
import com.id.mimikopi.ui.components.Hairline
import com.id.mimikopi.ui.components.fallingKeyRange
import com.id.mimikopi.ui.theme.Tokens

/**
 * How to hold a chord: pick the root and the kind, then see it on the keyboard with the finger numbers, for the right
 * hand (root position and inversions), the left hand, and both hands together; each picture can be heard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordBook(onPlay: (List<Int>) -> Unit) {
    var root by remember { mutableStateOf(ChordFingering.ROOTS.first()) }
    var quality by remember { mutableStateOf("") }
    val chord = Chord(root, quality)

    Label(tr("รากคอร์ด", "Root", "ルート"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordFingering.ROOTS.forEach { r -> FilterChip(selected = r == root, onClick = { root = r }, label = { Text(r.display()) }) }
    }
    Label(tr("ชนิดคอร์ด", "Chord type", "コードの種類"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordFingering.QUALITIES.forEach { q ->
            FilterChip(selected = q == quality, onClick = { quality = q }, label = { Text(Chord(root, q).display()) })
        }
    }

    Column(Modifier.padding(top = 24.dp)) {
        Text(chord.display(), fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = Tokens.Palette.Ink)
        Text(
            (ChordQualities.NAMES[quality] ?: quality) + "  ·  " + ChordFingering.spelled(chord).joinToString("  ") { "${it.display()} (${it.letter.solfege}${it.display().drop(1)})" },
            style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted,
        )
        Text(tr("ตัวเลขบนคีย์คือนิ้ว: 1 นิ้วโป้ง 2 ชี้ 3 กลาง 4 นาง 5 ก้อย", "Numbers on keys are fingers: 1 thumb 2 index 3 middle 4 ring 5 little", "鍵盤の数字は指番号：1親指 2人差し指 3中指 4薬指 5小指"), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
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
    Text(text, style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

@Composable
private fun Section(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp))
    Text(body, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 4.dp))
}

/** One shape: its title, the keyboard picture and a button to hear it. */
@Composable
private fun ShapeRow(shape: HandShape, splitAt: Int = 0, onPlay: (List<Int>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row {
            Text(shape.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(top = 10.dp))
            OutlinedButton(onClick = { onPlay(shape.notes) }) { Text(tr("ฟัง", "Listen", "聞く")) }
        }
        ChordKeys(shape, splitAt, Modifier.padding(top = 8.dp).widthIn(max = 560.dp).fillMaxWidth().height(110.dp))
    }
    Hairline()
}

/**
 * A keyboard picture with the chord's keys marked by a disc holding the finger number: dark on white keys, light on
 * black keys. White keys stay light and black keys dark in both looks, as on the other keyboards. [splitAt] notes from
 * the left belong to the left hand (a small gap is not needed: the hands' notes are octaves apart).
 */
@Composable
fun ChordKeys(shape: HandShape, splitAt: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val range = remember(shape.notes) { fallingKeyRange(shape.notes.min(), shape.notes.max(), minOctaves = 1) }
    val whites = remember(range) { KeyboardLayout(range.first, range.last, 1f, 1f).whiteKeys.size }
    val whiteKey = if (Tokens.dark) Color(0xFFE6E6E6) else Color(0xFFFFFFFF)
    Canvas(modifier) {
        val layout = KeyboardLayout(range.first, range.last, size.width / whites, size.height)
        val finger = shape.notes.zip(shape.fingers).toMap()
        fun disc(center: Offset, radius: Float, n: Int, dark: Boolean, left: Boolean) {
            drawCircle(if (dark) Color(0xFF151515) else Color(0xFFFFFFFF), radius, center)
            // Left-hand fingers get a ring, so the two hands can be told apart in the two-hand picture.
            if (left) drawCircle(if (dark) Color(0xFFFFFFFF) else Color(0xFF151515), radius * 0.82f, center, style = Stroke(radius * 0.12f))
            val t = measurer.measure("$n", TextStyle(fontSize = (radius * 1.1f).toSp(), fontWeight = FontWeight.SemiBold, color = if (dark) Color.White else Color(0xFF151515)))
            drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - t.size.height / 2f))
        }
        val leftNotes = shape.notes.take(splitAt).toSet()
        for (k in layout.whiteKeys) {
            drawRoundRect(whiteKey, Offset(k.left + 1f, 0f), Size(k.width - 2f, k.bottom), CornerRadius(k.width * 0.12f))
            drawRoundRect(Color(0xFF9E9E9E), Offset(k.left + 1f, 0f), Size(k.width - 2f, k.bottom), CornerRadius(k.width * 0.12f), style = Stroke(1.5f))
            finger[k.note]?.let { disc(Offset(k.left + k.width / 2, k.bottom - k.width * 0.55f), (k.width * 0.36f).coerceAtMost(15.dp.toPx()), it, dark = true, left = k.note in leftNotes) }
        }
        for (k in layout.blackKeys) {
            drawRoundRect(Color(0xFF151515), Offset(k.left, 0f), Size(k.width, k.bottom), CornerRadius(k.width * 0.15f))
            finger[k.note]?.let { disc(Offset(k.left + k.width / 2, k.bottom - k.width * 0.75f), (k.width * 0.42f).coerceAtMost(13.dp.toPx()), it, dark = false, left = k.note in leftNotes) }
        }
    }
}
