# Known limitations

Updated each milestone. Mirrors the "ยังไม่มีในรุ่นนี้" list on the About screen.

## Current list (M15, v0.15.x)
What the app does not do, or does only partly, as of the last milestone. Details per milestone follow below.

**Not built**
- English UI, reminders (the user does not want them now), audio-file import for transcription. (Dark theme came in
  M15, the Windows version in v0.17-0.18, the falling-notes view in v0.19.)
- On-screen keyboard: removed on purpose in M15; every exercise is played on a MIDI keyboard.
- Release ("damper") noise and sympathetic resonance samples; velocity layers are switched, not cross-faded.
- Section practice: rhythm is not recorded or judged; no tempo mode for sections.
- Measuring legato/staccato, dynamics and fingering (the app sees notes and timing only).

**Partial / approximations**
- Practice songs: public-domain melodies written from memory and simply arranged; not yet checked against
  printed editions (each song says so).
- The app's accompaniment in tempo mode is sent early by Oboe's output-latency estimate (capped at 250 ms); with
  Bluetooth headphones the estimate can be off. Your own playing is judged with your calibrated latency.
- Many thresholds (clean runs needed for a section phrase, pieces needed per repertoire step, tempos of test
  drills) are my own choices where learning-system.md gives none.

**Only verifiable on the phone (never confirmed by CI)**
- Audio quality, latency and glitch-free playback; the metronome's feel; USB and Bluetooth MIDI; the new tabs,
  section recording/practice and the large notation layout; the timing feel of judging.

## Ear training and MIDI status (v0.20.0)
- Ear training is functional (scale-degree) training: a cadence, one note, the answer by key (any octave) or button,
  then the note resolves stepwise to do. The earlier 36-rung ear ladder is gone from both apps; its content
  (content/ear) and core generators stay in the repository, unused by the UI, and old ear records stay in backups.
- Custom levels and results live in the settings rows "ear.tone.levels" and "ear.tone.stats". Results are the last
  20 runs per level (first-try accuracy); there is no spacing or unlocking.
- MIDI "Connected" (green tick, the one colour in the app, asked for by the user) is shown only when the instrument
  sends Active Sensing, answers the MIDI identity request (asked every 2 s; silence for 4.5 s = off), or a key arrived
  in the last 10 minutes. An instrument that does none of these is shown as linked but unconfirmed. BLE instruments
  are closed when their GATT link reads disconnected twice in a row (about 2.4 s). Whether the user's piano answers
  the identity request is a phone check. The desktop has no identity request yet (Active Sensing and keys only).
- Songs: the app's own pieces were removed; steps 3.15 and 4.19 use public-domain pieces instead.

## Falling notes (v0.19.0)
- Songs: a "falling notes" chip in the reader (library pieces only, not lesson drills). The bars come from the score
  (ties merged) and move with the same cursor as the score view, so wait mode, tempo mode, listen and the judge are
  unchanged. A run in this view counts as a helper (like note names): it does not count toward passing the piece.
- Sections: "falling notes" / "big note" switch on the practice page (remembered); one step per row, no rhythm (the
  recorded sections keep note order only). Black and white: right hand white bars, left hand grey; the keyboard is
  always light keys / dark keys. Hand split in sections is not known, so all bars are one colour.

## Windows version, v0.18.1
- In-program update: reads `windows.json` from the latest release (attached by the release workflow's Windows job a
  few minutes after the release itself; until then the check finds nothing new). The MSI is unsigned, so its
  integrity rests on HTTPS from github.com and the SHA-256 in windows.json (the same trust as the APK update). The
  installer runs through a small cmd script with `msiexec /passive`; Windows may still show its own prompts.
