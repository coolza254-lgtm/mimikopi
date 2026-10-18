package com.id.mimikopi.ui.ear

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import com.id.mimikopi.R
import com.id.mimikopi.core.ear.EarCourse
import com.id.mimikopi.core.ear.EarItemView
import com.id.mimikopi.core.ear.EarKind
import com.id.mimikopi.core.ear.EarProgress
import com.id.mimikopi.core.ear.EarState
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.GroupHeader
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.LevelBars
import com.id.mimikopi.ui.components.MidiPill
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.PrimaryButton
import com.id.mimikopi.ui.components.StepMark
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EarHomeUi(
    val loaded: Boolean = false,
    val state: EarState = EarState(),
    val stage: EarCourse.Stage = EarCourse.STAGES.first(),
    val level: Int = 0,
    val overview: Map<EarKind, List<EarItemView>> = emptyMap(),
    val weakest: List<EarItemView> = emptyList(),
    /** First-try accuracy for each of the last 30 days (null = no ear work that day), oldest first. */
    val days: List<Double?> = emptyList(),
    /** The kinds the next round will contain. */
    val nextKinds: List<EarKind> = emptyList(),
)

class EarHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val _ui = MutableStateFlow(EarHomeUi())
    val ui: StateFlow<EarHomeUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val state = EarProgress.parse(services.database.settingsDao().get(EarProgress.KEY))
            services.progress.load()
            val today = LocalDate.now()
            val daily = EarProgress.daily(state, today, 30).toMap()
            val plan = EarProgress.plan(state, System.currentTimeMillis(), kotlin.random.Random(today.toEpochDay()))
            _ui.value = EarHomeUi(
                loaded = true,
                state = state,
                stage = EarProgress.currentStage(state),
                level = SkillBook.status(SkillId.EAR, services.progress.checks.value, today).level,
                overview = EarProgress.overview(state),
                weakest = EarProgress.weakest(state),
                days = (29 downTo 0).map { daily[today.minusDays(it.toLong())] },
                nextKinds = plan.map { EarCourse.kindOf(it) }.distinct(),
            )
        }
    }
}

/**
 * The ear course: the next round in one button, the path of ten stages, how each kind of listening stands (items
 * known of items taught, first-try accuracy) and the items still missed, with a round of one kind a tap away.
 */
@Composable
fun EarHomeScreen(
    onRound: (EarKind?) -> Unit,
    onTest: () -> Unit,
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    vm: EarHomeViewModel = viewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth()) {
            LargeTitle("ฝึกหู", if (ui.loaded) "ระดับหู ${ui.level} จาก 8 · ขั้น ${ui.stage.n} จาก 10" else null, onBack = onBack) {
                MidiPill(onOpenMidi)
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "ตั้งค่า", tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
                }
            }
            if (!ui.loaded) return@Column
            Column(Modifier.padding(horizontal = PageMargin)) {
                Spacer(Modifier.height(16.dp))
                Group {
                    Column(Modifier.padding(20.dp)) {
                        Text("รอบถัดไป · ขั้น ${ui.stage.n}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted)
                        Text(ui.stage.title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 2.dp))
                        Text(ui.stage.about, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 4.dp))
                        Text("${EarProgress.ROUND} ข้อ ราว 4 นาที: " + ui.nextKinds.joinToString(" · ") { it.title }, fontSize = 14.sp, lineHeight = 19.sp,
                            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton("เริ่มฝึก", { onRound(null) })
                    }
                }
                GroupFooter("ข้อที่ยังพลาดจะกลับมาบ่อยขึ้น ข้อที่แม่นแล้วกลับมาเป็นระยะไม่ให้ลืม ขั้นถัดไปเปิดเมื่อทุกข้อของขั้นนี้แม่นแล้ว (ถูกครั้งแรกสม่ำเสมอ อย่างน้อย 6 ครั้ง)")

                GroupHeader("30 วันล่าสุด")
                Group {
                    Column(Modifier.padding(16.dp)) {
                        AccuracyBars(ui.days)
                        val heard = ui.days.filterNotNull()
                        Text(
                            if (heard.isEmpty()) "ยังไม่ได้ฝึกหูในช่วงนี้" else "ฝึก ${heard.size} วัน · ถูกครั้งแรกเฉลี่ย ${(heard.average() * 100).toInt()}%",
                            fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                GroupFooter("แท่งสูง = ถูกครั้งแรกมาก แท่งว่าง = วันที่ไม่ได้ฝึกหู")

                GroupHeader("หูของฉันตอนนี้")
                Group {
                    val kinds = ui.overview.entries.toList()
                    kinds.forEachIndexed { i, (kind, items) ->
                        if (i > 0) GroupDivider()
                        val known = items.count { it.stat.mastered }
                        val asked = items.sumOf { it.stat.asked }
                        val right = items.sumOf { it.stat.right }
                        GroupRow(
                            kind.title,
                            subtitle = "แม่นแล้ว $known จาก ${items.size}" + if (asked > 0) " · ถูกครั้งแรก ${right * 100 / asked}% ทั้งหมด" else " · ยังไม่ได้ฝึก",
                            onClick = { onRound(kind) },
                            trailing = { LevelBars(known, items.size, width = 7.dp) },
                        )
                    }
                }
                GroupFooter("แตะหมวดเพื่อฝึกเฉพาะเรื่องนั้นหนึ่งรอบ")

                if (ui.weakest.isNotEmpty()) {
                    GroupHeader("ยังพลาดบ่อย")
                    Group {
                        ui.weakest.forEachIndexed { i, w ->
                            if (i > 0) GroupDivider()
                            GroupRow(w.label, subtitle = "${w.kind.title} · ถูก ${w.stat.right} จาก ${w.stat.asked} ครั้ง", onClick = { onRound(w.kind) })
                        }
                    }
                }

                GroupHeader("เส้นทาง 10 ขั้น")
                Group {
                    EarCourse.STAGES.forEachIndexed { i, st ->
                        if (i > 0) GroupDivider(indent = 52.dp)
                        val (known, total) = EarProgress.stageProgress(ui.state, st)
                        val open = st.n <= ui.stage.n
                        GroupRow(
                            "ขั้น ${st.n} · ${st.title}",
                            subtitle = if (open) "แม่นแล้ว $known จาก $total · " + st.adds.joinToString(" ") { EarCourse.label(it) } else st.about,
                            leading = { StepMark(done = known == total, current = st.n == ui.stage.n && known < total) },
                            titleColor = if (open) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
                        )
                    }
                }

                GroupHeader("ระดับหู")
                Group { GroupRow("ทดสอบระดับหู", subtitle = "ระดับวัดจากแบบทดสอบในหน้าทักษะเท่านั้น ระดับตอนนี้ ${ui.level} จาก 8", onClick = onTest) }
                GroupFooter("ตัวเลขทั้งหมดนับเฉพาะคำตอบครั้งแรก")
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** One thin bar per day: height = first-try accuracy, an empty mark for a day without ear work. */
@Composable
private fun AccuracyBars(days: List<Double?>) {
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { a ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                if (a == null) Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.dp)).background(Tokens.Palette.Line))
                else Box(Modifier.fillMaxWidth().fillMaxHeight(a.toFloat().coerceIn(0.06f, 1f)).clip(RoundedCornerShape(2.dp)).background(Tokens.Palette.Ink))
            }
        }
    }
}
