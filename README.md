# ミミコピ (Mimi Kopi)

โปรแกรมฝึกเปียโนส่วนตัวบน **Windows (PC อย่างเดียว)**: อ่านโน้ต จังหวะ ฝึกหู คอร์ด เล่นตามโน้ต/โน้ตตก และแกะเพลงด้วยหู (耳コピ) ผู้ใช้คนเดียว ภาษาไทย (+ อังกฤษ/ญี่ปุ่น) ข้อมูลอยู่ในเครื่อง

- ระบบมีอะไรบ้าง หลักการเรียน เงื่อนไข: [`docs/SYSTEM.md`](docs/SYSTEM.md)
- สเปกการเรียนฉบับเต็ม: [`docs/learning-system.md`](docs/learning-system.md)
- ส่งต่องาน / งานค้าง: [`docs/HANDOFF-CODEX.md`](docs/HANDOFF-CODEX.md)
- โปรแกรม Windows: [`docs/DESKTOP.md`](docs/DESKTOP.md) · สถาปัตยกรรม: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · ใบอนุญาต: [`CREDITS.md`](CREDITS.md)

## โครงสร้าง
- `core/` กฎและเนื้อหาที่ไม่ขึ้นกับจอ (Kotlin JVM)
- `desktop/platform/` เก็บข้อมูล เสียง MIDI session · `desktop/app/` หน้าจอ Compose Desktop · `desktop/native/` เสียง C++
- `content/` บทเรียนและแบบฝึก · `release-notes/` โน้ตรุ่นภาษาไทย

## สร้างและปล่อย
- ทดสอบ: `./gradlew -p desktop :core:test test` · รัน: `./gradlew -p desktop :app:run`
- ปล่อย: เขียน `release-notes/vX.Y.Z.md` แล้วรัน workflow **Desktop** ใส่ `version` และติ๊ก `release`
