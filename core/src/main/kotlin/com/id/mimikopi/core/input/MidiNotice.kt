package com.id.mimikopi.core.input

import com.id.mimikopi.core.i18n.tr

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
        Kind.PLUGGED -> tr("เสียบสาย $name แล้ว · Connected", "$name cable connected · Connected", "${name}をケーブルで接続しました · Connected")
        Kind.UNPLUGGED -> tr("ถอดสาย $name แล้ว", "$name cable unplugged", "${name}のケーブルが外れました")
        Kind.BLE_CONNECTED -> tr("เชื่อมต่อ $name ทางบลูทูธแล้ว · Connected", "$name connected over Bluetooth · Connected", "${name}とBluetoothで接続しました · Connected")
        Kind.BLE_LOST -> tr("การเชื่อมต่อบลูทูธกับ $name หลุด กดค้นหาในหน้าต่อเปียโนเพื่อต่อใหม่", "Bluetooth connection to $name dropped; search on the piano connection page to reconnect", "${name}とのBluetooth接続が切れました。ピアノ接続ページで探して再接続してください")
        Kind.BLE_FAILED -> tr("เชื่อมต่อ $name ไม่สำเร็จ เปิดเปียโนให้อยู่ใกล้ๆ แล้วลองอีกครั้ง", "Couldn't connect to $name; turn the piano on, keep it close and try again", "${name}に接続できません。ピアノの電源を入れて近くに置き、もう一度")
    }
}
