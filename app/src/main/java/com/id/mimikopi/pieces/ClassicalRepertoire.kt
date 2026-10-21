package com.id.mimikopi.pieces

/** A suggested practice order for these arrangements, independent of tested skill levels. */
object ClassicalRepertoire {
    data class Step(val id: String, val focus: String, val startHand: String)

    val steps = listOf(
        Step("ode-to-joy-first", "ห้าโน้ต C–G · เริ่มมือขวา รักษาจังหวะเท่ากัน แล้วเพิ่มเบสค้าง", "RIGHT"),
        Step("ode-to-joy", "ทำนองเดิม เพิ่มจังหวะประจุด · แยกมือก่อนรวมสองมือ", "RIGHT"),
        Step("brahms-lullaby", "นับ 1–2–3 · ฟังโน้ตรับและจังหวะประจุดก่อนเริ่ม", "RIGHT"),
        Step("chopin-prelude-e-minor-easy", "Chopin ฉบับเบสง่าย · ค้างทำนองให้ครบและอ่านเครื่องหมายแปลงเสียง", "RIGHT"),
        Step("heroic-polonaise-easy", "Heroic ฉบับเริ่มต้น · ท่อนหลักใน C มี B♭ ฝึกมือขวาทีละ 2 ห้อง แล้วเพิ่มเบสค้าง", "RIGHT"),
        Step("canon-in-d-study", "อ่าน F♯ และ C♯ · ฝึกมือซ้ายเปลี่ยนเบสทีละครึ่งห้อง", "LEFT"),
        Step("eine-kleine-nachtmusik", "ท่อนเปิดสั้น · แยกโน้ตกับตัวหยุดให้ชัด แล้วเพิ่มความเร็ว", "RIGHT"),
        Step("minuet-in-g", "ท่อนแรก 16 ห้อง · ฝึกย้ายมือและจังหวะ 3/4 ทีละ 4 ห้อง", "RIGHT"),
        Step("fur-elise", "ท่อนเปิด · สลับ E–D♯ และประสานสองมืออย่างช้า ๆ", "RIGHT"),
        Step("chopin-prelude-e-minor-chords", "ทำนองเดียวกับขั้น 4 · ฝึกคอร์ดมือซ้ายที่เคลื่อนทีละเสียงก่อนรวมมือ", "LEFT"),
        Step("prelude-in-c", "8 ห้องแรก · ทำอาร์เปจจิโอให้สม่ำเสมอโดยไม่เร่ง", "RIGHT"),
        Step("heroic-polonaise-theme", "Heroic ท่อนเดียวกับขั้น 5 · คีย์ A♭ เพิ่มคอร์ด จังหวะประจุด และมือซ้ายที่ย้ายตำแหน่ง", "RIGHT"),
        Step("mozart-k545-opening", "8 ห้องแรก · แยก Alberti bass กับสเกล เริ่มครั้งละ 2 ห้อง", "RIGHT"),
    )
}
