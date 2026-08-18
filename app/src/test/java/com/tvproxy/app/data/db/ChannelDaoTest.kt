package com.tvproxy.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tvproxy.app.core.model.PlaylistType
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ChannelDao tests (JVM via Robolectric + in-memory Room). */
@RunWith(RobolectricTestRunner::class)
class ChannelDaoTest {

    private lateinit var db: TvProxyDatabase
    private var playlistId: Long = 0

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TvProxyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistId = db.playlistDao().upsert(PlaylistEntity(name = "P", type = PlaylistType.M3U, url = "http://x/l.m3u"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun channels_upsertByUrl_isDuplicateSafe() = runTest {
        val channel = channel(name = "One", url = "http://x/1.m3u8")
        db.channelDao().upsertAll(listOf(channel))

        val reimported = channel(name = "One Renamed", url = "http://x/1.m3u8")
        val returned = db.channelDao().upsertAll(listOf(reimported))

        assertThat(db.channelDao().countForPlaylist(playlistId)).isEqualTo(1)
        assertThat(returned.single()).isGreaterThan(0L)
        assertThat(db.channelDao().channelSlice(playlistId, null, 10, 0).single().name).isEqualTo("One Renamed")
    }

    @Test
    fun channelSlice_ordersNumberedFirst_thenAlphabetical() = runTest {
        db.channelDao().upsertAll(
            listOf(
                channel(name = "Zulu", url = "http://x/z.m3u8"),
                channel(name = "Alpha", url = "http://x/a.m3u8"),
                channel(name = "Beta", url = "http://x/b.m3u8", number = 2),
                channel(name = "Gamma", url = "http://x/g.m3u8", number = 1),
            ),
        )

        val all = db.channelDao().channelSlice(playlistId, null, 10, 0)
        assertThat(all.map { it.name }).containsExactly("Gamma", "Beta", "Alpha", "Zulu").inOrder()

        val firstPage = db.channelDao().channelSlice(playlistId, null, 2, 0)
        val secondPage = db.channelDao().channelSlice(playlistId, null, 2, 2)
        assertThat(firstPage.map { it.name }).containsExactly("Gamma", "Beta").inOrder()
        assertThat(secondPage.map { it.name }).containsExactly("Alpha", "Zulu").inOrder()
    }

    @Test
    fun favorites_toggleAndObserve() = runTest {
        val ids = db.channelDao().upsertAll(listOf(channel(name = "One", url = "http://x/1.m3u8")))
        assertThat(db.channelDao().observeFavorites(playlistId).first()).isEmpty()

        db.channelDao().setFavorite(ids.single(), true)
        val favorites = db.channelDao().observeFavorites(playlistId).first()
        assertThat(favorites).hasSize(1)
        assertThat(favorites.single().isFavorite).isTrue()

        db.channelDao().setFavorite(ids.single(), false)
        assertThat(db.channelDao().observeFavorites(playlistId).first()).isEmpty()
    }

    @Test
    fun watchHistory_touchWatchedPersists() = runTest {
        val ids = db.channelDao().upsertAll(listOf(channel(name = "One", url = "http://x/1.m3u8")))
        db.channelDao().touchWatched(ids.single(), 42_000L)

        val row = db.channelDao().channelSlice(playlistId, null, 10, 0).single()
        assertThat(row.lastWatchedAtEpochMs).isEqualTo(42_000L)
    }

    @Test
    fun deleteMissingByUrl_removesOnlyAbsentUrls() = runTest {
        db.channelDao().upsertAll(
            listOf(
                channel(name = "Keep", url = "http://x/keep.m3u8"),
                channel(name = "Drop", url = "http://x/drop.m3u8"),
            ),
        )

        val deleted = db.channelDao().deleteMissingByUrl(playlistId, listOf("http://x/keep.m3u8"))

        assertThat(deleted).isEqualTo(1)
        val remaining = db.channelDao().channelSlice(playlistId, null, 10, 0)
        assertThat(remaining.map { it.name }).containsExactly("Keep")
    }

    private fun channel(name: String, url: String, number: Int? = null): ChannelEntity =
        ChannelEntity(playlistId = playlistId, number = number, name = name, streamUrl = url)
}
