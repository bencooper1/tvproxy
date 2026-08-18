package com.tvproxy.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tvproxy.app.data.db.entity.EpisodeEntity
import com.tvproxy.app.data.db.entity.SeriesItemEntity
import com.tvproxy.app.data.db.entity.VodItemEntity

@Dao
interface VodDao {

    @Upsert
    suspend fun upsertVod(items: List<VodItemEntity>): List<Long>

    /** Offset paging window; the movies grid (M5) pages through this. */
    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId ORDER BY name LIMIT :limit OFFSET :offset")
    suspend fun vodSlice(playlistId: Long, limit: Int, offset: Int): List<VodItemEntity>

    @Query("SELECT COUNT(*) FROM vod_items WHERE playlistId = :playlistId")
    suspend fun vodCount(playlistId: Long): Int

    @Query(
        "SELECT * FROM vod_items WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit",
    )
    suspend fun searchVod(playlistId: Long, query: String, limit: Int): List<VodItemEntity>

    @Upsert
    suspend fun upsertSeries(items: List<SeriesItemEntity>): List<Long>

    @Query("SELECT * FROM series_items WHERE playlistId = :playlistId ORDER BY name LIMIT :limit OFFSET :offset")
    suspend fun seriesSlice(playlistId: Long, limit: Int, offset: Int): List<SeriesItemEntity>

    @Query("SELECT COUNT(*) FROM series_items WHERE playlistId = :playlistId")
    suspend fun seriesCount(playlistId: Long): Int

    @Query(
        "SELECT * FROM series_items WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit",
    )
    suspend fun searchSeries(playlistId: Long, query: String, limit: Int): List<SeriesItemEntity>

    @Upsert
    suspend fun upsertEpisodes(items: List<EpisodeEntity>): List<Long>

    @Query(
        """SELECT * FROM episodes
           WHERE playlistId = :playlistId AND seriesXtreamId = :seriesXtreamId
           ORDER BY seasonNo, episodeNo""",
    )
    suspend fun episodesForSeries(playlistId: Long, seriesXtreamId: Long): List<EpisodeEntity>

    /** Replaced before a series-info re-sync so renumbered episodes do not linger. */
    @Query("DELETE FROM episodes WHERE playlistId = :playlistId AND seriesXtreamId = :seriesXtreamId")
    suspend fun deleteEpisodesForSeries(playlistId: Long, seriesXtreamId: Long): Int
}
