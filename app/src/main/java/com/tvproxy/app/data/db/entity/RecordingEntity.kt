package com.tvproxy.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tvproxy.app.core.model.RecordingState

/**
 * Scheduled/finished recording (architecture.md §4, §5.4). The channel FK uses
 * SET_NULL so recordings survive a deleted playlist (the file is still playable).
 */
@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("channelId"), Index("startEpochMs")],
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long? = null,
    val programTitle: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val state: RecordingState = RecordingState.SCHEDULED,
    val filePath: String? = null,
    val sizeBytes: Long? = null,
)

/** EPG reminder (architecture.md §4): notification at programme start (M4/M6). */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("channelId"), Index("startEpochMs")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val programTitle: String,
    val startEpochMs: Long,
    val notified: Boolean = false,
)
