package com.id.mimikopi.core.input

/**
 * A change in what is connected, told once to the user wherever they are in the app (a short notice at the top):
 * a cable plugged in or pulled out, a Bluetooth instrument connected, lost or not reached. [atNanos] makes two
 * notices about the same instrument distinct, so the second one is shown too.
 */
data class MidiNotice(val kind: Kind, val name: String, val atNanos: Long = System.nanoTime()) {
    enum class Kind { PLUGGED, UNPLUGGED, BLE_CONNECTED, BLE_LOST, BLE_FAILED }

    /** Something the user may need to act on (shown until they look at it a little longer). */
    val isProblem: Boolean get() = kind == Kind.UNPLUGGED || kind == Kind.BLE_LOST || kind == Kind.BLE_FAILED

    /** The notice in Thai, as shown on both platforms. */
    fun text(): String = when (kind) {
        Kind.PLUGGED -> "เสียบสาย $name แล้ว · Connected"
        Kind.UNPLUGGED -> "ถอดสาย $name แล้ว"
        Kind.BLE_CONNECTED -> "เชื่อมต่อ $name ทางบลูทูธแล้ว · Connected"
        Kind.BLE_LOST -> "การเชื่อมต่อบลูทูธกับ $name หลุด กดค้นหาในหน้าต่อเปียโนเพื่อต่อใหม่"
        Kind.BLE_FAILED -> "เชื่อมต่อ $name ไม่สำเร็จ เปิดเปียโนให้อยู่ใกล้ๆ แล้วลองอีกครั้ง"
    }
}
