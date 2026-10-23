package com.id.mimikopi.pieces

import com.id.mimikopi.core.i18n.tr

/** A suggested practice order for these arrangements, independent of tested skill levels. */
object ClassicalRepertoire {
    data class Step(val id: String, val focus: String, val startHand: String)

    val steps = listOf(
        Step("ode-to-joy-first", tr("ห้าโน้ต C–G · เริ่มมือขวา รักษาจังหวะเท่ากัน แล้วเพิ่มเบสค้าง", "Five notes C–G · start with the right hand, keep the beat even, then add held bass notes", "5つの音C–G · 右手から始めて一定のリズムで、次に伸ばすベースを加える"), "RIGHT"),
        Step("ode-to-joy", tr("ทำนองเดิม เพิ่มจังหวะประจุด · แยกมือก่อนรวมสองมือ", "The same melody with dotted rhythms · hands separately before together", "同じメロディに付点リズム · 両手の前に片手ずつ"), "RIGHT"),
        Step("brahms-lullaby", tr("นับ 1–2–3 · ฟังโน้ตรับและจังหวะประจุดก่อนเริ่ม", "Count 1–2–3 · listen to the pickup and the dotted rhythm before starting", "1–2–3と数える · 始める前に弱起と付点リズムを聞く"), "RIGHT"),
        Step("moonlight-intro-easy", tr("Moonlight · รูปมือช่วงต้น 4 ห้องใน A minor มี G♯ และ B♭ · เล่นสามโน้ตเท่ากันต่อจังหวะหลัก", "Moonlight · the opening 4-bar hand shape in A minor with G♯ and B♭ · three even notes per main beat", "月光 · イ短調の冒頭4小節の手の形、G♯とB♭あり · 1拍に3つの均等な音"), "RIGHT"),
        Step("chopin-prelude-e-minor-easy", tr("Chopin ฉบับเบสง่าย · ค้างทำนองให้ครบและอ่านเครื่องหมายแปลงเสียง", "Chopin, easy-bass edition · hold the melody full length and read the accidentals", "ショパン やさしいベース版 · メロディを最後まで伸ばし、臨時記号を読む"), "RIGHT"),
        Step("heroic-polonaise-easy", tr("Heroic ฉบับเริ่มต้น · ท่อนหลักใน C มี B♭ ฝึกมือขวาทีละ 2 ห้อง แล้วเพิ่มเบสค้าง", "Heroic starter edition · the main section in C with B♭; right hand 2 bars at a time, then add held bass", "Heroic 入門版 · Cのメイン部分にB♭あり。右手を2小節ずつ、次に伸ばすベース"), "RIGHT"),
        Step("canon-in-d-study", tr("อ่าน F♯ และ C♯ · ฝึกมือซ้ายเปลี่ยนเบสทีละครึ่งห้อง", "Reading F♯ and C♯ · left hand changes bass every half bar", "F♯とC♯を読む · 左手は半小節ごとにベースを変える"), "LEFT"),
        Step("eine-kleine-nachtmusik", tr("ท่อนเปิดสั้น · แยกโน้ตกับตัวหยุดให้ชัด แล้วเพิ่มความเร็ว", "Short opening · keep notes and rests distinct, then speed up", "短い冒頭 · 音と休符をはっきり分けてからテンポを上げる"), "RIGHT"),
        Step("moonlight-intro-c-sharp", tr("Moonlight · ช่วงต้น 4 ห้องในคีย์ C♯ minor · เพิ่มเบสอ็อกเทฟแล้วเทียบโน้ตอ้างอิง", "Moonlight · the opening 4 bars in C♯ minor · add octave bass and check against the reference", "月光 · 嬰ハ短調の冒頭4小節 · オクターブのベースを加えて参照と比べる"), "RIGHT"),
        Step("minuet-in-g", tr("ท่อนแรก 16 ห้อง · ฝึกย้ายมือและจังหวะ 3/4 ทีละ 4 ห้อง", "First 16-bar section · practise shifts and 3/4 time 4 bars at a time", "最初の16小節 · 手の移動と3/4拍子を4小節ずつ"), "RIGHT"),
        Step("moonlight-first-theme", tr("Moonlight · ห้อง 5–12 ฉบับแยกสองมือ · ฝึกคอร์ดแตกมือซ้ายก่อนเติมทำนอง", "Moonlight · bars 5–12, hands-separate edition · left-hand broken chords before adding the melody", "月光 · 5–12小節 片手ずつ版 · メロディの前に左手の分散和音を"), "LEFT"),
        Step("fur-elise", tr("ท่อนเปิด · สลับ E–D♯ และประสานสองมืออย่างช้า ๆ", "Opening · alternate E–D♯ and coordinate both hands slowly", "冒頭 · E–D♯の交互と両手の協調をゆっくり"), "RIGHT"),
        Step("chopin-prelude-e-minor-chords", tr("ทำนองเดียวกับฉบับเบสง่าย · ฝึกคอร์ดมือซ้ายที่เคลื่อนทีละเสียงก่อนรวมมือ", "Same melody as the easy-bass edition · practise the stepwise left-hand chords before hands together", "やさしいベース版と同じメロディ · 両手の前に順次進行する左手のコードを"), "LEFT"),
        Step("prelude-in-c", tr("8 ห้องแรก · ทำอาร์เปจจิโอให้สม่ำเสมอโดยไม่เร่ง", "First 8 bars · keep the arpeggios even without rushing", "最初の8小節 · 急がずにアルペジオを均等に"), "RIGHT"),
        Step("heroic-polonaise-theme", tr("Heroic ท่อนเดียวกับฉบับง่าย · คีย์ A♭ เพิ่มคอร์ด จังหวะประจุด และมือซ้ายที่ย้ายตำแหน่ง", "Heroic, same section as the easy edition · in A♭ with chords, dotted rhythms and a moving left hand", "Heroic やさしい版と同じ部分 · A♭でコード、付点リズム、移動する左手"), "RIGHT"),
        Step("mozart-k545-opening", tr("8 ห้องแรก · แยก Alberti bass กับสเกล เริ่มครั้งละ 2 ห้อง", "First 8 bars · separate the Alberti bass and the scales, 2 bars at a time", "最初の8小節 · アルベルティ・バスとスケールを分けて2小節ずつ"), "RIGHT"),
    )
}
