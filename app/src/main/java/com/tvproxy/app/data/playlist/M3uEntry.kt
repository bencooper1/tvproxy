package com.tvproxy.app.data.playlist

import com.tvproxy.app.core.model.CatchupType

/**
 * One parsed M3U entry (feed line → entry mapping per architecture.md §5.1).
 * Pure value type — no Android dependencies so the parser unit-tests on the JVM.
 */
data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val tvgChno: Int? = null,
    val groupTitle: String? = null,
    val catchupType: CatchupType = CatchupType.NONE,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    /** EXTINF duration in seconds; -1 = live stream (no duration advertised). */
    val durationSec: Int? = null,
)
