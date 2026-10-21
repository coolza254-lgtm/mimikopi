# Mimikopi Desktop (Windows)

The desktop version continues from mobile v0.29.0. It uses the same `core/` as the mobile app (lessons, pass criteria, history, skill tests, ear training, Heroic plan), and the same database format and backup files, so data moves back and forth between the two.

## Structure

| Part | Location | Contents |
|---|---|---|
| Separate Gradle build | `desktop/` | Includes `:core` from `../core` and uses the version catalog from `../gradle`, so the Android build is not touched |
| `:platform` | `desktop/platform/` | Pure JVM: SQLite (same schema as Room v1), backups, merging, MIDI (`javax.sound.midi`, input only), computer keyboard, sessions for each page (ported from the Android ViewModels; they write the same `paramsJson` and history rows) |
| `:app` | `desktop/app/` | Compose Desktop UI, Thai throughout, packaged as MSI |
| Native audio | `desktop/native/` | miniaudio 0.11.25 → WASAPI, locked to shared mode with 256-frame periods (no setting to change), sampler and effects shared with the Android engine (`audio/src/main/cpp/effects.h`), one clock for audio, MIDI and judging that already includes the output delay |

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
./gradlew -p desktop :app:packageMsi :app:createDistributable -PversionName=0.31.0

# Draw real score layouts to HTML (checking the engraving without the apps)
MIMIKOPI_ENGRAVE_DIR=/tmp/engrave ./gradlew -p desktop :core:test --tests '*EngravingPreview*'
```

- `:app` needs Google Maven (androidx), so a machine that blocks `dl.google.com` can only build `:core` and `:platform`.
- CI ([`.github/workflows/desktop.yml`](../.github/workflows/desktop.yml)) runs everything on both Linux and Windows and uploads the MSI and app folder as an artifact of the run. Started by hand with **prerelease** ticked, it also publishes them as a pre-release `desktop-v<version>` of this repository; the program's updater reads those.
- Every Windows build is version `0.31.<run number>`, so a newer build always upgrades an older one. The MSI version is `1.x.y` because Windows Installer requires a major version greater than 0; the program shows `0.x.y`.

## Main features

- **Look.** Minimal pastel from the app icon (warm white, plum text, deep pink for the main action), the same on the phone. Light by default; dark can be chosen.
- **Big, standard score.** Lines fill the window width like printed music. A one-hand exercise shows only its staff, so the notes are about twice as large. Beams slope with the melody, finger numbers sit in one row, the time signature is printed on the first line only. Use F11 for full screen; while playing only a thin bar stays.
- **Feedback while playing.** A soft pink band marks the place; a note played right turns green with a short ring; a wrong key shows a clear red cross at its pitch, then fades; early or late notes are amber.
- **Falling notes.** For songs, notes coloured by hand fall to the keys; judged keys light up. A run in this mode does not count toward passing.
- **Today.** The same daily plan as the phone, easy to hard (review of notes already known first, no finger warm-up), 10–60 minutes (20 by default), one Start on the part to do now.
- **Clean practice pages.** A lesson step or a test shows only Start (and Listen where allowed); a song shows the mode, the hands and the tempo, with the rest under "more".
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
- **Chords.** By name, by roman numeral or changes against the clock, and a chord book with finger numbers for each hand.
- **Progress.** Three numbers first (practice time this week, total level, ear accuracy, each against the week or month before), then the details: level over 4 months, weekly minutes, ear accuracy per week, past exam results and repeated mistakes with where to practise each.
- **Updates.** Settings → Update: check for a new version, then download, verify (SHA-256) and install from inside the program; it closes and opens again. Nothing is checked without the button.
- **Moving data from mobile.**
  - Open a mobile backup file in Settings → Data. Choose "merge" (keeps what is already on the computer) or "replace".
  - Merging is row by row. An attempt is identified by exercise + start time + hand + parameters, and is never matched by the database's own ID numbers.
  - A backup is always made before importing.

## What has been checked, and how

| Item | Checked with | Status |
|---|---|---|
| core rules (233 tests: plan, engraving, updates…) | unit test on Linux and Windows CI | pass |
| Database, backup, merging with mobile data, no loss on close | unit test (`DataTransferTest`, `HistoryMergeTest`) | pass |
| Drill/reader sessions: wait mode, real tempo, Heroic, ear | unit test with simulated notes and clock | pass |
| Native audio: loads the library, plays notes, sampler, null backend | unit test on Linux and Windows (WASAPI library built with MSVC) | pass |
| Salamander sample pack (real, 79 MB) | opt-in test `MIMIKOPI_PACK_TEST=1` | passed on Linux |
| Page appearance | offscreen screenshots (`ScreenshotTest`) in both themes, 1280×800 | images are generated in CI |
| MSI / app folder | built on `windows-latest` CI, then checked: the exe and the audio DLL are in the app folder, and SHA-256 is printed | builds (MSI about 79 MB, app ZIP about 77 MB); not yet installed on a real machine |

**Not yet tested on a real machine** (needs Windows with a real sound card and a real MIDI piano):

- The real output delay in shared mode on each sound card (the metronome page's self-test shows it).
- MIDI hot-plugging with real drivers (Windows' `javax.sound.midi` sees only WinMM ports).
- Installing and uninstalling the MSI on a real machine, including Start-menu and desktop shortcuts.
- Thai fonts on machines that have no Thai fonts at all. The program uses the system fonts. Bravura (the music font) is bundled.

## Not done yet

- **Grade exams:** retired on the phone as well; past results show on the Progress page.
- **The in-program updater** works from the first build that has it: a computer with an older build installs the next one by hand once.
- **LAN sync between the computer and phone:** not done. Use backup files. Private songs are excluded from automatic sync by design (export/import JSON only).
- **Code signing:** the installer is not signed, so Windows SmartScreen will warn the first time.
