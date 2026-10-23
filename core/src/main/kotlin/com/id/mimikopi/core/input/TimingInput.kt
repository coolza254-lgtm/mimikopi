package com.id.mimikopi.core.input

import com.id.mimikopi.core.i18n.tr

/**
 * Timing is judged only from a piano on a cable (learning-system.md 2.11): Bluetooth MIDI lags and wobbles too much
 * for a fair verdict, and touch or computer keys are not a piano. Without a cable, practice that judges timing does
 * not start; reading, keys, chords, the ear course without rhythm taps, and wait-mode playing all work as before.
 */
object TimingInput {
    val NEED_CABLE: String get() = tr("ข้อนี้ตัดสินจังหวะ ต้องต่อเปียโนด้วยสาย USB · ฝึกบนคอม หรือเสียบสายที่มือถือ", "This one judges timing: connect the piano with a USB cable · practise on the PC or plug the cable into the phone", "リズムを判定するのでUSBケーブルでピアノを接続してください · PCで練習するか、スマホにケーブルを挿してください")
    val NEED_CABLE_SHORT: String get() = tr("ต่อสายก่อน (ฝึกบนคอม)", "Needs a cable (practise on the PC)", "ケーブル接続が必要（PCで練習）")

    /** Notes from [source] carry onset times good enough to judge. */
    fun judges(source: InputSource): Boolean = source == InputSource.USB_MIDI || source == InputSource.SIMULATED
}
