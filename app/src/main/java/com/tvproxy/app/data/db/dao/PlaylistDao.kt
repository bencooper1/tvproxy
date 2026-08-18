package com.tvproxy.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.tvproxy.app.data.db.entity.ChannelGroupEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY position, id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists WHERE enabled = 1")
    suspend fun countEnabled(): Int

    @Query("UPDATE playlists SET lastSyncAtEpochMs = :epochMs WHERE id = :id")
    suspend fun updateLastSync(id: Long, epochMs: Long)
}

@Dao
interface GroupDao {

    @Upsert
    suspend fun upsertAll(groups: List<ChannelGroupEntity>): List<Long>

    @Query("SELECT * FROM channel_groups WHERE playlistId = :playlistId ORDER BY position, name")
    fun observeForPlaylist(playlistId: Long): Flow<List<ChannelGroupEntity>>

    @Query("SELECT * FROM channel_groups WHERE playlistId = :playlistId AND name = :name")
    suspend fun findByName(playlistId: Long, name: String): ChannelGroupEntity?

    @Query("SELECT COUNT(*) FROM channel_groups WHERE playlistId = :playlistId")
    suspend fun countForPlaylist(playlistId: Long): Int

    /** Removes groups absent from a re-import. Callers chunk [names]; empty lists are skipped. */
    @Query("DELETE FROM channel_groups WHERE playlistId = :playlistId AND name NOT IN (:names)")
    suspend fun deleteMissing(playlistId: Long, names: List<String>): Int
}
