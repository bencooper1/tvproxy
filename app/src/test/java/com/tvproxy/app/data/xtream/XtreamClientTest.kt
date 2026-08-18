package com.tvproxy.app.data.xtream

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Xtream client unit tests against MockWebServer fixtures (plan.md M1 gate).
 */
class XtreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: XtreamClient

    private val credentials: XtreamCredentials
        get() = XtreamCredentials(server.url("/").toString().removeSuffix("/"), "user1", "pass1")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = XtreamClient(OkHttpClient(), Moshi.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun accountInfo_success_parsesUserInfo() = runTest {
        server.enqueueJson(
            """
            {"user_info":{"username":"user1","status":"Active","exp_date":"1767225600",
             "is_trial":"0","max_connections":"2","allowed_output_formats":["m3u8","ts"]},
             "server_info":{"url":"provider.example","port":"8080"}}
            """.trimIndent(),
        )

        val response = client.accountInfo(credentials)

        assertThat(response).isInstanceOf(XtreamResponse.Ok::class.java)
        val info = (response as XtreamResponse.Ok).value.userInfo
        assertThat(info?.status).isEqualTo("Active")
        assertThat(info?.allowedOutputFormats).containsExactly("m3u8", "ts")
        assertThat(info?.maxConnections.asLongOrNull()).isEqualTo(2)

        val request = server.takeRequest()
        assertThat(request.path).contains("player_api.php")
        assertThat(request.path).contains("username=user1")
        assertThat(request.path).contains("password=pass1")
    }

    @Test
    fun accountInfo_httpError_mapsToHttpResponse() = runTest {
        server.enqueueJson(code = 403, body = """{"error":"forbidden"}""")

        val response = client.accountInfo(credentials)

        assertThat(response).isInstanceOf(XtreamResponse.Http::class.java)
        assertThat((response as XtreamResponse.Http).code).isEqualTo(403)
    }

    @Test
    fun liveStreams_mixedTypeFields_parsesLooselyTypedValues() = runTest {
        // Real-world fixture shape: stream_id as number, category_id as string, flags as strings.
        server.enqueueJson(
            """
            [{"num":1,"name":"BBC One HD","stream_type":"live","stream_id":11,
              "stream_icon":"http://img/bbc.png","epg_channel_id":"bbc1.uk","added":"1690000000",
              "category_id":"5","custom_sid":"","tv_archive":1,"tv_archive_duration":"7",
              "thumbnail":""},
             {"num":2,"name":"Discovery HD","stream_type":"live","stream_id":"12",
              "stream_icon":"","epg_channel_id":"","added":"","category_id":5,
              "tv_archive":"0","tv_archive_duration":0}]
            """.trimIndent(),
        )

        val response = client.liveStreams(credentials)

        assertThat(response).isInstanceOf(XtreamResponse.Ok::class.java)
        val streams = (response as XtreamResponse.Ok).value
        assertThat(streams).hasSize(2)
        assertThat(streams[0].streamId.asLongOrNull()).isEqualTo(11)
        assertThat(streams[0].tvArchive.asBooleanFlag()).isTrue()
        assertThat(streams[0].tvArchiveDuration.asIntOrNull()).isEqualTo(7)
        assertThat(streams[1].streamId.asLongOrNull()).isEqualTo(12) // string → number coerced
        assertThat(streams[1].tvArchive.asBooleanFlag()).isFalse()
    }

    @Test
    fun liveStreams_serverReturnsObjectInsteadOfList_mapsToMalformed() = runTest {
        // Some panels answer list actions with {} — must not crash the sync pipeline.
        server.enqueueJson("""{"error":"no streams"}""")

        val response = client.liveStreams(credentials)

        assertThat(response).isInstanceOf(XtreamResponse.Malformed::class.java)
    }

    @Test
    fun seriesInfo_v2Returns404_fallsBackToV1() = runTest {
        server.enqueueJson(code = 404, body = "not found")
        server.enqueueJson(
            """
            {"seasons":[{"season_number":1,"name":"Season 1"}],
             "episodes":{"1":[{"id":"9001","episode_num":1,"title":"Pilot","container_extension":"mkv",
               "info":{"movie_image":"http://img/pilot.jpg","plot":"Start.","duration_secs":2700}}]},
             "info":{"name":"Test Show"}}
            """.trimIndent(),
        )

        val response = client.seriesInfo(credentials, seriesId = 42)

        assertThat(response).isInstanceOf(XtreamResponse.Ok::class.java)
        val info = (response as XtreamResponse.Ok).value
        assertThat(info.episodes?.get("1")).hasSize(1)
        assertThat(info.episodes?.get("1")?.single()?.id.asLongOrNull()).isEqualTo(9001)

        // Two calls were made in order: v2 first, v1 fallback second.
        assertThat(server.takeRequest().path).contains("action=get_series_info_v2")
        assertThat(server.takeRequest().path).contains("action=get_series_info")
    }

    @Test
    fun seriesInfo_v2Ok_noFallbackCall() = runTest {
        server.enqueueJson("""{"seasons":[],"episodes":{"1":[]},"info":{"name":"Show"}}""")

        val response = client.seriesInfo(credentials, seriesId = 42)

        assertThat(response).isInstanceOf(XtreamResponse.Ok::class.java)
        assertThat(server.takeRequest().path).contains("action=get_series_info_v2")
        assertThat(server.requestCount).isEqualTo(1)
    }

    private fun MockWebServer.enqueueJson(body: String, code: Int = 200) {
        enqueue(
            MockResponse()
                .setResponseCode(code)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )
    }
}