- MIDI: Settings shows messages received per keyboard and a copyable report of every device Java Sound sees, to find
  out why a keyboard does not connect on a given PC (the cause on the user's PC is not known yet).

## Windows version, phase 2 (v0.18.0)
- Reading (steps, drills, review), the score reader and songs are the Android screens copied into
  desktop/.../port with only their imports changed; a small layer supplies the Android names (R generated from
  strings.xml and the drawables at build time, view-model base, lifecycle effects, services). Changes to those Android
  files have to be copied again by hand.
- Metronome clicks on the PC: a thread sends each click at its time; a click counts as heard at send time plus the
  piano's output buffer (Java synthesizer: its reported latency; sampled piano: the line buffer + 10 ms). Windows'
  own mixer delay is not included, so tempo-mode timing can read consistently "late" on some PCs; the steadiness
  (SD) is unaffected. There is no latency calibration on the PC (phone calibration values are keyed by phone outputs).
- Attempts and practice sessions are not stored on the PC (the Android app's statistics screens are gone since M15;
  lesson records, piece progress, spaced-repetition items and benchmarks are stored and move with a backup).
- No on-screen tap pad for the pulse step other than the large pad (clicking it counts as a tap; a MIDI key is better).

## Windows version, phase 1 (v0.17.0)
- Ear training and section practice (reading and songs added in phase 2). Built with Compose Multiplatform for the
  desktop JVM; the logic is the shared :core module, the screens are a desktop copy of the Android ones.
- USB MIDI only, through javax.sound.midi. Java Sound has no plug/unplug events, so devices are polled every 2 s;
  on some Windows setups a keyboard plugged in after the program started only shows after a restart. Active Sensing
  silence is used for "switched off" as on Android (whether Windows passes Active Sensing through is a PC check).
- Sound: Java's built-in synthesizer until the sampled piano (the Android pack, decoded with jflac, LGPL 2.1) is
  installed; the sampled piano plays through a SourceDataLine with about 40 ms of buffer. No metronome screen yet;
  the rhythm rungs use clicks played by the piano.
- Data in %APPDATA%\Mimikopi\data.json; moved to and from the phone with the Android backup file format (settings
  rows and item stats only; practice sessions and attempts are not carried).
- The MSI is unsigned (SmartScreen warns on first install); per-user install, upgrades keep the same upgrade UUID.
- Nothing of the desktop app has been run by anyone yet: CI builds and unit-tests it, and packages the MSI on Windows.

## As of v0.16.0
- "Connected" for MIDI: an instrument whose USB-MIDI chip stays powered by the phone stays listed by Android after it
  is switched off. The app detects that only for instruments that send Active Sensing (silence > 1.2 s); for others
  it cannot tell and says so on the MIDI screen. Bluetooth links are watched through ACL disconnect broadcasts
  (needs the "nearby devices" permission). Whether the Badger C-918 sends Active Sensing is unknown (phone check).

## As of M15 (v0.15.x)
- The app is slimmed down to two skills plus songs and sections: reading (play from notation) and ear training
  (towards transcribing by ear). Removed: the free on-screen piano and every on-screen keyboard, the transcribe
  helper, the knowledge library, the song library (Song Ladder), the score editor ("my scores") and the
  benchmarks/progress tab. Every exercise now needs a MIDI keyboard (USB or Bluetooth).
- Ladder progress started again from step 1 in both skills (the user chose this). Earlier records stay in the
  settings table under `lesson.records.v2` and in backups.
- The reading ladder keeps stages 1-4 (98 steps); the chord, accompaniment and transposition stages (5-6) are gone.
  The ear ladder is 36 rungs with more melody-dictation rungs (2 to 8 notes) before intervals and chords.
- New practice songs (13) are public-domain melodies written from memory and arranged simply; they are marked in
  their `source` as not yet checked against a printed edition, and the app says so on the song card.
- The whole UI is black and white (no colour at all): OLED black by default, a light mode (white) and "follow the
  phone" in settings since v0.15.1 (the stored choice is read at start, so light mode may flash black briefly); note results in the reader are told apart by grey
  levels (played notes fade) and right/wrong by words, inverted labels and haptics. Untested on the phone.
- Section practice records pitches and their order only (no rhythm); notes struck within 60 ms are one chord.
  Wait mode only: a wrong note restarts the phrase; 3 clean runs in a row pass a phrase; 3 clean play-throughs of
  the whole section in a row mark it as mastered. Untested on the phone.

## As of M14 (v0.14.x)
- The step ladder is built mechanically from the 50 lessons' drills (one step per drill, ear drills moved to the ear
  ladder); step titles come from the drill titles and some read a little terse. Stage tests became several steps.
- "Repeat an older step" picks one confirmed step by the date, not by weakness yet.
- The ear ladder starts at rung 1 for everyone (old ear statistics still steer which items come up within a rung).
- The daily list is fixed in the morning (confirmations, review, repetition); steps met later in the day appear
  as they happen. A failed confirmation reopens the step the next day it is opened.
- The old lesson pages are gone; their full explanations are under "อ่านละเอียด" on each step. Song Ladder and
  knowledge links still name the old lesson numbers and open their first step.

