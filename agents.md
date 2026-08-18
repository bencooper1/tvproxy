# TVProxy — Agent Roster & Work Board

> Living status board: **what each agent has done, whether it is tested (PASS / needs fix),
> and what each agent does next (as milestones).** Every agent updates their own section at the
> end of every task. Last updated: 2026-08-18.

## Status legend

| Mark | Meaning |
|---|---|
| ⬜ Planned | Task defined, not started |
| 🏗️ In progress | Work underway |
| ✅ Done | Code complete (committed) |
| 🧪 Tested | Tests written and **PASS** (with evidence) |
| ❌ Needs fix | Tests FAIL or review found defects — fix before moving on |
| ⛔ Blocked | Waiting on another agent/dependency (note the blocker) |
| ✔️ Released | Shipped in a release build |

**Test result column:** `PASS` (all gates green) · `FAIL` (something broke — details in
*Evidence/notes*) · `—` (no tests yet, e.g. not started / docs-only).

**Rule:** a task is never left at ✅ Done — it must move to 🧪 Tested (PASS) or ❌ Needs fix
before the next milestone gate. Milestones are defined in `plan.md`.

## Project dashboard (as of 2026-08-18)

| Milestone | Title | Overall status | Test gate |
|---|---|---|---|
| M0 | Foundation & CI | 🏗️ Docs ✅, Gradle scaffold ⬜ | CI green |
| M1 | Data layer | ⬜ | Unit PASS |
| M2 | Player engine | ⬜ | Instrumented PASS |
| M3 | Live TV UI | ⬜ | Manual matrix L* |
| M4 | EPG | ⬜ | Manual matrix E* |
| M5 | VOD / Series / Search | ⬜ | Manual matrix V* |
| M6 | Premium pack | ⬜ | Manual matrix P* |
| M7 | Settings & personalization | ⬜ | Manual matrix S* |
| M8 | Hardening & release | ⬜ | Full regression |

**Honest baseline:** this repo currently contains **documentation only** (`README.md`,
`plan.md`, `architecture.md`, `agents.md`, `decisions.md`). No application code, tests, or
build files exist yet. All code tasks below are therefore ⬜ Planned with test result `—`;
they are sequenced by milestone so the board can be filled in as work lands.

---

## Agent A1 — Foundation & Build

- **Mission:** project scaffold, Gradle config, CI, lint/detekt, shared tooling. Unblocks everyone.
- **Scope:** root Gradle files, `com.tvproxy.app` package skeleton, `.github/workflows`, README/docs.
- **Depends on:** — · **Unblocks:** A2–A8.

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 1.1 | Project docs (plan, architecture, agents, decisions, README) | ✅ Done | — | Docs-only deliverable, committed 2026-08-18 | Review by A8 QA |
| 1.2 | Gradle scaffold: Kotlin DSL, version catalog, minSdk 23 / target 35, split APKs | ⬜ Planned | — | — | **M0:** scaffold + `assembleDebug` |
| 1.3 | CI: build + lint + detekt + unit tests; instrumented jobs on API 23 & API 35 emulators | ⬜ Planned | — | — | **M0:** green CI on first PR |
| 1.4 | Baseline unit-test rig (JUnit4, Truth, Turbine, MockWebServer) | ⬜ Planned | — | — | **M0** |
| 1.5 | R8/ProGuard rules + release signing (debug keystore for now) | ⬜ Planned | — | — | **M8** (draft in M0) |

---

## Agent A2 — Data Layer (playlists, Xtream, EPG ingest)

- **Mission:** everything that turns a provider URL into typed data: M3U parser, Xtream client,
  XMLTV parser, Room schema, repositories. No UI.
- **Scope:** `data/`, `core/model`, `core/storage`.
- **Depends on:** A1 (M0) · **Unblocks:** A4, A5, A6, A7.

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 2.1 | Room schema: entities, DAOs, migrations (playlist/channel/group/EPG/VOD/recording/reminder) | ⬜ Planned | — | — | **M1** |
| 2.2 | M3U/M3U8 tag parser (EXTINF, tvg-*, group-title, catchup-*, kodi props) + malformed-input fuzz | ⬜ Planned | — | — | **M1** |
| 2.3 | Xtream Codes client (live, VOD, series v2→v1 fallback, catchup) via Retrofit/Moshi | ⬜ Planned | — | — | **M1** |
| 2.4 | XMLTV parser + batch upsert (100k programs < 30 s, resumable) | ⬜ Planned | — | — | **M1** |
| 2.5 | Playlist import pipeline (fetch → parse → transactional upsert → EPG refresh), progress flow | ⬜ Planned | — | — | **M1** |
| 2.6 | SettingsRepository (DataStore) + SecurePrefs (Keystore AES-GCM) | ⬜ Planned | — | — | **M1** |
| 2.7 | Repository unit tests (MockWebServer Xtream fixtures, parser fixtures) | ⬜ Planned | — | — | **M1** — must end **PASS** |

