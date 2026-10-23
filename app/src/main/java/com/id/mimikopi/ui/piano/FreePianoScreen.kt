package com.id.mimikopi.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.input.VelocityMode
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.SpelledPitch
import com.id.mimikopi.ui.components.Chip
import com.id.mimikopi.ui.components.MidiPill
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.Segmented
import com.id.mimikopi.ui.components.TouchKeyboard
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * The free piano, minimal (user-approved design 2026-10-23): no title or cards. The MIDI status and one sound button
 * at the top, the last note played in large type, the octave arrows and the keys filling the rest. Sound, key weight,
 * note names and transpose open in a sheet. [soundSetup] shows where the app's piano sound is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val app = LocalContext.current.applicationContext as MimikopiApp
    val pressed by app.inputHub.pressed.collectAsState()
    var last by remember { mutableIntStateOf(-1) }
    pressed.maxOrNull()?.let { if (it != last) last = it }

    var showNames by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().paper().padding(horizontal = PageMargin).padding(bottom = 96.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            MidiPill(onOpenMidi)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { sheet = true }) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "เสียง ความดัง ก้อง นุ่ม", tint = Tokens.Palette.InkMuted, modifier = Modifier.size(22.dp))
            }
        }
        if (ui.noSound) soundSetup(vm::soundInstalled)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (last >= 0) {
                val p = SpelledPitch.fromMidiSharp(last)
                Text(p.name(NoteNameStyle.SOLFEGE, withOctave = false), fontFamily = Tokens.Fonts.kana, fontSize = 64.sp, color = Tokens.Palette.Ink)
                Text(p.name(NoteNameStyle.LETTER, withOctave = true), fontSize = 15.sp, color = Tokens.Palette.InkMuted)
            }
        }
        Keys(ui.lowNote, ui.transpose, showNames, vm::shiftOctave)
    }

    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Tokens.Palette.Group) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val s = ui.sound
            SliderRow("ความดัง", s.volume, "${if (s.db > 0) "+" else ""}${s.db.roundToInt()} dB", { vm.setSound(s.copy(volume = it), false) }) { vm.setSound(ui.sound, true) }
            if (vm.supportsTone) {
                SliderRow("ก้อง", s.reverb, "${(s.reverb * 100).roundToInt()}%", { vm.setSound(s.copy(reverb = it), false) }) { vm.setSound(ui.sound, true) }
                SliderRow("นุ่ม", s.warmth, "${(s.warmth * 100).roundToInt()}%", { vm.setSound(s.copy(warmth = it), false) }) { vm.setSound(ui.sound, true) }
            }
            val v = ui.velocity
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(if (v.on) "น้ำหนักคีย์" else "ดังเท่ากัน: ${level(v.fixed)}", selected = v.on) { vm.setVelocity(VelocityMode(!v.on, v.fixed)) }
                Chip("ชื่อโน้ต", selected = showNames) { showNames = !showNames }
            }
            if (!v.on) Segmented(VelocityMode.LEVELS, v.fixed.takeIf { it in VelocityMode.LEVELS } ?: VelocityMode.MEDIUM, { level(it) }, { vm.setVelocity(VelocityMode(false, it)) })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("คีย์", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.weight(1f))
                RoundButton("−") { vm.transpose(-1) }
                Text("${if (ui.transpose > 0) "+" else ""}${ui.transpose}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 40.dp))
                RoundButton("+") { vm.transpose(1) }
            }
        }
    }
}

/** The keys, as many octaves as fit, with quiet arrows above them to move an octave; MIDI keys light them too. */
@Composable
private fun Keys(lowNote: Int, transpose: Int, showNames: Boolean, onShift: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val octaves = (maxWidth / 220.dp).toInt().coerceIn(1, 4)
        val low = lowNote.coerceAtMost(108 - 12 * octaves)
        val high = low + 12 * octaves + 4
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                QuietArrow("‹", "เลื่อนลงหนึ่งอ็อกเทฟ") { onShift(-1) }
                Text(
                    "C${low / 12 - 1} — ${SpelledPitch.fromMidiSharp(high).name(NoteNameStyle.LETTER, withOctave = true)}" +
                        if (transpose != 0) "  ·  ${if (transpose > 0) "+" else ""}$transpose" else "",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(horizontal = 24.dp),
                )
                QuietArrow("›", "เลื่อนขึ้นหนึ่งอ็อกเทฟ") { onShift(1) }
            }
            TouchKeyboard(low..high, emptySet(), showNames, NoteNameStyle.LETTER, Modifier.fillMaxWidth().height(300.dp))
        }
    }
}

@Composable
private fun QuietArrow(text: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 22.sp, color = Tokens.Palette.InkMuted)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, valueText: String, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
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

@Composable
private fun RoundButton(text: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Tokens.Palette.Paper).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 20.sp, color = Tokens.Palette.Ink)
    }
}

private fun level(v: Int) = when (v) {
    VelocityMode.SOFT -> "เบา"
    VelocityMode.LOUD -> "ดัง"
    else -> "กลาง"
}
