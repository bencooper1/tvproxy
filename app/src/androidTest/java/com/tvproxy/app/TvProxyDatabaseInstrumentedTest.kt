package com.tvproxy.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tvproxy.app.core.model.PlaylistType
import com.tvproxy.app.core.model.RecordingState
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.EpgProgramEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import com.tvproxy.app.data.db.entity.RecordingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room smoke test (plan.md M1 gate: "verified via instrumented repo
 * tests on API 23"): builds the real database file on the emulator — API 23 and API
 * 35 in CI — and round-trips enum-converted entities plus the EPG tvg-id join.
 */
@RunWith(AndroidJUnit4::class)
class TvProxyDatabaseInstrumentedTest {

    private lateinit var db: TvProxyDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, TvProxyDatabase::class.java, "instrumented-test.db")
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("instrumented-test.db")
    }

    @Test
    fun database_roundTripsEntitiesWithConverters() = runBlocking {
        val playlistId = db.playlistDao().upsert(
            PlaylistEntity(name = "Portal", type = PlaylistType.XTREAM, url = "http://portal.example"),
        )
        val channelId = db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    playlistId = playlistId,
                    name = "BBC One HD",
                    streamUrl = "http://portal.example/live/u/p/11.m3u8",
                    tvgId = "bbc1.uk",
                    catchupType = com.tvproxy.app.core.model.CatchupType.XTREAM,
                ),
            ),
        ).single()
        db.epgDao().upsertAll(
            listOf(
                EpgProgramEntity(
                    channelEpgId = "bbc1.uk",
                    startEpochMs = 1_704_132_000_000L,
                    endEpochMs = 1_704_135_600_000L,
                    title = "Evening News",
                ),
            ),
        )
        val recordingId = db.recordingDao().upsert(
            RecordingEntity(
                channelId = channelId,
                programTitle = "Evening News",
                startEpochMs = 1_704_132_000_000L,
                endEpochMs = 1_704_135_600_000L,
                state = RecordingState.SCHEDULED,
            ),
        )

        val channel = db.channelDao().channelSlice(playlistId, null, 10, 0).single()
        assertThat(channel.catchupType).isEqualTo(com.tvproxy.app.core.model.CatchupType.XTREAM)
        assertThat(channel.tvgId).isEqualTo("bbc1.uk")

        val programmes = db.epgDao().programsForChannelId(channelId, 0, Long.MAX_VALUE)
        assertThat(programmes.map { it.title }).containsExactly("Evening News")

        assertThat(db.recordingDao().getById(recordingId)?.state).isEqualTo(RecordingState.SCHEDULED)
        val playlist = db.playlistDao().getById(playlistId)
        assertThat(playlist?.type).isEqualTo(PlaylistType.XTREAM)
    }
}
