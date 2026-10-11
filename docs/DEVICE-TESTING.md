# Device testing

The build session cannot run the phone. Phone-only behaviour (audio latency, timing feel, MIDI,
touch, performance) is verified by the user with the steps below and reported back with the
Diagnostics JSON (**ข้อมูลเครื่อง → คัดลอก JSON**).

## Baseline: Galaxy A54 (first install, v0.1.0, 2026-10-10)

| Item | Value |
|---|---|
| Model | samsung SM-A546E (`a54x`), Android 16 (API 36), arm64-v8a |
| Features | MIDI ✓, USB host ✓, audio low-latency ✓, **audio pro ✓**, BLE ✓ |
| Output sample rate | 48000 Hz |
| Frames per buffer (system hint) | 192 (= 4 ms at 48 kHz) |
| Route at test time | Bluetooth A2DP (Galaxy Buds Core) |
| Outputs | earpiece, speaker, Buds Core (SCO + A2DP), telephony |

Implications:
- `FEATURE_AUDIO_PRO` means the device claims round-trip audio latency ≤ 20 ms on wired/USB
  paths, so the Oboe engine (M3) should get a small burst. Plan for 48 kHz native samples.
- Bluetooth A2DP (Buds Core) typically adds well over 100 ms. Timing-judged practice should use the
  speaker or wired/USB-C headphones, or a per-route latency offset (calibration, M3/M5).

## Measuring true touch-to-sound latency (external method)

The system's latency number is only an estimate. To measure it for real:
1. Put the A54 next to a second phone that records **slow-motion video** (240 fps or more) with sound.
2. In ミミコピ, tap an on-screen key sharply with a fingernail (from M3) while the second phone films
   the finger and records audio.
3. In a video editor, step frame by frame: find the frame where the nail touches the glass, and the
   first frame where the click is visible in the audio waveform.
4. Latency ≈ (sound frame − touch frame) × (1000 / fps) ms. Repeat 10 times; report the median.
5. Repeat for each route: speaker, wired/USB-C headphones, Bluetooth.

For MIDI-to-sound latency (M5), do the same with a key on the Badger C-918, filming the key.

## Metronome timing (M4)
- In-app: Metronome → ทดสอบความแม่นยำ (20 s). It maps each click's frame to its presentation time via the
  stream timestamps and reports the mean and max deviation of click intervals plus xruns during the test.
  Run it once while idle and once while scrolling/switching apps; both should stay well under 1 ms
  with 0 xruns.
- External (true acoustic jitter): record the phone speaker with another device for 60 s at 120 BPM,
  open the recording in an audio editor (e.g. Audacity), mark click onsets, and compare intervals to 500 ms.
