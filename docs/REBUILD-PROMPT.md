# สร้าง ミミコピ ใหม่เป็นโปรแกรม Windows (ออฟไลน์) ให้ Claude ออกแบบให้เลือกก่อน

มีไฟล์ที่ใช้ 2 ไฟล์:
- **`CLAUDE.md`:** วางไว้ในโฟลเดอร์งาน Claude Code จะอ่านไฟล์นี้เองทุกครั้งที่เปิด จึงไม่ลืมกฎแม้ทำงานหลายวัน
- **ข้อความเริ่มงาน:** พิมพ์ครั้งแรกครั้งเดียว

## ส่วนที่ 1: เตรียมเครื่อง (ครั้งเดียว ราว 30–60 นาที)

1. **ติดตั้ง JDK 21:** ดาวน์โหลด "Temurin 21 (LTS) Windows x64 .msi" จาก adoptium.net ตอนติดตั้งให้ติ๊ก "Set JAVA_HOME"
2. **ติดตั้ง Git for Windows** จาก git-scm.com ใช้เก็บประวัติงานในเครื่อง ไม่ต้องมี GitHub
3. **ติดตั้ง Visual Studio Build Tools 2022** แล้วเลือก "Desktop development with C++"
   - ใช้คอมไพล์ส่วนเสียงความหน่วงต่ำ
4. **ติดตั้ง WiX Toolset v3** จาก wixtoolset.org ใช้สร้างไฟล์ติดตั้ง .msi
5. **ติดตั้ง Claude Code บน Windows** ตามคำสั่งที่ docs.claude.com (Claude Code → Setup) แล้วพิมพ์ `claude` เพื่อล็อกอิน
6. **สร้างโฟลเดอร์งาน** `C:\dev\mimikopi-pc`
7. **ดาวน์โหลดต้นแบบเดิม** จาก
   `https://github.com/coolza254-lgtm/mimikopi/archive/refs/tags/v0.25.0.zip`
   แล้วแตกไว้ที่ `C:\dev\mimikopi-old` (รุ่นนี้มีทั้งระบบเดิมและรุ่น Windows เดิม)
8. **สร้างไฟล์ `C:\dev\mimikopi-pc\CLAUDE.md`** แล้วก๊อปเนื้อหาจาก **ส่วนที่ 2** ทั้งหมดไปใส่
   - ใช้ Notepad ได้ ตอนบันทึกให้เลือก Encoding เป็น UTF-8
9. **ตั้งค่าเครื่องเพื่อลดเสียงดีเลย์**
   - ใช้หูฟังหรือลำโพงแบบมีสาย ไม่ใช้บลูทูธ
   - ปิด Audio enhancements: Settings → System → Sound → อุปกรณ์ → ปิด
   - ตั้ง Power mode เป็น Best performance
10. **เริ่มงาน**
    - เปิด PowerShell ในโฟลเดอร์งาน (`cd C:\dev\mimikopi-pc`) แล้วพิมพ์ `claude`
    - เปิด ultracode
    - วาง **ข้อความเริ่มงานในส่วนที่ 3**
11. **เลือกดีไซน์:** Claude จะสร้างหน้าตาให้เลือก 3 แบบ เป็นไฟล์ HTML แล้วเปิดในเบราว์เซอร์ให้เอง
    - ดูแล้วตอบได้เลย เช่น "เอาแบบ B แต่ใช้ตัวหนังสือแบบ A" หรือ "แก้หน้าเล่นให้ตัวโน้ตใหญ่กว่านี้"
    - มันจะแก้ให้จนคุณพอใจ แล้วค่อยเริ่มสร้างโปรแกรมจริง
12. **ระหว่างสร้าง:** ทุกช่วงมันจะเปิดโปรแกรมให้ลองด้วย `gradlew run` และบอกรายการที่ต้องลองกับเปียโน
13. **ติดตั้งจริง:** ดับเบิลคลิกไฟล์ `.msi` ที่ได้
    - ถ้า Windows ขึ้นเตือน ให้กด More info → Run anyway
14. **ย้ายความคืบหน้าจากมือถือ:** ในแอพมือถือไปที่ ตั้งค่า → สำรองข้อมูล → ส่งออกไฟล์ ส่งไฟล์มาที่คอม แล้วนำเข้าในโปรแกรมใหม่

ถ้าจะหยุดกลางคัน ให้พิมพ์ "สรุปงานที่ค้างแล้ว commit" ก่อนปิด แล้ววันหลังเปิด `claude --continue` ในโฟลเดอร์เดิม

