# Final report (M12)

Status of ミミコピ at the end of the planned milestones (M0-M12). The Thai report to the user is given in the
milestone PR conversation; this file is the English record. What is not done is listed in
[`KNOWN-LIMITATIONS.md`](KNOWN-LIMITATIONS.md) ("Current list").

## Milestones
| Milestone | Release | Content |
|---|---|---|
| M1-M2 | v0.1-v0.2 | App shell, diagnostics, signing, release repo, in-app update with SHA-256 check, JSON backup |
| M3 | v0.3 | Salamander grand piano on Oboe (8 velocity layers, sustain, effects), on-screen keyboard |
| M4 | v0.4 | Metronome driven by the audio callback, self-test with measured jitter |
| M5 | v0.5 | USB MIDI, one input stream, simulator, monitor, latency and octave calibration |
| M6 | v0.6 | Staff renderer (Bravura), first lessons, Leitner SRS, Today plan |
| M7 | v0.7 | Own engraver, sheet reader and play-along, coach, tempo ramp, piece rule |
| M8 | v0.8 | Stages 1-3, generators L1-L5, progress screens, benchmarks |
| M9 | v0.9 | Ear training E1-E9, transcribe helper |
| M10 | v0.10 | Sheet editor, MusicXML / MIDI / PDF |
| M11 | v0.11 | Stages 4-6, chord theory and drills, accompaniment ladder, song library + Song Ladder, knowledge library, Capstone A/B |
| M12 | v0.12 | Timing strictness setting, reader page view, song project in Today, output-latency lead for the app's hand, local crash log, pinned Gradle checksum, docs |

## How it was verified
- **CI (every push):** `:core` unit tests (theory, timing, scoring, SRS with a fake clock, pass rules, MIDI parsing,
  generators with seeds, MusicXML/MIDI round trips, content validation incl. the no-real-songs check), Android lint,
  debug APK build. Releases are signed in CI and published with SHA-256 to the public releases repo.
- **Phone (by the user, each milestone):** installed every release on the Galaxy A54 and confirmed it works before
  each merge. Open phone item: the Badger C-918 checklist (issue #9) once the USB-C data cable is in use.

## Privacy and content rules kept
- Nothing leaves the device except the update check (unauthenticated `latest.json` from the public repo).
- No tokens or keystore in git (Actions secrets only). No copyrighted scores or lyrics bundled: pieces are public
  domain or original; the three target songs are empty shells with unverified facts.

## Next steps (only on the user's request)
- Windows desktop (PROMPT.md: later, only on approval).
- Optional: dark theme, English strings, reminders (off by default), audio-file import for transcription.
