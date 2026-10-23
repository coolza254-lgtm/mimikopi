package com.id.mimikopi.ui.skills

import com.id.mimikopi.core.i18n.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.progress.CheckRecord
import com.id.mimikopi.core.progress.SkillBook
import com.id.mimikopi.core.progress.SkillId
import com.id.mimikopi.core.progress.SkillLevel
import com.id.mimikopi.core.progress.SkillStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SkillsUi(
    val loaded: Boolean = false,
    val statuses: List<SkillStatus> = emptyList(),
    val overall: String? = null,
    /** Every test taken, oldest first. */
    val records: List<CheckRecord> = emptyList(),
)

/** The skill levels: what each test said, nothing added (levels come only from [SkillBook] tests). */
class SkillsViewModel(app: Application) : AndroidViewModel(app) {
    private val progress = (app as MimikopiApp).progress
    private val _ui = MutableStateFlow(SkillsUi())
    val ui: StateFlow<SkillsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { progress.checks.collect { if (_ui.value.loaded) publish() } }
    }

    fun refresh() {
        viewModelScope.launch {
            progress.load()
            publish()
        }
    }

    private fun publish() {
        val today = LocalDate.now()
        val checks = progress.checks.value
        _ui.value = SkillsUi(
            loaded = true,
            statuses = SkillId.entries.map { SkillBook.status(it, checks, today) },
            overall = SkillBook.overall(checks, today),
            records = checks,
        )
    }

    companion object {
        /** "วันนี้", "เมื่อวาน", "5 วันก่อน". */
        fun ago(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (val d = ChronoUnit.DAYS.between(date, today)) {
            0L -> tr("วันนี้", "Today", "今日")
            1L -> tr("เมื่อวาน", "Yesterday", "昨日")
            else -> tr("$d วันก่อน", "$d days ago", "${d}日前")
        }

        /** The numbers a test measured, in one short line ("ถูก 92% · 1.8 วินาที"). */
        fun metricsText(r: CheckRecord): String = listOfNotNull(
            r.metrics.accuracy?.let { tr("ถูก ${SkillLevel.pct(it)}", "Right ${SkillLevel.pct(it)}", "正解 ${SkillLevel.pct(it)}") },
            r.metrics.timing?.let { tr("ตรงจังหวะ ${SkillLevel.pct(it)}", "On time ${SkillLevel.pct(it)}", "リズム ${SkillLevel.pct(it)}") },
            r.metrics.medianSec?.let { SkillLevel.sec(it) },
            r.metrics.count?.let { tr("เปลี่ยนคอร์ด $it ครั้ง", "$it chord changes", "コードチェンジ${it}回") },
        ).joinToString(" · ")
    }
}