## ส่วนที่ 2: เนื้อหาไฟล์ `CLAUDE.md`

```markdown
# Mimikopi PC: project rules

A personal piano-practice program for Windows only, rebuilt from the previous app. Read this file fully before any
work. These rules win over anything else unless the user says otherwise in chat.

## The user
- "Id", a Thai adult beginner who practises sitting at the computer.
- Piano: a Badger C-918 digital piano over USB MIDI. It has its own speakers and a headphone jack.
- Goal: play Hello! Project and other J-pop idol songs, with honest, visible long-term progress.
- All user-facing text in Thai. Note names in katakana (ド レ ミ ファ ソ ラ シ) or letters, the user's choice.
- Code, comments and commit messages in English. Reports to the user in Thai.

## Reference implementation
C:\dev\mimikopi-old is the previous version (v0.25.0), a working, tested Kotlin project. It is the specification
of what the program does.
- core/ (pure Kotlin): all the logic. Reuse it with its tests, improve where needed.
  - Music theory and the timing judge (PlayAlongJudge with latency compensation).
  - Skill levels and tests (SkillBook) and the daily plan.
  - Generators: scales with fingering, sight-reading, accompaniment.
  - Ear course, grade exams, mistakes summary, progress history, idol-song goal.
  - The backup format.
- content/: lessons, public-domain pieces and exercises.
- desktop/: the old Windows version. Use it for MIDI handling, the data store, the sample-pack loader and MSI
  packaging. Do NOT copy its UI or its phone-port shim layer, and do NOT copy its javax.sound audio output.
- docs/learning-system.md: the learning design (sections 2.5-2.10 are the latest decisions).
- docs/KNOWN-LIMITATIONS.md: what was decided and why.
- app/ (Android): only a reference for features and Thai wording.

## Hard rules
- Windows only, 100% offline. Kotlin + Compose Multiplatform for Desktop on JVM 21. No network code at all: no
  update checks, accounts, analytics or cloud.
- Data lives in %APPDATA%\MimikopiPC, with automatic local backups.
- Deliverables: `gradlew run` while building, then a .msi installer (Compose packaging with WiX). If WiX is missing,
  also an app-image folder.
- Git: local only. Commit after every working milestone. Never push.
- No gamification (no stars, streaks, rewards, characters). No reminders or notifications.
- Never bundle copyrighted scores or lyrics. Unverified song facts are labelled as such.
- No placeholder features or dead screens: every visible control works.
- The visual style is decided in the Design phase below. Until then, nothing is fixed.

## Audio latency (critical)
- Default: the digital piano makes its own sound and the program only listens and judges (zero added delay). The
  program's own piano sound is an option in Settings.
- The program's sound must NOT use javax.sound (50-150 ms). Use a small native engine, miniaudio (preferred) or
  RtAudio, built with CMake and MSVC and called through JNI or JNA:
  - WASAPI exclusive mode by default, shared mode as fallback, ASIO when a driver is present;
  - 48 kHz, buffer 128 / 256 / 512 frames selectable in Settings (default 256);
  - the sampled grand piano decoded into memory at start, so notes start instantly; polyphony at least 64 with a
    proper release; the metronome and count-in clicks from the same engine on a sample-accurate clock.
- A "Latency test" page:
  - it measures the round trip (the user taps along to clicks) and shows the output latency in ms;
  - it stores the result per output device and feeds it to the judge's latency compensation.
- Warn in Thai if the output device looks like Bluetooth (it adds 150-300 ms), with a suggestion to use wired
  headphones or the piano's speakers.
- MIDI input on its own high-priority thread, with timestamps taken at arrival. No allocation in the audio callback.

## Features (keep all from the old app)
1. MIDI:
   - USB MIDI with plug/unplug detection and reconnect.
   - A clear "used by another program" message.
   - Velocity on/off.
   - Without a piano: computer-keyboard rows and mouse clicks on an on-screen keyboard.
2. Today: a daily plan for the minutes chosen (10-60).
   - It is ticked only by real practice time.
   - Extra time goes to the weakest skill.
   - One button (and Enter) starts the next part.
3. Skills: 7 skills (keys, note reading, rhythm, sight-reading, technique/scales, ear, chords).
   - Levels come only from short fixed-criteria tests.
   - A level drops after two failed re-tests.
   - Every result is shown with its numbers.
4. Practice:
   - note and key drills;
   - a score reader: wait and tempo modes, hands separately, the other hand played by the program, loops, a tempo
     ramp, falling-notes view, page view;
   - the reading ladder and review on spaced repetition;
   - chord practice with a chord book showing left-hand and right-hand shapes.
5. Ear course: 7 kinds of question, 10 adaptive stages, per-kind progress.
6. Grade exams modelled on ABRSM, Initial to Grade 5.
   - Parts: 2 library pieces, an app-made pop piece, scales and arpeggios, sight-reading with a 30 s look, aural.
   - Marks: 150 total; pass 100, merit 120, distinction 130.
   - Rules: each part is taken once; leaving mid-run does not count. Label it as the app's own model, not an
     official grade.
7. Progress page:
   - level history and minutes per week;
   - ear accuracy and exam results;
   - "what to fix", built from repeated mistakes.
8. Goal page: the first three idol target songs, the skill levels each needs, and practice in their style.
9. Free piano with transpose. Songs list from the public-domain library.
10. Backup: export and import one file. Import must also accept the phone app's backup format (v0.26.0).

## UX principles (whichever design is chosen)
- Made for sitting at the piano with the computer in front.
- A left sidebar and a large main area. Each section keeps its own page stack. Window size and position are
  remembered.
- Keyboard shortcuts:
  - Space: start/stop; Esc: back; R: restart; +/-: tempo; Enter: next.
  - Show them in tooltips and in a "Shortcuts" sheet.
- From opening the program to playing: at most 2 clicks.
- While playing:
  - only the music, the keyboard and the stop control; the score scales to the window;
  - screen saver and sleep are blocked;
  - after each run: one sentence about what went wrong and one button for what to practise.
- Large, Thai-friendly type. Clear hover, focus and pressed states. Dark and light themes. Short, subtle motion at
  60 fps.
- Empty, loading and error states written in plain Thai, and they say what to do.

## Phases (do not skip; wait for the user's OK where marked)
0. Read: study the reference. Write docs/PLAN.md:
   - architecture (modules core, audio-native, app);
   - what is reused and what is rewritten;
   - risks.
   Ask only the questions that block you.
1. Design (wait for the user's choice):
   - Create design/ with THREE distinct visual directions, each a self-contained HTML file (inline CSS, no internet,
     real Thai text and realistic data).
   - Suggested directions:
     A "Apple calm": white or black, generous space, SF-like type.
     B "Paper and ink": warm off-white, editorial serif headings, like printed music.
     C "Studio dark": dark focus mode, high-contrast score, subtle accent.
   - Each file shows the same 4 screens side by side or as tabs:
     1. Today;
     2. the score reader while playing;
     3. the result after a run;
     4. the progress page.
     Include a light/dark toggle where it makes sense.
   - Add design/index.html linking all three, with a short Thai note on the idea behind each.
   - Open index.html in the default browser (start "" design\index.html).
   - Ask the user to choose or mix. Revise until they say OK.
   - Then write docs/DESIGN.md: colour tokens, type scale, spacing, components, motion. All UI code must follow it.
2. Foundation:
   - the Gradle project and core ported with all tests green;
   - the native audio engine with the latency test page;
   - MIDI input;
   - the data store and backup import (including the phone format).
3. Screens, in this order: Today → score reader → Skills and tests → ear course → chords → exams → progress and
   goal → free piano and songs → settings.
4. Polish and package: performance pass, accessibility (focus order, contrast), the .msi.

Each phase or screen group ends with:
- the build and all tests passing, plus off-screen render tests of every screen in both themes;
- a commit;
- `gradlew run` started for the user;
- a short Thai report in two parts: (1) checked by tests, (2) what to check with the piano, as a checklist.

Use ultracode workflows for substantive work: parallel reviewers and adversarial verification of findings before a
phase is called done. Keep docs/KNOWN-LIMITATIONS.md and docs/CHANGELOG.md (Thai) current.
```

## ส่วนที่ 3: ข้อความเริ่มงาน (พิมพ์ครั้งแรก)

```text
ultracode
อ่าน CLAUDE.md ในโฟลเดอร์นี้ให้ครบ แล้วเริ่ม Phase 0 และ Phase 1 ได้เลย
ทำแบบดีไซน์ 3 แบบให้ฉันเลือกก่อน ยังไม่ต้องเขียนโปรแกรมจริงจนกว่าฉันจะเลือกดีไซน์และบอกว่าโอเค
```
