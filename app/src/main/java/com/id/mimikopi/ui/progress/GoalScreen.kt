package com.id.mimikopi.ui.progress

import com.id.mimikopi.core.i18n.tr
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.goal.IdolGoal
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillStatus
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.Open
import com.id.mimikopi.ui.today.ProgressStore
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
fun GoalScreen(onBack: () -> Unit, onOpen: (Open) -> Unit, vm: GoalViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val statuses by vm.statuses.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle(tr("เป้าหมาย", "Goal", "目標"), tr("เล่นเพลง Hello! Project และเพลงไอดอลได้เอง", "Play Hello! Project and idol songs on your own", "Hello! Projectやアイドル曲を自分で弾けるように"), onBack = onBack)
            val st = statuses ?: return@Column
            Column(Modifier.padding(horizontal = PageMargin)) {
                GroupFooter(
                    tr("แอพไม่มีโน้ตหรือเนื้อร้องของเพลงจริง (ลิขสิทธิ์) เป้าหมายคือฝึกจนแกะและเล่นเองได้ ", "The app has no sheets or lyrics of the real songs (copyright). The goal is to practise until you can work them out and play them yourself. ", "アプリには実際の曲の楽譜や歌詞はありません（著作権）。目標は自分で耳コピして弾けるまで練習することです。") +
                        tr("ตัวเลขข้างล่างเทียบกับระดับจากแบบทดสอบ ส่วนระดับที่แต่ละเพลงต้องการเป็นค่าประเมินของแอพ ไม่ใช่เกรดทางการ", "The numbers below compare with levels from tests; the level each song needs is the app's estimate, not an official grade", "下の数字はテストのレベルと比べたもの。各曲に必要なレベルはアプリの推定で、公式のグレードではありません"),
                    modifier = Modifier.padding(top = 12.dp),
                )
                IdolGoal.SONGS.forEach { song ->
                    val ready = IdolGoal.readiness(song, st)
                    val gaps = IdolGoal.gaps(song, st)
                    GroupHeader("${song.title} · ${song.artist}")
                    Group {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                            Text(if (gaps.isEmpty()) tr("ทักษะถึงแล้ว", "Skills reached", "スキル到達") else tr("พร้อม ${(ready * 100).toInt()}%", "${(ready * 100).toInt()}% ready", "準備 ${(ready * 100).toInt()}%"), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Accent)
                            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Tokens.Palette.Track)) {
                                Box(Modifier.fillMaxWidth(ready.toFloat().coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Tokens.Palette.Accent))
                            }
                            Text(song.why, fontSize = 14.sp, lineHeight = 19.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                            Text(tr("ข้อมูลเพลง (ยังไม่ได้ยืนยัน): ", "Song info (unconfirmed): ", "曲の情報（未確認）：") + song.facts.joinToString(" · "), fontSize = 13.sp, lineHeight = 18.sp,
                                color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (gaps.isEmpty()) {
                            GroupDivider()
                            GroupRow(tr("ลองแกะเพลงนี้ด้วยหูได้แล้ว", "You can try working this song out by ear now", "この曲はもう耳コピに挑戦できます"), subtitle = tr("ฟังหาคีย์ แกะทางเดินคอร์ดท่อนฮุก แล้วแกะทำนองทีละวลี", "Find the key by ear, work out the chorus progression, then the melody phrase by phrase", "耳でキーを探し、サビのコード進行、次にメロディをフレーズごとに耳コピ"), onClick = { onOpen(Open.Ear(EarKind.MELODY.name)) })
                        }
                        gaps.forEach { g ->
                            GroupDivider()
                            GroupRow(g.skill.title, subtitle = tr("ตอนนี้ระดับ ${g.have} ต้องการ ${g.need}", "Now ${g.have}, needs ${g.need}", "現在${g.have}、必要${g.need}"), onClick = { onOpen(Open.Skill(g.skill)) })
                        }
                    }
                }

                GroupHeader(tr("ฝึกแบบเพลงไอดอล", "Idol-song practice", "アイドル曲練習"))
                Group {
                    IdolGoal.DRILLS.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider()
                        GroupRow(d.title, subtitle = d.about + tr(" · เปลี่ยนคอร์ดให้ได้มากที่สุดใน 1 นาที", " · as many chord changes as you can in 1 minute", " · 1分間でできるだけ多くコードチェンジ"), onClick = { vm.drill(d.practice) { onOpen(Open.ChordsToday) } })
                    }
                    GroupDivider()
                    GroupRow(tr("ฟังทางเดินคอร์ด", "Hear progressions", "コード進行を聞く"), subtitle = tr("แยก Axis 小室進行 王道進行 Canon ด้วยหู", "Tell Axis, 小室進行 (Komuro shinkō), 王道進行 (Ōdō shinkō) and Canon apart by ear", "Axis、小室進行、王道進行、カノン進行を耳で聞き分ける"), onClick = { onOpen(Open.Ear(EarKind.PROGRESSION.name)) })
                    GroupDivider()
                    GroupRow(tr("แกะทำนอง", "Melody dictation", "メロディの耳コピ"), subtitle = tr("ฟังวลีสั้นแล้วเล่นกลับ ทักษะหลักของการแกะเพลง", "Hear a short phrase and play it back, the core skill of working out songs", "短いフレーズを聞いて弾き返す。耳コピの基本スキル"), onClick = { onOpen(Open.Ear(EarKind.MELODY.name)) })
                }
                GroupFooter(tr("ทางเดินคอร์ดไม่มีลิขสิทธิ์ เพลงป็อปญี่ปุ่นจำนวนมากใช้ทางเดินเหล่านี้", "Chord progressions have no copyright; many Japanese pop songs use these", "コード進行に著作権はなく、多くのJ-POPがこれらを使っています"))
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
