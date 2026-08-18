package com.tvproxy.app

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * M0 test-rig smoke test: proves MockWebServer + OkHttp + Truth are wired and
 * working. From M1 onward this rig is used to test the Xtream/M3U clients.
 */
class MockWebServerRigTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mockWebServer_servesResponse_andTruthAsserts() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val response = OkHttpClient().newCall(
            Request.Builder().url(server.url("/player_api.php")).build()
        ).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).contains("\"ok\":true")
    }
}
