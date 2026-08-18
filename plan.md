# TVProxy — Project Plan

> A TiviMate-style IPTV player for **Android phones (API 23 / Android 6.0 and up)** with the full
> premium feature set unlocked **out of the box — no subscription, no login/account, no ads**.
>
> Working title: **TVProxy** (repo: `tvproxy`). Clean-room implementation — features and interface
> modeled on TiviMate, but no TiviMate code, assets, or branding are used (see `decisions.md` ADR-001).

- **Last updated:** 2026-08-18
- **Current phase:** M0 (Foundation) — documentation created; no application code written yet
- **Status legend:** ⬜ Planned · 🏗️ In progress · ✅ Done · 🧪 Tested (all gates green) · ❌ Needs fix · ⛔ Blocked · ✔️ Released

---

## 1. Product goal

Build a free, self-contained IPTV player for Android phones that:

1. Plays **Live TV**, **Movies (VOD)** and **TV Series** from **M3U/M3U8 playlists** and
   **Xtream Codes** providers.
2. Shows an **EPG (TV guide)** — timeline, now/next, and list views — with XMLTV and Xtream EPG.
3. Unlocks every feature TiviMate sells as *premium*, for free, with **no login, no account,
   no license check**:
   - Multiple playlists (up to 5) and full group/channel management
   - Favorites, channel history, manual sorting, custom logos
   - Multi-view (watch 2–4 channels at once, capability-capped)
   - PVR-style recording with scheduler (incl. one-touch + program-based recording)
   - Catch-up TV and timeshift (when the provider supports it)
   - EPG reminders, sleep timer, parental PIN lock
   - Backup & restore (playlists, favorites, settings)
   - Appearance customization (themes, accent color, panel transparency)
   - Auto frame-rate (where the device supports it), audio passthrough where available
   - Zero ads, zero telemetry

## 2. Non-goals (explicitly out of scope)

- Android TV / Fire TV builds in v1 (phone-first; architecture leaves room for a TV variant)
- DRM-protected streams (Widevine) beyond what Media3 provides for free
- Server-side components, cloud sync, or multi-device license sync (no backend at all)
- Any form of paid tier, in-app purchase, or ad SDK
- Copying TiviMate's proprietary code, UI assets, or brand (clean-room clone only)

## 3. Target device matrix & performance budget

| Tier | Devices | API | RAM | Baseline expectations |
|---|---|---|---|---|
| **Baseline (must work)** | Nexus 5X-class / low-end 2015–2016 phones | 23 (Android 6.0) | 2 GB | All features; multi-view capped to 2 streams |
| **Main** | Mid-range 2017–2020 phones | 26–30 | 3–4 GB | Multi-view 4 streams |
| **Modern** | 2021+ phones | 31–35 | 4 GB+ | Everything incl. PiP (26+), reminders, AFR |

**Performance budgets (measured in M8, re-checked each milestone):**

- Cold start → channel list ready: **≤ 2.5 s** on baseline tier
- Channel zap → first frame: **≤ 1.5 s** local playlist, **≤ 3 s** network
- EPG timeline scroll: **60 fps** with 10k cached programs on baseline tier
- Multi-view: 2 streams on 1 GB RAM, 4 streams on ≥ 2 GB; audio switch ≤ 300 ms
- Player process RSS ≤ 300 MB; APK ≤ 40 MB (armeabi-v7a + arm64-v8a)

## 4. Milestones

Each milestone is a shippable, test-gated increment. A milestone is **Done** only when its
**acceptance criteria** and **test gate** pass and the agent board in `agents.md` is updated.

### M0 — Foundation & CI ✅ (docs) / ⬜ (scaffold)
- **Owner:** Agent A1 · **Depends on:** —
- **Deliverables:** Gradle project scaffold, module/package layout (`architecture.md` §5), lint + detekt config, unit-test rig, GitHub Actions CI (build + lint + unit tests + instrumented tests on API 23 and API 35 emulators), README + this documentation set.
- **Acceptance criteria:** `./gradlew assembleDebug` green on CI; lint 0 errors; sample unit test runs; API 23 emulator boots and runs a smoke instrumented test.
- **Test gate:** CI green on PR; manual `gradlew` build on clean checkout.
- **Status:** 🏗️ Partially done — project docs (this file, `architecture.md`, `agents.md`, `decisions.md`) ✅; Gradle scaffold ⬜ (next).