## As of M13 (v0.13.x)
- Redesign from the Claude Design canvas: bottom bar (home, today, piano, songs, me), new home, timeline Today,
  lesson badges and "นับผ่าน" tags, chord prompt card, ladder Song list, stage tiles, bundled fonts (IBM Plex Sans
  Thai Looped; Zen Maru Gothic subset), dot-grid paper. The reader, editor, piano, ear, metronome, MIDI, settings,
  song detail and knowledge screens take the new colours and fonts but keep their layouts.
- The Today plan does not track which blocks are done, so the timeline numbers the blocks instead of ticking them.
- Japanese song titles use the system font (the bundled fonts cover Thai and Latin).

## As of M12 (v0.12.x)
- Timing strictness (strict / normal / forgiving = ×0.75 / ×1 / ×1.3 of the 6.2 tolerances) is a setting and applies
  to reader runs, drills and the rhythm echo; benchmarks always use normal so results stay comparable.
- Reader page view: the score wrapped into lines (same breaking as the PDF), scrolling down with the cursor. Lines are
  not justified to the full width. The single long strip stays the default.
- Today: with 45 minutes or more, the free block offers the next pipeline day of the first unfinished song of the
  Song Ladder (section 3).
- The last crash (time, thread, stack trace) is kept in app storage and shown in Diagnostics / included in its JSON.

## As of M11 (v0.11.x)
- Song library: only what the user types (or the three bundled shells with web facts marked unverified). No song
  data is fetched; no real scores or lyrics are bundled. Song Ladder difficulty is a heuristic over key, tempo,
  range, chord vocabulary and, when a score is linked, rhythm density, hand moves and left-hand texture; parts with
  no data count as missing and are listed, so a song with little data looks easier than it is.
- Key finder: Krumhansl-Kessler profile correlation on the linked score's notes (counts, not durations); it needs at
  least 8 notes and can confuse relative major/minor or neighbouring keys on short melodies.
- Chord drills accept any inversion and octave (slash chords need the bass lowest); extra notes fail the chord. The
  build drill judges 0.22 s after the last key so rolled chords count; the change drill counts a change once the
  held keys match the next chord (keys shared with the previous chord may stay down).
- Accompaniment patterns are generated (levels 1-7 of 8.2) with a simple chord-tone melody; voicings are chosen for
  smooth movement, not taken from real arrangements. Level 7 (16-beat syncopation) uses one fixed rhythm.
- Transposition drill: a 4-bar phrase shown in the first key is played in a target key; the reader shows the
  original notes only (the keyboard range covers both).
- Pedal check is informative only and needs the piano to send CC64 over MIDI; touch keys have no pedal. It checks
  that the pedal comes up within -0.15..+0.5 s of each new chord and goes down again within 0.7 s.
- Capstone A uses the 8.4 sections stretched to 32 bars (the chorus runs 12 bars: two "Chorus" marks); it counts
  once its right hand has at least 16 notes and the score is passed with both hands. Capstone B counts library songs
  with all 7 pipeline days ticked and the linked score passed with both hands.
- Knowledge library: 18 bundled Thai articles; examples play with the app piano (close voicings around Middle C).

## As of M10 (v0.10.x)
- Editor scope: one voice per staff (chords allowed), notes and rests down to 16ths with dots, ties, accidentals,
  key and time signature (one for the whole piece), clefs per staff, tempo, chord symbols, repeat bar lines.
  Not yet: tuplets, second voices, slurs, dynamics, articulations, lyrics, 1st/2nd endings, D.C./D.S., key or time
  changes inside a piece, pickup measures (an imported pickup is placed at the end of a full first measure).
- Writing overwrites (score-editor style); a note longer than what is left in the bar is split and tied over the bar
  line automatically. Accidentals are spelled from the key (sharps in sharp keys and C, flats in flat keys) when
  entered from keys/MIDI; touch entry can choose ♯ ♭ ♮.
- MusicXML import: partwise files (.musicxml/.xml/.mxl), first part, staves 1-2, first voice of each staff; other
  voices and unpitched notes are skipped (the app says how many). Durations must sit on the 16th grid (no triplets).
- MIDI import: notes quantised to 16ths; hands from tracks (higher track = right hand) or split at Middle C;
  overlapping notes in one hand are cut where the next starts; no chord symbols or spelling hints from MIDI.
- MIDI export plays repeats out; MusicXML export writes them as repeat bar lines. Play-along in the reader follows
  the written order (repeats are not played out there).
