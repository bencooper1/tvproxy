# TVProxy

A TiviMate-style IPTV player for **Android phones (API 23 / Android 6.0 and up)** — Live TV,
Movies, Series, EPG, and the full premium feature set (multi-view, recording, catch-up,
timeshift, sleep timer, parental controls, backup/restore, …) **free, with no login, no
subscription, and no ads**.

> Clean-room implementation: features and interface modeled on TiviMate, but no TiviMate code,
> assets, or branding. See [decisions.md](decisions.md) ADR-001.

**Status (2026-08-18):** **M0 (Foundation & CI) — scaffold complete**, CI-verified on GitHub Actions
(unit tests, lint, detekt, debug+release builds, instrumented smoke tests on API 23 & API 35
emulators). Next: **M1** data layer. See [agents.md](agents.md) for the live board.

## Building

Requirements: JDK 17+, Android SDK (platform 35). Point Gradle at your SDK via
`local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.

```bash
./gradlew assembleDebug                 # debug APK(s) — ABI splits + universal
./gradlew testDebugUnitTest             # unit tests (test rig: JUnit4/Truth/Turbine/MockWebServer)
./gradlew lintDebug                     # Android lint (0 errors required)
./gradlew detekt                        # static analysis (0 issues required)
./gradlew connectedDebugAndroidTest     # instrumented tests (emulator/device)
./gradlew assembleRelease               # release build (R8/minify sanity)
```

CI (`.github/workflows/ci.yml`) runs all of the above on every push/PR, including the
instrumented smoke test on **API 23 and API 35** emulators.

## Project docs

| Document | Purpose |
|---|---|
| [plan.md](plan.md) | Milestones M0–M8, acceptance criteria, test gates, risks, release plan |
| [architecture.md](architecture.md) | System overview, stack (minSdk 23), modules, data flows, API gates |
| [agents.md](agents.md) | Agent roster + work board: what's done, tested (PASS / needs fix), next steps |
| [decisions.md](decisions.md) | Architecture Decision Records (ADR-001 … ADR-016) |

## Quick links

- Milestone status dashboard → [agents.md](agents.md) and [plan.md](plan.md) §4
- Tech stack & SDK levels → [architecture.md](architecture.md) §2–3
- Device tiers & performance budgets → [plan.md](plan.md) §3
