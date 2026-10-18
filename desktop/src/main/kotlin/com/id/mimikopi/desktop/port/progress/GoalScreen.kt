package com.id.mimikopi.desktop.port.progress

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/progress/GoalScreen.kt): imports changed, other changes marked "Desktop:".

import com.id.mimikopi.desktop.port.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.port.AndroidViewModel
import com.id.mimikopi.desktop.port.Lifecycle
import com.id.mimikopi.desktop.port.LifecycleEventEffect
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.viewModelScope
import com.id.mimikopi.desktop.port.MimikopiApp
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.goal.IdolGoal
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillStatus
import com.id.mimikopi.desktop.port.SettingEntity
import com.id.mimikopi.desktop.port.components.Group
import com.id.mimikopi.desktop.port.components.GroupDivider
import com.id.mimikopi.desktop.port.components.GroupFooter
import com.id.mimikopi.desktop.port.components.GroupHeader
import com.id.mimikopi.desktop.port.components.GroupRow
import com.id.mimikopi.desktop.port.components.LargeTitle
import com.id.mimikopi.desktop.port.components.PageMargin
import com.id.mimikopi.desktop.port.components.PageMaxWidth
import com.id.mimikopi.desktop.port.components.paper
import com.id.mimikopi.desktop.ui.Tokens
import com.id.mimikopi.desktop.port.today.Open
import com.id.mimikopi.desktop.port.today.ProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class GoalViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val _statuses = MutableStateFlow<List<SkillStatus>?>(null)
    val statuses: StateFlow<List<SkillStatus>?> = _statuses.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            services.progress.load()
            val today = LocalDate.now()
            _statuses.value = SkillId.entries.map { SkillBook.status(it, services.progress.checks.value, today) }
        }
    }

    /** Opens a chord drill in the songs' style: written where the chord runner reads the Today chord part. */
    fun drill(practice: ChordPractice, go: () -> Unit) {
        viewModelScope.launch {
            services.database.settingsDao().put(SettingEntity(ProgressStore.CHORDS_TODAY, ChordPractice.encode(practice)))
            go()
        }
    }
}

/**
 * The goal: the first idol songs the user wants to play, each with what it asks for against the levels from tests
 * and what is still missing, then practice in the songs' style. No notes or lyrics of the songs (copyright); the song
 * facts come from the web and are marked as not checked.
 */
@Composable
fun GoalScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: GoalViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val statuses by vm.statuses.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle("เป้าหมาย", "เล่นเพลง Hello! Project และเพลงไอดอลได้เอง", onBack = onBack)
            val st = statuses ?: return@Column
            Column(Modifier.padding(horizontal = PageMargin)) {
                GroupFooter(
                    "แอพไม่มีโน้ตหรือเนื้อร้องของเพลงจริง (ลิขสิทธิ์) เป้าหมายคือฝึกจนแกะและเล่นเองได้ " +
                        "ตัวเลขข้างล่างเทียบกับระดับจากแบบทดสอบ ส่วนระดับที่แต่ละเพลงต้องการเป็นค่าประเมินของแอพ ไม่ใช่เกรดทางการ",
                    modifier = Modifier.padding(top = 12.dp),
                )
                IdolGoal.SONGS.forEach { song ->
                    val ready = IdolGoal.readiness(song, st)
                    val gaps = IdolGoal.gaps(song, st)
                    GroupHeader("${song.title} · ${song.artist}")
                    Group {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (gaps.isEmpty()) "ทักษะถึงแล้ว" else "พร้อม ${(ready * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Line)) {
                                Box(Modifier.fillMaxWidth(ready.toFloat().coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Ink))
                            }
                            Text(song.why, fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                            Text("ข้อมูลเพลง (ยังไม่ได้ยืนยัน): " + song.facts.joinToString(" · "), fontSize = 13.sp, lineHeight = 18.sp,
                                color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (gaps.isEmpty()) {
                            GroupDivider()
                            GroupRow("ลองแกะเพลงนี้ด้วยหูได้แล้ว", subtitle = "ฟังหาคีย์ แกะทางเดินคอร์ดท่อนฮุก แล้วแกะทำนองทีละวลี", onClick = { onOpen(Open.Ear(EarKind.MELODY.name)) })
                        }
                        gaps.forEach { g ->
                            GroupDivider()
                            GroupRow(g.skill.title, subtitle = "ตอนนี้ระดับ ${g.have} ต้องการ ${g.need}", onClick = { onOpen(Open.Skill(g.skill)) })
                        }
                    }
                }

                GroupHeader("ฝึกแบบเพลงไอดอล")
                Group {
                    IdolGoal.DRILLS.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider()
                        GroupRow(d.title, subtitle = d.about + " · เปลี่ยนคอร์ดให้ได้มากที่สุดใน 1 นาที", onClick = { vm.drill(d.practice) { onOpen(Open.ChordsToday) } })
                    }
                    GroupDivider()
                    GroupRow("ฟังทางเดินคอร์ด", subtitle = "แยก Axis 小室進行 王道進行 Canon ด้วยหู", onClick = { onOpen(Open.Ear(EarKind.PROGRESSION.name)) })
                    GroupDivider()
                    GroupRow("แกะทำนอง", subtitle = "ฟังวลีสั้นแล้วเล่นกลับ ทักษะหลักของการแกะเพลง", onClick = { onOpen(Open.Ear(EarKind.MELODY.name)) })
                }
                GroupFooter("ทางเดินคอร์ดไม่มีลิขสิทธิ์ เพลงป็อปญี่ปุ่นจำนวนมากใช้ทางเดินเหล่านี้")
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
