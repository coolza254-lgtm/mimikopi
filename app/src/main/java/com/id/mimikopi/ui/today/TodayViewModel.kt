package com.id.mimikopi.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.lesson.ProgressRules
import com.id.mimikopi.core.progress.CheckRun
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillStatus
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.core.session.DailyPlan
import com.id.mimikopi.core.session.PlanBlock
import com.id.mimikopi.core.session.PlanContent
import com.id.mimikopi.core.session.PlanInput
import com.id.mimikopi.core.session.PracticeLog
import com.id.mimikopi.data.db.SettingEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Where a page of the app is opened from the Today or Skills pages (each platform maps it to its navigation). */
sealed interface Open {
    data class LessonDrill(val lessonId: String, val index: Int, val via: String? = null) : Open
    data object Review : Open
    data object Ladder : Open
    data class Sight(val level: Int) : Open
    /** An ear-course round ([focus]: one kind only, an EarKind name). */
    data class Ear(val focus: String? = null) : Open
    data object EarHome : Open
    data object ChordsToday : Open
    data object ChordHome : Open
    /** A level test; [score] (reader) or ear, otherwise a drill. */
    data class Check(val key: String, val run: CheckRun) : Open
    data object Songs : Open
    data class Skill(val skill: SkillId) : Open
    data object Skills : Open
    data object Piano : Open
    data object Metronome : Open
    data object Progress : Open
    data object Goal : Open
    /** A library piece or exam material ("exam-pop-2") in the reader, to practise. */
    data class Piece(val id: String) : Open
    data object Exams : Open
    data class ExamGrade(val grade: Int) : Open
    data class ExamResult(val id: String) : Open
    /** One part of the exam in progress ([part]: an ExamPart name). */
    data class ExamTake(val sitting: String, val part: String) : Open
}

data class BlockUi(val block: PlanBlock, val title: String, val detail: String, val done: Boolean)

data class TodayUi(
    val loaded: Boolean = false,
    val dateText: String = "",
    val minutesChosen: Int = 30,
    val blocks: List<BlockUi> = emptyList(),
    /** Minutes practised today (all kinds) and planned. */
    val minutesDone: Int = 0,
    val minutesPlanned: Int = 0,
    /** The last 14 days' minutes, oldest first. */
    val strip: List<Pair<LocalDate, Int>> = emptyList(),
    val weekDays: Int = 0,
    val weekMinutes: Int = 0,
    val lastWeekMinutes: Int = 0,
    val overall: String? = null,
    val weakest: SkillStatus? = null,
    val untested: Int = SkillId.entries.size,
    /** The top patterns in recent mistakes (what to fix). */
    val fixes: List<com.id.mimikopi.core.progress.Mistake> = emptyList(),
    /** The first target song not yet within reach, and how far along it is. */
    val goalSong: String? = null,
    val goalReady: Double = 0.0,
) {
    val next: BlockUi? get() = blocks.firstOrNull { !it.done }
    val allDone: Boolean get() = blocks.isNotEmpty() && blocks.all { it.done }
}

