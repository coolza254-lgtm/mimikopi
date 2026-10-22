package com.id.mimikopi.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.TargetSong
import com.id.mimikopi.core.recall.VideoReference
import com.id.mimikopi.desktop.ui.components.Bravura
import com.id.mimikopi.desktop.ui.components.Chip
import com.id.mimikopi.desktop.ui.components.PageCard
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.theme.Tokens
import java.time.LocalDate

/**
 * The goal song (Design J "เพลงเป้าหมาย", as on the phone): the private song chosen as the goal, a ring of how much is
 * learned, one segment per phrase, the phrase to practise now and one button that opens it. With more than one private
 * song, chips change the goal here (private songs stay on each device, so each picks its own).
 */
@Composable
fun TargetCard(
    songs: List<PrivateSong>,
    chosenId: String?,
    onChoose: (String) -> Unit,
    onStart: (songId: String?, hand: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = TargetSong.pick(songs, chosenId)
    val phrases = song?.phrases.orEmpty()
    val states = phrases.map(TargetSong::state)
    val next = song?.let { TargetSong.next(it, LocalDate.now()) }
    val nextIndex = song?.phrases?.indexOfFirst { it.id == next?.phrase?.id } ?: -1
    PageCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("เพลงเป้าหมาย", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
                Text(song?.title ?: "ยังไม่ได้เลือกเพลง", fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        song == null -> "เพลงส่วนตัวที่แกะเอง · เก็บในเครื่องนี้"
                        states.isEmpty() -> "ยังไม่ได้แบ่งช่วง"
                        else -> "${states.size} ช่วง · เรียนแล้ว ${states.count { it == TargetSong.State.LEARNED }} ช่วง"
                    },
                    fontSize = 14.sp, color = Tokens.Palette.InkMuted,
                )
            }
            ClefRing(song?.let(TargetSong::fraction) ?: 0f, 84.dp)
        }
        if (songs.size > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            songs.take(6).forEach { s -> Chip(s.title, selected = s.id == song?.id) { onChoose(s.id) } }
        }
        if (states.isNotEmpty()) {
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                states.forEachIndexed { i, st ->
                    val color = when {
                        st == TargetSong.State.LEARNED -> Tokens.Palette.Accent
                        i == nextIndex || st == TargetSong.State.PRACTISING -> Tokens.Palette.AccentMid
                        else -> Tokens.Palette.Hair
                    }
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                        if (states.size <= 8) Text(phrases[i].title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (i == nextIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == nextIndex) Tokens.Palette.Ink else Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.box)).background(Tokens.Palette.Paper)
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        song == null -> "เพิ่มเพลงแรก"
                        next != null -> "${next.phrase.title} · ${next.hand.title}"
                        states.isEmpty() -> "แบ่งเพลงเป็นช่วงสั้น ๆ"
                        else -> "จำได้ครบทุกช่วงแล้ว"
                    },
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        song == null -> "ตั้งชื่อเพลง ใส่ลิงก์ YouTube แล้วแบ่งทีละ 1–2 ห้อง"
                        next == null -> "ทวนทั้งเพลงจากความจำ"
                        next.due -> "ถึงวันทวน · ลองเองก่อนเปิดคลิป"
                        TargetSong.state(next.phrase) == TargetSong.State.NEW -> "ช่วงใหม่ · ฟังคลิปแล้วลองเอง"
                        else -> "กำลังฝึก · เล่นเองให้ได้ ${TargetSong.LEARNED_DAYS} วัน"
                    },
                    fontSize = 14.sp, color = Tokens.Palette.InkMuted,
                )
            }
            if (next != null) Text("${VideoReference.clock(next.phrase.startSeconds)}–${VideoReference.clock(next.phrase.endSeconds)}", fontSize = 14.sp,
                color = Tokens.Palette.InkMuted, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Tokens.Palette.Group).padding(horizontal = 12.dp, vertical = 6.dp))
            PrimaryButton(
                when {
                    song == null -> "เพิ่มเพลง"
                    states.isEmpty() -> "แบ่งช่วง"
                    next == null -> "▶  ทวนเพลง"
                    else -> "▶  เริ่มช่วงนี้"
                },
                { onStart(song?.id, next?.hand?.name) },
                Modifier.width(180.dp),
            )
        }
    }
}

/** How much of the goal song is learned, round a treble clef. */
@Composable
fun ClefRing(fraction: Float, size: Dp = 68.dp) {
    val soft = Tokens.Palette.AccentSoft
    val accent = Tokens.Palette.Accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(soft)
            drawArc(accent, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = true)
        }
        Box(Modifier.size(size - 12.dp).clip(CircleShape).background(Tokens.Palette.Group), contentAlignment = Alignment.Center) {
            // Bravura draws the clef from a baseline far below the middle of the line box; lifted 0.78 em to centre it.
            val font = (size.value * 0.38f)
            Text("", fontFamily = Bravura, fontSize = font.sp, color = accent, modifier = Modifier.offset(y = (-font * 0.78f).dp))
        }
    }
}
