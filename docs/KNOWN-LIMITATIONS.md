# Known limitations

Updated each milestone. Mirrors the "ยังไม่มีในรุ่นนี้" list on the About screen.

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
- USB MIDI is the tested path; Bluetooth MIDI devices already paired in Android may appear but there is
  no in-app Bluetooth pairing/scan yet.
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
- The Gradle wrapper has no `distributionSha256Sum` pinned yet (the checksum host was unreachable from
  the build session); CI validates the wrapper JAR.
