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
  update/    UpdateManifest (latest.json), UpdatePolicy (newer + safe URL/hash/size), Sha256
  backup/    BackupFile/BackupData (JSON mirror of all tables), BackupValidator
  piano/     PackDescriptor/PackManifest (+ validation), SampleMap (note/velocity -> sample, pitch ratio)
  keyboard/  KeyboardLayout (key geometry and multi-touch hit testing)
  metronome/ MetronomeSettings, TapTempo, JitterAnalysis
  notation/  Clef, Staff (positions, ledger lines, stems)
  lesson/    Lesson/Drill/PassRule (content/lessons/*.json), LessonRecord + ProgressRules
  exercise/  Generators (seeded), Scoring and pass checks; ScoreGenerators: five-finger positions, finger
             patterns, rhythm cells (5.3), sight reading L1-L5 with LevelSpec (5.2), interval sets; IntervalQuiz
  session/   SessionPlanner (section 3 template, free play always); BlockContent (warm-up, new-reading level)
  editor/    Timeline (notes on a 16th grid per hand, overwrite edits, re-barring with ties and beat-aligned rests),
             ScoreMeta
  interchange/ MusicXml (write; read partwise/.mxl subset), MidiFile (SMF format 1 write with repeats; read 0/1 with
             quantisation), ImportException (Thai messages)
  ear/       EarLevel E1-E9, EarQuestion (sounds + answers), EarGenerator (weighted by weak items), EarStats,
             EarAnswers, chord voicing by degree in major keys
  theory/    Chords.kt: PitchName, Keys (scales, spelling), Chord (parse/matches/voicings/transpose), Roman
             (analysis incl. secondary dominants and borrowed chords), KeyFinder; Arrange.kt: Accompaniment ladder
             (8.2 levels 1-7), StageGenerators (8.1 progressions, comp patterns, sight reading L6-L8, lead sheets,
             transposition pairs), Transpose, Templates.capstoneA
  songs/     Song, SongSection, PipelineDay (9.5), SongLadder (difficulty parts, requirements -> lessons)
  knowledge/ Article + Knowledge (search, tap-to-hear example sequences) for content/knowledge/*.json
  practice/Pedal  PedalCheck (legato pedal changes against chord onsets)
  exercise/ChordDrills  E_CHORD_BUILD items and E_CHORD_CHANGE progressions
  progress/  TrackProgress (per track / per stage), BenchmarkResult + Benchmarks (fixed sets, 14-day schedule)
  score/     PieceFile (content/pieces/*.json, hand-writable tokens), Score (+ measure checks), KeySignature,
             ScoreLayout: own engraver -> one long grand-staff strip in staff spaces (SMuFL glyphs, stems,
             flags, dots, rests, ledger lines, accidentals with bar carry, key/time signatures, beat->x anchors)
  playalong/ PlayAlongJudge (wait / tempo modes, per-note correct/early/late/missed/corrected, wrong keys,
             stumbles), PlayResult (RunSummary, per-bar errors, problem bars), Coach (learning-system.md 7),
             PieceRules (tempo ramp per hands+section, pass = 3 clean runs at target on 2 days, problem-spot heat)
  input/     InputEvent stream model, MidiParser (running status, vel-0 off, CC64, sysex/realtime),
             VelocityProbe, OctaveCalibration, LatencyCalibration, InputConfig
  time/      Clock
app/    com.id.mimikopi
  data/db/   Room: practice_sessions, attempts, attempt_events, item_stats, settings
  diag/      DiagnosticsCollector (reads device facts, probes the Oboe stream)
  input/     InputHub: the one event stream (sound on the caller's thread, SharedFlow for observers,
             held-note set); InputConfigStore; OutputRoute
  midi/      MidiInputManager: device callback, hot-plug, one MidiParser per output port, octave offset
  metronome/ MetronomeController (settings, self-test)
  learning/  LearningRepository (lessons from assets, records, SRS via Leitner + item_stats, attempts,
             sessions, neutral stats), SessionTracker
  ui/today, ui/lesson, ui/practice   Today plan, lesson page, drill runner (key / note / pulse)
  pieces/    PieceRepository (bundled pieces + per-piece progress JSON in settings)
  ui/editor  LibraryScreen (my scores: new, import, export, duplicate, delete), EditorScreen/EditorViewModel
             (toolbar entry, step entry from keys/MIDI, touch entry on the staff, undo/redo, autosave)
  pieces/ScoreFiles, ScorePdf  document-picker import/export; PdfDocument renderer with wrapped systems
  songs/     SongRepository (settings rows "song:<id>", bundled shells copied once, Capstone B completion)
  ui/songs   SongListScreen (Song Ladder), SongScreen (facts + verified flags, sections with roman numerals,
             7-day pipeline, linked score, key finder, transposed copy)
  ui/knowledge KnowledgeScreen (search), ArticleScreen (examples on the app piano)
  ui/ear     EarScreen (levels, stats, transcribe entry), EarDrillViewModel (questions, keys or buttons, rhythm echo)
  piano/BeatClock  countInDownbeat: metronome count-in mapped to presentation time
  ui/progress ProgressScreen (stages, tracks, weekly minutes, daily trends, benchmarks)
  ui/reader  ReaderViewModel.Source: library piece, lesson score drill, new reading, benchmark level;
             PieceListScreen (optionally filtered by a repertoire drill), ReaderScreen/ReaderViewModel (count-in from native metronome tick frames
             -> downbeat t0 on the System.nanoTime clock; judging on the main thread; per-source latency),
             ScoreView (Compose Canvas, cached glyph layouts, cursor read per frame)
  piano/NoteScheduler  accompaniment/demo notes at nanoTime instants from one thread (parkNanos)
  piano/     SamplePackManager (download, SHA-256, safe unzip, decode, atomic install),
             PianoController (shares one engine; stream open only while a screen plays)
  backup/    BackupService: export/import via the document picker, automatic backups in app storage,
             restore in one transaction after a safety backup
  update/    UpdateService: check, streaming download with SHA-256, APK package/version/signer check,
             PackageInstaller session; InstallResultReceiver -> InstallEvents -> UI
  ui/        theme, components, home, diag, about
audio/  com.id.mimikopi.audio (Android library + C++)
  PianoEngine   Kotlin interface (note on/off, sustain, gain, metrics); NativePiano = JNI implementation
  FlacDecoder   pack FLAC -> raw PCM with the platform MediaCodec (decoded once at install)
  cpp/          Oboe output stream (LowLatency, Exclusive, float stereo), 48-voice sampler with
                cubic interpolation, exponential release, sustain, voice stealing, soft limiter;
                lock-free command queue into the audio callback; samples memory-mapped with the first
                1.5 s in RAM and a prefetch thread paging ahead of active voices
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

## Update flow (M2)
`latest.json` is fetched from `https://github.com/coolza254-lgtm/mimikopi-releases/releases/latest/download/latest.json`
(no token). `UpdatePolicy` accepts only schema 1, an HTTPS `github.com` APK URL, a lowercase SHA-256 and a sane
size, and only a higher versionCode. Before downloading, an automatic backup is written. The APK is streamed to
`cache/updates/*.part`, hashed while downloading, checked (size, hash, package, versionCode, signing
certificate equal to the installed app's) and only then renamed and handed to `PackageInstaller` with
`USER_ACTION_REQUIRED`. The system confirmation intent is launched from the foreground screen. Any failure
deletes the download; the installed app and its data are untouched.

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

## Notation decision (M7)
The M0 plan listed Verovio (C++, LGPL-3) as a spike. I kept the own engine in `:core` instead: the reader
needs per-note colouring, a moving cursor, tap-to-select bars and later editing, which are simple with our own
layout model and awkward through Verovio's SVG output; it also avoids an LGPL native dependency. The cost is
engraving scope: M7 covers single-voice-per-staff music (stages 1-4). Beams, ties, slurs, multiple voices and
system wrapping are not drawn yet (see KNOWN-LIMITATIONS.md).
