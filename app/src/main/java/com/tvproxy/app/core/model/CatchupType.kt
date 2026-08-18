package com.tvproxy.app.core.model

/**
 * Catch-up archive flavor declared by a channel (M3U `catchup="…"` attribute or the
 * Xtream `tv_archive` flag). URL building lives in `core/player` (M2, ADR-013).
 */
enum class CatchupType {
    NONE,

    /** Xtream Codes catch-up (provider serves `timeshift`/`catchup` API endpoints). */
    XTREAM,

    /** M3U `catchup="default"` — `catchup-source` template with `{start}`/`{end}` vars. */
    DEFAULT,

    /** M3U `catchup="append"` — archive URL appended to the stream URL. */
    APPEND,

    /** M3U `catchup="shift"` — `?utc=<start>&lutc=<now>` style query params. */
    SHIFT,

    /** M3U `catchup="flussonic"` — Flussonic DVR path template. */
    FLUSSONIC,
}
