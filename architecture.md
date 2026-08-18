# TVProxy — Architecture

> Working title **TVProxy** — a TiviMate-style IPTV player for Android phones, **minSdk 23
> (Android 6.0)**, all premium features unlocked, no login/subscription, no ads.
> Last updated: 2026-08-18. Status: architecture baseline — no application code written yet.

---

## 1. System overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                               TVProxy app                                  │
│                                                                            │
│  ┌─────────────┐   ┌──────────────┐   ┌───────────────┐   ┌─────────────┐ │
│  │  UI layer   │   │  Domain      │   │  Data layer   │   │  Core       │ │
│  │ (Compose)   │──▶│  (use cases) │──▶│  (repo/DAO)   │──▶│  (player,   │ │
│  │  screens,   │   │  interactors │   │  parsers,     │   │  services,  │ │
│  │  viewmodels │   │  models      │   │  network,     │   │  storage)   │ │
│  └─────────────┘   └──────────────┘   │  Room/DataStore│   └─────────────┘ │
│                                        └──────┬────────┘         │          │
│                                               │                  │          │
└───────────────────────────────────────────────┼──────────────────┼──────────┘
                                                │                  │
                      ┌─────────────────────────┼──────────────────┼──────────┐
                      │  Internet (user's IPTV) │                  │          │
                      ▼                         ▼                  ▼          │
              M3U/M3U8 URL            Xtream Codes API      XMLTV EPG feed     │
              (playlist)              (live/vod/series/     (program guide)    │
                                       catchup)                                │
                                                                               ┘
```

- **No backend of our own.** All data comes from the user's IPTV provider(s).
- **All premium features are local.** There is no license server, no account, no purchase
  flow — unlock logic simply doesn't exist (see `decisions.md` ADR-002).

## 2. Technology stack (minSdk 23 safe)

| Concern | Choice | Min-API notes |
|---|---|---|
| Language | Kotlin 2.x (kotlinx.coroutines, Flow) | works on API 23 |
| UI | Jetpack Compose + Material 3 (BOM current), edge-to-edge | Compose min API 21 ✔ |
| Playback | **Media3 (ExoPlayer) 1.5.x** — HLS, MPEG-TS, DASH, SmoothStreaming, progressive | min API 21 ✔ |
| Persistence | Room 2.7.x (playlists, channels, EPG, VOD, recordings); DataStore Preferences (settings) | min API 21 ✔ |
| DI | Hilt (Dagger) | min API 21 ✔ |
| Network | OkHttp 4.12 + Retrofit 2.11 (Xtream JSON) + Moshi | ✔ |
| Parsers | Custom M3U tag parser & XMLTV parser (XmlPullParser) — no heavy deps | ✔ |
| Images | Coil 3 (channel logos, posters) | ✔ |
| Scheduling | WorkManager (EPG refresh); AlarmManager `setExactAndAllowWhileIdle` (recordings/reminders, API 23+); Foreground Service for recording/player | exact alarm perm on API 31+ |
| Security | Android Keystore AES-GCM + PBKDF2 (parental PIN), app-private storage for credentials | Keystore on API 23 ✔ |
| Logging | Timber; local crash log file + "Send log" export (opt-in) | ✔ |
| Build | Gradle (Kotlin DSL), R8, split APKs (armeabi-v7a, arm64-v8a) | ✔ |
| Test | JUnit4, Truth, Turbine, MockWebServer, Robolectric, compose-ui-test, Espresso, Macrobenchmark | API 23 emulator in CI |

SDK levels: **minSdk 23 · targetSdk 35 · compileSdk 35** (bump policy in `decisions.md` ADR-004).

## 3. Module & package layout (single module, strict boundaries)

One Gradle module (`:app`) with package layering, so agents can work in parallel on separate
packages. Split into Gradle modules only if build time/conflicts demand it (ADR-003).

```
com.tvproxy.app
├── core/                    # no Android framework deps except where needed
│   ├── model/               # Playlist, Channel, ChannelGroup, EpgProgram, VodItem,
│   │                        #   SeriesItem, Episode, Recording, Reminder, AppSettings
│   ├── di/                  # Hilt modules (OkHttp, DB, dispatchers, player factory)
│   ├── player/              # PlayerSession, PlayerService, TrackSelector, AudioFocus,
│   │                        #   CatchUpUrlBuilder, TimeshiftController, SleepTimer
│   ├── media/               # MultiViewSession (N PlayerSessions), stream capability probe
│   ├── storage/             # SecurePrefs (Keystore), BackupManager (SAF JSON), CrashLog
│   ├── scheduling/          # RecordingScheduler, ReminderScheduler, EpgRefreshWorker
│   └── util/                # time grid math, format helpers, dispatchers
├── data/
│   ├── db/                  # Room: entities, DAOs, converters, migrations
│   ├── playlist/            # M3uParser, PlaylistRepository (import/sync/refresh)
│   ├── xtream/              # XtreamClient (Moshi DTOs), XtreamRepository
│   ├── epg/                 # XmltvParser, EpgRepository (batch upsert, paging)
│   ├── vod/                 # VOD/series repositories (Xtream-backed)
│   └── settings/            # SettingsRepository (DataStore)
├── domain/                  # use cases / interactors (thin; orchestrates data → UI)
│   ├── livetv/  ├── epg/  ├── vod/  ├── record/  ├── multiview/  └── settings/
└── ui/
    ├── theme/               # color schemes (light/dark/black), accent, typography
    ├── components/          # shared Compose components (ChannelRow, ProgramCard, …)
    ├── main/                # home shell, bottom nav (Live / Movies / Series / Recordings / Settings)
    ├── livetv/              # group list, channel list, zap bar, player screen
    ├── epg/                 # EpgTimeline (custom layout), NowNextList, ProgramDetails
    ├── vod/                 # movie/series grids, detail screens, search
    ├── multiview/           # 2×1 / 2×2 grid activity + tile controls
    ├── recordings/          # scheduler UI, recordings list, export
    └── settings/            # playlists, parental, appearance, backup, playback
└── service/                 # PlayerService, RecordingService (foreground)
```

## 4. Data model (Room entities, key fields)

- **Playlist** — id, name, type (`M3U`|`XTREAM`), url, username, password, userAgent, epgUrl,
  enabled, order, lastSyncAt, logoMode.
- **ChannelGroup** — id, playlistId, name, order, isHidden.
- **Channel** — id, playlistId, groupId, number, name, logoUrl, streamUrl, catchupSource,
  catchupType, catchupDuration, catchupArchiveId, isFavorite, isHidden, sortOrder, lastWatchedAt.
- **EpgProgram** — id, channelId, startEpochMs, endEpochMs, title, subTitle, description,
  episodeNo/seasonNo, imageUrl. Index: (channelId, startEpochMs, endEpochMs).
- **VodItem / SeriesItem / Episode** — Xtream catalog data + poster; search index.
- **Recording** — id, channelId, programTitle, startEpochMs, endEpochMs, state
  (`SCHEDULED|RECORDING|DONE|FAILED|CANCELED`), filePath, sizeBytes.
- **Reminder** — id, channelId, programTitle, startEpochMs, notified flag.
- **AppSettings** (DataStore) — theme, accent, buffers, UA, autoFrameRate, startOnLastChannel,
  sleepTimer defaults, PIN hash (Keystore-wrapped), lastPlayed channel, multi-playlist order.

## 5. Key flows

### 5.1 Playlist import (M3U or Xtream)
```
User adds URL/Xtream creds
  → PlaylistRepository.importPlaylist()   [coroutine, progress flow]
      → OkHttp fetch (custom UA, timeouts, redirects, gzip)
      → M3uParser (tags: #EXTM3U, #EXTINF, tvg-id/tvg-logo/group-title,
                    catchup="…" catchup-source="…" catchup-days="…", kodi props)
        or XtreamClient (GET /player_api.php?username=..&password=..&action=…)
      → Room: upsert playlist, groups, channels (batch, transaction)
      → optional EPG URL fetch → XmltvParser → EpgRepository upsert (paged)
  → UI shows progress; failure keeps previous data (import is transactional)
```

### 5.2 Live playback
```
Channel tapped
  → PlayerActivity binds PlayerSession (Media3 ExoPlayer)
      → setMediaItem(streamUrl, headers {UA, referer} + catch-up params if past program)
      → audio focus (duck/stop per settings), buffering policy per playlist
      → on error: retry ladder (3×) → error card with "Retry" / "Open in external player"
  → Zap bar: prev/next channel (pre-warm next stream), number pad, history, sleep timer
  → Background: PlayerService foreground notification (media controls) while playing
  → AFR: if enabled + device supports Display.Mode (API 23+) and stream fps known → set
        preferredDisplayModeId; fallback silently
```

### 5.3 EPG pipeline
```
XmltvParser → EpgPrograms (batch 500/transaction, index for upsert)
EpgRefreshWorker: periodic (per-playlist interval) + manual refresh
EpgTimeline composable: channels on Y, time on X, now-line; virtualized (lazy rows/cols)
  → unit-tested time-grid math (startSlot, slotWidth, now-line, DST-safe epoch math)
Program details → "Remind me" → ReminderScheduler (AlarmManager) → notification at start
```

### 5.4 Recording
```
Schedule (EPG long-press / program details / one-touch zap)
  → RecordingScheduler: AlarmManager.setExactAndAllowWhileIdle(start/end, API 23+;
    API 31+: SCHEDULE_EXACT_ALARM permission, fallback inexact)
  → RecordingService (foreground, wake lock, notification):
      ExoPlayer → Media3 MediaMuxerSink → .ts/.mp4 in app-specific external storage
      (no storage permission needed; API 23 safe)
  → Recordings tab: play, rename, delete, export/share via SAF (Storage Access Framework)
  → Wake-up/warm-up: pre-roll start 30 s before program start
```

### 5.5 Multi-view
```
MultiViewActivity hosts 1–4 PlayerSessions, each on its own SurfaceView tile
  → Cap by capability probe: RAM < 2 GB → max 2; else 4 (MediaCodecList check)
  → Exactly one tile "has audio" (audio focus held by its session; others muted);
    tap tile → swap audio (< 300 ms); long-press → full-screen promote
  → Same retry/error policy per tile; PiP not offered inside multi-view
```

### 5.6 Catch-up & timeshift
- Catch-up: channel has `catchup` flags (Xtream `catchup=1` / M3U `catchup-source`) →
  EPG past programs show play button → `CatchUpUrlBuilder` computes provider URL
  (Xtream: `&start=…&duration=…&catchup=…`; M3U: `{start}/{end}` template substitution).
- Timeshift: pause/resume live HLS where provider allows (buffer-based); otherwise
  surfaced as "not supported by provider". Local test server validates both.

### 5.7 Parental PIN
```
PIN set → PBKDF2 hash → encrypted with Keystore AES-GCM key → SecurePrefs
Locked areas: Live TV, Movies, Series, Settings (configurable). Grace period setting.
Wrong PIN: rate-limit (5 tries → 30 s lockout). PIN reset only via backup restore.
```

### 5.8 Backup & restore
```
Backup: SAF "create document" → single JSON bundle
  {playlists (creds incl.), settings, favorites, sort orders, history, recordings metadata}
  EPG cache optional (size).
Restore: SAF pick → validate (schema version) → transactional import → app restart prompt.
```

## 6. API-level gates (minSdk 23 compatibility)

| Feature | Gate | Behavior below gate |
|---|---|---|
| Picture-in-Picture | API 26+ | Hidden (not offered) |
| Notification channels | API 26+ | Legacy notifications |
| `POST_NOTIFICATIONS` runtime permission | API 33+ | Requested at first reminder/recording use |
| Exact alarms | `SCHEDULE_EXACT_ALARM` API 31+ | Permission screen + inexact fallback |
| Display.Mode / AFR | API 23+ | Available; silent fallback if provider reports no fps |
| Foreground services | all (23+) | Notification required (no channel below 26) |
| SAF export/import | API 19+ | ✔ everywhere |
| Multi-view | all | Capped by RAM/codecs (see 5.5) |
| MediaCodec list probing | API 21+ | ✔ everywhere |

## 7. Security & privacy

- Playlist credentials stored **only on-device** (Room, app-private); encrypted at rest via
  SQLCipher only if M8 shows need (default: app sandbox + no telemetry).
- No analytics, no ads SDKs, no network calls to anything but the user's configured providers.
- Optional crash log file (local) + manual "Send log" export — opt-in, no auto-upload.
- Parental PIN: PBKDF2 + Keystore AES-GCM (see 5.7).

## 8. Error handling & observability

- Sealed `Result`-style errors in repositories; UI surfaces one actionable message per error
  (retry / check URL / provider unsupported).
- Player error ladder: retry → switch track → "Open in external player" (ACTION_VIEW intent).
- Timber logs to logcat; last 2k lines written to crash log file on uncaught exception
  (custom `Thread.setDefaultUncaughtExceptionHandler`).

## 9. Testing architecture

- **Unit (JVM):** parsers (fixtures + fuzz/malformed), time-grid math, URL builders,
  backup serialization, PIN crypto, scheduler logic (fake AlarmManager).
- **Instrumented (API 23 + API 35 emulators):** Room DAOs, PlayerSession with local test
  streams (HLS/TS/MP4 served from emulator-local HTTP server), recording end-to-end,
  reminder notification firing, multi-view lifecycle.
- **UI (API 35 fast + API 23 spot):** Compose tests per screen; navigation smoke.
- **Performance:** Macrobenchmark startup/zap; `adb shell dumpsys meminfo` RSS checks.

## 10. Open architecture questions (to resolve in M0/M1)

1. SQLCipher for credentials at rest — default **no** (app sandbox), revisit in M8.
2. External player integration — include in v1 (low cost, high user value). ✅ decided: yes.
3. Series Xtream API v2 vs v1 fallback — support v2, fall back to v1 on 404.
4. Split `:app` into feature modules — deferred (ADR-003).
