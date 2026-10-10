# Project: "ミミコピ" (Mimi Kopi) — a personal piano learning app (Android first)

**App name (shown to the user):** ミミコピ (katakana; from 耳コピ, "copying music by ear"). Tagline (Thai): "สมุดฝึกเปียโนและแกะเพลงของอิด".
**Identifiers (fixed once chosen, they cannot change later without breaking updates):** `applicationId` = `com.id.mimikopi` (the user may still change this before M1; ask once in M0 and then freeze it). Repos: main `mimikopi` (private), releases `mimikopi-releases` (public).
**Brand assets:** `docs/brand/icon-source.png` (app icon: pink spiral notebook with a mustard music note) and `docs/brand/wordmark-source.png` (ミミコピ wordmark). See section 3 for how to use them.

This is the single, complete prompt. It **supersedes** the earlier `prompt.md` and `mvp-prompt.md` (do not use them). The learning content is specified in `docs/learning-system.md` (Thai); **it is the source of truth for everything about teaching, exercises, pass rules and song targets**. Read both files fully before doing anything else.

Write code, comments, commits and docs in **English**. All **user-facing text and lesson content is Thai** (strings in resource files, English addable later).

---

## 0. Who this is for and how we work

- One user, "Id": Thai speaker, **complete beginner**, Android phone **Samsung Galaxy A54, Android 16**. Practices about 30 min/day (often more).
- **Goal:** read piano sheet music fluently, and play most songs of Hello! Project and AKB48 (lead sheet first, by ear, then full piano score), and eventually write his own arrangements. He plays only (does not sing). First target songs: **I WISH** (Morning Musume) and **心のプラカード** (AKB48); later 恋愛レボリューション21.
- **You are probably running on the user's phone (Claude Code web/mobile).** Assume you may **not** be able to build Android locally. In M0 check what the environment offers (JDK, Android SDK, NDK, Gradle, network allowlist for Maven/Google repos). If local build is impossible, **GitHub Actions is the build system**: you push, CI builds, you read CI logs with `gh`, and the user downloads/installs the APK. Never claim something builds or runs unless a CI run or test proves it.
- **You cannot run the user's phone.** Every milestone report must separate: (1) verified by automated tests/CI, (2) **only verifiable on the phone**, with exact steps and what to send back (preferably the Diagnostics JSON). Never state phone-only behavior (audio latency, timing feel, USB MIDI, touch, performance) works if you only wrote the code.
- Before code: read the two docs, check the environment, then propose a short plan (M0) and **wait for my OK**.
- Commit small. **One branch and one PR per milestone** (`m1-...`), one GitHub issue per milestone with an acceptance checklist, issues for known problems. Report in Thai, briefly, after each milestone.
- The learning content in `docs/learning-system.md` marks which numbers and song facts are unverified. Keep those caveats visible in the app where relevant; do not invent chords, keys or scores for real songs.

## 1. Repositories, signing, install, in-app update

- **Main repo: private** (source, content, docs). **Release repo: separate, public, contains only release APKs and a small `latest.json`** (no source, no content). Reason: the user wants the source private (copyright worry), and the app must check for updates without a secret. The user creates both repos and gives you access; you must not ask for or store tokens in the repo.
- CI (GitHub Actions) in the main repo: on every push/PR run `:core` unit tests, lint, `assembleDebug`, upload the debug APK as an artifact. On a version tag: build the **signed release APK**, compute SHA-256, publish to the **release repo's GitHub Release** (using a fine-grained token stored as an Actions secret, scoped to the release repo only), and update `latest.json` (versionCode, versionName, apk url, sha256, Thai release notes).
- **Signing:** one release keystore, generated once, stored **only as encrypted Actions secrets** (base64 + passwords), never in git. Tell the user clearly that they must keep their own offline backup of the keystore: if it is lost, updates over the installed app become impossible. Same `applicationId` and key for every release.
- **In-app update:** Settings > "Check for updates": fetch `latest.json` from the release repo (unauthenticated), compare versionCode, show Thai release notes, download the APK, **verify SHA-256 before installing**, install via `PackageInstaller` (needs "Install unknown apps" permission for this app once). Never auto-install silently. **Before an update, offer/perform a JSON backup.** Handle failure cleanly (no partial state).
- **IMPORTANT, verify and document in M0:** Google's Android developer verification is being enforced for apps installed outside Play in **Thailand from 30 Sep 2026** (one secondary source; confirm on developer.android.com). Unverified apps may need the "advanced flow" (Developer Mode steps and a waiting period), or the developer registers for a **free limited-distribution account (up to 20 devices)**. Research the current rules, tell the user exactly what to do (register the package name and signing key, or use the advanced flow), and write it into `docs/INSTALL.md` with steps for Android 16 on Samsung One UI. Check whether this also affects in-app updates. Do not guess: say what you confirmed and what you could not.
- Do not commit large binaries naively. Piano samples: propose in M0 either Git LFS (check quota) or a Gradle download task with checksum. `CREDITS.md` lists all sample and font licenses.

