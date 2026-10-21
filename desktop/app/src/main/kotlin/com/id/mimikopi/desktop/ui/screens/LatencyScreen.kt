package com.id.mimikopi.desktop.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.session.LatencySession
import com.id.mimikopi.desktop.session.LatencyUi
import com.id.mimikopi.desktop.ui.LocalApp
import com.id.mimikopi.desktop.ui.components.LargeTitle
import com.id.mimikopi.desktop.ui.components.Page
import com.id.mimikopi.desktop.ui.components.PianoKeys
import com.id.mimikopi.desktop.ui.components.PrimaryButton
import com.id.mimikopi.desktop.ui.components.SecondaryButton
import com.id.mimikopi.desktop.ui.theme.Tokens

/** Measures the delay of the input used to tap along with the program's clicks, on the current output path. */
@Composable
fun LatencyScreen() {
    val app = LocalApp.current
    val s = app.services
    val scope = rememberCoroutineScope()
    val session = remember { LatencySession(s, scope) }
    val ui by session.ui.collectAsState()
    val pressed by s.hub.pressed.collectAsState()
    val ports by s.midi.ports.collectAsState()
    val lowC by s.keysLowC.collectAsState()
    DisposableEffect(session) {
        app.keys.onEscape = {
            val running = session.ui.value is LatencyUi.CountIn || session.ui.value is LatencyUi.Tapping
            if (running) session.cancel()
            running
        }
        onDispose {
            app.keys.onEscape = null
            session.close()
        }
    }
    Page {
        LargeTitle("วัดความหน่วง", "เส้นทางเสียง ${s.routeKey()} · กดคีย์ใดก็ได้ให้ตรงกับคลิก 16 ครั้งหลังนับเข้า 4", onBack = app.nav::back)
        Text("ใช้สิ่งที่จะใช้ฝึกจริง: เปียโน MIDI หรือแป้นพิมพ์คอมพิวเตอร์ หรือคลิกคีย์บนจอ แต่ละอย่างเก็บผลแยกกัน · ฟังคลิกจากลำโพงหรือหูฟังที่ใช้จริง",
            color = Tokens.Palette.InkMuted)
        when (val u = ui) {
            LatencyUi.Idle -> PrimaryButton("เริ่มวัด", onClick = session::start)
            is LatencyUi.CountIn -> Text("นับเข้า ${u.beat}/4", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            is LatencyUi.Tapping -> {
                Text("คลิก ${u.clicks}/16 · กดแล้ว ${u.taps}", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                SecondaryButton("ยกเลิก (Esc)", onClick = session::cancel)
            }
            is LatencyUi.Done -> {
                Text("${sourceName(u.source.name)}: ${"%.0f".format(u.result.offsetMs)} ms", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("ช่วงกลาง ±${"%.0f".format(u.result.spreadMs / 2)} ms จาก ${u.result.taps} คลิก · บันทึกแล้ว ใช้หักเวลาเมื่อตัดสินจังหวะครั้งเดียวต่อเส้นทาง", color = Tokens.Palette.InkMuted)
                if (u.result.spreadMs > 60) Text("ผลกระจายมาก ลองวัดใหม่ตอนตั้งใจฟังคลิก", color = Tokens.Palette.Ink)
                PrimaryButton("วัดอีกครั้ง", onClick = session::start)
            }
            is LatencyUi.Failed -> {
                Text(u.reason, fontSize = 18.sp)
                PrimaryButton("ลองใหม่", onClick = session::start)
            }
        }
        if (ports.none { it.open }) PianoKeys(48..71, pressed, { n, down -> s.hub.dispatch(keyEvent(n, down)) }, computerKeysLowC = lowC, height = 120.dp)
    }
}
