package com.id.mimikopi.desktop.ui.screens

import com.id.mimikopi.core.i18n.tr
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
        LargeTitle(tr("เครื่องเคาะจังหวะ", "Metronome", "メトロノーム"), tr("Space เริ่ม/หยุด · Esc หยุดหรือย้อนกลับ", "Space start/stop · Esc stop or go back", "Space 開始/停止 · Esc 停止または戻る"), onBack = app.nav::back)
        if (!ready) Text("เปิดเสียงไม่ได้: ${audio.error ?: tr("ตรวจอุปกรณ์เสียงที่หน้าตั้งค่า", "Check audio devices in Settings", "設定でオーディオ機器を確認")}", color = Tokens.Palette.Ink)
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
                    PrimaryButton(if (running) tr("■ หยุด", "■ Stop", "■ 停止") else tr("▶ เริ่ม", "▶ Start", "▶ 開始"), vm::toggle, Modifier.weight(2f), enabled = ready)
                    SecondaryButton(tr("แตะจังหวะ", "Tap tempo", "タップテンポ"), vm::tap, Modifier.weight(1f))
                }
            }
        }
        GroupHeader(tr("จังหวะ", "Beats", "拍"))
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(TimeSignature.entries, s.signature, { it.label }, vm::setSignature)
                if (s.signature.fixedSubdivision == null) {
                    Text(tr("แบ่งย่อย", "Subdivision", "分割"), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                    Segmented(listOf(1, 2, 3, 4), s.subdivision, { mapOf(1 to tr("ไม่แบ่ง", "None", "なし"), 2 to tr("เขบ็ด 1 ชั้น", "Quavers", "8分"), 3 to tr("สามพยางค์", "Triplets", "3連"), 4 to tr("เขบ็ด 2 ชั้น", "Semiquavers", "16分"))[it]!! }, vm::setSubdivision)
                }
                Segmented(listOf(true, false), s.accent, { if (it) tr("เน้นจังหวะแรก", "Accent beat 1", "1拍目を強調") else tr("ไม่เน้น", "No accent", "強調なし") }, vm::setAccent)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ความดังคลิก", "Click volume", "クリック音量"), modifier = Modifier.width(100.dp))
                    Slider(s.volume, { vm.setVolume(it, save = false) }, onValueChangeFinished = { vm.setVolume(vm.settings.value.volume) }, modifier = Modifier.weight(1f))
                }
            }
        }
        GroupFooter(tr("ค่าเดียวกับเครื่องเคาะในมือถือ ย้ายไปมาได้ด้วยไฟล์สำรอง", "Same settings as the phone's metronome; move them with a backup file", "スマホのメトロノームと同じ設定。バックアップファイルで移せます"))
        GroupHeader(tr("ตรวจความแม่นของคลิก", "Check click accuracy", "クリックの正確さを確認"))
        Group {
            Column(Modifier.padding(20.dp)) {
                Text(tr("เล่นคลิก 20 วินาทีแล้ววัดระยะห่างแต่ละคลิกบนนาฬิกาเดียวกับที่ใช้ตัดสิน MIDI พร้อมดูว่าเวลาจากการ์ดเสียงนิ่งแค่ไหน", "Plays the click for 20 seconds and measures each gap on the same clock that judges MIDI, and how steady the sound card's time is", "20秒クリックを鳴らし、MIDI判定と同じ時計で各間隔と、サウンドカードの時刻の安定度を測ります"),
                    fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                when (val t = test) {
                    is ClickTestUi.Running -> {
                        Text(tr("กำลังวัด…", "Measuring…", "測定中…"), modifier = Modifier.padding(top = 10.dp))
                        LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    is ClickTestUi.Done -> {
                        val r = t.result
                        Text(tr("${r.bpm} BPM · ${r.report.clicks} คลิก · ${r.sampleRate} Hz", "${r.bpm} BPM · ${r.report.clicks} clicks · ${r.sampleRate} Hz", "${r.bpm} BPM · クリック${r.report.clicks}回 · ${r.sampleRate} Hz"), fontSize = 15.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                        Text("ระยะห่างคลิกคลาดเฉลี่ย ${"%.2f".format(r.report.meanAbsMs)} ms สูงสุด ${"%.2f".format(r.report.maxAbsMs)} ms", fontSize = 15.sp, color = Tokens.Palette.Ink)
                        Text("เวลาจากการ์ดเสียงแกว่งเฉลี่ย ${"%.1f".format(r.clockNoiseMeanMs)} ms สูงสุด ${"%.1f".format(r.clockNoiseMaxMs)} ms · นาฬิกาการ์ดต่างจากเครื่อง ${"%.0f".format(r.driftPpm)} ppm",
                            fontSize = 14.sp, color = Tokens.Palette.InkMuted)
                    }
                    ClickTestUi.Failed -> Text(tr("วัดไม่ได้ (การ์ดเสียงไม่ส่งเวลา หรือหยุดระหว่างวัด)", "Couldn't measure (the sound card gave no time, or it stopped while measuring)", "測定できません（サウンドカードが時刻を出さないか、測定中に停止しました）"), color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 10.dp))
                    ClickTestUi.Idle -> Unit
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(tr("วัด 20 วินาที", "Measure 20 s", "20秒測る"), { vm.runSelfTest() }, enabled = ready && test !is ClickTestUi.Running)
            }
        }
        GroupFooter(tr("คลิกนับเป็นตัวอย่างเสียง จึงห่างเท่ากันเสมอถ้าการ์ดไม่สะดุด ค่าแกว่งของเวลาคือความคลาดเคลื่อนตอนเทียบเสียงกับปุ่มที่กด", "The click is counted in audio samples, so it is always evenly spaced unless the card stutters; the time wobble is the error when matching sound to key presses", "クリックはサンプル単位で数えるので、カードが止まらない限り常に等間隔です。時刻の揺れは音と押鍵を照合するときの誤差です"))
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
