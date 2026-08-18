package com.tvproxy.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tvproxy.app.core.model.PlaylistType
import com.tvproxy.app.core.model.RecordingState
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import com.tvproxy.app.data.db.entity.RecordingEntity
import com.tvproxy.app.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** RecordingDao + ReminderDao tests (JVM via Robolectric + in-memory Room). */
@RunWith(RobolectricTestRunner::class)
class RecordingReminderDaoTest {

    private lateinit var db: TvProxyDatabase
    private var channelId: Long = 0

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TvProxyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val playlistId = db.playlistDao().upsert(PlaylistEntity(name = "P", type = PlaylistType.M3U, url = "http://x/l.m3u"))
        channelId = db.channelDao().upsertAll(
            listOf(ChannelEntity(playlistId = playlistId, name = "Ch", streamUrl = "http://x/ch.m3u8")),
        ).single()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordings_dueWindow_findsScheduledOnly() = runTest {
        db.recordingDao().upsert(recording(programTitle = "In window", start = WINDOW_START + 1000))
        db.recordingDao().upsert(recording(programTitle = "Outside", start = WINDOW_END + 60_000))
        val doneId = db.recordingDao().upsert(recording(programTitle = "Done", start = WINDOW_START + 2000))
        db.recordingDao().updateState(doneId, RecordingState.DONE)

        val due = db.recordingDao().dueBetween(WINDOW_START, WINDOW_END)

        assertThat(due.map { it.programTitle }).containsExactly("In window")
    }

    @Test
    fun recordings_stateAndFileResultTransitions() = runTest {
        val id = db.recordingDao().upsert(recording(programTitle = "Show", start = WINDOW_START + 1000))

        db.recordingDao().updateState(id, RecordingState.RECORDING)
        assertThat(db.recordingDao().getById(id)?.state).isEqualTo(RecordingState.RECORDING)

        db.recordingDao().updateFileResult(id, RecordingState.DONE, "/data/rec/show.ts", 12_345L)
        val finished = db.recordingDao().getById(id)
        assertThat(finished?.state).isEqualTo(RecordingState.DONE)
        assertThat(finished?.filePath).isEqualTo("/data/rec/show.ts")
        assertThat(finished?.sizeBytes).isEqualTo(12_345L)
        assertThat(db.recordingDao().observeAll().first()).hasSize(1)
    }

    @Test
    fun recordings_surviveChannelDeletion_setNull() = runTest {
        val id = db.recordingDao().upsert(recording(programTitle = "Keep me", start = WINDOW_START + 1000))

        // Deleting the playlist cascades to the channel; the recording keeps its file data.
        val playlist = db.playlistDao().observeAll().first().single()
        db.playlistDao().delete(playlist)

        val recording = db.recordingDao().getById(id)
        assertThat(recording).isNotNull()
        assertThat(recording?.channelId).isNull()
    }

    @Test
    fun reminders_dueWindow_andNotifiedExclusion() = runTest {
        val id = db.reminderDao().upsert(
            ReminderEntity(channelId = channelId, programTitle = "Soon", startEpochMs = WINDOW_START + 1000),
        )
        db.reminderDao().upsert(
            ReminderEntity(channelId = channelId, programTitle = "Far", startEpochMs = WINDOW_END + 60_000),
        )

        assertThat(db.reminderDao().dueBetween(WINDOW_START, WINDOW_END).map { it.programTitle })
            .containsExactly("Soon")

        db.reminderDao().markNotified(id)
        assertThat(db.reminderDao().dueBetween(WINDOW_START, WINDOW_END)).isEmpty()
    }

    private fun recording(programTitle: String, start: Long): RecordingEntity = RecordingEntity(
        channelId = channelId,
        programTitle = programTitle,
        startEpochMs = start,
        endEpochMs = start + 3_600_000L,
    )

    private companion object {
        const val WINDOW_START = 1_800_000_000_000L
        const val WINDOW_END = WINDOW_START + 60_000L
    }
}
