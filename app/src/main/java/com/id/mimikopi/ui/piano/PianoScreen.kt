package com.id.mimikopi.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.music.NoteNameStyle
import com.id.mimikopi.core.music.isBlackKey
import com.id.mimikopi.piano.PackException
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.PianoKeyboard
import com.id.mimikopi.ui.metronome.MetronomeBar
import com.id.mimikopi.ui.theme.Tokens

/** Free play on the on-screen keyboard (no scoring). Landscape-friendly: the keyboard takes the rest of the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoScreen(onBack: () -> Unit, vm: PianoViewModel = viewModel()) {
    val pack by vm.pack.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val engineError by vm.engineError.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.piano_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (pack != PackUi.Ready) {
                Box(Modifier.fillMaxWidth().padding(Tokens.Space.m), contentAlignment = Alignment.TopCenter) {
                    PackCard(pack, onInstall = vm::installPack, modifier = Modifier.widthIn(max = 720.dp))
                }
                return@Column
            }
            if (keys.soundTried && !keys.soundOn) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.m, vertical = Tokens.Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        engineError?.let { stringResource(R.string.piano_engine_error, it) } ?: stringResource(R.string.piano_no_sound),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = vm::startSound, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.piano_sound_retry))
                    }
                }
            }
            Controls(keys, vm)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).heightIn(min = 140.dp).padding(Tokens.Space.s)) {
                val visibleWhite = (maxWidth.value / keys.whiteKeyDp).toInt().coerceAtLeast(7)
                val high = noteAfterWhiteKeys(keys.lowNote, visibleWhite)
                PianoKeyboard(
                    hideWithMidi = false,
                    lowNote = keys.lowNote,
                    highNote = high,
                    pressed = pressed,
                    showNames = keys.showNames,
                    nameStyle = keys.nameStyle,
                    onNoteOn = vm::noteOn,
                    onNoteOff = vm::noteOff,
                )
            }
        }
    }
}

@Composable
private fun Controls(keys: KeyboardUi, vm: PianoViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { vm.shiftOctave(-1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.piano_octave_down)) }
            OutlinedButton(onClick = { vm.shiftOctave(1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.piano_octave_up)) }
            OutlinedButton(onClick = { vm.zoom(-6) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.piano_zoom_out)) }
            OutlinedButton(onClick = { vm.zoom(6) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.piano_zoom_in)) }
            FilterChip(selected = keys.showNames, onClick = vm::toggleNames, label = { Text(stringResource(R.string.piano_show_names)) })
            FilterChip(selected = keys.showTone, onClick = vm::toggleTonePanel, label = { Text(stringResource(R.string.piano_tone)) })
            SustainButton(down = keys.sustain, onChange = vm::setSustain)
            MetronomeBar()
        }
        if (keys.showNames) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                NoteNameStyle.entries.forEach { style ->
                    FilterChip(
                        selected = keys.nameStyle == style,
                        onClick = { vm.setNameStyle(style) },
                        label = {
                            Text(stringResource(when (style) {
                                NoteNameStyle.LETTER -> R.string.names_letter
                                NoteNameStyle.SOLFEGE -> R.string.names_solfege
                                NoteNameStyle.BOTH -> R.string.names_both
                            }))
                        },
                    )
                }
            }
        }
        if (keys.showTone) {
            LabeledSlider(stringResource(R.string.piano_volume), keys.gain, vm::setGain)
            LabeledSlider(stringResource(R.string.piano_reverb), keys.reverb, vm::setReverb)
            LabeledSlider(stringResource(R.string.piano_warmth), keys.warmth, vm::setWarmth)
            LabeledSlider(stringResource(R.string.piano_velocity, keys.velocity), (keys.velocity - 20) / 107f) { vm.setVelocity((20 + it * 107).toInt()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                FilterChip(
                    selected = keys.touchVelocity,
                    onClick = { vm.setTouchVelocity(!keys.touchVelocity) },
                    label = { Text(stringResource(R.string.piano_touch_velocity)) },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 110.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.weight(1f))
    }
}

/** Hold to sustain, like the pedal (release lets the held notes ring out naturally). */
@Composable
private fun SustainButton(down: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(if (down) Tokens.Palette.Mustard else Tokens.Palette.MustardSoft, RoundedCornerShape(Tokens.Radius.button))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onChange(true)
                    waitForUpOrCancellation()
                    onChange(false)
                }
            }
            .padding(horizontal = Tokens.Space.m, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.piano_sustain), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PackCard(pack: PackUi, onInstall: () -> Unit, modifier: Modifier = Modifier) {
    PageCard(modifier = modifier, title = stringResource(R.string.pack_title)) {
        Text(stringResource(R.string.pack_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.Space.xs))
        when (pack) {
            PackUi.Checking, PackUi.Ready -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Tokens.Space.s))
            is PackUi.Missing -> {
                if (pack.downloadBytes != null && pack.decodedBytes != null) {
                    Text(stringResource(R.string.pack_size, pack.downloadBytes / 1_000_000, pack.decodedBytes / 1_000_000),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Space.xs))
                }
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s).heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.pack_download))
                }
            }
            is PackUi.Downloading -> {
                Text(stringResource(R.string.pack_downloading, (pack.fraction * 100).toInt()), modifier = Modifier.padding(top = Tokens.Space.s))
                LinearProgressIndicator(progress = { pack.fraction }, modifier = Modifier.fillMaxWidth())
            }
            is PackUi.Decoding -> {
                Text(stringResource(R.string.pack_decoding, pack.done, pack.total), modifier = Modifier.padding(top = Tokens.Space.s))
                LinearProgressIndicator(progress = { pack.done.toFloat() / pack.total }, modifier = Modifier.fillMaxWidth())
            }
            is PackUi.Failed -> {
                Text(
                    stringResource(when (pack.kind) {
                        PackException.Kind.NO_SPACE -> R.string.pack_err_space
                        PackException.Kind.CHECKSUM -> R.string.pack_err_checksum
                        PackException.Kind.NETWORK, null -> R.string.pack_err_network
                        else -> R.string.pack_err_other
                    }) + (pack.detail?.let { "\n($it)" } ?: ""),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Tokens.Space.s),
                )
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s).heightIn(min = Tokens.minTouch)) {
                    Text(stringResource(R.string.pack_retry))
                }
            }
        }
    }
}

/** The note reached after counting [whiteKeys] white keys starting at [low], capped at C8. */
internal fun noteAfterWhiteKeys(low: Int, whiteKeys: Int): Int {
    var note = low
    var count = 1
    while (count < whiteKeys && note < 108) {
        note++
        if (!isBlackKey(note)) count++
    }
    return note
}
