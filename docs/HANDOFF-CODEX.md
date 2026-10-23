# ส่งต่องาน ミミコピ (Mimikopi) — สำหรับ ChatGPT Codex

อัปเดต 2026-10-23 · รุ่นล่าสุดที่ปล่อย: **desktop-v0.36.0** · branch ทำงาน: `ccr-90326602-b8qis8`

## ภาพรวม
- แอปฝึกเปียโนส่วนตัว **บน PC (Windows) อย่างเดียว** แอปมือถือ Android ถูกเลิกแล้ว (ลบ `app/`, `audio/`, CI มือถือ, release APK ออกจาก repo)
- ผู้ใช้คนเดียว ภาษาไทยเป็นหลัก (มีอังกฤษ/ญี่ปุ่น) ข้อมูลอยู่ในเครื่อง
- บทบาท: **ผู้ใช้ออกแบบเอง** (canvas/รูป) แล้วให้ Codex เขียนโค้ดตามแบบ

## โครงสร้าง
| ที่ | คืออะไร |
|---|---|
| `core/` | กฎทั้งหมด (Kotlin JVM ล้วน): บทเรียน, ตัดสินโน้ต `playalong/`, `score/` engrave, `i18n/` (`tr(th,en,ja)`), `fx/AnswerSound.kt`, sync |
| `desktop/platform/` | เก็บข้อมูล (SQLite), เสียง, MIDI, session ของแต่ละหน้า (`session/ReaderSession.kt`, `RecordSession.kt` …) |
| `desktop/app/` | UI Compose Desktop: `ui/screens/*Screen.kt`, `ui/components/` (`Design.kt`, `FallingNotes.kt`, `PianoKeys.kt`), `ui/score/` (`ScoreView`, `LiveScore`), `ui/theme/Tokens.kt`, ฟอนต์ใน `desktop/app/fonts/` |
| `desktop/native/` | ไลบรารีเสียง C++ (miniaudio, CMake) |
| `content/` | เนื้อหาบทเรียน/แบบฝึก (แก้ได้), แปลใน `core/src/main/resources/i18n/content-*.json` |
| `docs/learning-system.md` | สเปกระบบการเรียน (ต้นฉบับ) · `docs/DESKTOP.md` · `docs/ARCHITECTURE.md` |
| `release-notes/vX.Y.Z.md` | โน้ตรุ่นภาษาไทย (ต้องมีก่อนปล่อย) · `docs/CHANGELOG.md` |

## สร้าง / ทดสอบ / ปล่อย
- ทดสอบ: `./gradlew -p desktop :core:test test` (ต้อง build `desktop/native` ด้วย CMake ก่อนสำหรับเทสต์เสียง)
- รัน: `./gradlew -p desktop :app:run`
- CI: `.github/workflows/desktop.yml` (Linux tests + Windows MSI)
- **ปล่อยรุ่น**: เขียน `release-notes/vX.Y.Z.md` → รัน workflow **Desktop** (workflow_dispatch) ใส่ `version` และติ๊ก `release` → ได้ release `desktop-vX.Y.Z` (MSI + zip) โปรแกรมอัปเดตตัวเองจาก release นี้

## กติกาโค้ด
- ข้อความบนจอทุกอันผ่าน `tr("ไทย", "English", "日本語")`; ถ้า `$ตัวแปร` ตามด้วยอักษรญี่ปุ่นให้เขียน `${ตัวแปร}`
- ห้ามฝัง GitHub token ในแอป (ผู้ใช้ใส่เองตอนกด Sync ครั้งแรก: fine-grained token, Contents read/write เฉพาะ repo นี้)
- ไม่แสดงเลขบท/เลขขั้น, ไม่ใช้คำว่า "วันนี้", ป้ายผ่านสีเขียว, แถวที่ล็อกเป็นสีเทา
- ตอบถูก: ✓ เขียว + เสียงเบา; ตอบผิดในโหมดไหล/โน้ตตก: แถบจางเป็นสีเทา ไม่มี ✕

## ทำเสร็จแล้ว (ล่าสุด)
- v0.36.0: โน้ตตกแบบใหม่ (แถบชมพู/เหลืองเรืองแสง พื้นมืด แสงวาบเมื่อถูก จางเทาเมื่อพลาด % สด), เล่นตามโน้ตแบบแถวเดียวไหลผ่านเส้นเล่นคงที่ (`ScoreView(fixedPlayhead = true)`), ปุ่ม ‹ ย้อนห้อง / ↻ เล่นห้องนี้ใหม่ (`ReaderSession.replayFrom`, ปุ่มลูกศรซ้าย), ปุ่มชื่อโน้ต
- ทวนเพลงแบบเว้นระยะ (`core/playalong/SongReview.kt`), อัดโน้ตที่ซื้อ (`ScoresScreen.kt`, `RecordSession.kt`), เอฟเฟกต์ตอบถูก/ผิด, ไอคอน Windows, 3 ภาษา

## งานที่ค้าง (ตามแบบที่ผู้ใช้อนุมัติแล้ว)
1. เล่นตามโน้ต: กุญแจซอลติดซ้ายพร้อมเฟดขาว, ชื่อโน้ต (ド レ ミ) อยู่ในหัวโน้ต, โน้ตที่เล่นแล้วเป็นสีเขียว, หัวชื่อเพลงใหญ่ (ไอคอน ♪ พาสเทล, ชื่อ 30px Zen Maru Gothic, บรรทัดรอง "ผู้แต่ง · มือ · ตามจังหวะ · ♩ = 72", ป้าย "ห้อง 5 / 16" ขวา)
2. โน้ตตก: ย้าย % ไปต่อท้ายชื่อเพลง ขนาดเท่ากัน สี `#8FE3A8`; ตัวนับ ถูก/ช้าไป/ผิด ด้านล่าง; แถบความคืบหน้าและป้ายด้านบน
3. ทำทุกหน้าให้ตรงแบบ (เริ่มที่ หน้าแรก/ทักษะ/ทักษะย่อย) และเอาข้อความอธิบายในแบบฝึกอ่านโน้ตออก
4. แผนการเรียนขั้นต่อไป: ทำนองพร้อมคอร์ด (ขั้น 4), เพลงเป้าหมาย → ทักษะ (ขั้น 5), อัดคอร์ดเป็นเลขโรมัน

## หมายเหตุ
- ยังไม่ได้ทดสอบบนเครื่องจริงหลายส่วน ให้ถามผู้ใช้ผลการลองเสมอ
- แบบ (design canvas) อยู่ใน Claude artifact ส่วนตัวของผู้ใช้ — ผู้ใช้จะส่งรูป/สเปกให้เอง
