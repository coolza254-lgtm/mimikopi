package com.id.mimikopi.core.input

/**
 * Timing is judged only from a piano on a cable (learning-system.md 2.11): Bluetooth MIDI lags and wobbles too much
 * for a fair verdict, and touch or computer keys are not a piano. Without a cable, practice that judges timing does
 * not start; reading, keys, chords, the ear course without rhythm taps, and wait-mode playing all work as before.
 */
object TimingInput {
    const val NEED_CABLE = "ข้อนี้ตัดสินจังหวะ ต้องต่อเปียโนด้วยสาย USB · ฝึกบนคอม หรือเสียบสายที่มือถือ"
    const val NEED_CABLE_SHORT = "ต่อสายก่อน (ฝึกบนคอม)"

    /** Notes from [source] carry onset times good enough to judge. */
    fun judges(source: InputSource): Boolean = source == InputSource.USB_MIDI || source == InputSource.SIMULATED
}
