package com.id.mimikopi.piano

/** Live input and scheduled examples share this output; tests can observe notes without starting native audio. */
interface NoteOutput {
    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun sustain(down: Boolean)
}
