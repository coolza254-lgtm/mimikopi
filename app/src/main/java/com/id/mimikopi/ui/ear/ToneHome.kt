package com.id.mimikopi.ui.ear

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.id.mimikopi.core.ear.CadenceMode
import com.id.mimikopi.core.ear.FunctionalEar
import com.id.mimikopi.core.ear.ToneLevel
import com.id.mimikopi.core.ear.ToneStats
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.ui.components.Hairline
import com.id.mimikopi.ui.components.MeterBar
import com.id.mimikopi.ui.components.TabHeader
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings rows of the ear trainer (backups carry them). */
object ToneKeys {
    const val LEVELS = "ear.tone.levels"
    const val STATS = "ear.tone.stats"
}

/** The user's own levels and every level's recent runs, read from and written to the settings rows. */
class ToneStore(private val services: MimikopiApp) {
    suspend fun customLevels(): List<ToneLevel> = FunctionalEar.parseLevels(services.database.settingsDao().get(ToneKeys.LEVELS))
    suspend fun saveCustom(levels: List<ToneLevel>) = services.database.settingsDao().put(SettingEntity(ToneKeys.LEVELS, FunctionalEar.levelsJson(levels)))
    suspend fun stats(): Map<String, ToneStats> = FunctionalEar.parseStats(services.database.settingsDao().get(ToneKeys.STATS))
    suspend fun saveStats(stats: Map<String, ToneStats>) = services.database.settingsDao().put(SettingEntity(ToneKeys.STATS, FunctionalEar.statsJson(stats)))
    suspend fun level(id: String): ToneLevel? = (FunctionalEar.intro + FunctionalEar.basic).firstOrNull { it.id == id } ?: customLevels().firstOrNull { it.id == id }
}

class ToneHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ToneStore(app as MimikopiApp)
    private val _custom = MutableStateFlow<List<ToneLevel>>(emptyList())
    val custom: StateFlow<List<ToneLevel>> = _custom.asStateFlow()
    private val _stats = MutableStateFlow<Map<String, ToneStats>>(emptyMap())
    val stats: StateFlow<Map<String, ToneStats>> = _stats.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _custom.value = store.customLevels()
            _stats.value = store.stats()
        }
    }
}

/** "1 อ็อกเทฟ · I–IV–V–I · 20 ข้อ" */
fun ToneLevel.summary(): String = listOf(
    "$octaves อ็อกเทฟ",
    if (scale == com.id.mimikopi.core.ear.EarScale.MAJOR) "I–IV–V–I" else "i–iv–V–i",
    "$questions ข้อ",
).joinToString(" · ") + if (cadence == CadenceMode.FIRST) " · คอร์ดนำข้อแรก" else ""

/**
 * The ear tab (M19): functional ear training. The app plays a cadence to set the key, then one note; the answer is
 * its place in the key. Three lists: getting started (small sets of notes), the basic levels, and the user's own.
 */
@Composable
fun ToneHomeScreen(
    onOpen: (String) -> Unit,
    onEdit: (String?) -> Unit,
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    vm: ToneHomeViewModel = viewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val custom by vm.custom.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var howTo by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TabHeader("ฝึกฟัง", "ฟังคอร์ดนำให้รู้คีย์ แล้วบอกว่าโน้ตที่ได้ยินคือตัวไหน", onOpenMidi, onOpenSettings, onBack)
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf("เริ่มต้น", "พื้นฐาน", "สร้างเอง").forEachIndexed { i, label ->
                        Column(Modifier.clickable { tab = i }.padding(vertical = 6.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (tab == i) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
                            Spacer(Modifier.height(4.dp))
                            Spacer(Modifier.width(28.dp).height(2.dp).background(if (tab == i) Tokens.Palette.Ink else androidx.compose.ui.graphics.Color.Transparent))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    0 -> {
                        Text(if (howTo) "ซ่อนวิธีฝึก" else "วิธีฝึก", style = MaterialTheme.typography.labelLarge, color = Tokens.Palette.InkMuted,
                            modifier = Modifier.clickable { howTo = !howTo }.padding(vertical = 6.dp))
                        if (howTo) Text(
                            "แอพเล่นคอร์ด 4 ตัว (I–IV–V–I) ให้หูจับคีย์ได้ก่อน แล้วเล่นโน้ตหนึ่งตัว ตอบว่าเป็นตัวไหนของคีย์ (ド レ ミ …) " +
                                "โดยกดโน้ตนั้นบนคีย์บอร์ด (อ็อกเทฟไหนก็ได้) หรือแตะปุ่มบนจอ พอตอบถูก แอพจะเล่นโน้ตไล่กลับไปหา ド " +
                                "แต่ละตัวมี \"ความรู้สึก\" ต่างกันเมื่ออยู่ในคีย์ ฝึกจนจำความรู้สึกนั้นได้ ก็จะแกะทำนองเพลงได้",
                            style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 12.dp),
                        )
                        FunctionalEar.intro.forEachIndexed { i, l -> LevelRow("%02d".format(i + 1), l.title, "${l.questions} ข้อ", stats[l.id], showMeter = false) { onOpen(l.id) } }
                    }
                    1 -> FunctionalEar.basic.forEach { l -> LevelRow(null, l.title, l.summary(), stats[l.id], showMeter = true) { onOpen(l.id) } }
                    else -> {
                        if (custom.isEmpty()) Text("สร้างระดับของตัวเอง: เลือกคีย์ บันไดเสียง ช่วงเสียง และโน้ตที่จะให้ถาม", style = MaterialTheme.typography.bodyMedium,
                            color = Tokens.Palette.InkMuted, modifier = Modifier.padding(bottom = 12.dp))
                        custom.forEach { l -> LevelRow(null, l.title, l.summary(), stats[l.id], showMeter = true, onEdit = { onEdit(l.id) }) { onOpen(l.id) } }
                        OutlinedButton(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp)) { Text("+  สร้างระดับใหม่") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelRow(number: String?, title: String, subtitle: String, stats: ToneStats?, showMeter: Boolean, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    val last = stats?.last
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (number != null) Text(number, fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = Tokens.Palette.Dim, modifier = Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = Tokens.Palette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text("แก้", color = Tokens.Palette.InkMuted) }
            Column(horizontalAlignment = Alignment.End) {
                Text(last?.let { "${(it.accuracy * 100).toInt()}%" } ?: "—", fontFamily = Tokens.Fonts.mono, fontSize = 15.sp,
                    color = if (last != null) Tokens.Palette.Ink else Tokens.Palette.Dim)
                if (stats != null && stats.runs.isNotEmpty()) Text("${stats.runs.size} รอบ", style = MaterialTheme.typography.labelSmall, color = Tokens.Palette.InkMuted)
            }
        }
        if (showMeter && last != null) MeterBar(last.accuracy.toFloat(), Modifier.fillMaxWidth().padding(top = 10.dp))
    }
    Hairline()
}