## 2. Platform and architecture

- **Android only now.** Kotlin, Jetpack Compose, Room (SQLite). Choose minSdk (justify; MIDI API needs 23+), targetSdk per current Google requirements (Android 16 device; check current Play/target rules).
- **Pure-Kotlin JVM module `:core` with no Android imports**: music theory, note/interval math, rhythm model, spaced repetition (injectable clock), lesson/exercise engine, pass rules, scoring, content models, MusicXML/MIDI parsing and writing. It must be unit-testable on any JVM and be Kotlin-Multiplatform-convertible later. Audio, MIDI, notifications, files and updates live behind interfaces in `:app`.
- **Windows desktop is a later phase and requires the user's explicit approval.** Do not build it; do not make it hard to add.
- Local storage only. No accounts, cloud, analytics. **JSON export/import of all user data** as manual backup (user songs, progress, settings).
- Lesson and knowledge content lives in editable files under `content/` (JSON/Markdown with a schema), not hard-coded.
- Phone-first UI, portrait and landscape. **Sheet screens must be comfortable in landscape** (phone sits on the music stand). Large touch targets, scalable text, good contrast.

## 3. Look and feel

- Theme: **cute music-notebook** (paper texture or soft cream background, hand-drawn-style dividers, pastel accents, rounded cards), friendly like a kids' app, but **easy to read and uncluttered**. Single theme/token file; the UI layer stays thin and logic stays out of composables, because the user will redo the design later in Claude Design.
- **Notation must look like real international standard notation.** Use an open-licensed music font (Bravura/SMuFL, SIL OFL; confirm license). Decoration goes around the notation, never changes the glyphs or engraving rules.
- **Brand and palette (from the user's chosen logo):** cream paper `#FFF6E9` (approximately; sample the exact colors from the source images and record them as tokens), dusty rose `#E8A0A8` as the primary, warm mustard `#F2C14E` as the accent. Use at most these plus neutral text colors; ensure text contrast stays accessible (do not put rose text on cream for small body text; use a darker neutral).
- **App icon:** build an Android **adaptive icon** from `docs/brand/icon-source.png`. The notebook mark is small in the source image (about one third of the canvas), so **crop and scale it** so the mark fills roughly 60% of the 108dp canvas and stays inside the 66dp safe zone; cream as the background layer, the notebook as the foreground, plus a monochrome (themed icon) layer. Vectorize it (redraw as SVG/VectorDrawable) rather than shipping a blurry upscale; the source is a raster AI-generated image, so tell me if you cannot get a clean result and I will ask for a higher-resolution version. Check it at 48px.
- **Wordmark:** use `docs/brand/wordmark-source.png` for the splash/about screen (vectorize or re-set the text in a rounded Japanese font; check the font license, e.g. Zen Maru Gothic under SIL OFL, and add it to `CREDITS.md`). The UI body font must support Thai and Japanese.
- **No game mechanics:** no stars, stickers, characters, rewards. Show useful data instead: accuracy, timing early/late, due reviews, week-over-week comparison, minutes practiced, days practiced (neutral, non-punishing). Encouragement through plain, kind Thai coach text only.

## 4. Sound (the user is serious about sound quality and accuracy)

- **Real sampled acoustic grand piano**, free license (evaluate Salamander Grand Piano, University of Iowa samples; **verify the license yourself**, tell me what you picked and why, add credits screen). Storage size is not a constraint for the user, but keep install and download sensible; report size. Multi-velocity layers, correct mapping across 88 keys, natural release, sustain pedal (CC64), 32+ voices.
- **Low-latency native audio** (Oboe/AAudio is the expected route; justify it and the build risk, especially since you may be building through CI only). SoundPool/MediaPlayer are not acceptable for the piano or metronome. Audio engine behind an interface.
- The Badger C-918 is an electric piano with its own sound; app audio is for touch keyboard, lessons and playback. The user's Bluetooth headphones add latency and the piano does not output Bluetooth audio: the app must **measure and state** latency by output route and let the user choose a latency offset per route (wired/speaker/Bluetooth).

## 5. Metronome (must be excellent)

30–240 BPM, tap tempo, 2/4 3/4 4/4 6/8, accented downbeat, optional subdivisions, separate volume. **Sample-accurate, driven by the audio callback (frame counting), never UI timers.** Must not stutter under UI load. Built-in self-test reporting measured jitter and how it was measured. Usable standalone and inside every practice screen.

## 6. MIDI input

- Android MIDI API, **USB host first** (the user's USB-C data cable arrives in about 2 days), Bluetooth MIDI later. Handle note-on velocity 0 as note-off, fixed-velocity devices, CC64, hot-plug/reconnect, multiple devices.
- **Badger C-918 behavior is unverified** (key count, velocity sensitivity, Bluetooth, MIDI over USB). The user believes it is not velocity-sensitive. Build a **capability probe** after connecting (ask the user to play soft then hard, then record observed velocity range) and expose the result in Diagnostics. If velocity is constant, hide all dynamics exercises (see `learning-system.md` 2.3 and 2.4).
- **Simulated input** (on-screen keys + debug panel) emits the same internal event stream; all logic is written against that stream so everything works without the piano.
- MIDI monitor screen; **latency calibration** per connection type; **octave offset** calibration.
- Provide a short **how to test with the Badger C-918** checklist (USB-C data cable, choose device, play a C scale, check notes, check latency, check pedal if any).

## 7. Diagnostics (essential because you cannot run the phone)

A Diagnostics screen with a **"Copy diagnostics (JSON)"** button: device model, Android version, feature flags (`FEATURE_MIDI`, `FEATURE_USB_HOST`, `FEATURE_AUDIO_LOW_LATENCY`, `FEATURE_AUDIO_PRO`), output sample rate and frames per burst, system-reported output latency (an estimate, say so), audio route, MIDI devices and probe result, metronome jitter, app version/versionCode, last update check. `docs/DEVICE-TESTING.md` explains an external method to measure true touch-to-sound latency (e.g., record screen and speaker with another device).

## 8. Modules (what to build; order is in section 9)

1. **Learning path** (core of the app): implement `docs/learning-system.md` in full: 7 skill tracks, 6 stages, lesson blueprint, exercise engine and generators, pass rules (clean runs, tempo ramp), Leitner spaced repetition, adaptive difficulty, daily session, coach messages, benchmarks every 2 weeks, problem-spot detector. **Enforce reading from day one:** on-screen keys show no note names by default; a "show name" helper exists but is counted, and a lesson passed with helper use does not count as a full pass (`learning-system.md` 2.4). Note-name style switchable (C D E / โด เร มี).
2. **Today screen / daily session:** time options 15/30/45/60 min, blocks per `learning-system.md` section 3, always including a free-play / transcribe block with no scoring. Timer, saved minutes and results, end-of-session summary (what improved, what is due next). **No reminders or notifications for now** (the user will open the app himself); keep a notification interface so it can be added later as an opt-in setting.
3. **Sheet reader and play-along:** grand staff renderer in standard notation, moving cursor, wait-mode and tempo-mode, per-note correct/wrong/early/late, loop sections, separate hands, speed ramp, hand muting, playback with the sampled piano, optional falling-notes view. Landscape-optimized.
4. **Sheet editor** (user writes his own versions): grand staff, clefs, key/time signature, notes/rests, ties, dots, accidentals, chord symbols, tempo, repeats; entry by touch, on-screen keyboard and **MIDI step entry**; import/export **MusicXML and MIDI**, **PDF export**. User scores live in a personal local library. **Never bundle copyrighted scores or lyrics** (Hello! Project, AKB48 or any others); bundle only original exercises you write and public-domain pieces.
5. **Ear training** (E1–E9 in `learning-system.md` section 11): intervals, chords, melody echo, rhythm, pop progressions by degree; answers by MIDI, on-screen keys or buttons; transcribe helper (loop, slow down, check the guess by playing it); stats. Remember the user can hum melodies, so the order is melody first, then bass/root, chords, full chords.
6. **Song library and Song Ladder** (`learning-system.md` section 9): user-entered songs (key, BPM, chords, sections, notes), the "what am I missing" analyzer, transpose mode, the 7-day song-learning pipeline. Start with the two target songs as **empty song shells** with only the facts from 9.6 marked unverified; the user transcribes the content himself.
7. **Piano Pop knowledge library:** Thai reference (theory, scales, chords, J-Pop progressions such as royal road and Komuro, rhythm patterns, practice tips), searchable, notation examples with tap-to-hear, local files.
8. **Progress:** minutes, accuracy trends, lessons passed, benchmark results, simple weekly charts. No gamification.

Content you write (lessons, exercises, original arrangement pieces) must be accurate and in Thai. Mark anything you are not sure of; do not fabricate music facts or song data.

## 9. Milestones (stop and report after each; one PR each)

- **M0 Plan (no code):** environment check, architecture, library choices (audio engine, sample format/loading, MIDI, notation rendering, DB), sample license and size plan, binary handling, signing and release plan, **developer-verification research (section 1)**, risks, minSdk/targetSdk. Wait for my OK.
- **M1 Skeleton + CI + release pipeline:** repo layout, Gradle, Compose shell with Thai strings and theme tokens, Room schema, `:core` with first tests, Diagnostics screen, GitHub Actions producing a debug APK, signed-release workflow to the public release repo, `docs/INSTALL.md`. Done when the user has installed the CI-built APK on the A54.
- **M2 Sound:** sample engine, on-screen keyboard (multi-touch, scrollable, no names by default), audio numbers in Diagnostics, credits screen.
- **M3 Metronome:** audio-callback metronome, jitter self-test, settings.
- **M4 In-app update + backup:** JSON export/import, update check/download/verify/install, tested end to end with two successive versions (the user verifies on the phone).
- **M5 MIDI:** USB MIDI, simulator, monitor, calibrations, capability probe, Badger test checklist.
- **M6 Learning loop (first slice):** staff renderer, lessons 1.1, 1.3, 1.5, spaced repetition, Today screen, local progress. Use the full sample lesson format in `learning-system.md` section 10.
- **M7 Reader and play-along** with scoring, practice tools.
- **M8 Rest of stages 1–3 content + progress screens.**
- **M9 Ear training.**
- **M10 Sheet editor + MusicXML/MIDI/PDF.**
- **M11 Stages 4–6, chords/lead sheets, song library + Song Ladder, pop knowledge library, one original full arrangement.**
- **M12 Polish, hardening, known-limitations list, final report.**
- **Later, only on the user's approval:** Windows desktop (USB MIDI, desktop audio, installer).

Milestones M1–M6 are the first release candidate ("MVP"): it must be usable daily on the phone. If you think a different split is better, say so in M0.

## 10. Quality bar

- Unit tests (JVM, run in CI) for note and interval math, rhythm and timing tolerance, scoring, spaced repetition with a fake clock, pass rules and unlocking, MIDI event handling, exercise generators (determinism with a seed), MusicXML round-trip.
- Timing tolerance setting (strict/normal/forgiving), applied after latency compensation.
- No placeholder features, fake data or dead screens. If something is not done, say so plainly in the report and in the app's known-limitations list.
- No dropped audio when the UI scrolls or animates.
- Privacy: nothing leaves the device except the update check (to the public release repo). No secrets or personal data in git.

## 11. Deliverables

Working source and CI; README (build, test, install on the A54, update); `docs/ARCHITECTURE.md`, `docs/INSTALL.md`, `docs/DEVICE-TESTING.md`, `docs/BADGER-C918-CHECKLIST.md`; `docs/brand/` (logo sources and the final icon files); `CREDITS.md` (include the logo note: the logo was generated with an AI image tool and used for personal use); known-limitations list; per-milestone report in Thai with the two-part verification split from section 0.
