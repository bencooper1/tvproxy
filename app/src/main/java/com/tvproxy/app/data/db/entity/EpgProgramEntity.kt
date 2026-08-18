package com.tvproxy.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One EPG programme (architecture.md §4 "EpgProgram").
 *
 * Keyed by [channelEpgId] — the XMLTV channel id — rather than the internal channel
 * FK, so EPG imports are independent of playlist import order. Rows join to
 * channels via `ChannelEntity.tvgId == channelEpgId` (see `EpgDao`).
 *
 * Duplicate-safe upsert key: (channelEpgId, startEpochMs).
 */
@Entity(
    tableName = "epg_programs",
    indices = [
        Index("startEpochMs"),
        Index(value = ["channelEpgId", "startEpochMs"], unique = true),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelEpgId: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val title: String,
    val subTitle: String? = null,
    val description: String? = null,
    val seasonNo: Int? = null,
    val episodeNo: Int? = null,
    val imageUrl: String? = null,
)
