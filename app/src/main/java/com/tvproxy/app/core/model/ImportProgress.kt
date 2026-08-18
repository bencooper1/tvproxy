package com.tvproxy.app.core.model

/**
 * Progress states emitted by playlist/EPG import pipelines
 * (architecture.md §5.1). Imports never throw to the caller; failures are
 * surfaced as [Failed] and previous data is kept (transactional upsert).
 */
sealed interface ImportProgress {
    data object Idle : ImportProgress

    /** Downloading the playlist / EPG payload. */
    data object Fetching : ImportProgress

    /** Parsing the payload into typed entries. */
    data object Parsing : ImportProgress

    /** Writing rows to Room in batches. [savedSoFar] = cumulative row count. */
    data class Saving(val savedSoFar: Int) : ImportProgress

    data class Done(
        val playlistId: Long,
        val channelCount: Int,
        val groupCount: Int,
        val vodCount: Int = 0,
        val seriesCount: Int = 0,
        val programCount: Int = 0,
    ) : ImportProgress

    data class Failed(val reason: String) : ImportProgress
}
