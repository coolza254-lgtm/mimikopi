# M0 Plan — ミミコピ (Mimi Kopi)

Status: **proposal, waiting for the user's OK.** No code yet. Date: 2026-10-10.
Inputs: `docs/PROMPT.md` (project prompt), `docs/learning-system.md` (source of truth for the learning content).

---

## 1. Environment check (this cloud session)

| Item | Result |
|---|---|
| JDK | OpenJDK 21 — OK |
| Gradle | 8.14.3 (system); the project will use the Gradle wrapper |
| Android SDK / NDK | **Not installed** |
| `dl.google.com` (SDK, AGP, AndroidX) | **Blocked by the network policy (403)**, so the SDK can't be installed and AGP/AndroidX can't be resolved |
| `maven.google.com` index page | 200, but artifacts redirect to `dl.google.com` → effectively blocked |
| Maven Central | 429 (rate-limited) at check time |
| Gradle Plugin Portal | OK |
| archive.org, freepats, uiowa.edu, fonts.google.com | Blocked |
| GitHub | API works through the GitHub MCP tools; `gh` isn't usable in this session |

**Conclusion:** Android builds can't run locally. **GitHub Actions is the build system** (the hosted Ubuntu runners ship the Android SDK and NDK). I push, CI builds, I read the logs, and the user installs the APK.

Even the `:core` JVM tests can't run here reliably while Maven Central is rate-limited, so CI is the authority for those too.

*Optional:* if the user adds `dl.google.com`, `repo.maven.apache.org` and `repo1.maven.org` to the environment's allowed domains (environment settings → Network access), I could run `:core` tests and possibly lint locally before pushing. That would mean fewer red CI runs. It is not required.

## 2. Architecture

```
mimikopi/
  core/      Pure Kotlin/JVM, no Android imports (KMP-convertible later)
             theory (pitch, interval, key, chord), rhythm & timing tolerance,
             Leitner SRS (injectable Clock), exercise generators (seeded RNG),
             lesson engine, pass rules, scoring, problem-spot detector,
             content models + schema validation, MusicXML / MIDI-file read/write,
             notation layout model (engraving decisions, not drawing),
             MIDI event stream model (NoteOn/NoteOff/CC64, vel-0 = off)
  engine/    Android library + C++ (CMake, Oboe): sampler, mixer, metronome,
             all in the audio callback. Kotlin facade `AudioEngine` interface.
  app/       Compose UI (thin), Room, DI by hand (no Hilt), MIDI (android.media.midi),
             updater, backup, diagnostics, platform impls of core interfaces
  content/   Lessons / knowledge in JSON + Markdown (Thai), with a JSON schema
  tools/     Sample-pack build script (Python), content validator runner
  docs/
```

- **Event stream:** all input (USB MIDI, the on-screen keyboard, and the debug simulator) becomes one `Flow<InputEvent>` with timestamps on a single monotonic clock (`System.nanoTime`). Audio-output time is mapped to that clock via `AAudioStream_getTimestamp` (Oboe `getTimestamp`). Scoring lives in `:core` and only sees this stream plus the per-route latency offsets.
- **Interfaces in `:app`:** `AudioEngine`, `MidiSource`, `Notifier` (no-op for now), `UpdateSource`, `FileStore`, `Clock`.
- **Storage:** Room (SQLite) for attempts, item stats, sessions, user songs and settings. `kotlinx.serialization` for content files and the full JSON export/import.
- **UI:** Jetpack Compose and Material 3, with **one token file** (colors, type, shapes, spacing). ViewModels hold state; composables stay dumb, so the design can be redone later.