- PDF: A4 portrait, systems wrapped to the page width, the same engraving as the screen (no justification of
  systems to the full width).
- User scores live in the settings table (one row per score), so JSON backups include them.
- The transcribe helper now works with your own scores too (write or import the phrase, then hide notes in the reader).

## As of M9 (v0.9.x)
- Ear training E1-E9 (learning-system.md 11). Levels unlock by stage (E1-E2 stage 1, E3/E4/E6 stage 2, E5 stage 3,
  E7-E8 stage 5, E9 stage 6); locked levels can still be tried early. E3/E4/E6 unlock stages are my own choice
  (the spec fixes only E1-E2 and E7).
- Progressions use major keys C, G, F, D and degrees I ii iii IV V vi (no vii°, no minor keys, no 7th chords in
  progressions yet). Chords are played in close root position near Middle C with the root in the bass.
- E8 bass lines use the progressions of 8.1 with some chords in first or second inversion.
- E9 is a generated two-bar phrase (key note, chords, melody), not a real song.
- Answers by keys are judged on the first press (melodies: the whole line, any wrong note = wrong; can be retried
  without counting). Statistics use the last 10 answers per kind; weak kinds are asked more often.
- Ear items join spaced repetition (as the spec lists); reading reviews and ear items are kept apart.
- Transcribe helper: works on the app's pieces (hide notes, loop the section, slow down, play the guess in wait
  mode). Your own phrases come with the editor (M10); importing audio files is a later optional feature.
- Fixed: finger numbers from M8 were laid out but not drawn in the reader; they are drawn now.

## As of M8 (v0.8.x)
- Lessons: stages 1-3 (1.1-3.8). Stage 3's "pieces + stage test" (3.7 in learning-system.md) is split into 3.7 (pieces) and
  3.8 (test) so piece passes and the test count separately. Position D moved from 2.3 to 2.4 (it needs F♯, taught in 2.4).
- Repertoire lessons (2.8, 3.7) pass when 4 of 6 / 2 of 3 pieces are passed; those counts are my own choice.
- Generated material: finger patterns (C/G/D/F five-finger positions), rhythm reading (cells of 5.3, any key counts),
  interval reading, sight reading L1-L5 (5.2). L6-L8 come with stages 4-5. Ties are not generated or drawn (2.2 explains
  them only). Legato/staccato and dynamics (2.7) are explained but not measured.
- Score drills count clean runs within one sitting (leaving the screen resets the count); sight-reading pieces change
  after a clean run or three runs.
- The app checks notes and timing but cannot see which finger plays (fingering is shown, not judged).
- Benchmarks (12.1): note-reading speed (30 fixed notes, time counted only while answering), highest sight-reading
  level played clean once (fixed pieces L1-L5), pulse SD at 70 bpm, and (from M9) a fixed 20-question ear set. Chord
  changes join when that feature exists. Benchmark runs do not update spaced repetition.
- Progress: lessons per stage and per track, minutes per week (8 weeks), daily reading accuracy and timing (30 days).
  Charts are drawn natively without touch tooltips; the numbers are shown as text under each chart.
- Warm-up uses the latest five-finger or scale drill reached; new reading is one level below the highest level reached.

## As of M7 (v0.7.x)
- Pieces bundled: Mary Had a Little Lamb, Ode to Joy, Twinkle Twinkle (public-domain melodies in the app's own
  simple arrangements) and two original pieces. Own pieces arrive with the editor / MusicXML import (M10).
- Engraving: one voice per staff, chords, dots, flags, rests, accidentals, key and time signatures, ledger lines.
  No beams (eighths have flags), ties, slurs, dynamics, fingering numbers, repeats or line wrapping (the score is
  one long strip that scrolls).
- The optional falling-notes view from PROMPT.md is not built; reading the staff is the point of the app.
- Accompaniment and demo notes are timed by a high-priority thread (about 1 ms error) and are not compensated for
  audio output latency, so the app's hand can sound a few tens of ms after the click. Your notes are judged
  against the click timeline (count-in clicks are mapped to presentation time) minus your calibrated latency.
- Wait mode does not judge timing; only tempo-mode runs move the tempo ramp or count toward passing a piece.
- Timing tolerance uses the piece level (L1-3 = stage 1, L4-5 = stage 2, L6 = stage 3) in normal mode;
  strict/forgiving modes are not exposed yet.
- In tempo mode, wrong key presses subtract from note accuracy (a wrong key followed by the right one counts once).
- Notes that go wrong repeatedly are added to the SRS review (note reading), standing in for the
  "practise this note for 2 minutes" drill.

