# Mimikopi Desktop (Windows)

The desktop version continues from mobile v0.29.0. It uses the same `core/` as the mobile app (lessons, pass criteria, history, skill tests, ear training, Heroic plan), and the same database format and backup files, so data moves back and forth between the two.

## Structure

| Part | Location | Contents |
|---|---|---|
| Separate Gradle build | `desktop/` | Includes `:core` from `../core` and uses the version catalog from `../gradle`, so the Android build is not touched |
| `:platform` | `desktop/platform/` | Pure JVM: SQLite (same schema as Room v1), backups, merging, MIDI (`javax.sound.midi`, input only), computer keyboard, sessions for each page (ported from the Android ViewModels; they write the same `paramsJson` and history rows) |
| `:app` | `desktop/app/` | Compose Desktop UI, Thai throughout, packaged as MSI |
| Native audio | `desktop/native/` | miniaudio 0.11.25 → WASAPI (exclusive first, falls back to shared), buffer 128/256/512 frames, sampler and effects shared with the Android engine (`audio/src/main/cpp/effects.h`), one clock for audio, MIDI and judging |

Data lives in `%APPDATA%\MimikopiPC` (set `MIMIKOPI_DATA` to use another folder):

- Every write is atomic.
- A daily automatic backup is made in `backups\`.
- A backup is made before every import.

Private songs are kept only in this database and in the backup files you export yourself. They are never in Git or the installer.

## Commands

```sh
# Native library (Linux: gcc; Windows: run from a VS Developer prompt)
cmake -S desktop/native -B desktop/native/build -DCMAKE_BUILD_TYPE=Release
cmake --build desktop/native/build --config Release

# Tests: core + platform + offscreen page screenshots (in desktop/app/build/screenshots)
./gradlew -p desktop :core:test test

# Run the program
./gradlew -p desktop :app:run

# MSI and app folder (Windows only)
./gradlew -p desktop :app:packageMsi :app:createDistributable -PversionName=0.30.0
```

- `:app` needs Google Maven (androidx), so a machine that blocks `dl.google.com` can only build `:core` and `:platform`.
- CI ([`.github/workflows/desktop.yml`](../.github/workflows/desktop.yml)) runs everything on both Linux and Windows. It uploads the MSI and app folder as an artifact of the run.
- The MSI version is `1.x.y` because Windows Installer requires a major version greater than 0. The version shown in the program stays `0.x.y`.

## Main features

- **Big score.** Bars wrap into lines sized to the window height, filling the screen. Use F11 for full screen, and the controls shrink to a thin bar while playing.
- **MIDI.**
  - Plug in a piano and the program finds it within about 1.5 seconds; you can plug and unplug it while the program is running.
  - The on-screen keys hide while a MIDI piano is connected.
  - The piano plays through its own speaker. The program does not play the notes you press a second time, but still plays prompts, answers, demos and the click.
- **No piano.**
  - Mouse: click and drag across the keys.
  - Computer keyboard: the Z and Q rows are white and black keys; `[` and `]` move the octave.
  - Space starts and stops; Esc stops or goes back.
- **Ear training.** One player at a time: prompt → gap → answer → explanation. The keys you press to answer make no sound over the prompt.
- **Per-lesson history.** Every attempt goes in the history, passed or not, with a comparison against previous attempts. The criteria are the same values as on mobile, from `core`.
- **Skills and level tests.** Fixed criteria. Practice time does not raise levels. A level drops after failing twice in a row.
- **Metronome.** Same settings row as the phone, tap tempo, and a self-test that measures click spacing on the clock MIDI is judged on, reported separately from the sound card's timestamp noise and clock drift (ppm).
- **Progress.** Total level over 4 months, weekly minutes, ear accuracy per week, and repeated mistakes, each with where to practise it.
- **Moving data from mobile.**
  - Open a mobile backup file in Settings → Data. Choose "merge" (keeps what is already on the computer) or "replace".
  - Merging is row by row. An attempt is identified by exercise + start time + hand + parameters, and is never matched by the database's own ID numbers.
  - A backup is always made before importing.

## What has been checked, and how

| Item | Checked with | Status |
|---|---|---|
| core rules (226 tests) | unit test on Linux and Windows CI | pass |
| Database, backup, merging with mobile data, no loss on close | unit test (`DataTransferTest`, `HistoryMergeTest`) | pass |
| Drill/reader sessions: wait mode, real tempo, Heroic, ear | unit test with simulated notes and clock | pass |
| Native audio: loads the library, plays notes, sampler, null backend | unit test on Linux and Windows (WASAPI library built with MSVC) | pass |
| Salamander sample pack (real, 79 MB) | opt-in test `MIMIKOPI_PACK_TEST=1` | passed on Linux |
| Page appearance | offscreen screenshots (`ScreenshotTest`) in both themes, 1280×800 | images are generated in CI |
| MSI / app folder | built on `windows-latest` CI | see the latest CI run |

**Not yet tested on a real machine** (needs Windows with a real sound card and a real MIDI piano):

- Actual WASAPI exclusive latency on each sound card, and the measured latency compensation.
- MIDI hot-plugging with real drivers (Windows' `javax.sound.midi` sees only WinMM ports).
- Installing and uninstalling the MSI on a real machine, including Start-menu and desktop shortcuts.
- Thai fonts on machines that have no Thai fonts at all. The program uses the system fonts. Bravura (the music font) is bundled.

## Not done yet

- **Grade exams and the chord book:** these pages are not on the desktop. Results of exams already taken on mobile still appear on the Progress page.
- **LAN sync between the computer and phone:** not done. Use backup files. Private songs are excluded from automatic sync by design (export/import JSON only).
- **Code signing:** the installer is not signed, so Windows SmartScreen will warn the first time.
