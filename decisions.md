# TVProxy — Decisions (ADR log)

> Architecture Decision Records for the TVProxy Android app (TiviMate-style IPTV player,
> minSdk 23, no login/subscription, premium features free). Last updated: 2026-08-18.

## Decision summary

| ID | Title | Status | Date |
|---|---|---|---|
| ADR-001 | Clean-room clone; codename TVProxy; no TiviMate branding/assets | Accepted | 2026-08-18 |
| ADR-002 | No accounts, no subscriptions, no license checks — everything local | Accepted | 2026-08-18 |
| ADR-003 | Kotlin + Jetpack Compose + Material 3, single `:app` module | Accepted | 2026-08-18 |
| ADR-004 | minSdk 23 (Android 6.0), targetSdk 35 | Accepted | 2026-08-18 |
| ADR-005 | Media3 (ExoPlayer) as the only playback engine | Accepted | 2026-08-18 |
| ADR-006 | Room + DataStore + custom M3U/XMLTV parsers + Moshi (Xtream) | Accepted | 2026-08-18 |
| ADR-007 | Hilt for dependency injection | Accepted | 2026-08-18 |
| ADR-008 | Multi-view = N independent ExoPlayers in one activity, one audio stream, capability-capped | Accepted | 2026-08-18 |
| ADR-009 | Recording via Media3 MediaMuxerSink + AlarmManager exact alarms + foreground service; export via SAF | Accepted | 2026-08-18 |
| ADR-010 | Picture-in-Picture gated to API 26+ | Accepted | 2026-08-18 |
| ADR-011 | Parental PIN: PBKDF2 hash encrypted with Keystore AES-GCM | Accepted | 2026-08-18 |
| ADR-012 | Zero ads SDKs, zero analytics, opt-in local crash log only | Accepted | 2026-08-18 |
| ADR-013 | Catch-up & timeshift are provider-dependent; pluggable URL builders + conformance test server | Accepted | 2026-08-18 |
| ADR-014 | Backup/restore = single JSON bundle via SAF | Accepted | 2026-08-18 |
| ADR-015 | Background work: WorkManager (EPG refresh) + AlarmManager (recordings/reminders) | Accepted | 2026-08-18 |
| ADR-016 | Performance budgets & device tiers define the supported baseline (2 GB RAM / API 23) | Accepted | 2026-08-18 |

---

## ADR-001 — Clean-room clone; codename TVProxy; no TiviMate branding

- **Status:** Accepted · 2026-08-18
- **Context:** The product must look and feel like TiviMate (interface + feature set), but
  TiviMate's code, UI assets, and trademark are proprietary.
- **Decision:** Build a clean-room implementation. Feature list and general UX patterns
  (channel list + groups, EPG timeline, zap bar, multi-view grid) are modeled on the public
  behavior of TiviMate, but every line of code, layout, icon, and string is original. The app
  is named **TVProxy**; the TiviMate name appears only in docs for comparison, never in the app.
- **Consequences:** Safe from copyright/trademark issues; more design work (original icons,
  wording). Feature parity is a goal, pixel parity is not.
- **Alternatives:** Forking/ripping TiviMate assets — rejected (legal risk, unmaintainable).

## ADR-002 — No accounts, no subscriptions, no license checks

- **Status:** Accepted · 2026-08-18
- **Context:** TiviMate sells premium (multi-view, recording, catch-up, multi-playlist, etc.).
  This app must include all of those features free, with no login and no purchase flow.
- **Decision:** There is no account system, no billing integration, no license server, and no
  feature flag that gates premium features. "Premium" features are regular features. The only
  credentials in the app are the user's own IPTV provider credentials, stored locally.
- **Consequences:** No Play billing complexity, no network dependency for unlock; simpler
  architecture. App is a sideload APK; not eligible for Play Store paid tiers (fine — no paid
  tier exists).
- **Alternatives:** Freemium with local unlock — rejected (contradicts the requirement).

## ADR-003 — Kotlin + Jetpack Compose + Material 3, single `:app` module

- **Status:** Accepted · 2026-08-18
- **Context:** Need a modern UI toolkit that runs on API 23 and can express custom views
  (EPG timeline) without fighting RecyclerView adapters. Team is small (8 parallel agents).
- **Decision:** Kotlin 2.x with Jetpack Compose + Material 3 (Compose supports minSdk 21).
  Single Gradle module with strict package boundaries (`architecture.md` §3) instead of
  multi-module, to keep builds fast and refactors cheap. Split into feature modules only if
  build time or merge conflicts become a problem.
- **Consequences:** Compose adds memory overhead vs Views — mitigated by lazy layouts,
  image downsizing, and the M8 performance budget on 2 GB devices. Custom EPG timeline is
  easier in Compose (canvas + lazy rows/columns).
- **Alternatives:** XML Views + RecyclerView — lower memory but slower to build custom EPG;
  Multi-module — rejected for now (build overhead).

## ADR-004 — minSdk 23 (Android 6.0), targetSdk 35