---

## Agent A3 — Media & Player Engine

- **Mission:** playback core used by every screen: `PlayerSession` (Media3), audio focus,
  errors/retry, catch-up & timeshift URL building, AFR, foreground `PlayerService`.
- **Scope:** `core/player`, `core/media`, `service/`.
- **Depends on:** A1 (M0) · **Unblocks:** A4, A5, A6.

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 3.1 | `PlayerSession` wrapper: play/pause/seek, headers/UA, buffer config, track selection | ⬜ Planned | — | — | **M2** |
| 3.2 | Audio focus handling + error/retry ladder + "open in external player" | ⬜ Planned | — | — | **M2** |
| 3.3 | `CatchUpUrlBuilder` (Xtream params + M3U `{start}/{end}` templates) + unit tests | ⬜ Planned | — | — | **M2** |
| 3.4 | Timeshift controller (pause/buffer where provider allows) | ⬜ Planned | — | — | **M2** |
| 3.5 | AFR hook via `preferredDisplayModeId` (API 23+) with silent fallback | ⬜ Planned | — | — | **M2** |
| 3.6 | `PlayerService` foreground notification + media session | ⬜ Planned | — | — | **M2** |
| 3.7 | Instrumented playback tests (local HLS/TS/MP4 test server) on API 23 + API 35 | ⬜ Planned | — | — | **M2** — must end **PASS** |

---

## Agent A4 — Live TV & EPG UI

- **Mission:** the TiviMate-style guide experience: groups/channels, player screen with zap,
  favorites, EPG timeline & now/next, reminders UI.
- **Scope:** `ui/livetv`, `ui/epg`, `domain/livetv`, `domain/epg`.
- **Depends on:** A2 (M1), A3 (M2) · **Unblocks:** A6.

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 4.1 | Home shell + bottom nav (Live / Movies / Series / Recordings / Settings) | ⬜ Planned | — | — | **M3** |
| 4.2 | Channel list: groups sidebar, search/filter, sort, hide, custom logos | ⬜ Planned | — | — | **M3** |
| 4.3 | Favorites (multi-playlist) + recent history + "start on last channel" | ⬜ Planned | — | — | **M3** |
| 4.4 | Player screen: zap bar (prev/next, number pad, history), buffer/status overlays | ⬜ Planned | — | — | **M3** |
| 4.5 | EPG timeline composable (now-line, past shading, catch-up icons) — virtualized, 60 fps | ⬜ Planned | — | — | **M4** |
| 4.6 | Now/Next list view + program details + reminders (set/cancel) | ⬜ Planned | — | — | **M4** |
| 4.7 | Compose UI tests (lists, favorites, timeline math) + manual matrix L1–L8 / E1–E6 | ⬜ Planned | — | — | **M3/M4** — must end **PASS** |

---

## Agent A5 — VOD, Series & Search

- **Mission:** movies/series catalogs, detail screens, global search, continue-watching.
- **Scope:** `ui/vod`, `domain/vod`, `data/vod`.
- **Depends on:** A2 (M1), A3 (M2).

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 5.1 | Movies grid (posters, genres, year, paging) + detail screen | ⬜ Planned | — | — | **M5** |
| 5.2 | Series: seasons/episodes tree + detail + playback | ⬜ Planned | — | — | **M5** |
| 5.3 | Search across channels/movies/series (< 1 s over 20k items) | ⬜ Planned | — | — | **M5** |
| 5.4 | Continue-watching / recent VOD | ⬜ Planned | — | — | **M5** |
| 5.5 | UI tests + manual matrix V1–V5 | ⬜ Planned | — | — | **M5** — must end **PASS** |

---

## Agent A6 — Premium Feature Pack (multi-view, recording, catch-up, timeshift, sleep timer)

- **Mission:** the "TiviMate premium" crown jewels, all free: multi-view, PVR recording with
  scheduler, catch-up playback, timeshift, sleep timer, EPG reminders, startup channel.
