package com.id.mimikopi.ui.midi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.input.InputSource
import com.id.mimikopi.core.input.MidiNotice
import com.id.mimikopi.midi.BleLink
import com.id.mimikopi.midi.BleMidiScanner
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens

@Composable
fun MidiScreen(onBack: () -> Unit, vm: MidiViewModel = viewModel()) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val soundThrough by vm.soundThrough.collectAsStateWithLifecycle()
    val velocity by vm.velocity.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()
    val octave by vm.octave.collectAsStateWithLifecycle()
    var showSim by remember { mutableStateOf(false) }
    val bleFound by vm.bleFound.collectAsStateWithLifecycle()
    val bleScanning by vm.bleScanning.collectAsStateWithLifecycle()
    val bleSaved by vm.bleSaved.collectAsStateWithLifecycle()
    val bleProblem by vm.bleProblem.collectAsStateWithLifecycle()
    val bleScanFinished by vm.bleScanFinished.collectAsStateWithLifecycle()
    val bleLink by vm.bleLink.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    // The scan or reconnect continues once the user answers the "nearby devices" dialog.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterPermission?.invoke()
        afterPermission = null
    }
    fun withPermission(action: () -> Unit) {
        afterPermission = action
        permissionLauncher.launch(BleMidiScanner.REQUIRED)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }

    ScreenScaffold(title = stringResource(R.string.midi_title), onBack = onBack) {
        // The connection, told as it really is: connected (green tick), silent, unplugged or absent.
        var now by remember { mutableLongStateOf(System.nanoTime()) }
        LaunchedEffect(Unit) { while (true) { delay(1000); now = System.nanoTime() } }
        PageCard {
            if (!vm.midiSupported) {
                Body(stringResource(R.string.midi_unsupported), error = true)
            } else if (devices.isEmpty()) {
                // Just pulled out (or the Bluetooth link lost): say so, with which instrument.
                val gone = notice?.takeIf { it.kind == MidiNotice.Kind.UNPLUGGED || it.kind == MidiNotice.Kind.BLE_LOST }
                StatusHero(
                    ConnState.NONE,
                    title = stringResource(
                        when (gone?.kind) {
                            MidiNotice.Kind.UNPLUGGED -> R.string.midi_state_unplugged
                            MidiNotice.Kind.BLE_LOST -> R.string.midi_state_ble_lost
                            else -> R.string.midi_state_none
                        },
                    ),
                    body = stringResource(R.string.midi_none_help),
                    device = gone?.name,
                    lastNote = null,
                )
            } else {
                devices.sortedBy { it.name }.forEachIndexed { i, d ->
                    if (i > 0) com.id.mimikopi.ui.components.Hairline(Modifier.padding(vertical = Tokens.Space.m))
                    val state = connState(d)
                    StatusHero(
                        state,
                        title = when (state) {
                            ConnState.CONNECTED -> stringResource(R.string.midi_state_connected)
                            ConnState.SILENT -> stringResource(R.string.midi_state_silent)
                            ConnState.CONNECTING -> d.error?.let { stringResource(R.string.midi_state_open_failed) } ?: stringResource(R.string.midi_state_connecting)
                            ConnState.NONE -> stringResource(R.string.midi_state_none)
                        },
                        body = when (state) {
                            ConnState.CONNECTED -> stringResource(if (d.verified) R.string.midi_state_connected_body else R.string.midi_state_connected_plain)
                            ConnState.SILENT -> stringResource(R.string.midi_state_silent_body)
                            ConnState.CONNECTING -> d.error?.let { stringResource(R.string.midi_state_open_failed_body) }
                            else -> null
                        },
                        device = d.name + " · " + stringResource(if (d.source == InputSource.BLE_MIDI) R.string.midi_bluetooth else R.string.midi_usb),
                        lastNote = d.lastNote?.let { n ->
                            val secs = ((now - (d.lastNoteNanos ?: now)) / 1_000_000_000L).toInt()
                            val name = com.id.mimikopi.core.music.SpelledPitch.fromMidiSharp(n).name(com.id.mimikopi.core.music.NoteNameStyle.BOTH, withOctave = true)
                            if (secs < 2) stringResource(R.string.midi_last_note_now, name) else if (secs < 3600) stringResource(R.string.midi_last_note, name, secs) else null
                        },
                    )
                }
            }
            if (vm.midiSupported) OutlinedButton(onClick = vm::recheck, modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.l).heightIn(min = Tokens.minTouch)) {
                Text(stringResource(R.string.midi_recheck))
            }
        }
        PageCard(title = stringResource(R.string.midi_settings)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.midi_sound_through), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = soundThrough, onCheckedChange = vm::setSoundThrough)
            }
            // Key strength: off at the start (every note at one level), on later to practise touch.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Tokens.Space.m)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.midi_velocity), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(if (velocity.on) R.string.midi_velocity_on else R.string.midi_velocity_off), style = MaterialTheme.typography.bodySmall, color = Tokens.Palette.InkMuted)
                }
                Switch(checked = velocity.on, onCheckedChange = { vm.setVelocity(velocity.copy(on = it)) })
            }
            if (!velocity.on) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.padding(top = Tokens.Space.xs)) {
                listOf(com.id.mimikopi.core.input.VelocityMode.SOFT to R.string.midi_velocity_soft, com.id.mimikopi.core.input.VelocityMode.MEDIUM to R.string.midi_velocity_medium,
                    com.id.mimikopi.core.input.VelocityMode.LOUD to R.string.midi_velocity_loud).forEach { (level, label) ->
                    FilterChip(selected = velocity.fixed == level, onClick = { vm.setVelocity(velocity.copy(fixed = level)) }, label = { Text(stringResource(label)) })
                }
            }
        }

        if (vm.bleSupported) {
            PageCard(title = stringResource(R.string.ble_title)) {
                val bleDevice = devices.firstOrNull { it.source == InputSource.BLE_MIDI && it.open }
                val bleConnected = bleDevice != null
                val connecting = bleLink as? BleLink.Connecting
                bleSaved?.let { saved ->
                    val state = when {
                        bleDevice != null -> connState(bleDevice)
                        connecting?.address == saved.address -> ConnState.CONNECTING
                        else -> ConnState.NONE
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = Tokens.Space.xs)) {
                        StatusMark(state, small = true)
                        Text(saved.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            when (state) {
                                ConnState.CONNECTED -> stringResource(R.string.midi_state_connected)
                                ConnState.SILENT -> stringResource(R.string.midi_state_silent)
                                ConnState.CONNECTING -> stringResource(R.string.midi_state_connecting)
                                ConnState.NONE -> stringResource(R.string.ble_not_connected)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state == ConnState.CONNECTED) Tokens.Palette.Positive else Tokens.Palette.InkMuted,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s), modifier = Modifier.fillMaxWidth()) {
                        when {
                            bleConnected -> OutlinedButton(onClick = vm::bleDisconnect, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.ble_disconnect)) }
                            connecting != null -> OutlinedButton(onClick = vm::bleDisconnect, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.ble_cancel)) }
                            else -> Button(onClick = { withPermission(vm::bleConnectSaved) }, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.ble_connect)) }
                        }
                        OutlinedButton(onClick = vm::bleForget, modifier = Modifier.weight(1f).heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.ble_forget)) }
                    }
                } ?: Body(stringResource(R.string.ble_help), muted = true)
                when {
                    bleProblem == BleProblem.PERMISSION -> Body(stringResource(R.string.ble_need_permission), error = true)
                    bleProblem == BleProblem.OFF -> Body(stringResource(R.string.ble_off), error = true)
                    connecting != null -> Body(stringResource(R.string.ble_connecting_to, connecting.name, (com.id.mimikopi.midi.MidiInputManager.CONNECT_TIMEOUT_MS / 1000).toInt()), muted = true)
                    bleLink is BleLink.Failed && !bleConnected -> Body(stringResource(R.string.ble_failed, (bleLink as BleLink.Failed).name), error = true)
                    bleScanFinished && !bleScanning && bleFound.isEmpty() && !bleConnected -> Body(stringResource(R.string.ble_not_found), error = true)
                }
                if (!bleConnected && connecting == null) Button(
                    onClick = { withPermission(vm::bleScan) },
                    enabled = !bleScanning,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch).padding(top = Tokens.Space.s),
                ) { Text(stringResource(if (bleScanning) R.string.ble_scanning else R.string.ble_scan)) }
                if (!bleConnected && connecting == null) bleFound.forEach { d ->
                    OutlinedButton(onClick = { withPermission { vm.bleConnect(d) } }, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) {
                        Text(stringResource(R.string.ble_connect_to, d.name))
                    }
                }
            }
        }

        PageCard(title = stringResource(R.string.calib_title)) {
            Body(stringResource(R.string.calib_status, config.octaveOffset,
                config.velocityProbe?.let { if (it.velocitySensitive) stringResource(R.string.calib_vel_yes) else stringResource(R.string.calib_vel_no) } ?: stringResource(R.string.calib_unknown)))

            Section(stringResource(R.string.probe_title))
            when (val p = probe) {
                ProbeUi.Idle -> Unit
                is ProbeUi.Soft -> Body(stringResource(R.string.probe_soft, p.velocities.size, MidiViewModel.PROBE_PRESSES))
                is ProbeUi.Hard -> Body(stringResource(R.string.probe_hard, p.velocities.size, MidiViewModel.PROBE_PRESSES))
                is ProbeUi.Done -> Body(stringResource(if (p.result.velocitySensitive) R.string.probe_done_yes else R.string.probe_done_no, p.result.describe()))
            }
            OutlinedButton(onClick = vm::startProbe, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.probe_start)) }

            Section(stringResource(R.string.octave_title))
            when (val o = octave) {
                OctaveUi.Idle -> Unit
                OctaveUi.Waiting -> Body(stringResource(R.string.octave_waiting))
                is OctaveUi.Done -> Body(stringResource(R.string.octave_done, o.offset))
                is OctaveUi.Rejected -> Body(stringResource(R.string.octave_rejected, o.note), error = true)
            }
            OutlinedButton(onClick = vm::startOctave, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.minTouch)) { Text(stringResource(R.string.octave_start)) }
        }

        PageCard(title = stringResource(R.string.monitor_title)) {
            if (monitor.isEmpty()) Body(stringResource(R.string.monitor_empty), muted = true)
            monitor.take(20).forEach { line ->
                Text("%6.2f s  %s".format(line.millis / 1000.0, line.text), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        }

        PageCard(title = stringResource(R.string.sim_title), onClick = { showSim = !showSim }) {
            Body(stringResource(R.string.sim_help), muted = true)
            if (showSim) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s)) {
                    FilterChip(selected = false, onClick = { vm.simNote(60, 90) }, label = { Text("C4") })
                    FilterChip(selected = false, onClick = vm::simRandomVelocity, label = { Text(stringResource(R.string.sim_random)) })
                    FilterChip(selected = false, onClick = vm::simChord, label = { Text(stringResource(R.string.sim_chord)) })
                    FilterChip(selected = false, onClick = vm::simScale, label = { Text(stringResource(R.string.sim_scale)) })
                    FilterChip(selected = false, onClick = { vm.simPedal(true) }, label = { Text(stringResource(R.string.sim_pedal_down)) })
                    FilterChip(selected = false, onClick = { vm.simPedal(false) }, label = { Text(stringResource(R.string.sim_pedal_up)) })
                }
            }
        }
    }
}

