package com.id.mimikopi.core.input

import kotlinx.serialization.Serializable

/**
 * Where an input event came from. Latency offsets are calibrated per kind. TOUCH also covers mouse clicks on the
 * desktop program's on-screen keys; COMPUTER_KEYS is the desktop program's typing keyboard.
 */
@Serializable
enum class InputSource { TOUCH, USB_MIDI, BLE_MIDI, SIMULATED, COMPUTER_KEYS }

/**
 * The one internal event stream. On-screen keys, USB/Bluetooth MIDI and the debug simulator all
 * produce these, and all practice logic is written against them (PROMPT.md section 6).
 * [timeNanos] is on the CLOCK_MONOTONIC timeline (System.nanoTime), the same clock as audio timestamps.
 */
sealed interface InputEvent {
    val timeNanos: Long
    val source: InputSource

    data class NoteOn(val note: Int, val velocity: Int, override val timeNanos: Long, override val source: InputSource) : InputEvent
    data class NoteOff(val note: Int, override val timeNanos: Long, override val source: InputSource) : InputEvent
    data class Sustain(val down: Boolean, override val timeNanos: Long, override val source: InputSource) : InputEvent
    data class Control(val controller: Int, val value: Int, override val timeNanos: Long, override val source: InputSource) : InputEvent
}

/** Applies the calibrated octave offset, dropping notes that leave the MIDI range. */
fun InputEvent.shifted(semitones: Int): InputEvent? = when (this) {
    is InputEvent.NoteOn -> (note + semitones).takeIf { it in 0..127 }?.let { copy(note = it) }
    is InputEvent.NoteOff -> (note + semitones).takeIf { it in 0..127 }?.let { copy(note = it) }
    else -> this
}
