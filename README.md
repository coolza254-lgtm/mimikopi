# ミミコピ (Mimi Kopi)

A personal piano-learning app for Android: reading sheet music, rhythm, ear training and
"copying songs by ear" (耳コピ). Single user, Thai UI, local data only.

- Project spec: [`docs/PROMPT.md`](docs/PROMPT.md). Learning content spec (Thai, source of truth): [`docs/learning-system.md`](docs/learning-system.md)
- Plan and decisions: [`docs/M0-PLAN.md`](docs/M0-PLAN.md), architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Install on the phone and update: [`docs/INSTALL.md`](docs/INSTALL.md)
- What is not done yet: [`docs/KNOWN-LIMITATIONS.md`](docs/KNOWN-LIMITATIONS.md). Licenses: [`CREDITS.md`](CREDITS.md)

## What the app does (v0.12)

- Lessons for stages 1-6 of `docs/learning-system.md` (50 lessons, Thai) with seeded drills, pass rules on two
  different days, spaced repetition, a daily plan (15/30/45/60 min) and neutral progress statistics.
- Sheet reader with play-along (wait / tempo modes, per-note judging, tempo ramp, problem spots, page view),
  sight-reading generators L1-L8, chord and accompaniment drills, ear training E1-E9.
- Sheet editor with MusicXML / MIDI import-export and PDF, a song library with Song Ladder and the 7-day
  transcription pipeline, a searchable pop-theory library.
- Sampled grand piano (Salamander) on Oboe, sample-accurate metronome, USB MIDI with latency calibration,
  JSON backup, in-app update from the public releases repo. Final status: [`docs/FINAL-REPORT.md`](docs/FINAL-REPORT.md).

## Build

CI (GitHub Actions) is the build system. Every push runs `:core` tests, lint and a debug APK
(downloadable from the run's artifacts). A `vX.Y.Z` tag builds the signed release and publishes it
as a release of this repository.

Locally (needs JDK 17+ and the Android SDK with API 36):

```sh
./gradlew :core:test            # pure-JVM unit tests, no Android SDK needed
./gradlew :app:assembleDebug    # debug APK (application id com.id.mimikopi.debug)
```

Windows desktop (separate Gradle build that shares `core/`, see [`docs/DESKTOP.md`](docs/DESKTOP.md)):

```sh
./gradlew -p desktop :core:test test   # core + desktop tests and page screenshots
./gradlew -p desktop :app:run          # run the program (needs desktop/native built with CMake)
./gradlew -p desktop :app:packageMsi   # MSI installer (Windows)
```

## Modules

| Module | What |
|---|---|
| `core/` | Pure Kotlin/JVM: music theory, timing rules, spaced repetition, pass rules, data models. No Android imports. |
| `app/` | Android app: Compose UI, Room database, diagnostics, platform services. |
| `content/` | Editable lesson and config files (JSON/Markdown). |
| `desktop/` | Windows desktop: `platform` (JVM services), `app` (Compose Desktop), `native` (miniaudio/WASAPI). |
| `tools/` | Brand asset generators, later the sample-pack builder. |

## Releasing

1. Add Thai notes at `release-notes/vX.Y.Z.md`.
2. Push the tag `vX.Y.Z`, or run the **Release** workflow manually with version `X.Y.Z` (it creates
   the tag). versionCode = X·10000 + Y·100 + Z.
3. The release workflow publishes `mimikopi-vX.Y.Z.apk` as the release `vX.Y.Z` of this repository, keeps the
   phone's update file on the fixed release `phone-update`, copies the piano sound pack here once
   (`samples-v1`), and starts the Windows release `desktop-vX.Y.Z` of the same version.

Required Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`. Optional: `RELEASES_TOKEN`, only
while phones still run v0.30 or older: it puts each new `latest.json` on the old
[`mimikopi-releases`](https://github.com/coolza254-lgtm/mimikopi-releases) repo too, so they find the update.
Once every installed app is newer, remove the secret; the old repo then stays as an archive.

Progress sync between the phone and the PC: [`docs/SYNC.md`](docs/SYNC.md) (the `sync-data` branch of this repo).
