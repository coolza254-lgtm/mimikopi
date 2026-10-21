package com.id.mimikopi.desktop.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.core.metronome.ClickLevel
import com.id.mimikopi.core.metronome.MetronomeSettings
import com.id.mimikopi.core.metronome.TimeSignature
import com.id.mimikopi.desktop.session.ClickTestUi
import com.id.mimikopi.desktop.session.MetronomeSession
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.components.Group
import com.id.mimikopi.desktop.ui.components.GroupFooter
import com.id.mimikopi.desktop.ui.components.GroupHeader
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.components.Segmented
import com.id.mimikopi.desktop.ui.theme.Tokens

/** The metronome: tempo, meter, subdivision and accent (the same settings row as the phone), tap tempo and a timing self-test. */
@Composable
fun MetronomeScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val vm = remember { MetronomeSession(app.services, scope) }
    val s by vm.settings.collectAsState()
    val running by vm.running.collectAsState()
    val ready by vm.ready.collectAsState()
    val test by vm.test.collectAsState()
    val audio by app.services.piano.status.collectAsState()
    DisposableEffect(vm) {
        vm.enter()
        app.keys.onSpace = vm::toggle
        app.keys.onEscape = { if (vm.running.value) { vm.setRunning(false); true } else false }
        onDispose {
            app.keys.onSpace = null
            app.keys.onEscape = null
            vm.leave()
        }
    }
    Page {
        LargeTitle("เครื่องเคาะจังหวะ", "Space เริ่ม/หยุด · Esc หยุดหรือย้อนกลับ", onBack = app.nav::back)
        if (!ready) Text("เปิดเสียงไม่ได้: ${audio.error ?: "ตรวจอุปกรณ์เสียงที่หน้าตั้งค่า"}", color = Tokens.Palette.Ink)
        Group {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BeatDots(s, running, vm::tickCount)
                Text("${s.bpm}", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text("BPM · ${s.signature.label}", fontSize = 15.sp, color = Tokens.Palette.InkMuted)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-5, -1, 1, 5).forEach { d -> SecondaryButton(if (d > 0) "+$d" else "$d", { vm.nudge(d) }, Modifier.weight(1f)) }
                }
                Slider(s.bpm.toFloat(), { vm.setBpm(it.toInt()) }, valueRange = MetronomeSettings.MIN_BPM.toFloat()..MetronomeSettings.MAX_BPM.toFloat())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (running) "■ หยุด" else "▶ เริ่ม", vm::toggle, Modifier.weight(2f), enabled = ready)
                    SecondaryButton("แตะจังหวะ", vm::tap, Modifier.weight(1f))
                }
            }
        }
        GroupHeader("จังหวะ")
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(TimeSignature.entries, s.signature, { it.label }, vm::setSignature)
                if (s.signature.fixedSubdivision == null) {
                    Text("แบ่งย่อย", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                    Segmented(listOf(1, 2, 3, 4), s.subdivision, { mapOf(1 to "ไม่แบ่ง", 2 to "เขบ็ด 1 ชั้น", 3 to "สามพยางค์", 4 to "เขบ็ด 2 ชั้น")[it]!! }, vm::setSubdivision)
                }
                Segmented(listOf(true, false), s.accent, { if (it) "เน้นจังหวะแรก" else "ไม่เน้น" }, vm::setAccent)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ความดังคลิก", modifier = Modifier.width(100.dp))
                    Slider(s.volume, { vm.setVolume(it, save = false) }, onValueChangeFinished = { vm.setVolume(vm.settings.value.volume) }, modifier = Modifier.weight(1f))
                }
            }
        }
        GroupFooter("ค่าเดียวกับเครื่องเคาะในมือถือ ย้ายไปมาได้ด้วยไฟล์สำรอง")
        GroupHeader("ตรวจความแม่นของคลิก")
        Group {
            Column(Modifier.padding(20.dp)) {
                Text("เล่นคลิก 20 วินาทีแล้ววัดระยะห่างแต่ละคลิกบนนาฬิกาเดียวกับที่ใช้ตัดสิน MIDI พร้อมดูว่าเวลาจากการ์ดเสียงนิ่งแค่ไหน",
                    fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                when (val t = test) {
                    is ClickTestUi.Running -> {
                        Text("กำลังวัด…", modifier = Modifier.padding(top = 10.dp))
                        LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    is ClickTestUi.Done -> {
                        val r = t.result
                        Text("${r.bpm} BPM · ${r.report.clicks} คลิก · ${r.sampleRate} Hz", fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                        Text("ระยะห่างคลิกคลาดเฉลี่ย ${"%.2f".format(r.report.meanAbsMs)} ms สูงสุด ${"%.2f".format(r.report.maxAbsMs)} ms", fontSize = 15.sp, color = Tokens.Palette.Ink)
                        Text("เวลาจากการ์ดเสียงแกว่งเฉลี่ย ${"%.1f".format(r.clockNoiseMeanMs)} ms สูงสุด ${"%.1f".format(r.clockNoiseMaxMs)} ms · นาฬิกาการ์ดต่างจากเครื่อง ${"%.0f".format(r.driftPpm)} ppm",
                            fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                    }
                    ClickTestUi.Failed -> Text("วัดไม่ได้ (การ์ดเสียงไม่ส่งเวลา หรือหยุดระหว่างวัด)", color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    ClickTestUi.Idle -> Unit
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton("วัด 20 วินาที", { vm.runSelfTest() }, enabled = ready && test !is ClickTestUi.Running)
            }
        }
        GroupFooter("คลิกนับเป็นตัวอย่างเสียง จึงห่างเท่ากันเสมอถ้าการ์ดไม่สะดุด ค่าแกว่งของเวลาคือความคลาดเคลื่อนตอนเทียบเสียงกับปุ่มที่กด")
    }
}

/** One dot per tick of the bar; the current tick lights up, following the engine's tick counter each frame. */
@Composable
fun BeatDots(s: MetronomeSettings, running: Boolean, tickCount: () -> Long, dotSize: Int = 18) {
    val pattern = remember(s) { s.barPattern() }
    var count by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        while (running) {
            withFrameNanos { }
            count = tickCount()
        }
        count = 0
    }
    val current = if (running && count > 0) ((count - 1) % pattern.size).toInt() else -1
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        pattern.forEachIndexed { i, level ->
            val on = i == current
            val size = if (level == ClickLevel.SUB) dotSize * 0.6f else dotSize.toFloat()
            Box(Modifier.size(size.dp).background(when {
                on && level == ClickLevel.STRONG -> Tokens.Palette.Ink
                on -> Tokens.Palette.InkMuted
                else -> Tokens.Palette.Line
            }, CircleShape))
        }
    }
}
