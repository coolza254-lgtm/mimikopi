# สร้าง ミミコピ ใหม่บน Windows (ออฟไลน์ ได้ไฟล์ .apk)

## ส่วนที่ 1: สิ่งที่ต้องทำเอง (ครั้งเดียว ราว 1–2 ชั่วโมง ส่วนใหญ่เป็นเวลาดาวน์โหลด)

1. **ติดตั้ง Android Studio** จาก developer.android.com/studio
   - เปิดครั้งแรกให้กด Next ตามค่าเริ่มต้นจนจบ มันจะลง Android SDK และ JDK ให้เอง
   - เปิด More Actions → SDK Manager แล้วติ๊ก Android SDK Platform รุ่นล่าสุด, Android SDK Build-Tools และ NDK (Side by side) กับ CMake เพราะเสียงเปียโนใช้โค้ด C++
2. **ติดตั้ง Git for Windows** จาก git-scm.com ไม่ได้ใช้ GitHub แต่ Claude Code ใช้ Git เก็บประวัติในเครื่องและย้อนกลับได้ถ้าพัง
3. **ติดตั้ง Claude Code บน Windows**
   - เปิด PowerShell แล้วทำตามคำสั่งติดตั้งที่ docs.claude.com (หัวข้อ Claude Code → Setup)
   - พิมพ์ `claude` แล้วล็อกอินบัญชีของคุณ
4. **ตั้งค่าตัวแปรระบบ** (ค้นหา "Environment Variables" ใน Start)
   - `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`
   - `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
   - ใน `Path` เพิ่ม `%ANDROID_HOME%\platform-tools` (เพื่อให้มีคำสั่ง `adb`)
5. **สร้างโฟลเดอร์งาน** เช่น `C:\dev\mimikopi2`
6. **เอาเนื้อหาเดิมมาใช้ (แนะนำมาก):** ดาวน์โหลด zip ของโปรเจกต์เดิมจาก GitHub ครั้งเดียว (ปุ่ม Code → Download ZIP) แล้วแตกไฟล์ไว้ที่ `C:\dev\mimikopi-old`
   - ข้างในมีบทเรียน เพลงสาธารณะ ตัวสร้างแบบฝึก ระบบสอบ คอร์สฝึกหู และเอกสาร `docs/learning-system.md` ที่ทดสอบแล้ว
   - ให้ Claude อ่านเป็นต้นแบบ ไม่ต้องเริ่มคิดระบบใหม่จากศูนย์
   - ไม่อยากใช้ก็ได้ แต่จะเสียเวลาและได้ผลไม่ตรงเดิม
7. **เปิดโหมดนักพัฒนาบนมือถือ (Galaxy A54)**
   - ตั้งค่า → เกี่ยวกับโทรศัพท์ → ข้อมูลซอฟต์แวร์ → แตะ "หมายเลขรุ่น" 7 ครั้ง
   - แล้วเข้า ตัวเลือกนักพัฒนา → เปิด USB debugging
8. **เริ่มงาน**
   - เปิด PowerShell ในโฟลเดอร์งาน (`cd C:\dev\mimikopi2`) แล้วพิมพ์ `claude`
   - พิมพ์ `/config` แล้วเปิด ultracode หรือพิมพ์คำว่า ultracode ในข้อความ
   - วาง prompt ในส่วนที่ 2 ทั้งก้อน
9. **ระหว่างทำ**
   - Claude จะถามขออนุญาตรันคำสั่ง ให้กดอนุญาตได้ ถ้าไม่แน่ใจให้ถามมันว่าคำสั่งนั้นทำอะไร
   - ทุกครั้งที่จบช่วง มันจะบอกว่าต้องลองอะไรบนมือถือ
10. **ติดตั้งลงมือถือ**
    - เสียบสาย USB แล้วกด "อนุญาต" บนมือถือ จากนั้นพิมพ์ `adb install -r app\build\outputs\apk\release\app-release.apk` หรือบอก Claude ให้ติดตั้งให้
    - อีกทางคือก๊อปไฟล์ .apk ลงมือถือแล้วแตะติดตั้ง
11. **สำรองไฟล์กุญแจเซ็นแอพ (สำคัญ):** Claude จะสร้างไฟล์ `release.jks` ไว้นอกโปรเจกต์
    - ให้ก๊อปไฟล์นี้กับรหัสผ่านเก็บไว้ที่ปลอดภัย เช่น USB หรือ password manager
    - ถ้าไฟล์นี้หาย จะอัปเดตแอพทับของเดิมไม่ได้ ต้องลบแล้วลงใหม่ และข้อมูลจะหาย ยกเว้นที่สำรองเป็นไฟล์ไว้
12. **ย้ายความคืบหน้าจากแอพเก่า:** ในแอพเก่า (v0.26.0) ให้ไป ตั้งค่า → สำรองข้อมูล → ส่งออกไฟล์ แล้วนำเข้าในแอพใหม่
    - prompt สั่งให้แอพใหม่อ่านไฟล์สำรองของแอพเก่าได้
    - แอพใหม่ใช้ชื่อแพ็กเกจต่างจากเดิม จึงติดตั้งคู่กันได้ระหว่างลอง

## ส่วนที่ 2: Prompt (ก๊อปทั้งหมดในกรอบไปวาง)

```text
ultracode

