package com.tvproxy.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tvproxy.app.data.db.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    /** Duplicate-safe upsert keyed on the unique (playlistId, streamUrl) index. */
    @Upsert
    suspend fun upsertAll(channels: List<ChannelEntity>): List<Long>

    /**
     * Channel feed for a playlist, ungrouped-first ordering by name.
     * [groupId] == null means "all groups" (ungrouped channels included).
     */
    @Query(
        """SELECT * FROM channels
           WHERE playlistId = :playlistId AND (:groupId IS NULL OR groupId = :groupId)
           ORDER BY number IS NULL, number, name""",
    )
    fun observeChannels(playlistId: Long, groupId: Long?): Flow<List<ChannelEntity>>

    /** Offset paging window for large lists (Paging-3 adoption lands with the UI in M3). */
    @Query(
        """SELECT * FROM channels
           WHERE playlistId = :playlistId AND (:groupId IS NULL OR groupId = :groupId)
           ORDER BY number IS NULL, number, name
           LIMIT :limit OFFSET :offset""",
    )
    suspend fun channelSlice(playlistId: Long, groupId: Long?, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun observeFavorites(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countForPlaylist(playlistId: Long): Int

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :channelId")
    suspend fun setFavorite(channelId: Long, favorite: Boolean)

    @Query("UPDATE channels SET isHidden = :hidden WHERE id = :channelId")
    suspend fun setHidden(channelId: Long, hidden: Boolean)

    @Query("UPDATE channels SET lastWatchedAtEpochMs = :epochMs WHERE id = :channelId")
    suspend fun touchWatched(channelId: Long, epochMs: Long)

    /**
     * Re-import cleanup: removes rows whose stream URL disappeared from the provider.
     * Callers chunk [keptUrls]; empty lists must be skipped by the repository.
     */
    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND streamUrl NOT IN (:keptUrls)")
    suspend fun deleteMissingByUrl(playlistId: Long, keptUrls: List<String>): Int

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long): Int
}
