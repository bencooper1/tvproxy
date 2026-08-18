package com.tvproxy.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tvproxy.app.core.model.CatchupType

/**
 * A live channel (architecture.md §4).
 *
 * Duplicate-safe upsert key: (playlistId, streamUrl) — stable for both M3U
 * (re-import upserts by URL) and Xtream (URL is derived from the stream id).
 * Re-importing therefore preserves favorites/hidden/history on existing rows
 * (the repository merges instead of delete-all).
 */
@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChannelGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("groupId"),
        Index("tvgId"),
        Index(value = ["playlistId", "isFavorite"]),
        Index(value = ["playlistId", "streamUrl"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val groupId: Long? = null,
    /** Provider channel number (M3U `tvg-chno`); UI numbering falls back to sortOrder. */
    val number: Int? = null,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    /** XMLTV channel id used to join EPG programmes (M3U `tvg-id` / Xtream `epg_channel_id`). */
    val tvgId: String? = null,
    val tvgName: String? = null,
    val catchupType: CatchupType = CatchupType.NONE,
    /** M3U `catchup-source` URL template (`{start}`/`{end}` placeholders) — see ADR-013. */
    val catchupSource: String? = null,
    /** Archive depth in days (M3U `catchup-days` / Xtream `tv_archive_duration`). */
    val catchupDays: Int? = null,
    /** Xtream stream id — needed for catch-up archive calls and stream-info APIs. */
    @ColumnInfo(defaultValue = "0") val xtreamStreamId: Long = 0,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    /** In-playlist sort order after the last sync; user re-sorting lands in M3. */
    val sortOrder: Int = 0,
    val lastWatchedAtEpochMs: Long? = null,
)