You are building "ミミコピ (Mimikopi) 2", a personal Android piano-practice app, from scratch on this Windows PC.
Work in the current folder. Use git locally for history (commit after every working milestone); never push anywhere.

## Who it is for
- One user, "Id": a Thai adult beginner on a Samsung Galaxy A54 (Android 16).
- Pianos: a Badger C-918 (USB MIDI) and a BLE MIDI piano.
- Goal: play Hello! Project and other J-pop idol songs, with honest, visible long-term progress.
- All user-facing text in Thai. Note names in katakana (ド レ ミ ファ ソ ラ シ) or letters, the user's choice.
- Code, comments and commit messages in English.

## Reference implementation (read first)
The previous version is in C:\dev\mimikopi-old (if present). It is a working, tested Kotlin app; treat it as the
specification of WHAT the app does, not of how it looks:
- docs/learning-system.md: the full learning design (sections 2.5-2.10 are the latest decisions).
- core/ (pure Kotlin): music theory, timing judge (PlayAlongJudge), skill levels and tests (SkillBook), daily plan,
  generators (scales with fingering, sight-reading, accompaniment), ear course (EarCourse/EarProgress), grade exams
  (Exams/ExamScales), mistakes summary, progress history, idol-song goal, backup format.
- content/ (lessons, public-domain pieces, exercises).
- docs/KNOWN-LIMITATIONS.md (what was decided and why).
Reuse the core logic and content (copy, then improve) and keep their unit tests. Rebuild the UI. If the folder is
missing, design these systems yourself from the feature list below.

## Hard rules
- 100% offline, native Android (Kotlin + Jetpack Compose). The INTERNET permission is not in the manifest at all.
  The piano sound samples ship inside the APK, or come from a file the user picks. No accounts, analytics, ads,
  update checks or cloud.
- Output: a signed release APK that installs on the A54 and updates in place. Package id com.id.mimikopi2.
  Create the keystore OUTSIDE the project folder (e.g. %USERPROFILE%\keys\mimikopi2-release.jks). Read the passwords
  from %USERPROFILE%\.gradle\gradle.properties, never from files inside the project. Tell me where the keystore is
  and remind me to back it up.
- No gamification (no stars, streaks, rewards, characters). No reminders or notifications.
- Never bundle copyrighted scores or lyrics. Song facts that are not verified are labelled as such.
- No placeholder features or dead screens: every visible control works.
- Black-and-white design. The only colour is a green "Connected" mark for MIDI.
- minSdk 26, targetSdk latest; it must run smoothly on a Galaxy A54 (no jank while playing).

## Features (keep all from the old app)
1. MIDI: USB and BLE input.
   - Plug in, unplug and reconnect handled; latency calibration.
   - Velocity on/off.
   - An on-screen keyboard appears only when no MIDI piano is connected.
2. Sound: the app's own sampled grand piano (low latency, C++/Oboe as in the old audio module) with volume, reverb
   and warmth, plus a metronome with an accurate count-in.