- **Scope:** `ui/multiview`, `ui/recordings`, `core/media`, `core/scheduling`, `core/player`.
- **Depends on:** A3 (M2), A4 (M3/M4).

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 6.1 | Multi-view: 2×1/2×2 grid, SurfaceView tiles, single audio stream, tap=audio swap, long-press=promote; RAM/codec capability cap (2 on <2 GB) | ⬜ Planned | — | — | **M6** |
| 6.2 | Recording: one-touch + schedule-from-EPG; AlarmManager exact alarms (API 31+ permission), warm pre-roll | ⬜ Planned | — | — | **M6** |
| 6.3 | RecordingService (foreground + wake lock) → MediaMuxerSink → .ts/.mp4 in app storage | ⬜ Planned | — | — | **M6** |
| 6.4 | Recordings list: play/rename/delete/export via SAF | ⬜ Planned | — | — | **M6** |
| 6.5 | Catch-up playback UI (play past EPG program) + timeshift pause/resume | ⬜ Planned | — | — | **M6** |
| 6.6 | Sleep timer (15/30/60/90/custom) + EPG reminders + startup-channel option | ⬜ Planned | — | — | **M6** |
| 6.7 | Instrumented tests (scheduler timing, multi-view lifecycle, recording e2e) + manual matrix P1–P10 | ⬜ Planned | — | — | **M6** — must end **PASS** |

---

## Agent A7 — Settings & Personalization

- **Mission:** playlists manager, parental PIN, appearance/themes, backup/restore, playback
  preferences (buffer, UA, AFR toggle), proxy support.
- **Scope:** `ui/settings`, `domain/settings`, `core/storage`.
- **Depends on:** A2 (M1); parallel with M3–M6.

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 7.1 | Playlist manager: add/edit/enable/disable/delete, up to 5 playlists, per-playlist UA/buffer | ⬜ Planned | — | — | **M7** |
| 7.2 | Parental PIN: PBKDF2 + Keystore AES-GCM, lock areas, grace period, rate limiting | ⬜ Planned | — | — | **M7** |
| 7.3 | Appearance: light/dark/black themes, accent color, panel transparency, fonts | ⬜ Planned | — | — | **M7** |
| 7.4 | Backup/restore via SAF JSON (playlists creds, settings, favorites, history, recording metadata) | ⬜ Planned | — | — | **M7** |
| 7.5 | Playback prefs: buffer size, AFR toggle, default player behavior, HTTP proxy | ⬜ Planned | — | — | **M7** |
| 7.6 | Unit tests (PIN crypto, backup round-trip) + manual matrix S1–S9 | ⬜ Planned | — | — | **M7** — must end **PASS** |

---

## Agent A8 — QA, Test & Release

- **Mission:** owns the test strategy, manual matrices, performance budgets, soak tests,
  release builds, and keeps this board honest.
- **Scope:** everything; gatekeeper for milestone sign-off.
- **Depends on:** all agents.

| # | Task | Status | Test result | Evidence / notes | Next step |
|---|---|---|---|---|---|
| 8.1 | Define manual matrices (L/E/V/P/S) & device lab (API 23, API 26, API 35) | ✅ Done | — | Matrix rows referenced in `plan.md` §6, committed 2026-08-18 | Execute from M3 onward |
| 8.2 | CI test rig on API 23 + API 35 emulators (with A1) | ⬜ Planned | — | — | **M0** |
| 8.3 | Performance budgets (startup ≤ 2.5 s, zap ≤ 1.5 s, RSS ≤ 300 MB, 60 fps EPG) | ⬜ Planned | — | — | **M8** (first pass M3) |
| 8.4 | 24 h soak on API 23 + API 35 + crash-log review | ⬜ Planned | — | — | **M8** |
| 8.5 | Release: R8, split APKs, signed build, install test, docs final pass | ⬜ Planned | — | — | **M8** |
| 8.6 | Full regression sign-off per milestone gate | ⬜ Planned | — | — | each milestone |

---

## Handoff matrix (who needs what from whom)

| Agent needs | From | At milestone |
|---|---|---|
| A2, A3, A7 | A1 scaffold + CI | M0 |
| A4 (UI) | A2 repos + A3 PlayerSession | M1, M2 |
| A5 | A2 Xtream VOD repos + A3 PlayerSession | M1, M2 |
| A6 | A3 player/service + A4 EPG/player UI | M2, M3, M4 |
| A8 | everything | all |

## How to update this board

1. After **any** code change: update the task's *Status* and *Test result* with evidence
   (test class names / CI link / device + log excerpt).
2. `FAIL` or `❌ Needs fix` blocks the milestone — resolve or escalate in the next sync.
3. When a milestone's test gate passes, the owner marks it 🧪 Tested in `plan.md` §4 and
   files its status here.
4. Never mark 🧪 Tested without runnable evidence; the board is the project's source of truth.
