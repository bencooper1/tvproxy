package com.tvproxy.app.data.playlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
 * PlaylistRepository integration-style unit tests (JVM): Room in-memory via Robolectric,
 * provider responses from MockWebServer. Covers plan.md M1 acceptance: transactional
 * import, duplicate-safe upserts, and state preservation across re-imports.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {

    private lateinit var db: TvProxyDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TvProxyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        repository = PlaylistRepository(
            db = db,
            playlistDao = db.playlistDao(),
            groupDao = db.groupDao(),
            channelDao = db.channelDao(),
            channelStateDao = db.channelStateDao(),
            m3uParser = M3uParser(),
            okHttpClient = OkHttpClient(),
            io = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun import_createsGroupsAndChannels() = runTest {
        server.enqueue(M3U_FIXTURE_V1.toResponse())
        val playlist = insertPlaylist()

        val events = repository.importM3uPlaylist(playlist).toList()

        val done = events.filterIsInstance<ImportProgress.Done>().single()
        assertThat(done.channelCount).isEqualTo(3)
        assertThat(done.groupCount).isEqualTo(2)
        assertThat(db.channelDao().countForPlaylist(playlist.id)).isEqualTo(3)
        assertThat(db.groupDao().countForPlaylist(playlist.id)).isEqualTo(2)
        // Group resolution: "News" group holds the joined channel.
        val channels = db.channelDao().channelSlice(playlist.id, groupId = null, limit = 10, offset = 0)
        val news = db.groupDao().findByName(playlist.id, "News")!!
        assertThat(channels.single { it.name == "BBC One HD" }.groupId).isEqualTo(news.id)
    }

    @Test
    fun import_emitsProgressSequence() = runTest {
        server.enqueue(M3U_FIXTURE_V1.toResponse())
        val playlist = insertPlaylist()

        repository.importM3uPlaylist(playlist).test {
            assertThat(awaitItem()).isEqualTo(ImportProgress.Fetching)
            assertThat(awaitItem()).isEqualTo(ImportProgress.Parsing)
            var item = awaitItem()
            while (item is ImportProgress.Saving) item = awaitItem()
            assertThat(item).isInstanceOf(ImportProgress.Done::class.java)
            awaitComplete()
        }
    }

    @Test
    fun reimport_preservesFavorites_andRemovesStaleUrls() = runTest {
        server.enqueue(M3U_FIXTURE_V1.toResponse())
        val playlist = insertPlaylist()
        repository.importM3uPlaylist(playlist).toList()

        val before = db.channelDao().channelSlice(playlist.id, null, 10, 0)
        val favorite = before.single { it.name == "Discovery HD" }
        db.channelDao().setFavorite(favorite.id, true)

        server.enqueue(M3U_FIXTURE_V2.toResponse()) // BBC removed, CNN added
        repository.importM3uPlaylist(playlist).toList()

        val after = db.channelDao().channelSlice(playlist.id, null, 10, 0)
        assertThat(after.map { it.name }).containsExactly("Discovery HD", "CNN Intl")
        assertThat(after.single { it.name == "Discovery HD" }.isFavorite).isTrue()
        assertThat(db.channelDao().countForPlaylist(playlist.id)).isEqualTo(2)
    }

    @Test
    fun import_failureKeepsPreviousData_andReportsFailed() = runTest {
        server.enqueue(M3U_FIXTURE_V1.toResponse())
        val playlist = insertPlaylist()
        repository.importM3uPlaylist(playlist).toList()

        server.enqueue(MockResponse().setResponseCode(500).setBody("server exploded"))
        val events = repository.importM3uPlaylist(playlist).toList()

        assertThat(events.last()).isInstanceOf(ImportProgress.Failed::class.java)
        assertThat((events.last() as ImportProgress.Failed).reason).contains("500")
        assertThat(db.channelDao().countForPlaylist(playlist.id)).isEqualTo(3)
    }

    @Test
    fun import_emptyPlaylist_isFailureNotWipe() = runTest {
        server.enqueue(M3U_FIXTURE_V1.toResponse())
        val playlist = insertPlaylist()
        repository.importM3uPlaylist(playlist).toList()

        server.enqueue(MockResponse().setBody("#EXTM3U\n# nothing else\n"))
        val events = repository.importM3uPlaylist(playlist).toList()

        assertThat(events.last()).isInstanceOf(ImportProgress.Failed::class.java)
        assertThat(db.channelDao().countForPlaylist(playlist.id)).isEqualTo(3)
    }

    private suspend fun insertPlaylist(): PlaylistEntity {
        val playlist = PlaylistEntity(
            name = "Test",
            type = PlaylistType.M3U,
            url = server.url("/list.m3u").toString(),
        )
        val id = db.playlistDao().upsert(playlist)
        return playlist.copy(id = id)
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.toList(): List<T> =
        kotlinx.coroutines.flow.toList(this, mutableListOf())

    private fun String.toResponse(): MockResponse =
        MockResponse().setBody(this).addHeader("Content-Type", "audio/x-mpegurl")

    private companion object {
        val M3U_FIXTURE_V1 = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://img/bbc.png" group-title="News",BBC One HD
            http://provider.example/live/bbc1.m3u8
            #EXTINF:-1 tvg-id="disco.us" group-title="Docs",Discovery HD
            http://provider.example/live/disco.m3u8
            #EXTINF:-1 tvg-id="sky.uk" group-title="News",Sky News
            http://provider.example/live/sky.m3u8
        """.trimIndent()

        val M3U_FIXTURE_V2 = """
            #EXTM3U
            #EXTINF:-1 tvg-id="disco.us" group-title="Docs",Discovery HD
            http://provider.example/live/disco.m3u8
            #EXTINF:-1 tvg-id="cnn.us" group-title="News",CNN Intl
            http://provider.example/live/cnn.m3u8
        """.trimIndent()
    }
}
