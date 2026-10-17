package com.id.mimikopi.ui.chords

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.id.mimikopi.core.exercise.ChordDrills
import com.id.mimikopi.core.exercise.ChordPractice
import com.id.mimikopi.core.exercise.ChordRun
import com.id.mimikopi.core.theory.Keys
import com.id.mimikopi.core.theory.StageGenerators
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.ui.components.Hairline
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The chord tab's choices (kept between visits) and the recent runs. */
class ChordHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as MimikopiApp).database.settingsDao()
    private val _practice = MutableStateFlow(ChordPractice())
    val practice: StateFlow<ChordPractice> = _practice.asStateFlow()
    private val _runs = MutableStateFlow<List<ChordRun>>(emptyList())
    val runs: StateFlow<List<ChordRun>> = _runs.asStateFlow()

    init {
        viewModelScope.launch { _practice.value = ChordPractice.parse(dao.get(SettingKeys.CHORDS_SETTINGS)) }
    }

    /** Re-reads the recent runs (a drill just finished). */
    fun refresh() {
        viewModelScope.launch { _runs.value = ChordRun.parse(dao.get(SettingKeys.CHORDS_RUNS)) }
    }

    fun change(next: ChordPractice) {
        _practice.value = next
        viewModelScope.launch { dao.put(SettingEntity(SettingKeys.CHORDS_SETTINGS, ChordPractice.encode(next))) }
    }

    /** Saves the choices before the drill opens (it reads them from settings). */
    fun start(open: () -> Unit) {
        viewModelScope.launch {
            dao.put(SettingEntity(SettingKeys.CHORDS_SETTINGS, ChordPractice.encode(_practice.value)))
            open()
        }
    }
}

/**
 * The chord tab: what to practise (chords by name, by roman numeral in a key, or changes through a progression
 * against the clock), then start. Runs use the lesson chord drills; nothing here unlocks or counts toward steps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordHomeScreen(onStart: () -> Unit, onOpenMidi: () -> Unit, onOpenSettings: () -> Unit, vm: ChordHomeViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val p by vm.practice.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TabHeader("คอร์ด", "ฝึกกดคอร์ดให้คล่อง: เห็นชื่อแล้วกดได้ทันที และเปลี่ยนคอร์ดได้ทันจังหวะ", onOpenMidi, onOpenSettings)
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf(ChordPractice.Mode.NAME to "ชื่อคอร์ด", ChordPractice.Mode.NUMERAL to "ตัวเลขโรมัน", ChordPractice.Mode.CHANGE to "เปลี่ยนคอร์ด").forEach { (mode, label) ->
                        val selected = p.mode == mode
                        Column(Modifier.clickable { vm.change(p.copy(mode = mode)) }.padding(vertical = 6.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                            Spacer(Modifier.height(4.dp))
                            Spacer(Modifier.width(28.dp).height(2.dp).background(if (selected) Tokens.Palette.Ink else Color.Transparent))
                        }
                    }
                }
                Text(
                    when (p.mode) {
                        ChordPractice.Mode.NAME -> "แอพบอกชื่อคอร์ด (เช่น Am, G7) ให้กดโน้ตของคอร์ดนั้นพร้อมกัน กลับเสียงหรืออ็อกเทฟไหนก็ได้ ถ้าผิดแอพจะชี้ตำแหน่งให้แล้วถามซ้ำท้ายรอบ"
                        ChordPractice.Mode.NUMERAL -> "แอพบอกตัวเลขโรมันในคีย์ (เช่น vi ในคีย์ G) ให้หาว่าเป็นคอร์ดอะไรแล้วกด ช่วยให้เปลี่ยนคีย์เพลงได้คล่อง"
                        ChordPractice.Mode.CHANGE -> "กดคอร์ดตามโปรเกรสชันวนไปเรื่อยๆ จนหมดเวลา แอพนับครั้งที่กดถูก ค้างโน้ตที่เหมือนกันระหว่างคอร์ดไว้ได้ แล้วขยับเฉพาะตัวที่ต่าง"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 14.dp),
                )

                when (p.mode) {
                    ChordPractice.Mode.NAME -> {
                        Label("ชนิดคอร์ด (เลือกได้หลายแบบ)")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChordPractice.GROUPS.forEach { g ->
                                FilterChip(selected = g.id in p.groups, onClick = { vm.change(p.copy(groups = p.groups.toggle(g.id).ifEmpty { listOf(g.id) })) }, label = { Text(g.title) })
                            }
                        }
                        Label("คีย์ (รากคอร์ดมาจากโน้ตในคีย์ที่เลือก)")
                        KeyChips(p.keys, multi = true) { vm.change(p.copy(keys = it)) }
                        Label("จำนวนคอร์ด")
                        ChoiceChips(ChordPractice.ITEMS, p.items, { "$it" }) { vm.change(p.copy(items = it)) }
                    }
                    ChordPractice.Mode.NUMERAL -> {
                        Label("ชุดคอร์ด (เลือกได้หลายชุด)")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChordPractice.NUMERAL_SETS.forEach { g ->
                                FilterChip(selected = g.id in p.numeralSets, onClick = { vm.change(p.copy(numeralSets = p.numeralSets.toggle(g.id).ifEmpty { listOf(g.id) })) }, label = { Text(g.title) })
                            }
                        }
                        Label("คีย์")
                        KeyChips(p.keys, multi = true) { vm.change(p.copy(keys = it)) }
                        Label("จำนวนคอร์ด")
                        ChoiceChips(ChordPractice.ITEMS, p.items, { "$it" }) { vm.change(p.copy(items = it)) }
                    }
                    ChordPractice.Mode.CHANGE -> {
                        Label("โปรเกรสชัน")
                        val key = p.keys.firstOrNull() ?: 0
                        StageGenerators.PROGRESSIONS.forEach { prog ->
                            val selected = p.progression == prog.name
                            Column(Modifier.fillMaxWidth().clickable { vm.change(p.copy(progression = prog.name)) }.padding(vertical = 10.dp)) {
                                Text(prog.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                                val chords = ChordDrills.progressionDegrees(prog.name).joinToString("  ") { StageGenerators.degreeChord(it, key).display() }
                                Text("${prog.degrees.joinToString(" ")}  ·  $chords", fontFamily = Tokens.Fonts.mono, fontSize = 13.sp,
                                    color = if (selected) Tokens.Palette.Ink else Tokens.Palette.Dim)
                            }
                            Hairline()
                        }
                        Label("คีย์")
                        KeyChips(listOf(key), multi = false) { vm.change(p.copy(keys = it)) }
                        Label("เวลา")
                        ChoiceChips(ChordPractice.SECONDS, p.seconds, { "$it วินาที" }) { vm.change(p.copy(seconds = it)) }
                    }
                }

                Text(p.summary(), style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 24.dp))
                Button(onClick = { vm.start(onStart) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) { Text("เริ่มฝึก") }

                if (runs.isNotEmpty()) {
                    Label("ผลล่าสุด")
                    runs.take(10).forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(r.day.drop(5), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = Tokens.Palette.Dim, modifier = Modifier.width(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.result, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.Ink)
                                Text(r.summary, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                            }
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

/** Keys as chips: several at once, or one ([multi] off). At least one stays chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyChips(selected: List<Int>, multi: Boolean, onChange: (List<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordPractice.KEYS.forEach { k ->
            FilterChip(
                selected = k in selected,
                onClick = { onChange(if (multi) selected.toggle(k).ifEmpty { listOf(k) } else listOf(k)) },
                label = { Text(Keys.name(k)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onChange: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onChange(o) }, label = { Text(label(o)) }) }
    }
}

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item
