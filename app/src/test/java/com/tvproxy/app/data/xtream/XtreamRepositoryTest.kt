package com.tvproxy.app.data.xtream

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tvproxy.app.core.model.CatchupType
import com.tvproxy.app.core.model.ImportProgress
import com.tvproxy.app.core.model.PlaylistType
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * XtreamRepository unit tests (JVM): Room in-memory + MockWebServer portal fixtures
 * (plan.md M1 gate: "Xtream live+vods+series sync" with duplicate-safe upserts).
 */
@RunWith(RobolectricTestRunner::class)
class XtreamRepositoryTest {

    private lateinit var db: TvProxyDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: XtreamRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TvProxyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        val okHttp = OkHttpClient()
        repository = XtreamRepository(
            db = db,
            client = XtreamClient(okHttp, Moshi.Builder().build()),
            playlistDao = db.playlistDao(),
            groupDao = db.groupDao(),
            channelDao = db.channelDao(),
            channelStateDao = db.channelStateDao(),
            vodDao = db.vodDao(),
            io = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun sync_fullCatalog_importsLiveVodSeries() = runTest {
        enqueueCatalogFixture()
        val playlist = insertXtreamPlaylist()

        val events = repository.syncPlaylist(playlist).toList()

        val done = events.filterIsInstance<ImportProgress.Done>().single()
        assertThat(done.channelCount).isEqualTo(1)
        assertThat(done.groupCount).isEqualTo(1)
        assertThat(done.vodCount).isEqualTo(1)
        assertThat(done.seriesCount).isEqualTo(1)

        val channel = db.channelDao().channelSlice(playlist.id, null, 10, 0).single()
        // Output format picked from allowed_output_formats (m3u8 preferred).
        val base = server.url("/").toString().removeSuffix("/")
        assertThat(channel.streamUrl).isEqualTo("$base/live/user1/pass1/11.m3u8")
        assertThat(channel.xtreamStreamId).isEqualTo(11)
        assertThat(channel.catchupType).isEqualTo(CatchupType.XTREAM)
        assertThat(channel.catchupDays).isEqualTo(7)
        assertThat(channel.tvgId).isEqualTo("bbc1.uk")
        assertThat(channel.groupId).isEqualTo(db.groupDao().findByName(playlist.id, "News")!!.id)

        assertThat(db.vodDao().vodCount(playlist.id)).isEqualTo(1)
        assertThat(db.vodDao().seriesCount(playlist.id)).isEqualTo(1)
    }

    @Test
    fun sync_reSync_isDuplicateSafe() = runTest {
        enqueueCatalogFixture()
        val playlist = insertXtreamPlaylist()
        repository.syncPlaylist(playlist).toList()
        db.channelDao().setFavorite(db.channelDao().channelSlice(playlist.id, null, 10, 0).single().id, true)

        enqueueCatalogFixture()
        repository.syncPlaylist(playlist).toList()

        val channels = db.channelDao().channelSlice(playlist.id, null, 10, 0)
        assertThat(channels).hasSize(1)
        assertThat(channels.single().isFavorite).isTrue()
        assertThat(db.vodDao().vodCount(playlist.id)).isEqualTo(1)
    }

    @Test
    fun sync_rejectedCredentials_failsWithoutTouchingDb() = runTest {
        server.enqueueJson("""{"user_info":{"auth":0,"status":"Disabled"}}""")
        val playlist = insertXtreamPlaylist()

        val events = repository.syncPlaylist(playlist).toList()

        assertThat(events.last()).isInstanceOf(ImportProgress.Failed::class.java)
        assertThat((events.last() as ImportProgress.Failed).reason).contains("rejected")
        assertThat(db.channelDao().countForPlaylist(playlist.id)).isEqualTo(0)
        assertThat(db.vodDao().vodCount(playlist.id)).isEqualTo(0)
    }

    @Test
    fun sync_seriesInfoEndpointError_reportsFailed() = runTest {
        server.enqueueJson("""{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts"]}}""")
        server.enqueueJson("[]") // live categories
        server.enqueueJson("[]") // live streams
        server.enqueueJson(code = 500, body = "boom") // vod streams explodes
        val playlist = insertXtreamPlaylist()

        val events = repository.syncPlaylist(playlist).toList()

        assertThat(events.last()).isInstanceOf(ImportProgress.Failed::class.java)
        assertThat((events.last() as ImportProgress.Failed).reason).contains("500")
    }

    @Test
    fun syncSeriesInfo_persistsEpisodesOrdered() = runTest {
        val playlist = insertXtreamPlaylist()
        server.enqueueJson(
            """
            {"seasons":[{"season_number":1,"name":"One"}],
             "episodes":{"1":[{"id":"901","episode_num":2,"title":"Second","container_extension":"mkv",
                                 "info":{"plot":"p","movie_image":"http://img/e2.jpg","duration_secs":"1800"}},
                                {"id":"900","episode_num":1,"title":"First","container_extension":"mp4",
                                 "info":{"plot":"p1","duration_secs":1700}}]},
             "info":{"name":"Show"}}
            """.trimIndent(),
        )

        val count = repository.syncSeriesInfo(playlist, seriesXtreamId = 77)

        assertThat(count).isEqualTo(2)
        val episodes = db.vodDao().episodesForSeries(playlist.id, 77)
        assertThat(episodes.map { it.episodeNo }).containsExactly(1, 2).inOrder()
        assertThat(episodes.map { it.title }).containsExactly("First", "Second").inOrder()
        assertThat(episodes[0].containerExtension).isEqualTo("mp4")
        assertThat(episodes[1].durationSec).isEqualTo(1800)
    }

    private suspend fun insertXtreamPlaylist(): PlaylistEntity {
        val playlist = PlaylistEntity(
            name = "Portal",
            type = PlaylistType.XTREAM,
            url = server.url("/").toString().removeSuffix("/"),
            username = "user1",
            password = "pass1",
        )
        return playlist.copy(id = db.playlistDao().upsert(playlist))
    }

    private fun enqueueCatalogFixture() {
        server.enqueueJson(
            """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["m3u8","ts"]}}""",
        )
        server.enqueueJson("""[{"category_id":"5","category_name":"News","parent_id":0}]""")
        server.enqueueJson(
            """[{"num":1,"name":"BBC One HD","stream_id":11,"stream_icon":"",
                "epg_channel_id":"bbc1.uk","category_id":"5","tv_archive":1,"tv_archive_duration":7}]""",
        )
        server.enqueueJson(
            """[{"stream_id":"501","name":"Big Buck Bunny","stream_icon":"http://img/bbb.jpg",
                "container_extension":"mp4","rating_5based":4.2,"category_id":"8","added":"1690000000"}]""",
        )
        server.enqueueJson(
            """[{"series_id":77,"name":"Test Show","cover":"http://img/show.jpg","plot":"A show.",
                "rating":"7.5","category_id":"9","last_modified":"1690000000"}]""",
        )
    }

    private fun MockWebServer.enqueueJson(body: String, code: Int = 200) {
        enqueue(MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"))
    }
}
