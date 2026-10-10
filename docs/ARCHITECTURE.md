# Architecture

## Principles
- **Logic in `:core`, pure Kotlin/JVM.** Everything that decides something (music theory, timing
  judgement, pass rules, spaced repetition, generators, scoring, file formats) lives in `:core`,
  has no Android imports, takes time from an injected `Clock`, and is unit-tested in CI. This keeps it
  convertible to Kotlin Multiplatform for a possible Windows version later.
- **Thin UI.** Composables render state from ViewModels and send events back. Visual design comes from
  one token file (`app/.../ui/theme/Tokens.kt`) so the look can be redone without touching logic.
- **Numbers in config.** The tunable values from `docs/learning-system.md` live in
  `content/config/learning.json`; `LearningConfig` defaults mirror it and a test keeps them equal.
- **Local only.** Room database on the device. The only network access (from M2) is the update check
  against the public releases repo.

## Modules

```
core/   com.id.mimikopi.core
  music/     SpelledPitch, Letter, NoteNameStyle, Motion (repeat/step/skip on the staff)
  practice/  TimingRules (6.2), RunSummary.isClean (6.1), TempoRamp (6.3)
  srs/       Leitner 5-box scheduler (6.6)
  config/    LearningConfig (+ content/config/learning.json)
  diag/      DiagnosticsReport model (JSON)
  time/      Clock
app/    com.id.mimikopi
  data/db/   Room: practice_sessions, attempts, attempt_events, item_stats, settings
  diag/      DiagnosticsCollector (reads device facts)
  ui/        theme, components, home, diag, about
engine/ (M3) C++ / Oboe sampler + metronome behind an AudioEngine interface
```

## Data model (Room, schema v1)
| Table | Purpose |
|---|---|
| `practice_sessions` | Daily session: start/end, planned minutes, active seconds |
| `attempts` | One exercise attempt: exercise id, generator params JSON (incl. seed), hand, tempo, accuracy, timing, stumbles, clean, helper uses |
| `attempt_events` | Expected vs played note per attempt: pitches, onsets, latency-compensated offset, velocity |
| `item_stats` | Leitner state per item (`note:G4:treble`, `key:C`, ...) |
| `settings` | Key/value settings, kept in the DB so one JSON backup covers everything |

Migrations are explicit; there is no destructive fallback, so user progress is never wiped silently.

## Input and timing (from M3/M5)
All input sources (USB MIDI, the on-screen keyboard, the debug simulator) emit one stream of timestamped
events on a single monotonic clock. Audio output time is mapped onto the same clock with the Oboe stream
timestamp. Judging subtracts the calibrated latency of the active route, then applies the stage tolerance.

## Build and release
- `ci.yml`: every push/PR runs `:core:test`, `:app:lintDebug`, `:app:assembleDebug`, and uploads the APK.
- `release.yml`: on `vX.Y.Z` tags it builds the APK signed with the release key from Actions secrets,
  verifies the signature, then publishes `mimikopi-vX.Y.Z.apk` and `latest.json` to
  `coolza254-lgtm/mimikopi-releases`.
- Toolchain: AGP 9.3 (built-in Kotlin), Gradle 9.6, Kotlin 2.4, KSP 2.3, Room 2.8, Compose BOM 2026.08,
  compileSdk 37, targetSdk 36, minSdk 26.
