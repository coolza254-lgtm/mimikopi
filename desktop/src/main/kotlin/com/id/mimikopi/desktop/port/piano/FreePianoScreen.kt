package com.id.mimikopi.desktop.port.piano

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/piano/FreePianoScreen.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.id.mimikopi.desktop.port.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.port.Lifecycle
import com.id.mimikopi.desktop.port.LifecycleEventEffect
import com.id.mimikopi.desktop.port.collectAsStateWithLifecycle
import com.id.mimikopi.desktop.port.R
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.desktop.port.components.Group
import com.id.mimikopi.desktop.port.components.GroupDivider
import com.id.mimikopi.desktop.port.components.GroupFooter
import com.id.mimikopi.desktop.port.components.GroupHeader
import com.id.mimikopi.desktop.port.components.GroupRow
import com.id.mimikopi.desktop.port.components.LargeTitle
import com.id.mimikopi.desktop.port.components.MidiPill
import com.id.mimikopi.desktop.port.components.PageMargin
import com.id.mimikopi.desktop.port.components.PageMaxWidth
import com.id.mimikopi.desktop.port.components.Segmented
import com.id.mimikopi.desktop.port.components.TouchKeyboard
import com.id.mimikopi.desktop.port.components.midiConnected
import com.id.mimikopi.desktop.port.components.paper
import com.id.mimikopi.desktop.ui.Tokens
import kotlin.math.roundToInt

/**
 * The free piano: the keys, then three short groups of settings (sound, key weight, transpose). Nothing to practise
 * or score; the app is just the piano's sound. [soundSetup] shows where the app's piano sound is missing (the phone
 * installs it here; [onReady] is called once it is).
 */
