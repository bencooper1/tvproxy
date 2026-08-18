package com.tvproxy.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tvproxy.app.data.db.dao.ChannelDao
import com.tvproxy.app.data.db.dao.EpgDao
import com.tvproxy.app.data.db.dao.GroupDao
import com.tvproxy.app.data.db.dao.PlaylistDao
import com.tvproxy.app.data.db.dao.RecordingDao
import com.tvproxy.app.data.db.dao.ReminderDao
import com.tvproxy.app.data.db.dao.VodDao
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.ChannelGroupEntity
import com.tvproxy.app.data.db.entity.EpgProgramEntity
import com.tvproxy.app.data.db.entity.EpisodeEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import com.tvproxy.app.data.db.entity.RecordingEntity
import com.tvproxy.app.data.db.entity.ReminderEntity
import com.tvproxy.app.data.db.entity.SeriesItemEntity
import com.tvproxy.app.data.db.entity.VodItemEntity

/**
 * Room database v1 (M1). Schema history starts here; every later bump requires a
 * migration plus a `MigrationTest` (none needed at v1 — fresh installs only).
 */
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelGroupEntity::class,
        ChannelEntity::class,
        EpgProgramEntity::class,
        VodItemEntity::class,
        SeriesItemEntity::class,
        EpisodeEntity::class,
        RecordingEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TvProxyDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun groupDao(): GroupDao
    abstract fun channelDao(): ChannelDao
    abstract fun channelStateDao(): ChannelStateDao
    abstract fun epgDao(): EpgDao
    abstract fun vodDao(): VodDao
    abstract fun recordingDao(): RecordingDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "tvproxy.db"
    }
}
