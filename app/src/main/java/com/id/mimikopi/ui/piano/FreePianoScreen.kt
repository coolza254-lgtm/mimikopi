package com.id.mimikopi.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.id.mimikopi.ui.components.Chip
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.softShadow
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.ui.components.GroupFooter
import com.id.mimikopi.ui.components.LargeTitle
import com.id.mimikopi.ui.components.MidiPill
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.Segmented
import com.id.mimikopi.ui.components.TouchKeyboard
import com.id.mimikopi.ui.components.midiConnected
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * The free piano: the keys, then three short groups of settings (sound, key weight, transpose). Nothing to practise
 * or score; the app is just the piano's sound. [soundSetup] shows where the app's piano sound is missing (the phone
 * installs it here; [onReady] is called once it is).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreePianoScreen(
    onOpenMidi: () -> Unit,
    onOpenSettings: () -> Unit,
    soundSetup: @Composable (onReady: () -> Unit) -> Unit = {},
    vm: FreePianoViewModel = viewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.enter() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.leave() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val connected = midiConnected()

    var showNames by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
            LargeTitle(
                "เปียโน",
                (if (connected) "เล่นจากเปียโน MIDI" else "เล่นอิสระ ไม่มีคะแนน") + " · วันนี้ ${ui.minutesToday} นาที",
            ) {
                MidiPill(onOpenMidi)
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "ตั้งค่า", tint = Tokens.Palette.Ink, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = PageMargin)) {
                if (ui.noSound) {
                    Spacer(Modifier.height(12.dp))
                    soundSetup(vm::soundInstalled)
                }
                Spacer(Modifier.height(12.dp))
                PageCard {
                    val s = ui.sound
                    SliderRow("ความดัง", s.volume, "${if (s.db > 0) "+" else ""}${s.db.roundToInt()} dB", { vm.setSound(s.copy(volume = it), false) }) { vm.setSound(ui.sound, true) }
                    if (vm.supportsTone) {
                        SliderRow("ความก้อง", s.reverb, "เสียงห้อง ${(s.reverb * 100).roundToInt()}%", { vm.setSound(s.copy(reverb = it), false) }) { vm.setSound(ui.sound, true) }
                        SliderRow("ความนุ่ม", s.warmth, "${(s.warmth * 100).roundToInt()}%", { vm.setSound(s.copy(warmth = it), false) }) { vm.setSound(ui.sound, true) }
                    }
                    val v = ui.velocity
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip(if (v.on) "น้ำหนักคีย์มีผล" else "ดังเท่ากัน: ${level(v.fixed)}", selected = v.on) {
                            vm.setVelocity(VelocityMode(!v.on, v.fixed))
                        }
                        Chip("แสดงชื่อโน้ต", selected = showNames) { showNames = !showNames }
                    }
                    if (!v.on) {
                        Box(Modifier.padding(top = 12.dp)) {
                            Segmented(VelocityMode.LEVELS, v.fixed.takeIf { it in VelocityMode.LEVELS } ?: VelocityMode.MEDIUM, { level(it) }, { vm.setVelocity(VelocityMode(false, it)) })
                        }
                    }
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ทรานสโพส", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                            Text(
                                when {
                                    ui.transpose > 0 -> "สูงขึ้น ${ui.transpose} ครึ่งเสียง"
                                    ui.transpose < 0 -> "ต่ำลง ${-ui.transpose} ครึ่งเสียง"
                                    else -> "เสียงตรงกับคีย์ที่กด"
                                },
                                fontSize = 13.sp, color = Tokens.Palette.InkMuted,
                            )
                        }
                        RoundButton("−", onGroup = true) { vm.transpose(-1) }
                        Text("${if (ui.transpose > 0) "+" else ""}${ui.transpose}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(min = 40.dp))
                        RoundButton("+", onGroup = true) { vm.transpose(1) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = PageMargin)) {
                Spacer(Modifier.height(20.dp))
                Keys(ui.lowNote, ui.transpose, showNames, vm::shiftOctave)
            }
            Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = PageMargin)) {
                GroupFooter(
                    "หน้านี้ใช้เสียงจากแอพเสมอ ปิดหรือลดเสียงลำโพงของเปียโน แล้วเสียบหูฟังที่เครื่องนี้ · " +
                        "เสียงและน้ำหนักคีย์ใช้กับทุกหน้า ทรานสโพสใช้เฉพาะหน้านี้ · นับเวลาเฉพาะช่วงที่เล่นจริง",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** The on-screen keys, as many octaves as fit, with arrows to move them; MIDI keys light them too. */
@Composable
private fun Keys(lowNote: Int, transpose: Int, showNames: Boolean, onShift: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val octaves = (maxWidth / 220.dp).toInt().coerceIn(2, 4)
        val low = lowNote.coerceAtMost(108 - 12 * octaves)
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton("‹", big = true) { onShift(-1) }
                Text(
                    "C${low / 12 - 1} – C${(low + 12 * octaves) / 12 - 1}" + if (transpose != 0) "  ·  ${if (transpose > 0) "+" else ""}$transpose" else "",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundButton("›", big = true) { onShift(1) }
            }
            TouchKeyboard(low..low + 12 * octaves, emptySet(), showNames, NoteNameStyle.LETTER, Modifier.fillMaxWidth().height(if (octaves > 2) 250.dp else 230.dp))
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, valueText: String, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 15.sp, color = Tokens.Palette.InkMuted)
        }
        Slider(
            value = value, onValueChange = onChange, onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Tokens.Palette.Group, activeTrackColor = Tokens.Palette.Accent, inactiveTrackColor = Tokens.Palette.Track,
                activeTickColor = Tokens.Palette.Accent, inactiveTickColor = Tokens.Palette.Track),
        )
    }
}

/** A round button: white with a soft shadow on the page ([big]), or the page colour inside a card ([onGroup]). */
@Composable
private fun RoundButton(text: String, onGroup: Boolean = false, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 44.dp else 36.dp).then(if (onGroup) Modifier else Modifier.softShadow(CircleShape, small = true))
            .clip(CircleShape).background(if (onGroup) Tokens.Palette.Paper else Tokens.Palette.Group).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 20.sp, color = Tokens.Palette.Ink) }
}

private fun level(v: Int) = when (v) {
    VelocityMode.SOFT -> "เบา"
    VelocityMode.LOUD -> "ดัง"
    else -> "กลาง"
}