- **Status:** Accepted · 2026-08-18
- **Context:** Requirement: works on Android phones from API 23 up. Modern behavior still
  desired on new devices.
- **Decision:** `minSdk = 23`, `compileSdk = targetSdk = 35` (Android 15). Policy: bump
  targetSdk yearly; never raise minSdk without a product decision. API-gated features
  (PiP 26+, notification channels 26+, exact-alarm permission 31+, `POST_NOTIFICATIONS` 33+)
  are handled with graceful fallbacks per `architecture.md` §6.
- **Consequences:** Must test on API 23 (CI emulator + baseline device). Some modern APIs need
  version guards; no `java.time` desugaring issues (enable core library desugaring if needed).
- **Alternatives:** minSdk 24/26 — rejected (requirement is 23).

## ADR-005 — Media3 (ExoPlayer) as the only playback engine

- **Status:** Accepted · 2026-08-18
- **Context:** Live TV (HLS/MPEG-TS), VOD, catch-up, and multi-view all need robust adaptive
  playback on old devices.
- **Decision:** Use Media3 ExoPlayer 1.5.x (minSdk 21 ✔) for everything: live, VOD,
  catch-up, and multi-view (one ExoPlayer instance per tile). Never drop to
  `MediaPlayer`/VLC libs; the "external player" option is an `ACTION_VIEW` intent, not a
  bundled engine.
- **Consequences:** Single well-tested engine; MediaMuxerSink available for recordings;
  format support (HLS/TS/DASH/MP4) out of the box. Multi-view uses more memory — capped per
  ADR-008.
- **Alternatives:** VLC/FFmpeg libs — heavy APK, licensing and codec complexity — rejected.

## ADR-006 — Room + DataStore + custom M3U/XMLTV parsers + Moshi (Xtream)

- **Status:** Accepted · 2026-08-18
- **Context:** Data: playlists (M3U), Xtream JSON APIs, XMLTV EPG feeds, settings. Want
  offline cache, fast queries at 10k+ channels/100k+ programs.
- **Decision:** Room for structured data (indexed EPG queries, paging), DataStore Preferences
  for settings, **hand-written M3U and XMLTV parsers** (XmlPullParser — lightweight, full
  control of tag quirks), Retrofit + Moshi for Xtream JSON.
- **Consequences:** Parser bugs are ours to fix (mitigated by fixture + fuzz tests, ADR-013
  test server); no dependency bloat; full control over nonstandard provider quirks.
- **Alternatives:** Third-party M3U/XMLTV libs — rejected (poor maintenance, less control).

## ADR-007 — Hilt for dependency injection

- **Status:** Accepted · 2026-08-18
- **Context:** Many agents wiring many components (player factory, repositories, schedulers).
- **Decision:** Hilt (Dagger) with `@Singleton` repositories and scoped ViewModels.
- **Consequences:** Fast DI wiring; slightly longer builds (acceptable). Manual DI rejected
  (boilerplate, inconsistent wiring across 8 agents).

## ADR-008 — Multi-view: N independent ExoPlayers, one audio stream, capability-capped

- **Status:** Accepted · 2026-08-18
- **Context:** TiviMate premium multi-view (up to 4–9 tiles). Phones are weaker than TV boxes;
  API 23 devices may have few hardware decoders and little RAM.
- **Decision:** `MultiViewActivity` hosts up to 4 independent ExoPlayers, each on a
  `SurfaceView` tile. Exactly one tile owns audio (others muted; tap swaps audio < 300 ms).
  Cap = 2 tiles when RAM < 2 GB or `MediaCodecList` reports insufficient concurrent decoders;
  4 otherwise. Long-press promotes a tile to full screen.
- **Consequences:** Simple, robust architecture (no video-mixing); caps needed to protect
  baseline tier; audio swap UX matches TiviMate.
- **Alternatives:** TextureView mixing/single-surface compositing — rejected (API 23
  performance risk, complexity).

## ADR-009 — Recording: MediaMuxerSink + exact alarms + foreground service; SAF export

- **Status:** Accepted · 2026-08-18
- **Context:** Scheduled PVR recording must survive app-in-background and even process death
  on Android 6–15.
- **Decision:** Schedule with `AlarmManager.setExactAndAllowWhileIdle` (API 23+; on API 31+
  request `SCHEDULE_EXACT_ALARM` with an inexact fallback). `RecordingService` runs as a
  foreground service with wake lock and notification; records with ExoPlayer →
  `MediaMuxerSink` → `.ts`/`.mp4` in app-specific external storage (no storage permission).
  Export/share via SAF. Pre-roll warm-up 30 s before program start.
- **Consequences:** Reliable recordings on 23–35; user sees a recording notification;
  exact-alarm permission adds a settings screen on 31+.
- **Alternatives:** WorkManager for recordings — rejected (not reliable for exact times).

## ADR-010 — Picture-in-Picture gated to API 26+