## As of M6 (v0.6.x)
- Lessons available: 1.1, 1.3, 1.5. Warm-up, new-reading and ear blocks are left out of the Today plan until
  their content exists (their time goes to the other blocks). The rest of stages 1-3 comes in M8.
- Lesson progress is stored as JSON in the settings table (covered by backups); attempts, events, item stats and
  sessions use the Room tables.
- Coach messages are rule-based and short; the "practise this note separately for 2 minutes" drill from
  learning-system.md 7 is approximated by SRS (missed items come back sooner).
- Test-out (6.4) and benchmarks every 2 weeks (12.1) are not implemented yet; problem-spot detection waits for
  pieces (M7).
- The staff renderer draws one note at a time (reading prompts); full scores come with the reader (M7).
- Session time is wall-clock from "start" to "finish" (capped), not measured activity.
- The pulse drill uses the calibrated latency for the input source and current output route when available;
  without calibration it says so in the result.

## As of M5 (v0.5.x)
- USB MIDI is the tested path. Bluetooth LE MIDI (scan + connect on the MIDI screen, from v0.14.2) is
  untested on real hardware: the Badger C-918 has no Bluetooth MIDI. BLE adds its own latency (often
  10-30 ms, varies by phone), so calibrate latency again while connected over Bluetooth; it is stored
  separately per input source.
- Latency calibration measures the whole chain (input + audio output + the player's habit) for the current
  output route; it is stored per "input source | route" and applied by the practice scoring (from M6/M7).
- The octave offset applies to all MIDI devices (one piano expected).
- The Badger C-918's real behaviour is unknown until the checklist in docs/BADGER-C918-CHECKLIST.md is run.

## As of M4 (v0.4.x)
- The metronome self-test measures timing as presented by the audio stream (frame clock mapped through
  getTimestamp). The speaker's fixed latency is not jitter and is not measured; true acoustic timing needs
  the external recording method in docs/DEVICE-TESTING.md.
- The visual beat follows the audio tick counter polled each screen frame, so the light can trail the click
  by up to one frame plus the output latency (the sound is the reference).
- In 6/8 the tempo counts dotted quarters (two beats per bar, three clicks each).
- The metronome stops when its screen (or the practice screen hosting it) closes.

## As of M3 (v0.3.x)
- Release ("damper") noise samples and sympathetic string resonance from Salamander are not used yet;
  note-off is a smooth exponential fade (0.6 s with dampers, 3-4 s for the top 20 undamped keys).
- Velocity layers are switched (8 layers) with loudness scaled continuously around each layer's centre; not cross-faded.
- Effects (from v0.3.2): Freeverb room reverb, speaker "warmth" EQ (high-pass 70 Hz, +mid body, high shelf cut), compressor and soft limiter.
- On-screen keys use a fixed velocity set by the "ความแรง" slider (touch pressure is unreliable on phones).
- Latency is shown as Oboe's estimate; per-route latency offsets and calibration come with MIDI (M5).
- The decoded pack takes about 440 MB of storage (79 MB download). The first 1.5 s of every sample is kept in
  RAM (~70 MB while the piano screen is open); the rest is read from storage ahead of playback.
- Audio quality, latency and glitch-free playback under UI load are only verifiable on the phone.

## As of M2 (v0.2.x)
- Automatic backups live inside the app's storage: they survive updates but **not uninstalling**.
  Save a backup file elsewhere for that.
- The update check runs only when the user taps it (no background checks, no notifications).
- Install success is confirmed only by the app restarting on the new version (the old process is replaced).
- No piano sound (M3), metronome (M4), MIDI (M5) or lessons (M6) yet.

## As of M1 (v0.1.0)
- Only the app shell, the Diagnostics screen and the About/credits screen exist.
- No in-app update check or JSON backup yet (M2). Install new versions manually from the releases page.
- No piano sound, on-screen keyboard (M3), metronome (M4), MIDI input (M5) or lessons (M6).
- Diagnostics: frames-per-burst and output latency are `null` until the Oboe engine exists (M3).
  The "frames per buffer" value is only the system's hint.
- Light theme only; Thai UI only (English later).
- The Room schema JSON (`app/schemas/`) is generated by CI and must be committed from the CI artifact.
- (Resolved in M12: the Gradle wrapper now pins `distributionSha256Sum` from gradle.org/release-checksums.)
