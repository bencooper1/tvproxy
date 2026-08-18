package com.tvproxy.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tvproxy.app.core.model.PlaylistType

/**
 * A configured playlist source (architecture.md §4). Up to 5 enabled playlists is
 * enforced at the manager layer (M7); the schema itself does not cap rows.
 *
 * For XTREAM playlists [url] is the portal base URL (scheme://host[:port]) and
 * [username]/[password] hold the credentials, kept only on-device (ADR-002/§7).
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    /** Per-playlist User-Agent override for fetches and (M2) player requests. */
    val userAgent: String? = null,
    /** Optional XMLTV EPG URL; Xtream playlists get EPG from the portal API (M4). */
    val epgUrl: String? = null,
    val enabled: Boolean = true,
    /** Display order in the playlist manager. Named `position`: `order` is an SQL keyword. */
    val position: Int = 0,
    val lastSyncAtEpochMs: Long? = null,
)

@Entity(
    tableName = "channel_groups",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "name"], unique = true),
    ],
)
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val position: Int = 0,
    val isHidden: Boolean = false,
)