- **Status:** Accepted · 2026-08-18
- **Context:** PiP is a premium-flavored feature; Android PiP API exists only from API 26.
- **Decision:** Offer PiP on API 26+ only (with `setAutoEnterEnabled` for live playback);
  hide it entirely below 26 (no floating-window hack — `SYSTEM_ALERT_WINDOW` rejected:
  permissions, UX, battery).
- **Consequences:** No PiP on Android 6–7 devices; honest, permission-free behavior elsewhere.

## ADR-011 — Parental PIN: PBKDF2 hash encrypted with Keystore AES-GCM

- **Status:** Accepted · 2026-08-18
- **Context:** Parental lock must survive restart and not be readable from plain prefs.
- **Decision:** PIN → PBKDF2 (high iteration count, random salt) → hash wrapped with an
  AES-GCM key stored in the Android Keystore → `SecurePrefs`. Wrong-PIN rate limiting
  (5 tries → 30 s lockout). Locked areas: Live, Movies, Series, Settings; configurable grace
  period.
- **Consequences:** Key unusable if Keystore is wiped (PIN reset then requires backup
  restore — documented). No external crypto dep needed.
- **Alternatives:** `androidx.security:security-crypto` — deprecated upstream; plain hash in
  prefs — too weak; both rejected.

## ADR-012 — Zero ads SDKs, zero analytics, opt-in local crash log

- **Status:** Accepted · 2026-08-18
- **Context:** "Premium without subscription" includes ad-free. Privacy is a selling point.
- **Decision:** No ad SDK, no analytics/telemetry SDK, no network calls except to the user's
  configured IPTV providers. Uncaught exceptions are written to a local log file; the user can
  opt-in to export it ("Send log"). No automatic upload.
- **Consequences:** Small APK, no tracking permissions, no Play policy risk from ad SDKs;
  support debugging is manual (log export).
- **Alternatives:** Crashlytics — rejected (telemetry + dependency).

## ADR-013 — Catch-up & timeshift are provider-dependent; pluggable URL builders + test server

- **Status:** Accepted · 2026-08-18
- **Context:** Catch-up works only when the provider supports it, and URL formats vary
  (Xtream params vs M3U `catchup-source` templates).
- **Decision:** Model catch-up as first-class data (channel flags + archive id), with a
  `CatchUpUrlBuilder` per playlist type plus user-overridable template. Timeshift = pause of
  live HLS where the provider permits buffering; otherwise surfaced as unsupported. A local
  conformance test server simulates catch-up/timeshift for automated tests.
- **Consequences:** Honest UX (no fake buttons); testable without real providers; some
  providers will need template tweaks (field notes in code).
- **Alternatives:** Universal magic timeshift — impossible; rejected.

## ADR-014 — Backup/restore = single JSON bundle via SAF

- **Status:** Accepted · 2026-08-18
- **Context:** TiviMate premium offers backup/restore; users switch phones/clear data.
- **Decision:** One JSON file (versioned schema) via SAF create/pick document containing
  playlists (incl. credentials), settings, favorites, sort orders, history, recording
  metadata (not media). Restore is transactional with validation; EPG cache optional (size).
- **Consequences:** Portable, human-inspectable backups; credentials in backup are a user
  responsibility (file is on their chosen storage).
- **Alternatives:** Room DB copy — fragile across versions — rejected.

## ADR-015 — Background work: WorkManager (EPG) + AlarmManager (recordings/reminders)

- **Status:** Accepted · 2026-08-18
- **Context:** EPG refresh is periodic and deferrable; recordings/reminders need exactness.
- **Decision:** WorkManager periodic + manual triggers for EPG refresh (constraints: network,
  not low-battery); AlarmManager for recordings and EPG reminders (exact where permitted,
  ADR-009). Reminder = notification at program start (`POST_NOTIFICATIONS` on 33+).
- **Consequences:** Battery-friendly refresh; exact alarms need permission handling on 31+;
  Doze on API 23+ respected via `setExactAndAllowWhileIdle`.

## ADR-016 — Performance budgets & device tiers define the supported baseline

- **Status:** Accepted · 2026-08-18
- **Context:** "Works on API 23" must mean something measurable; features like multi-view and
  Compose UI can degrade old hardware.
- **Decision:** Adopt `plan.md` §3: three device tiers; budgets (startup ≤ 2.5 s, zap ≤ 1.5 s,
  EPG 60 fps @ 10k programs, RSS ≤ 300 MB, APK ≤ 40 MB); multi-view capped by tier. Budgets
  are milestone gates enforced by Agent A8.
- **Consequences:** Clear pass/fail for every milestone; occasional perf work scheduled as
  first-class tasks.
- **Alternatives:** "Best effort" — rejected (untestable).

---

## Change process

- New decision → add ADR row + full record, set status **Proposed**, get milestone-gate
  review (Agent A8), then **Accepted**.
- Reversal → keep the old record, mark **Superseded**, reference the new ADR.
- Every ADR must be reflected in `architecture.md` and `plan.md` where relevant.
