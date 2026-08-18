package com.tvproxy.app.data.db

import com.tvproxy.app.data.db.entity.ChannelEntity

/**
 * Merge policy for re-imports: fresh provider rows keyed by streamUrl inherit the
 * user's state (favorites, hidden flag, custom sort, watch history, existing PK)
 * from the row they replace. Shared by PlaylistRepository (M3U) and
 * XtreamRepository (live streams) — duplicate-safe upsert requirement, plan.md M1.
 */
object ChannelStateMerger {

    fun merge(fresh: List<ChannelEntity>, existing: List<ChannelStateRow>): List<ChannelEntity> {
        val byUrl = existing.associateBy { it.streamUrl }
        return fresh.map { channel ->
            val prior = byUrl[channel.streamUrl]
            if (prior == null) {
                channel
            } else {
                channel.copy(
                    id = prior.id,
                    isFavorite = prior.isFavorite,
                    isHidden = prior.isHidden,
                    sortOrder = prior.sortOrder,
                    lastWatchedAtEpochMs = prior.lastWatchedAtEpochMs,
                )
            }
        }
    }
}
