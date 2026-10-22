package com.id.mimikopi.ui.songs

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.RecallHand
import com.id.mimikopi.core.recall.TargetSong
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.ui.components.Chip
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.Open
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val dao = services.database.settingsDao()
    val songs = services.privateSongs.songs
    private val _targetId = MutableStateFlow<String?>(null)
    val targetId = _targetId.asStateFlow()
    val pieceCount: Int get() = services.pieces.pieces.size

    fun refresh() {
        viewModelScope.launch {
            services.privateSongs.reload()
            services.pieces.load()
            _targetId.value = dao.get(TargetSong.SETTING_KEY)
        }
    }

    /** Makes [id] the goal song (Today and Songs show it first). */
    fun setTarget(id: String) {
        _targetId.value = id
        viewModelScope.launch { dao.put(SettingEntity(TargetSong.SETTING_KEY, id)) }
    }
}

/**
 * Songs (Design J): the goal song, then the private songs worked out by ear (long press makes one the goal), and the
 * practice library for reading and exams. No classical repertoire: the goal is Hello! Project piano solo
 * (learning-system.md 2.11).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SongsScreen(onOpen: (Open) -> Unit, onOpenLibrary: () -> Unit, vm: SongsViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val targetId by vm.targetId.collectAsStateWithLifecycle()
    val target = TargetSong.pick(songs, targetId)
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = PageMargin)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 32.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("เพลง", fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                    Text(if (songs.isEmpty()) "ยังไม่มีเพลงส่วนตัว" else "1 เพลงเป้าหมาย · ${songs.size} เพลงส่วนตัว", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Tokens.Palette.Accent).clickable { onOpen(Open.PrivateSongs) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_plus), contentDescription = "เพิ่มเพลงส่วนตัว", tint = Tokens.Palette.OnAccent, modifier = Modifier.size(20.dp)) }
            }

            Spacer(Modifier.height(20.dp))
            GoalSummary(target, today, onOpen = { target?.let { onOpen(Open.PrivateSong(it.id, TargetSong.next(it, today)?.hand?.name)) } ?: onOpen(Open.PrivateSongs) })

            Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp)) {
                Text("เพลงส่วนตัว", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text("ฝึกจำจาก YouTube · เก็บในเครื่องเท่านั้น", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("แบ่งช่วง", Tokens.Skill.Reading)
                Chip("หยุดแล้วลองเอง", Tokens.Skill.Ear)
                Chip("เช็กจุดติด", Tokens.Skill.Rhythm)
                Chip("ทวนข้ามวัน", Tokens.Skill.Chords)
            }
            Spacer(Modifier.height(14.dp))
            if (songs.isEmpty()) {
                PageCard(onClick = { onOpen(Open.PrivateSongs) }) {
                    Text("เพิ่มเพลงแรก", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink)
                    Text("ตั้งชื่อเพลง ใส่ลิงก์ YouTube แล้วแบ่งเป็นช่วงสั้น ๆ ทีละ 1–2 ห้อง", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
                }
            } else {
                Group {
                    songs.forEachIndexed { i, s ->
                        if (i > 0) GroupDivider(indent = 18.dp)
                        SongRow(s, i, isTarget = s.id == target?.id, today = today,
                            onOpen = { onOpen(Open.PrivateSong(s.id)) }, onMakeTarget = { vm.setTarget(s.id) })
                    }
                }
                if (songs.size > 1) Text("แตะค้างที่เพลงเพื่อตั้งเป็นเพลงเป้าหมาย", fontSize = 12.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }

            Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp)) {
                Text("คลังเพลงฝึกอ่าน", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            }
            Group {
                GroupRow("เพลงพื้นบ้านและเพลงสอบ", subtitle = "${vm.pieceCount} เพลงหมดลิขสิทธิ์ · ใช้ในบทเรียนและสอบเกรด", onClick = onOpenLibrary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The goal song in short: the clef, its name, one bar per phrase and what comes next; tap opens its phrases. */
@Composable
private fun GoalSummary(song: PrivateSong?, today: LocalDate, onOpen: () -> Unit) {
    PageCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Tokens.Palette.AccentSoft), contentAlignment = Alignment.Center) {
                Text("", fontFamily = com.id.mimikopi.ui.components.Bravura, fontSize = 28.sp, color = Tokens.Palette.Accent)
            }
            Column(Modifier.weight(1f)) {
                Text("เพลงเป้าหมาย", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Accent)
                Text(song?.title ?: "ยังไม่ได้เลือก", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.let { "${it.phrases.size} ช่วง · เพลงส่วนตัว" } ?: "เพิ่มเพลงส่วนตัวแล้วจะขึ้นที่นี่", fontSize = 13.sp, color = Tokens.Palette.InkMuted)
            }
        }
        if (song != null && song.phrases.isNotEmpty()) {
            val next = TargetSong.next(song, today)
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                song.phrases.forEach { p ->
                    val state = TargetSong.state(p)
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(when {
                        state == TargetSong.State.LEARNED -> Tokens.Palette.Accent
                        p.id == next?.phrase?.id || state == TargetSong.State.PRACTISING -> Tokens.Palette.AccentMid
                        else -> Tokens.Palette.Hair
                    }))
                }
            }
            Text(next?.let { "ต่อไป ${it.phrase.title} · ${it.hand.title} · ดูทั้งเพลง →" } ?: "ครบทุกช่วงแล้ว · ทวนทั้งเพลง →",
                fontSize = 13.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

/** One private song: a pastel play square, its phrases and what is due; long press makes it the goal. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(song: PrivateSong, index: Int, isTarget: Boolean, today: LocalDate, onOpen: () -> Unit, onMakeTarget: () -> Unit) {
    val color = listOf(Tokens.Skill.Ear, Tokens.Skill.Chords, Tokens.Skill.Reading, Tokens.Skill.Rhythm)[index % 4]
    val due = song.phrases.count { p -> RecallHand.entries.any { h -> PrivateRecall.due(p, h)?.let { !it.isAfter(today) } == true } }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onMakeTarget).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.fill), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play_fill), contentDescription = null, tint = color.ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    song.phrases.isEmpty() -> "ยังไม่ได้แบ่งช่วง"
                    due > 0 -> "${song.phrases.size} ช่วง · ถึงวันทวน $due ช่วง"
                    else -> "${song.phrases.size} ช่วง · เรียนแล้ว ${song.phrases.count { TargetSong.state(it) == TargetSong.State.LEARNED }} ช่วง"
                },
                fontSize = 12.sp, color = Tokens.Palette.InkMuted,
            )
        }
        when {
            isTarget -> Pill("เป้าหมาย", filled = true)
            due > 0 -> Pill("ทวนวันนี้")
        }
    }
}