### M1 — Data layer (playlists + EPG ingest)
- **Owner:** Agent A2 · **Depends on:** M0
- **Deliverables:** Room schema (playlist, channel, group, EPG program, VOD/series, recording, reminder); M3U/M3U8 tag parser; Xtream Codes client (live/vod/series/catchup APIs); XMLTV parser; repositories with paging; DataStore settings; unit tests.
- **Acceptance criteria:** Parse 10k-channel M3U < 5 s on baseline tier; Xtream live+vods+series sync; XMLTV 100k-program import < 30 s; resume/interrupt-safe imports; duplicate-safe upserts.
- **Test gate:** Unit tests PASS (parsers with fixture files incl. malformed input; MockWebServer for Xtream); no UI yet — verified via instrumented repo tests on API 23.
- **Status:** ⬜ Planned

### M2 — Media & player engine
- **Owner:** Agent A3 · **Depends on:** M0 (parallel with M1)
- **Deliverables:** Media3 wrapper (`PlayerSession`): play/pause/seek, audio focus, buffering & user-agent per playlist, retry/error policy, track selection, timeshift/catch-up URL builder, AFR hook (API 23+), `PlayerService` hooks; instrumented playback tests.
- **Acceptance criteria:** Live HLS/MPEG-TS and VOD MP4/HLS play; errors surface with retry; pause/seek works for VOD and catch-up; audio focus ducking/stop correct; no ANRs on API 23.
- **Test gate:** Instrumented tests on API 23 + API 35 emulators with local test streams; unit tests for URL/catch-up builders PASS.
- **Status:** ⬜ Planned

### M3 — Live TV UI (channels, groups, player, zapping)
- **Owner:** Agent A4 · **Depends on:** M1, M2
- **Deliverables:** Channel list (groups sidebar, search/filter, sort, hide), favorites, channel details/logo edit, player screen with zap controls (prev/next, number pad, history), channel refresh; landscape + portrait layouts.
- **Acceptance criteria:** Zap < 1.5 s; group switching instant; favorites persist across restarts; all list operations smooth at 10k channels.
- **Test gate:** Manual test matrix (see §6) rows L1–L8; Compose UI tests for list/favorites PASS; no crash in 30-min soak.
- **Status:** ⬜ Planned

### M4 — EPG (guide)
- **Owner:** Agent A4 · **Depends on:** M1, M3
- **Deliverables:** Timeline EPG (horizontal time axis, vertical channels, now-line, past shading), Now/Next list view, program details, EPG refresh (manual + WorkManager periodic), reminders (tap program → notify at start).
- **Acceptance criteria:** Timeline scrolls 60 fps with 10k cached programs; correct timezone handling incl. DST; reminders fire within ±30 s; EPG survives app restart (Room).
- **Test gate:** Unit tests for time-grid math PASS; UI tests; manual matrix E1–E6; reminder instrumented test on API 23 (notification).
- **Status:** ⬜ Planned

### M5 — VOD, Series & Search
- **Owner:** Agent A5 · **Depends on:** M1, M2
- **Deliverables:** Movies grid (posters, genres, year), Series (seasons/episodes), detail screens, search across channels/movies/series, continue-watching, recent VOD.
- **Acceptance criteria:** Xtream VOD/series catalogs browse smoothly (paged); search < 1 s over 20k items; playback via shared `PlayerSession`.
- **Test gate:** Repo unit tests PASS; UI tests; manual matrix V1–V5.
- **Status:** ⬜ Planned

### M6 — Premium feature pack
- **Owner:** Agent A6 · **Depends on:** M2, M3, M4
- **Deliverables:** **Multi-view** (2/4 tiles, single audio stream, tap to swap audio, promote to full screen, capability capping), **recording** (one-touch, scheduled from EPG, recordings list, play/delete/export via SAF), **catch-up** playback UI, **timeshift** (pause/buffer where supported), **sleep timer**, **EPG reminders** (moved from M4 if time), **"startup channel"** option.
- **Acceptance criteria:** Recording start/stop within ±5 s of schedule; recorded file plays back; multi-view 2 streams on baseline tier with audio switching < 300 ms; sleep timer stops playback; catch-up plays past programs when provider supports it (verified against a local test server).
- **Test gate:** Instrumented tests (recording scheduler, multi-view lifecycle) on API 23; manual matrix P1–P10.
- **Status:** ⬜ Planned