/**
 * Today's practice: a plan made once a day from the minutes chosen, the ladder, the review, the skill levels and the
 * test due (DailyPlan), then ticked off from what was really practised (the practice log), one button to the next part.
 */
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val learning = services.learning
    private val progress = services.progress
    private val dao = services.database.settingsDao()

    private val _ui = MutableStateFlow(TodayUi())
    /** The ear course's current stage (named on the ear part of the plan). */
    private var earStage = com.id.mimikopi.core.ear.EarCourse.STAGES.first()
    val ui: StateFlow<TodayUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { progress.log.collect { if (_ui.value.loaded) refresh() } }
        viewModelScope.launch { progress.checks.collect { if (_ui.value.loaded) refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            learning.load()
            progress.load()
            val today = LocalDate.now()
            val minutes = dao.get(ProgressStore.MINUTES)?.toIntOrNull()?.takeIf { it in DailyPlan.CHOICES } ?: 30
            val stored = DailyPlan.parse(dao.get(ProgressStore.PLAN))
            val plan = if (stored != null && stored.date == today.toString() && stored.minutes == minutes) stored.blocks
            else build(minutes, today).also { dao.put(SettingEntity(ProgressStore.PLAN, DailyPlan.encode(DailyPlan.Stored(today.toString(), minutes, it)))) }
            publish(plan, minutes, today)
        }
    }

    /** A new time for today: the plan is made again (what is done stays done: it comes from the log). */
    fun choose(minutes: Int) {
        viewModelScope.launch {
            dao.put(SettingEntity(ProgressStore.MINUTES, minutes.toString()))
            refresh()
        }
    }

    private suspend fun build(minutes: Int, today: LocalDate): List<PlanBlock> {
        val checks = progress.checks.value
        val lessons = learning.lessons
        val records = learning.records.value
        fun level(s: SkillId) = SkillBook.status(s, checks, today).level
        val ladder = ProgressRules.nextToLearn(lessons, records) != null || ProgressRules.awaitingConfirmation(lessons, records, today).isNotEmpty()
        return DailyPlan.build(
            PlanInput(
                minutes = minutes,
                warmup = PlanContent.warmup(lessons, records),
                reviewDue = learning.readingDueItems().size,
                ladder = ladder,
                sightLevel = PlanContent.sightLevel(lessons, records, level(SkillId.SIGHT)),
                earLevel = PlanContent.EAR_ROUND,
                chords = level(SkillId.KEYS) >= 2 || level(SkillId.CHORDS) >= 1,
                check = SkillBook.dueCheck(checks, today)?.let { s -> s.nextTest?.let { SkillBook.key(s.skill, it) } },
                weakest = SkillBook.weakest(checks, today),
            ),
        )
    }

    private suspend fun publish(plan: List<PlanBlock>, minutes: Int, today: LocalDate) {
        val earState = com.id.mimikopi.core.ear.EarProgress.parse(dao.get(com.id.mimikopi.core.ear.EarProgress.KEY))
        earStage = com.id.mimikopi.core.ear.EarProgress.currentStage(earState)
        val log = progress.log.value
        val day = PracticeLog.on(log, today)
        val checks = progress.checks.value
        val reviewDue = learning.readingDueItems().size
        val blocks = plan.map { b ->
            val takenToday = b.target?.let { key -> SkillBook.parseKey(key)?.let { l -> checks.any { it.skill == l.skill && it.level == l.level && it.date == today.toString() } } } ?: false
            BlockUi(b, b.kind.title, detail(b), DailyPlan.isDone(b, day, takenToday, reviewDue))
        }
        val (days, mins) = PracticeLog.week(log, today)
        val statuses = SkillId.entries.map { SkillBook.status(it, checks, today) }
        val goal = com.id.mimikopi.core.goal.IdolGoal.SONGS.firstOrNull { com.id.mimikopi.core.goal.IdolGoal.gaps(it, statuses).isNotEmpty() }
        _ui.value = TodayUi(
            loaded = true,
            dateText = thaiDate(today),
            minutesChosen = minutes,
            blocks = blocks,
            minutesDone = day.total / 60,
            minutesPlanned = plan.sumOf { it.minutes },
            strip = PracticeLog.lastDays(log, today, 14),
            weekDays = days,
            weekMinutes = mins,
            lastWeekMinutes = PracticeLog.week(log, today.minusDays(7)).second,
            overall = SkillBook.overall(checks, today),
            weakest = SkillBook.weakest(checks, today)?.let { w -> statuses.first { it.skill == w } },
            untested = statuses.count { it.lastTested == null },
            fixes = com.id.mimikopi.core.progress.Mistakes.summarize(progress.mistakes.value, earState, today).take(2),
            goalSong = goal?.title,
            goalReady = goal?.let { com.id.mimikopi.core.goal.IdolGoal.readiness(it, statuses) } ?: 1.0,
        )
    }

    private fun detail(b: PlanBlock): String = when (b.kind) {
        BlockKind.WARMUP -> b.target?.let { t ->
            learning.lesson(t.substringBefore('/'))?.let { l -> l.drills.getOrNull(t.substringAfter('/').toIntOrNull() ?: 0)?.title ?: l.title }
        } ?: ""
        BlockKind.CHECK -> SkillBook.parseKey(b.target)?.let { "${it.skill.title} ระดับ ${it.level}" } ?: ""
        BlockKind.REVIEW -> "โน้ตและคีย์ที่ถึงกำหนดทวน"
        BlockKind.LADDER -> ProgressRules.nextToLearn(learning.lessons, learning.records.value)?.let { "ขั้น ${it.id} · ${it.title}" } ?: "ยืนยันขั้นที่ผ่านแล้ว"
        BlockKind.SIGHT -> "ระดับ L${b.target}"
        BlockKind.CHORDS -> PlanContent.chords(SkillBook.status(SkillId.CHORDS, progress.checks.value, LocalDate.now()).level).summary()
        BlockKind.EAR -> "ขั้น ${earStage.n} ${earStage.title}"
        BlockKind.SONG -> "เพลงจากคลัง ฝึกแบบชีตเต็มแผ่น"
    } + " · ${b.minutes} นาที"

    /** What a block opens; the chord part first writes its practice for the drill to read. */
    fun open(b: PlanBlock, go: (Open) -> Unit) {
        viewModelScope.launch {
            val target = when (b.kind) {
                BlockKind.WARMUP -> b.target?.let { t -> Open.LessonDrill(t.substringBefore('/'), t.substringAfter('/').toIntOrNull() ?: 0, via = "warmup") }
                BlockKind.CHECK -> SkillBook.parseKey(b.target)?.let { Open.Check(b.target!!, it.run) }
                BlockKind.REVIEW -> Open.Review
                BlockKind.LADDER -> Open.Ladder
                BlockKind.SIGHT -> Open.Sight(b.target?.toIntOrNull() ?: 1)
                BlockKind.CHORDS -> {
                    val level = SkillBook.status(SkillId.CHORDS, progress.checks.value, LocalDate.now()).level
                    dao.put(SettingEntity(ProgressStore.CHORDS_TODAY, com.id.mimikopi.core.exercise.ChordPractice.encode(PlanContent.chords(level))))
                    Open.ChordsToday
                }
                BlockKind.EAR -> Open.Ear()
                BlockKind.SONG -> Open.Songs
            } ?: return@launch
            go(target)
        }
    }

    companion object {
        private val DAYS = listOf("จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์", "อาทิตย์")
        private val MONTHS = listOf("มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม")
        fun thaiDate(d: LocalDate) = "วัน${DAYS[d.dayOfWeek.value - 1]}ที่ ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }
}