/** What the app can honestly say about one instrument. */
enum class ConnState { CONNECTED, SILENT, CONNECTING, NONE }

/** Listed and open = connected, unless its Active Sensing or identity answers stopped (switched off while linked). */
fun connState(d: com.id.mimikopi.midi.MidiDeviceState): ConnState = when {
    !d.open -> ConnState.CONNECTING
    d.silent -> ConnState.SILENT
    else -> ConnState.CONNECTED
}

/** The round mark: a green disc with a white tick when connected, an outline otherwise. */
@Composable
fun StatusMark(state: ConnState, small: Boolean = false) {
    val size = if (small) 22.dp else 48.dp
    Box(
        Modifier.size(size).then(
            when (state) {
                ConnState.CONNECTED -> Modifier.background(Tokens.Palette.Positive, CircleShape)
                else -> Modifier.border(1.5.dp, Tokens.Palette.Dim, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ConnState.CONNECTED -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
            ConnState.CONNECTING -> Text("…", fontSize = if (small) 12.sp else 20.sp, color = Tokens.Palette.Dim)
            else -> Unit
        }
    }
}

@Composable
private fun StatusHero(state: ConnState, title: String, body: String?, device: String?, lastNote: String?) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusMark(state)
        Column(Modifier.weight(1f)) {
            Text(
                title, fontFamily = Tokens.Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp,
                color = if (state == ConnState.CONNECTED) Tokens.Palette.Positive else Tokens.Palette.Ink,
            )
            device?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp)) }
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 6.dp)) }
            lastNote?.let { Text(it, fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Tokens.Space.m))
}

@Composable
private fun Body(text: String, muted: Boolean = false, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            error -> MaterialTheme.colorScheme.error
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(top = Tokens.Space.xs),
    )
}
