package com.id.mimikopi.ui.ear

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.core.ear.CadenceMode
import com.id.mimikopi.core.ear.EarScale
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editor's choices for a custom level. [tonic] null = a different key every run. */
data class ToneDraft(
    val questions: Int = 20,
    val tonic: Int? = 0,
    val scale: EarScale = EarScale.MAJOR,
    val octaves: Int = 1,
    val chromatic: Boolean = false,
    val degrees: Set<Int> = FunctionalEar.MAJOR.toSet(),
    val cadence: CadenceMode = CadenceMode.ALWAYS,
) {
    /** Places offered as toggles: the scale's seven, or all twelve with chromatics. */
    val choices: List<Int> get() = if (chromatic) (0..11).toList() else FunctionalEar.scaleTones(scale)
    val enabled: List<Int> get() = degrees.filter { it in choices }.sorted()
    val valid: Boolean get() = enabled.size >= 2
}

class ToneEditViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val store = ToneStore(app as MimikopiApp)
    /** The level being edited, or null for a new one. */
    val editing: String? = args.get<String>("id")
    private val _draft = MutableStateFlow(ToneDraft())
    val draft: StateFlow<ToneDraft> = _draft.asStateFlow()

    init {
        if (editing != null) viewModelScope.launch {
            store.customLevels().firstOrNull { it.id == editing }?.let { l ->
                _draft.value = ToneDraft(l.questions, l.tonic, l.scale, l.octaves, l.chromatic, l.degrees.toSet(), l.cadence)
            }
        }
    }

    fun change(f: (ToneDraft) -> ToneDraft) = _draft.update(f)

    /** A new scale brings its seven places (the user then narrows them). */
    fun setScale(scale: EarScale) = _draft.update { d ->
        if (d.scale == scale) d else d.copy(scale = scale, degrees = if (d.chromatic) d.degrees else FunctionalEar.scaleTones(scale).toSet())
    }

    fun toggle(place: Int) = _draft.update { d -> d.copy(degrees = if (place in d.degrees) d.degrees - place else d.degrees + place) }

    fun save(onDone: () -> Unit) {
        val d = _draft.value
        if (!d.valid) return
        viewModelScope.launch {
            val id = editing ?: "custom-${System.currentTimeMillis()}"
            val level = FunctionalEar.custom(id, d.tonic, d.scale, d.octaves, d.chromatic, d.enabled, d.questions, d.cadence)
            val all = store.customLevels()
            store.saveCustom(if (all.any { it.id == id }) all.map { if (it.id == id) level else it } else all + level)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = editing ?: return
        viewModelScope.launch {
            store.saveCustom(store.customLevels().filterNot { it.id == id })
            val stats = store.stats()
            if (id in stats) store.saveStats(stats - id)
            onDone()
        }
    }
}

/** A custom level: number of questions, key, scale, range, chromatic notes, which places are asked, cadence. */
@Composable
fun ToneEditScreen(onBack: () -> Unit, vm: ToneEditViewModel = viewModel()) {
    val d by vm.draft.collectAsStateWithLifecycle()
    ScreenScaffold(title = if (vm.editing == null) "ระดับใหม่" else "แก้ระดับ", onBack = onBack) {
        PageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("จำนวนข้อ", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${d.questions}", fontFamily = Tokens.Fonts.mono, fontSize = 18.sp)
            }
            Slider(value = d.questions.toFloat(), onValueChange = { v -> vm.change { it.copy(questions = (v / 5).toInt() * 5) } },
                valueRange = 5f..50f, steps = 8)

            Label("คีย์")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = d.tonic == null, onClick = { vm.change { it.copy(tonic = null) } }, label = { Text("สุ่มทุกรอบ") })
                (0..11).forEach { t -> FilterChip(selected = d.tonic == t, onClick = { vm.change { it.copy(tonic = t) } }, label = { Text(FunctionalEar.letter(t, t)) }) }
            }
            Label("บันไดเสียง")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = d.scale == EarScale.MAJOR, onClick = { vm.setScale(EarScale.MAJOR) }, label = { Text("เมเจอร์") })
                FilterChip(selected = d.scale == EarScale.MINOR, onClick = { vm.setScale(EarScale.MINOR) }, label = { Text("ไมเนอร์") })
            }
            Label("ช่วงเสียง")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "1 อ็อกเทฟ", 2 to "2 อ็อกเทฟ", 3 to "3 อ็อกเทฟ").forEach { (n, label) ->
                    FilterChip(selected = d.octaves == n, onClick = { vm.change { it.copy(octaves = n) } }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("โน้ตครึ่งเสียง", style = MaterialTheme.typography.titleMedium)
                    Text("ถามโน้ตที่อยู่นอกบันไดเสียงด้วย (เช่น ฟา♯ ที♭)", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                }
                Switch(checked = d.chromatic, onCheckedChange = { on -> vm.change { it.copy(chromatic = on) } })
            }
            Label("โน้ตที่ถาม")
            val rows = if (d.choices.size > 7) d.choices.chunked(6) else listOf(d.choices)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            val on = p in d.degrees
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                Modifier.weight(1f).heightIn(min = 60.dp)
                                    .then(if (on) Modifier.background(Tokens.Palette.Ink, shape) else Modifier.border(1.dp, Tokens.Palette.Line, shape))
                                    .clickable { vm.toggle(p) }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(FunctionalEar.degree(p), fontFamily = Tokens.Fonts.mono, fontSize = 15.sp, color = if (on) Tokens.Palette.OnInk else Tokens.Palette.InkMuted)
                                Text(FunctionalEar.solfege(p), style = MaterialTheme.typography.labelMedium, color = if (on) Tokens.Palette.OnInk else Tokens.Palette.InkMuted)
                            }
                        }
                    }
                }
            }
            if (!d.valid) Text("เลือกอย่างน้อย 2 ตัว", style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 6.dp))
            Label("คอร์ดนำ")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = d.cadence == CadenceMode.ALWAYS, onClick = { vm.change { it.copy(cadence = CadenceMode.ALWAYS) } }, label = { Text("ทุกข้อ (มือใหม่)") })
                FilterChip(selected = d.cadence == CadenceMode.FIRST, onClick = { vm.change { it.copy(cadence = CadenceMode.FIRST) } }, label = { Text("ข้อแรกเท่านั้น") })
            }
        }
        Button(onClick = { vm.save(onBack) }, enabled = d.valid, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("บันทึก") }
        if (vm.editing != null) TextButton(onClick = { vm.delete(onBack) }, modifier = Modifier.fillMaxWidth()) { Text("ลบระดับนี้", color = Tokens.Palette.InkMuted) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}
