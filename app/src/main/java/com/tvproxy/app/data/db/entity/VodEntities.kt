package com.tvproxy.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Movie catalog entry (architecture.md §4 "VodItem"). Xtream `stream_id` is the
 * duplicate-safe upsert key together with the owning playlist. The playable URL is
 * derived at playback time: `{base}/movie/{user}/{pass}/{xtreamId}.{containerExtension}`.
 */
@Entity(
    tableName = "vod_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "xtreamId"], unique = true),
    ],
)
data class VodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val xtreamId: Long,
    val name: String,
    val posterUrl: String? = null,
    val rating: Double? = null,
    val categoryId: Long? = null,
    val containerExtension: String = DEFAULT_CONTAINER,
    val addedEpochMs: Long? = null,
) {
    companion object {
        const val DEFAULT_CONTAINER = "mp4"
    }
}

/** Series catalog entry; episodes are imported lazily per series (`syncSeriesInfo`). */
@Entity(
    tableName = "series_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "xtreamId"], unique = true),
    ],
)
data class SeriesItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val xtreamId: Long,
    val name: String,
    val posterUrl: String? = null,
    val plot: String? = null,
    val rating: Double? = null,
    val categoryId: Long? = null,
    val lastModifiedEpochMs: Long? = null,
)

/** One episode of a series (Xtream `get_series_info` response). */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId", "seriesXtreamId", "seasonNo", "episodeNo"]),
        Index(value = ["playlistId", "episodeXtreamId"], unique = true),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val seriesXtreamId: Long,
    val episodeXtreamId: Long,
    val seasonNo: Int,
    val episodeNo: Int,
    val title: String,
    val plot: String? = null,
    val stillUrl: String? = null,
    val durationSec: Int? = null,
    val containerExtension: String = VodItemEntity.DEFAULT_CONTAINER,
)
