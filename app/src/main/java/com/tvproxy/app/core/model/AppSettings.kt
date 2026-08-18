package com.tvproxy.app.core.model

/** Overall app theme. BLACK = true-black dark variant for OLED panels. */
enum class AppTheme { SYSTEM, LIGHT, DARK, BLACK }

/**
 * User-facing settings snapshot backed by DataStore Preferences
 * (architecture.md §4 "AppSettings"). All fields have safe defaults.
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    /** Custom User-Agent for playlist/EPG fetches; null = app default. */
    val userAgent: String? = null,
    /** Player buffer hint (ms); applied to live streams in M2. */
    val bufferMs: Int = DEFAULT_BUFFER_MS,
    /** Auto frame-rate switching (API 23+ Display.Mode); silent fallback when unsupported. */
    val autoFrameRate: Boolean = false,
    /** Resume the last channel on cold start. */
    val startOnLastChannel: Boolean = false,
    val lastPlayedChannelId: Long? = null,
    /** Periodic EPG refresh interval used by WorkManager (M4). */
    val epgUpdateIntervalHours: Int = DEFAULT_EPG_INTERVAL_H,
) {
    companion object {
        const val DEFAULT_BUFFER_MS = 15_000
        const val DEFAULT_EPG_INTERVAL_H = 12
    }
}