### M7 — Settings & personalization
- **Owner:** Agent A7 · **Depends on:** M1 (parallel with M3–M6)
- **Deliverables:** Playlist manager (add/edit/enable/disable/up to 5), parental PIN lock (lock Live/VOD/Series/Settings), appearance (light/dark/black themes, accent color, panel transparency), buffer-size & user-agent settings, **backup/restore** via SAF (JSON bundle), auto frame-rate toggle, EPG update interval, default player behavior (remember last channel, start on last channel).
- **Acceptance criteria:** PIN survives restart & is not recoverable from plain prefs; backup/restore round-trips playlists + favorites + settings on a fresh install; all settings take effect without app restart where feasible.
- **Test gate:** Unit tests for PIN hashing/backup serialization PASS; manual matrix S1–S9.
- **Status:** ⬜ Planned

### M8 — Hardening, performance & release
- **Owner:** Agent A8 · **Depends on:** M3–M7
- **Deliverables:** Full performance budget pass on baseline tier; memory profiling (RSS ≤ 300 MB); crash/leak fixes; ProGuard/R8 rules; release APK (armeabi-v7a, arm64-v8a); local crash log + "send log" export; docs final pass; full test matrix sign-off.
- **Acceptance criteria:** All §3 budgets met; 0 crashes in 24 h soak on API 23 + API 35; release build signed and installable over adb.
- **Test gate:** Full regression run (all manual matrices), instrumented suite on API 23/35, lint + detekt clean.
- **Status:** ⬜ Planned

## 5. Dependency graph

```
M0 ──► M1 ──► M4 ──► M6
 │      │
 │      └──► M3 ──────┘
 ├──► M2 ─────────────┘
 ├──► M7 (parallel, needs M1)
 └──► M5 (needs M1, M2)
            └──► M8 (needs M3–M7)
```

## 6. Testing strategy

| Layer | Tooling | Where it runs |
|---|---|---|
| Unit (parsers, time math, URL builders, PIN, backup) | JUnit4, Truth, Turbine, MockWebServer | CI (Ubuntu, JVM) |
| Repository / DB | Room in-memory + Robolectric where needed | CI |
| Player instrumentation | Media3 test streams served from local HTTP server | CI on API 23 + API 35 emulators |
| UI (Compose) | compose-ui-test + Espresso | CI on API 35 (fast) + spot-checks API 23 |
| Manual matrix | Checklist per milestone (L*, E*, V*, P*, S* rows) | Device lab: baseline API 23 phone, API 26 phone, modern API 35 phone |
| Performance | Macrobenchmark (startup, zap), Profiler (RSS), `adb shell dumpsys` | M8 + re-check each milestone |

**Definition of Done for any task:** code ✅ → unit/instrumented tests written and **PASS** →
lint/detekt clean → `agents.md` board updated (status + test result + evidence) → commit pushed.

## 7. Risks & mitigations

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Old/weak decoders on API 23 → multi-view stutter | High | Cap streams by RAM/`MediaCodecList`; SurfaceView (not TextureView); quality presets |
| R2 | Exact-alarm permission changes (API 31+) break recordings | High | `SCHEDULE_EXACT_ALARM` request + fallback to inexact alarm with early wakeup; document behavior |
| R3 | Compose memory pressure on 2 GB devices | Medium | Lazy layouts everywhere, image downsizing, leak checks in M8, fallback perf pass |
| R4 | Provider catch-up URL formats vary wildly | Medium | Plug-in URL builders per playlist type + custom overrides; conformance test server |
| R5 | Android 6 lacks modern codecs (HEVC/VP9 on some SoCs) | Medium | Codec whitelist + graceful error messages; rely on Media3 selection |
| R6 | Background streaming/recording killed on 8.0+ | Medium | Foreground service + wake lock for recording; user-visible notification |
| R7 | Large EPG imports block UI | Medium | Batch inserts, coroutine off main thread, progress UI, incremental refresh |

## 8. Release plan

- **v0.1 (alpha)** — after M3: live TV + playlists, sideload APK.
- **v0.2 (beta)** — after M4 + M5: EPG + VOD/series.
- **v1.0** — after M8: full premium feature set, release APKs, docs sign-off.
- Distribution: sideload/APK (no Play billing needed since there is no paid tier).
