# ミミコピ (Mimi Kopi)

A personal piano-learning app for Android: reading sheet music, rhythm, ear training and
"copying songs by ear" (耳コピ). Single user, Thai UI, local data only.

- Project spec: [`docs/PROMPT.md`](docs/PROMPT.md). Learning content spec (Thai, source of truth): [`docs/learning-system.md`](docs/learning-system.md)
- Plan and decisions: [`docs/M0-PLAN.md`](docs/M0-PLAN.md), architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Install on the phone and update: [`docs/INSTALL.md`](docs/INSTALL.md)
- What is not done yet: [`docs/KNOWN-LIMITATIONS.md`](docs/KNOWN-LIMITATIONS.md). Licenses: [`CREDITS.md`](CREDITS.md)

## Build

CI (GitHub Actions) is the build system. Every push runs `:core` tests, lint and a debug APK
(downloadable from the run's artifacts). A `vX.Y.Z` tag builds the signed release and publishes it
to the public [`mimikopi-releases`](https://github.com/coolza254-lgtm/mimikopi-releases) repo.

Locally (needs JDK 17+ and the Android SDK with API 36):

```sh
./gradlew :core:test            # pure-JVM unit tests, no Android SDK needed
./gradlew :app:assembleDebug    # debug APK (application id com.id.mimikopi.debug)
```

## Modules

| Module | What |
|---|---|
| `core/` | Pure Kotlin/JVM: music theory, timing rules, spaced repetition, pass rules, data models. No Android imports. |
| `app/` | Android app: Compose UI, Room database, diagnostics, platform services. |
| `content/` | Editable lesson and config files (JSON/Markdown). |
| `tools/` | Brand asset generators, later the sample-pack builder. |

## Releasing

1. Add Thai notes at `release-notes/vX.Y.Z.md`.
2. Push the tag `vX.Y.Z` (versionCode = X·10000 + Y·100 + Z).
3. The release workflow publishes `mimikopi-vX.Y.Z.apk` and `latest.json` to the releases repo.

Required Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `RELEASES_TOKEN`.
