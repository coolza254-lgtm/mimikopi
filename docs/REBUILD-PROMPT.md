# สร้าง ミミコピ ใหม่เป็นโปรแกรมบน Windows อย่างเดียว (ออฟไลน์)

ผลลัพธ์ที่ได้คือตัวติดตั้ง `.msi` ติดตั้งแล้วเปิดจากเมนู Start ได้เลย ใช้กับเปียโน USB MIDI และฝึกหน้าคอม

## ส่วนที่ 1: สิ่งที่ต้องทำเอง (ครั้งเดียว ราว 30–60 นาที)

1. **ติดตั้ง JDK 21:** ดาวน์โหลด "Temurin 21 (LTS) Windows x64 .msi" จาก adoptium.net ตอนติดตั้งให้ติ๊ก "Set JAVA_HOME"
2. **ติดตั้ง Git for Windows** จาก git-scm.com ไม่ได้ใช้ GitHub แต่ Claude Code ใช้ Git เก็บประวัติในเครื่องและย้อนกลับได้ถ้าพัง
3. **ติดตั้ง Claude Code บน Windows**
   - เปิด PowerShell แล้วทำตามคำสั่งติดตั้งที่ docs.claude.com (หัวข้อ Claude Code → Setup)
   - พิมพ์ `claude` แล้วล็อกอินบัญชีของคุณ
4. **ติดตั้ง WiX Toolset v3** จาก wixtoolset.org (ตัวสร้างไฟล์ .msi)
   - ถ้าไม่อยากลง ให้บอก Claude ใช้แบบ `.exe` หรือโฟลเดอร์ที่รันได้เลยแทน
5. **สร้างโฟลเดอร์งาน** เช่น `C:\dev\mimikopi-pc`
6. **เอาต้นแบบเดิมมาใช้ (แนะนำมาก):** ดาวน์โหลดโค้ดรุ่น v0.25.0 ครั้งเดียวจาก
   `https://github.com/coolza254-lgtm/mimikopi/archive/refs/tags/v0.25.0.zip`
   แล้วแตกไว้ที่ `C:\dev\mimikopi-old`
   - รุ่นนี้ยังมีรุ่น Windows เดิมครบ (`desktop/`) พร้อมระบบทั้งหมดใน `core/` เช่น บทเรียน สอบเกรด ฝึกหู พัฒนาการ และเพลงสาธารณะ
   - ให้ Claude ใช้เป็นต้นแบบ จะได้ระบบเหมือนเดิมเร็วกว่าเริ่มจากศูนย์มาก
7. **เตรียมไฟล์เสียงเปียโน (ไม่บังคับ):** ถ้าต้องการเสียงแกรนด์เปียโนเหมือนในมือถือ ให้หาไฟล์ชุดเสียงเปียโนเดิม (ไฟล์ zip ที่แอพเคยดาวน์โหลด) มาวางในโฟลเดอร์งาน
   - ถ้าไม่มี โปรแกรมจะใช้เสียงเปียโนที่มีใน Windows ก่อน
8. **เริ่มงาน**
   - เปิด PowerShell ในโฟลเดอร์งาน (`cd C:\dev\mimikopi-pc`) แล้วพิมพ์ `claude`
   - เปิด ultracode (พิมพ์ `/config` หรือใส่คำว่า ultracode ในข้อความ)
   - วาง prompt ในส่วนที่ 2 ทั้งก้อน
9. **ระหว่างทำ**
   - Claude จะขออนุญาตรันคำสั่ง ให้กดอนุญาตได้ ถ้าไม่แน่ใจให้ถามว่าคำสั่งนั้นทำอะไร
   - ทุกช่วงมันจะบอกว่าต้องลองอะไรบนคอม และเปิดโปรแกรมให้ลองได้ทันทีด้วยคำสั่ง `gradlew run` โดยไม่ต้องติดตั้ง