3. Today: a daily plan for the minutes chosen (10/20/30/45/60). It is ticked only by real practice time, extra time
   goes to the weakest skill, and one button leads to the next part.
4. Skills: 7 skills (keys, note reading, rhythm, sight-reading, technique/scales, ear, chords). Levels come only from
   short fixed-criteria tests, a level drops after two failed re-tests, and every test result is shown with its
   numbers.
5. Practice runners: note and key drills; a score reader (wait mode and tempo mode, hands separately, the other hand
   played by the app, section loops, a tempo ramp, falling-notes view, page view); the reading ladder; chord practice
   with a chord book showing left-hand and right-hand shapes; review on spaced repetition.
6. Ear course: 7 kinds of question in 10 adaptive stages. Melodies are played back on the piano, rhythms are tapped
   back. Progress is shown per kind.
7. Grade exams modelled on ABRSM, Initial to Grade 5.
   - Parts: 2 library pieces, an app-made pop piece, scales and arpeggios, sight-reading with a 30 s look, aural.
   - Marks: 150 total; pass 100, merit 120, distinction 130.
   - Rules: each part is taken once; leaving mid-run does not count; the screen stays on while playing.
8. Progress page:
   - level history and minutes per week;
   - ear accuracy and exam results;
   - "what to fix", built from repeated mistakes.
9. Goal page: the first three idol target songs, the skill levels each needs, and practice in their style.
10. Free piano with transpose. A songs list from the public-domain library.
11. Backup: export and import a single file. Import must also accept the old app's backup format, so my progress
    moves over.

## What to improve: UX/UI (the main point of this rebuild)
Make it feel like an app Apple would make: calm, clear, fast, usable one-handed and while sitting at the piano.
- One obvious next action on every screen. Fewer taps to start practising: from opening the app to playing in at
  most 2 taps.
- Large, readable type. Thai-friendly line heights. Big touch targets (at least 48 dp). Works in landscape on a
  music stand.
- While playing, nothing distracts: the notes, the keyboard and one stop button.
  - Feedback right after each run: one sentence about what went wrong and one button for what to practise.
- Clear, consistent navigation: 4 tabs (Today, Skills, Piano, Songs), back always goes where expected, and state is
  kept when switching tabs.
- Dark and light themes. Smooth 60 fps animations that are short and subtle.
- Empty, loading and error states written in plain Thai, and they say what to do.
- Before you build the screens, write docs/UX.md: user journeys, a screen map, and wireframes as ASCII sketches.
  Show it to me and wait for my OK.

## How to work
- Phase 0: read the reference implementation. Propose the architecture (modules: core, audio, app) and the UX plan.
  Ask me only the questions that block you.
- Then build in phases. Each phase ends with:
  - the build passing (gradlew assembleRelease) and all unit tests green;
  - a commit;
  - a short Thai report in two parts: (1) checked by tests, (2) what I must check on the phone, as a checklist.
- Use ultracode workflows for substantive work: parallel reviewers and adversarial verification of findings before
  each phase is called done. Check fingering, timing and marking logic with tests.
- After each phase, build the APK and, if the phone is connected (adb devices), install it.
- Keep docs/learning-system.md, docs/KNOWN-LIMITATIONS.md and docs/CHANGELOG.md (Thai) current.
```

## ส่วนที่ 3: เคล็ดลับ
- **ถ้ามันถามคำถาม** ตอบสั้นๆ เป็นภาษาไทยได้
- **ถ้าจะหยุดกลางคัน:** พิมพ์ "สรุปงานที่ค้างแล้ว commit" ก่อนปิด ครั้งหน้าเปิด `claude --continue` ในโฟลเดอร์เดิม
- **ถ้า build พัง:** บอกมันว่า "build ไม่ผ่าน แก้ให้หน่อย" มันจะอ่าน error เอง
- **ถ้าแอพบนมือถือมีปัญหา:** ถ่ายภาพหน้าจอแล้วลากไฟล์ใส่หน้าต่าง Claude พร้อมเล่าว่ากดอะไรแล้วเกิดอะไร
