package com.tvproxy.app.data.epg

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tvproxy.app.core.model.ImportProgress
import com.tvproxy.app.core.model.PlaylistType
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
import org.xmlpull.v1.XmlPullParserFactory

/**
 * EpgRepository unit tests (JVM): Room in-memory + MockWebServer XMLTV feed fixtures.
 * XMLTV timestamps are generated relative to "now" so the post-import prune does not
 * erase the fixtures during the test.
 */
@RunWith(RobolectricTestRunner::class)
class EpgRepositoryTest {

    private lateinit var db: TvProxyDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: EpgRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TvProxyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        repository = EpgRepository(
            db = db,
            epgDao = db.epgDao(),
            xmltvParser = XmltvParser(),
            pullParserProvider = PullParserProvider { XmlPullParserFactory.newInstance().newPullParser() },
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
    fun import_upsertsPrograms_andReimportDeduplicates() = runTest {
        server.enqueueXml(demoFeed())
        val playlist = insertPlaylistWithEpg()

        val firstRun = repository.importEpg(playlist).toList()
        val done = firstRun.filterIsInstance<ImportProgress.Done>().single()
        assertThat(done.programCount).isEqualTo(2)
        assertThat(db.epgDao().countForChannelEpgId("bbc1.uk")).isEqualTo(2)

        server.enqueueXml(demoFeed())
        val secondRun = repository.importEpg(playlist).toList()

        assertThat(secondRun.filterIsInstance<ImportProgress.Done>().single().programCount).isEqualTo(2)
        assertThat(db.epgDao().countForChannelEpgId("bbc1.uk")).isEqualTo(2)
    }

    @Test
    fun import_joinsToChannelByTvgId() = runTest {
        val playlist = insertPlaylistWithEpg()
        val channelId = db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    playlistId = playlist.id,
                    name = "BBC One HD",
                    streamUrl = "http://provider.example/bbc1.m3u8",
                    tvgId = "bbc1.uk",
                ),
            ),
        ).single()
        server.enqueueXml(demoFeed())

        repository.importEpg(playlist).toList()

        val programmes = db.epgDao().programsForChannelId(channelId, 0, Long.MAX_VALUE)
        assertThat(programmes).hasSize(2)
        assertThat(programmes.map { it.title }).containsExactly("Morning", "Evening").inOrder()
    }

    @Test
    fun import_prunesExpiredPrograms() = runTest {
        server.enqueueXml(demoFeed(startOffsetMs = -TEN_DAYS_MS, endOffsetMs = -TEN_DAYS_MS + HOUR_MS))
        val playlist = insertPlaylistWithEpg()

        repository.importEpg(playlist).toList()

        // Both programmes ended more than the 7-day retention window ago → pruned.
        assertThat(db.epgDao().countForChannelEpgId("bbc1.uk")).isEqualTo(0)
    }

    @Test
    fun import_malformedFeed_keepsPreviousData() = runTest {
        server.enqueueXml(demoFeed())
        val playlist = insertPlaylistWithEpg()
        repository.importEpg(playlist).toList()

        server.enqueueXml("""<tv><programme start="x" stop="y" channel="bbc1.uk"><title>broken</title>""")
        val events = repository.importEpg(playlist).toList()

        assertThat(events.last()).isInstanceOf(ImportProgress.Failed::class.java)
        assertThat(db.epgDao().countForChannelEpgId("bbc1.uk")).isEqualTo(2)
    }

    private suspend fun insertPlaylistWithEpg(): PlaylistEntity {
        val playlist = PlaylistEntity(
            name = "Test",
            type = PlaylistType.M3U,
            url = server.url("/list.m3u").toString(),
            epgUrl = server.url("/guide.xml").toString(),
        )
        return playlist.copy(id = db.playlistDao().upsert(playlist))
    }

    private fun MockWebServer.enqueueXml(body: String) {
        enqueue(MockResponse().setBody(body).addHeader("Content-Type", "application/xml"))
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val TEN_DAYS_MS = 10L * 24 * HOUR_MS

        /** Two programmes for channel `bbc1.uk`, spaced around offsets from "now". */
        fun demoFeed(startOffsetMs: Long = -HOUR_MS, endOffsetMs: Long = 3 * HOUR_MS): String {
            val now = System.currentTimeMillis()
            val p1Start = now + startOffsetMs
            val p1End = now + HOUR_MS + startOffsetMs
            val p2Start = now + 2 * HOUR_MS + startOffsetMs
            val p2End = now + endOffsetMs + 2 * HOUR_MS
            return """
                <?xml version="1.0"?>
                <tv>
                  <channel id="bbc1.uk"><display-name>BBC One</display-name></channel>
                  <programme start="${stamp(p1Start)}" stop="${stamp(p1End)}" channel="bbc1.uk">
                    <title>Morning</title>
                  </programme>
                  <programme start="${stamp(p2Start)}" stop="${stamp(p2End)}" channel="bbc1.uk">
                    <title>Evening</title>
                  </programme>
                </tv>
            """.trimIndent()
        }

        fun stamp(epochMs: Long): String {
            val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(Date(epochMs))
        }
    }
}