10. **ติดตั้งจริง:** เมื่อพอใจ ให้ดับเบิลคลิกไฟล์ `.msi` ที่มันสร้าง
    - Windows อาจเตือน "Windows protected your PC" เพราะไม่ได้เซ็นชื่อ ให้กด More info → Run anyway
11. **ย้ายความคืบหน้าจากมือถือ:** ในแอพมือถือ v0.26.0 ไปที่ ตั้งค่า → สำรองข้อมูล → ส่งออกไฟล์ ส่งไฟล์มาที่คอม แล้วนำเข้าในโปรแกรมใหม่
    - prompt สั่งให้โปรแกรมอ่านไฟล์สำรองของแอพมือถือได้

## ส่วนที่ 2: Prompt (ก๊อปทั้งหมดในกรอบไปวาง)

```text
ultracode

You are building "ミミコピ (Mimikopi) PC", a personal piano-practice program for Windows only, from scratch on this PC.
Work in the current folder. Use git locally for history (commit after every working milestone); never push anywhere.

## Who it is for
- One user, "Id": a Thai adult beginner who practises at the computer.
- Piano: a Badger C-918 digital piano over USB MIDI (a BLE MIDI piano may also be paired through Windows).
- Goal: play Hello! Project and other J-pop idol songs, with honest, visible long-term progress.
- All user-facing text in Thai. Note names in katakana (ド レ ミ ファ ソ ラ シ) or letters, the user's choice.
- Code, comments and commit messages in English.

## Reference implementation (read first)
C:\dev\mimikopi-old is the previous version (v0.25.0), a working, tested Kotlin project:
- desktop/ is the old Windows version: Compose Multiplatform, javax.sound.midi input with plug/unplug polling, a
  sampled piano and Java synth, a metronome, a JSON data store in %APPDATA%, MSI packaging. Its screens were
  phone screens ported with a shim layer: do NOT keep that shim. Write the UI natively for the desktop.
- core/ (pure Kotlin) holds all the logic: music theory, timing judge (PlayAlongJudge), skill levels and tests
  (SkillBook), daily plan, generators (scales with fingering, sight-reading, accompaniment), ear course, grade
  exams, mistakes summary, progress history, idol-song goal, backup format. Reuse it as-is, with its tests.
- content/ holds the lessons, public-domain pieces and exercises.
- docs/learning-system.md is the learning design (sections 2.5-2.10 are the latest decisions).
- docs/KNOWN-LIMITATIONS.md records what was decided and why.
Ignore app/ (Android) except as a reference for features and Thai wording.

## Hard rules
- Windows only, 100% offline. Kotlin + Compose Multiplatform for Desktop (JVM 21). No network code at all: no
  update checks, accounts, analytics or cloud.
- Deliverables: `gradlew run` for trying it out, and a .msi installer (jpackage through the Compose plugin; if WiX is
  missing, also offer an app-image folder). Data in %APPDATA%\MimikopiPC with automatic local backups.
- No gamification (no stars, streaks, rewards, characters). No reminders or notifications.
- Never bundle copyrighted scores or lyrics. Unverified song facts are labelled as such.
- No placeholder features or dead screens.
- Black-and-white design. The only colour is a green "Connected" mark for MIDI.
- It must feel instant on a normal laptop: no jank while playing. Audio latency as low as Java allows (small buffers,
  a measured latency calibration, and a clear note if the Windows mixer adds delay).

## Features (all of the old app, desktop-first)
1. MIDI:
   - USB MIDI with plug/unplug detection and reconnect.
   - A "used by another program" message.
   - Latency calibration and a velocity on/off switch.
   - Without a piano: the computer keyboard (A S D F ... rows) and mouse clicks on an on-screen keyboard.
2. Sound: the sampled grand piano (load the old pack if present, else the Java synth), with volume, a metronome and an
   accurate count-in.
3. Today: a daily plan for the minutes chosen.
   - It is ticked only by real practice time.
   - Extra time goes to the weakest skill.
   - One button leads to the next part.
4. Skills: 7 skills, with levels earned only from fixed-criteria tests.
   - A level drops after two failed re-tests.
   - Every test result is shown with its numbers.
5. Practice:
   - note and key drills;
   - a score reader: wait and tempo modes, hands separately, the other hand played by the app, loops, a tempo ramp,
     falling notes and page view; use the wide screen, e.g. score on top, keyboard below, side panel for options;
   - the reading ladder and review on spaced repetition;
   - chord practice with a chord book.
6. Ear course (7 kinds, 10 adaptive stages), grade exams modelled on ABRSM (Initial to Grade 5, 150 marks, each part
   taken once), progress page with "what to fix", idol-song goal page, free piano with transpose, songs list.
7. Backup: export and import one file. Import must also accept the phone app's backup format (v0.26.0), so the
   user's progress moves over.

## What to improve: desktop UX/UI (the main point of this rebuild)
Make it feel like a polished Mac/Windows app: calm, clear, fast, made for sitting at the piano with the computer in
front.
- A left sidebar (Today, Skills, Piano, Songs) and a large main area.
  - Each section keeps its own page stack.
  - Window size and position are remembered.
- Keyboard shortcuts:
  - Space: start/stop; Esc: back; R: restart; +/-: tempo.
  - Show them in tooltips and in a "Shortcuts" sheet.
- From opening the program to playing: at most 2 clicks (Enter on Today starts the next part).
- While playing:
  - only the music, the keyboard and the stop control; a large, readable score sized to the window;
  - feedback after each run: one sentence about what went wrong and one button for what to practise;
  - the screen saver and sleep are blocked during a run.
- Large, Thai-friendly typography. Clear hover, focus and pressed states. Dark and light themes. Short, subtle
  animations at 60 fps.
- Empty, loading and error states written in plain Thai, and they say what to do.
- Before you build the screens, write docs/UX.md: user journeys, a screen map, wireframes as ASCII sketches, and
  the shortcut list. Show it to me and wait for my OK.

## How to work
- Phase 0: read the reference. Propose the architecture (modules: core, app-desktop) and the UX plan. Ask me only
  the questions that block you.
- Then build in phases. Each phase ends with:
  - the build and all unit tests passing, plus UI tests that render every screen in both themes off screen;
  - a commit;
  - `gradlew run` started for me to try;
  - a short Thai report in two parts: (1) checked by tests, (2) what I must check with the piano, as a checklist.
- Use ultracode workflows for substantive work: parallel reviewers and adversarial verification before each phase is
  called done.
- Final phase: build the .msi and tell me exactly where it is.
- Keep docs/learning-system.md, docs/KNOWN-LIMITATIONS.md and docs/CHANGELOG.md (Thai) current.
```

## ส่วนที่ 3: เคล็ดลับ
- **ถ้ามันถามคำถาม** ตอบสั้นๆ เป็นภาษาไทยได้
- **ถ้าจะหยุดกลางคัน:** พิมพ์ "สรุปงานที่ค้างแล้ว commit" ก่อนปิด ครั้งหน้าเปิด `claude --continue` ในโฟลเดอร์เดิม
- **ถ้า build พัง:** บอกว่า "build ไม่ผ่าน แก้ให้หน่อย" มันจะอ่าน error เอง
- **ถ้าโปรแกรมมีปัญหา:** กด Win+Shift+S ถ่ายภาพหน้าจอ แล้ววางใส่หน้าต่าง Claude พร้อมเล่าว่ากดอะไรแล้วเกิดอะไร
- **ถ้าเปียโนไม่ขึ้น:** ปิดโปรแกรมอื่นที่ใช้ MIDI อยู่ (เช่น เว็บทดสอบ MIDI หรือ DAW) แล้วกดตรวจใหม่