### SDK levels
- **minSdk 26 (Android 8.0).** It gives native AAudio (Oboe's preferred path), adaptive icons, `java.time` and the MIDI API (which needs 23+). The only target device runs Android 16, so nothing is lost.
- **targetSdk / compileSdk 36 (Android 16).** Google Play requires API 36 for new apps and updates since 31 Aug 2026. We don't use Play, but following current rules keeps the behavior current. If CI images have API 37 and it's stable, I'll reconsider at M1.

## 3. Library choices

| Area | Choice | Why | License |
|---|---|---|---|
| Audio out | **Oboe** (prefab AAR, C++), AAudio on Android 8.1+ | Low latency, the callback drives everything (sampler and metronome are frame-counted). SoundPool/MediaPlayer are excluded per the prompt. | Apache-2.0 |
| Sample decode | **dr_flac** (single header) decoding FLAC to int16 in RAM at load time | Lossless, about 50% smaller download, no streaming complexity | Public domain / MIT-0 |
| MIDI | `android.media.midi` (MidiManager, USB host first, BLE later) | Built into the OS since 23; handles hot-plug via DeviceCallback | — |
| DB | Room + KSP | Standard, testable migrations | Apache-2.0 |
| JSON | kotlinx.serialization | Pure Kotlin, multiplatform | Apache-2.0 |
| XML (MusicXML) | `xmlutil` (pdvrieze), KMP | Keeps `:core` pure Kotlin | Apache-2.0 |
| MIDI files | Own small reader/writer in `:core` | The format is simple; round-trip tests | — |
| Notation glyphs | **Bravura** (SMuFL) + `bravura_metadata.json` anchors | Real engraving glyphs | SIL OFL 1.1 |
| Notation layout | **Own layout engine in `:core`**, drawn with Compose Canvas | There is no mature native Android engraver. A custom engine allows per-note coloring, a moving cursor, and the editor. Scope grows gradually (exercises in M6, full grand staff in M7/M10). | — |
| Alternative for full scores | Verovio (C++, LGPL-3), evaluated as a spike in M7 | Excellent engraving, but interactivity is harder and the license is copyleft | LGPL-3 |
| PDF export | `android.graphics.pdf.PdfDocument` with the same renderer | No extra dependency | — |
| Fonts | **Zen Maru Gothic** (wordmark, JP), **Noto Sans Thai** (body); Japanese body text falls back to the system font (One UI has JP) | Thai and JP coverage; bundling a full JP font adds 5–15 MB | SIL OFL 1.1 |

## 4. Piano samples

**Pick: Salamander Grand Piano V3 (Yamaha C5, by Alexander Holm).**
- License: **CC BY 3.0** (stated in the sfzinstruments/SalamanderGrandPiano README; secondary sources agree). It is redistributable with attribution and must go in `CREDITS.md` and the in-app credits screen. I found **no** evidence of a later CC0 relicense.
- 16 velocity layers, 48 kHz / 24-bit, sampled every **minor third** (pitch-shift up to ±1.5 semitones, done in the engine with cubic interpolation). Release and hammer samples are included. The A54's native output rate is likely 48 kHz (to be confirmed in Diagnostics).
- Rejected: **University of Iowa MIS piano.** Their site says "without restrictions", but it has only 3 dynamics (pp/mf/ff), was recorded in a small studio with room noise, and one library developer called the rights "unclear".

**Processing plan (`tools/build_samples.py`, run in a manual CI workflow, since the session can't reach archive.org):**
- Use **8 of the 16 velocity layers** (every other layer), keep stereo, convert to 16-bit, keep 48 kHz, trim tails per register with a fade, add a small release-sample set, and encode as FLAC.
- Estimated pack: ~**150–250 MB download**, RAM when loaded ~**200–300 MB** (native memory, not the Java heap). The exact numbers will be measured and reported in M2. A "quality" setting (4/8 layers) can lower RAM use.
- Voices: 48 (spec: 32+), with voice stealing, CC64 sustain, and natural releases.

**Binary handling:** samples are **not in git and not in the APK**.
- The processed pack is published once as a release asset in the public `mimikopi-releases` repo (tag `samples-v1`), with a SHA-256 checksum.
- The app downloads it on first run (or from Settings), verifies the checksum, and stores it in app storage. APK updates stay small and don't re-download the samples.
- Until the pack is downloaded, the keyboard shows a clear "download piano sound" state (not a fake sound). The metronome uses synthesized clicks, so it works without the pack.
- Git LFS is rejected: its free quota (1 GB of bandwidth) is too small and it doesn't help the in-app download.

## 5. Signing, CI and release

- **Workflows (main repo):**
  - `ci.yml` on every push/PR: `:core:test`, lint, `assembleDebug`, and upload the debug APK as an artifact.
  - `release.yml` on a `v*` tag: build the signed release APK, compute SHA-256, create a release in `mimikopi-releases`, and update `latest.json` there.
  - `samples.yml` (manual): build the sample pack.
- **Secrets** (the user adds them in GitHub web → Settings → Secrets and variables → Actions; nothing goes in git):
  - `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
  - `RELEASES_TOKEN`: a fine-grained PAT scoped **only** to `mimikopi-releases` with Contents: read & write.
- **Keystore:** in M1 I generate it once in this session with `keytool` (RSA 4096, 30+ years validity) and send the `.jks` file and passwords to the user privately in this chat. The user then:
  1. keeps an **offline backup** (if it is lost, updates over the installed app become impossible),
  2. pastes the secrets into GitHub,
  3. lets me delete the local copy.
  *Alternative:* the user generates it themselves, but that is hard on a phone.
- **Debug and release APKs use different signatures.** The user installs only release APKs on the phone; debug APKs are for checking CI.
- **`latest.json`:** `{versionCode, versionName, apkUrl, sha256, notesTh, minSamplesVersion}`, fetched unauthenticated from `raw.githubusercontent.com/coolza254-lgtm/mimikopi-releases/main/latest.json`.
- **In-app update:**
  1. Fetch `latest.json`, compare versionCode, and show the Thai release notes.
  2. Offer a JSON backup first.
  3. Download to the cache dir and verify SHA-256 (on a mismatch, delete the file and show an error).
  4. Install via a `PackageInstaller` session. This needs the `REQUEST_INSTALL_PACKAGES` permission, plus the one-time "Install unknown apps" toggle for ミミコピ.
  5. Never install silently.

## 6. Android developer verification (researched 2026-10-10)

**Confirmed from developer.android.com (verification overview, guides, FAQ):**
- From **30 Sep 2026**, enforcement is active in **Brazil, Indonesia, Singapore and Thailand**. It applies **only to installs from participating stores**: Google Play, Galaxy Store, HONOR, OPPO, Transsion Palm Store, vivo and Xiaomi GetApps.
- FAQ, quoted: *"If you distribute your app through other stores, or if users sideload your app directly, these new verification requirements won't apply to your app yet."* → **Installing our APK from GitHub on the A54 is not affected today.**
- **2027:** Google will "expand these protections globally for all apps on certified Android devices" (no exact date). After that, per the FAQ, an *unregistered* app can be installed **or updated** only with the **advanced flow** enabled or over ADB. That would affect our in-app updates.
- **Advanced flow** (for unregistered apps):
  1. Turn on Developer options.
  2. Pass a "not being coached" check.
  3. Restart and re-authenticate.
  4. Wait a **one-time 24 hours**.
  5. Confirm with biometrics or PIN.
  6. Allow installs for 7 days or indefinitely. A warning with "Install anyway" still appears.
  ADB installs are exempt.
- **Limited distribution account:** free, **no ID verification**, up to **20 devices**, for "testing and personal use". It has been globally available since Aug 2026. Users "accept an invitation" from the developer. Package registration in general works by uploading an APK signed with the release key in the **Android Developer Console** (`android.google.com/developerconsole`).

**Not confirmed:**
- The exact device-invitation steps of the limited-distribution flow.
- Whether `PackageInstaller` self-updates are treated differently from other sideloads (no official page mentions it).
- How the 30 Sep rollout is actually behaving on Samsung devices.

**Recommendation:** after M1 produces the first signed release APK, the user creates a **free limited distribution account** and registers `com.id.mimikopi` with our release key and their A54. That makes the app "registered" before the 2027 global rollout, so updates keep working without the advanced flow. Until then, nothing is needed. `docs/INSTALL.md` will hold the step-by-step guide plus the One UI specifics:
- "Install unknown apps" per app (here and for the browser/Files app),
- **Samsung Auto Blocker** (Settings → Security and privacy → Auto Blocker), which blocks installs from unknown sources when on and must be switched off to sideload. I know this from One UI 6+; it still needs confirming on the user's One UI 8.

## 7. Milestone split: one suggested change

I suggest **moving "in-app update + backup" (M4) to right after M1** (it would become M2, and Sound/Metronome/MIDI shift by one). From then on, each milestone is installed by tapping "Check for updates" instead of downloading APKs by hand, and the update path gets tested many times before the MVP. Order:

- **M1** Skeleton, CI, release pipeline, Diagnostics
- **M2** Update + backup
- **M3** Sound + sample pack
- **M4** Metronome
- **M5** MIDI
- **M6** Learning-loop slice

M1–M6 remain the MVP. Later milestones stay as written.

## 8. Risks

1. **CI-only build loop.** Each round trip takes about 5–10 minutes and lint can't run locally. Mitigation: small commits, keep logic in `:core` (fast JVM tests), cache Gradle in CI.
2. **Native code (Oboe/CMake) built only in CI.** It is well supported on GitHub runners. Crashes can only be seen on the phone, so Diagnostics adds a "last native error" field.
3. **Latency, jitter and audio feel are only verifiable on the phone.** Bluetooth headphones will add roughly 150–300 ms. The app measures this and lets the user set an offset per route; `DEVICE-TESTING.md` covers external measurement.
4. **Sample RAM and size** on the A54 (6/8 GB). This is the reason for the layer-count setting; I'll measure it in M3.
5. **Notation engraving effort.** A standard-looking grand staff with beaming, ties and accidentals is a big job. Exercises come first; Verovio is the fallback for full scores.
6. **Badger C-918 is unknown** (key count, velocity, USB MIDI class compliance). If it isn't USB-MIDI class compliant, USB won't work at all. The capability probe and checklist will show this.
7. **Scope.** M7, M10 and M11 are each large; I'll report honestly instead of shipping placeholders.
8. **Ephemeral session container.** Everything goes to git; the keystore lives only with the user and in Actions secrets.

## 9. Brand tokens (sampled from the source images)

| Token | Hex | Note |
|---|---|---|
| `paper` | `#FEF5E6` | background of both images (prompt guessed #FFF6E9) |
| `rose` (primary) | `#E6889E` | notebook and wordmark (prompt guessed #E8A0A8) |
| `mustard` (accent) | `#F3BE52` | music note (prompt guessed #F2C14E) |
| `ink` | dark warm neutral, ~`#3A3030` | body text (rose on cream fails contrast for small text) |

The icon mark covers about 398×428 px of the 1254 px canvas (~33%), which matches the prompt. I'll redraw it as a VectorDrawable at ~60% of 108 dp inside the 66 dp safe zone, with a monochrome layer for themed icons, and check it at 48 px.

## 10. Questions for the user before M1

1. Freeze **`applicationId = com.id.mimikopi`**? (It can't change after the first install.)
2. OK to **move in-app update to M2** (section 7)?
3. OK for the **sample pack to be a separate download** from the public releases repo (section 4)?
4. OK for me to **generate the keystore here** and send it to you privately (section 5)?
