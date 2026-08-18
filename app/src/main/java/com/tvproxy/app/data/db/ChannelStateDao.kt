package com.tvproxy.app.data.db

import androidx.room.Dao
import androidx.room.Query

/**
 * Minimal channel state used to preserve user data (favorites/hidden/history/custom
 * sort) across re-imports. Loaded before an upsert and merged back onto fresh rows.
 */
data class ChannelStateRow(
    val id: Long,
    val streamUrl: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val lastWatchedAtEpochMs: Long?,
)

/**
 * Preserved-state query for the merge-on-import flows
 * (PlaylistRepository, XtreamRepository). Kept apart from ChannelDao so the CRUD
 * surface stays small.
 */
@Dao
interface ChannelStateDao {

    @Query(
        """SELECT id, streamUrl, isFavorite, isHidden, sortOrder, lastWatchedAtEpochMs
           FROM channels WHERE playlistId = :playlistId""",
    )
    suspend fun stateRowsForPlaylist(playlistId: Long): List<ChannelStateRow>
}