@Composable
fun FreePianoScreen(
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    soundSetup: @Composable (onReady: () -> Unit) -> Unit = {},
    vm: FreePianoViewModel,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.enter() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.leave() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val connected = midiConnected()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
            LargeTitle("เปียโน", if (connected) "เล่นจากเปียโน MIDI เสียงออกจากแอพ" else "แตะคีย์บนจอ หรือต่อเปียโน MIDI") {
                MidiPill(onOpenMidi)
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "ตั้งค่า", tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.padding(horizontal = PageMargin)) {
                if (ui.noSound) {
                    Spacer(Modifier.height(12.dp))
                    soundSetup(vm::soundInstalled)
                }
                Spacer(Modifier.height(16.dp))
                Keys(ui.lowNote, ui.transpose, vm::shiftOctave)
            }
            Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = PageMargin)) {
                GroupHeader("เสียง")
                Group {
                    val s = ui.sound
                    SliderRow("ความดัง", s.volume, "${if (s.db > 0) "+" else ""}${s.db.roundToInt()} dB", { vm.setSound(s.copy(volume = it), false) }) { vm.setSound(ui.sound, true) }
                    if (vm.supportsTone) {
                        GroupDivider()
                        SliderRow("ความก้อง", s.reverb, "${(s.reverb * 100).roundToInt()}%", { vm.setSound(s.copy(reverb = it), false) }) { vm.setSound(ui.sound, true) }
                        GroupDivider()
                        SliderRow("ความนุ่ม", s.warmth, "${(s.warmth * 100).roundToInt()}%", { vm.setSound(s.copy(warmth = it), false) }) { vm.setSound(ui.sound, true) }
                    }
                }
                GroupFooter(
                    (if (vm.supportsTone) "ความก้อง: เสียงห้อง · ความนุ่ม: ลดเสียงแหลม ฟังสบายในหูฟัง · " else "") +
                        "ใช้กับเสียงของแอพทุกหน้า",
                )

                GroupHeader("น้ำหนักคีย์")
                Group {
                    val v = ui.velocity
                    GroupRow(
                        "น้ำหนักคีย์มีผลกับเสียง",
                        subtitle = if (v.on) "กดเบาเสียงเบา กดแรงเสียงดัง" else "ทุกโน้ตดังเท่ากัน",
                        trailing = { Switch(checked = v.on, onCheckedChange = { on -> vm.setVelocity(if (on) VelocityMode(true, v.fixed) else VelocityMode(false, v.fixed)) }) },
                    )
                    if (!v.on) {
                        GroupDivider()
                        Box(Modifier.padding(16.dp)) {
                            Segmented(VelocityMode.LEVELS, v.fixed.takeIf { it in VelocityMode.LEVELS } ?: VelocityMode.MEDIUM, { level(it) }, { vm.setVelocity(VelocityMode(false, it)) })
                        }
                    }
                }
                GroupFooter("ใช้กับทุกหน้า ไม่มีผลกับการตัดสินถูกผิด")

                GroupHeader("ทรานสโพส")
                Group {
                    GroupRow(
                        "เปลี่ยนระดับเสียง",
                        subtitle = when {
                            ui.transpose > 0 -> "สูงขึ้น ${ui.transpose} ครึ่งเสียง"
                            ui.transpose < 0 -> "ต่ำลง ${-ui.transpose} ครึ่งเสียง"
                            else -> "เสียงตรงกับคีย์ที่กด"
                        },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoundButton("−", onGroup = true) { vm.transpose(-1) }
                                Text("${if (ui.transpose > 0) "+" else ""}${ui.transpose}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink,
                                    modifier = Modifier.widthIn(min = 32.dp))
                                RoundButton("+", onGroup = true) { vm.transpose(1) }
                            }
                        },
                    )
                }
                GroupFooter("คีย์ที่กดยังเป็นตัวเดิม เปลี่ยนแค่เสียงที่ได้ยิน ใช้เฉพาะหน้านี้ ออกจากหน้านี้แล้วกลับเป็น 0")

                GroupFooter(
                    "หน้านี้ใช้เสียงจากแอพเสมอ ปิดหรือลดเสียงลำโพงของเปียโน แล้วเสียบหูฟังที่เครื่องนี้ · " +
                        "เล่นอิสระวันนี้ ${ui.minutesToday} นาที (นับเฉพาะช่วงที่เล่นจริง)",
                    modifier = Modifier.padding(top = 20.dp),
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** The on-screen keys, as many octaves as fit, with arrows to move them; MIDI keys light them too. */
@Composable
private fun Keys(lowNote: Int, transpose: Int, onShift: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val octaves = (maxWidth / 220.dp).toInt().coerceIn(2, 4)
        val low = lowNote.coerceAtMost(108 - 12 * octaves)
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton("‹") { onShift(-1) }
                Text(
                    "C${low / 12 - 1} – C${(low + 12 * octaves) / 12 - 1}" + if (transpose != 0) "  ·  ทรานสโพส ${if (transpose > 0) "+" else ""}$transpose" else "",
                    fontSize = 14.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundButton("›") { onShift(1) }
            }
            TouchKeyboard(low..low + 12 * octaves, emptySet(), false, NoteNameStyle.LETTER, Modifier.fillMaxWidth().height(if (octaves > 2) 210.dp else 180.dp))
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, valueText: String, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 15.sp, color = Tokens.Palette.InkMuted)
        }
        Slider(
            value = value, onValueChange = onChange, onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Tokens.Palette.Ink, activeTrackColor = Tokens.Palette.Ink, inactiveTrackColor = Tokens.Palette.Line),
        )
    }
}

/** A small round button; [onGroup] when it sits inside a group (then it takes the page colour to stand out). */
@Composable
private fun RoundButton(text: String, onGroup: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (onGroup) Tokens.Palette.Paper else Tokens.Palette.Group).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 20.sp, color = Tokens.Palette.Ink) }
}

private fun level(v: Int) = when (v) {
    VelocityMode.SOFT -> "เบา"
    VelocityMode.LOUD -> "ดัง"
    else -> "กลาง"
}
