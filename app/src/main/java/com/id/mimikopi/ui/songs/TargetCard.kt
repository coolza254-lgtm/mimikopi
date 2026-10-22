package com.id.mimikopi.ui.songs

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.id.mimikopi.core.recall.TargetSong
import com.id.mimikopi.core.recall.VideoReference
import com.id.mimikopi.ui.components.Bravura
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.TargetUi

/**
 * The goal song (Design J "เพลงเป้าหมาย"): the private song the user chose, a ring of how much is learned, one segment
 * per phrase (learned = accent, being practised = accentMid, not yet = divider), the phrase to practise now and one
 * button to start it. Without a private song yet it invites to add one.
 */
@Composable
fun TargetCard(target: TargetUi?, onStart: (TargetUi) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    PageCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("เพลงเป้าหมาย", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
                Text(target?.title ?: "ยังไม่ได้เลือกเพลง", fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        target == null -> "เพลงส่วนตัวที่แกะเอง · เก็บในเครื่อง"
                        target.states.isEmpty() -> "ยังไม่ได้แบ่งช่วง"
                        else -> "${target.states.size} ช่วง · เพลงส่วนตัว"
                    },
                    fontSize = 14.sp, color = Tokens.Palette.InkMuted,
                )
            }
            ClefRing(target?.fraction ?: 0f)
        }
        val t = target
        if (t != null && t.states.isNotEmpty()) {
            Segments(t)
            t.next?.let { n ->
                Row(
                    Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.box)).background(Tokens.Palette.Paper)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${n.phrase.title} · ${n.hand.title}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                n.due -> "ถึงวันทวน · ลองเองก่อนเปิดคลิป"
                                TargetSong.state(n.phrase) == TargetSong.State.NEW -> "ช่วงใหม่ · ฟังคลิปแล้วลองเอง"
                                else -> "กำลังฝึก · เล่นเองให้ได้ ${TargetSong.LEARNED_DAYS} วัน"
                            },
                            fontSize = 13.sp, color = Tokens.Palette.InkMuted,
                        )
                    }
                    Text("${VideoReference.clock(n.phrase.startSeconds)}–${VideoReference.clock(n.phrase.endSeconds)}", fontSize = 14.sp, color = Tokens.Palette.InkMuted,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Tokens.Palette.Group).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            t == null -> PrimaryButton("เพิ่มเพลงส่วนตัว", onAdd)
            t.states.isEmpty() -> PrimaryButton("แบ่งช่วงแรก", { onStart(t) })
            t.next == null -> PrimaryButton("ทวนทั้งเพลง", { onStart(t) }, play = true)
            else -> PrimaryButton("เริ่มช่วงนี้", { onStart(t) }, play = true)
        }
    }
}

/** One capsule per phrase; numbers under them while they fit, the current one in bold. */
@Composable
private fun Segments(t: TargetUi) {
    Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        t.states.forEachIndexed { i, s ->
            val color = when {
                s == TargetSong.State.LEARNED -> Tokens.Palette.Accent
                i == t.nextIndex || s == TargetSong.State.PRACTISING -> Tokens.Palette.AccentMid
                else -> Tokens.Palette.Hair
            }
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
    if (t.states.size <= 8) Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        t.states.indices.forEach { i ->
            val now = i == t.nextIndex
            Text(if (i == 0) "ช่วง 1" else "${i + 1}", fontSize = 11.sp, fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                color = if (now) Tokens.Palette.Ink else Tokens.Palette.InkMuted, modifier = Modifier.weight(1f))
        }
    }
}

/** The treble clef in a ring that fills with the learned share (a conic sweep in the accent over accentSoft). */
@Composable
fun ClefRing(fraction: Float, size: Dp = 68.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(Tokens.Palette.AccentSoft)
            drawArc(Tokens.Palette.Accent, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = true)
        }
        Box(Modifier.size(size - 12.dp).clip(CircleShape).background(Tokens.Palette.Group), contentAlignment = Alignment.Center) {
            ClefGlyph(26.sp)
        }
    }
}

/**
 * The treble clef centred in its circle. Bravura draws the clef from its baseline far below the line box's middle, so
 * it is lifted by 0.78 em (from the glyph's bounds, -0.66 to 1.1 em) to sit in the middle.
 */
@Composable
fun ClefGlyph(size: TextUnit) {
    val lift = with(LocalDensity.current) { (size * 0.78f).toDp() }
    Text("", fontFamily = Bravura, fontSize = size, color = Tokens.Palette.Accent, modifier = Modifier.offset(y = -lift))
}
