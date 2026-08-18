package com.tvproxy.app.core.di

import android.content.Context
import androidx.room.Room
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.dao.ChannelDao
import com.tvproxy.app.data.db.dao.ChannelStateDao
import com.tvproxy.app.data.db.dao.EpgDao
import com.tvproxy.app.data.db.dao.GroupDao
import com.tvproxy.app.data.db.dao.PlaylistDao
import com.tvproxy.app.data.db.dao.RecordingDao
import com.tvproxy.app.data.db.dao.ReminderDao
import com.tvproxy.app.data.db.dao.VodDao
import com.tvproxy.app.data.epg.PullParserProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TvProxyDatabase =
        Room.databaseBuilder(context, TvProxyDatabase::class.java, TvProxyDatabase.NAME).build()

    @Provides
    fun providePlaylistDao(db: TvProxyDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideGroupDao(db: TvProxyDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideChannelDao(db: TvProxyDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideChannelStateDao(db: TvProxyDatabase): ChannelStateDao = db.channelStateDao()

    @Provides
    fun provideEpgDao(db: TvProxyDatabase): EpgDao = db.epgDao()

    @Provides
    fun provideVodDao(db: TvProxyDatabase): VodDao = db.vodDao()

    @Provides
    fun provideRecordingDao(db: TvProxyDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideReminderDao(db: TvProxyDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun providePullParserProvider(): PullParserProvider = PullParserProvider { android.util.Xml.newPullParser() }
}
