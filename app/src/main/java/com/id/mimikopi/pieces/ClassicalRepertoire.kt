package com.id.mimikopi.pieces

/** Two arrangements of the only repertoire goal; foundational exercises are separate. */
object ClassicalRepertoire {
    data class Step(val id: String, val focus: String, val startHand: String)
    val steps = listOf(
        Step("heroic-polonaise-easy", "Heroic ฉบับง่ายใน C · ท่อนหลัก 8 ห้อง มี B♭ · เริ่มทีละ 2 ห้อง", "RIGHT"),
        Step("heroic-polonaise-theme", "Heroic ท่อนเดิมใน A♭ · เพิ่มคอร์ดและการย้ายมือ · เป็นฉบับฝึก ไม่ใช่ต้นฉบับเต็ม", "RIGHT"),
    )
}
