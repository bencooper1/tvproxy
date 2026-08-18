package com.tvproxy.app.data.xtream

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Xtream Codes API client (architecture.md §5.1, ADR-006).
 *
 * Owns Retrofit instances keyed by portal base URL (one per configured playlist).
 * Every call is non-throwing and returns [XtreamResponse]; the repository decides
 * how failures map to import progress.
 */
@Singleton
class XtreamClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {

    private val apis = ConcurrentHashMap<String, XtreamApi>()

    suspend fun accountInfo(credentials: XtreamCredentials): XtreamResponse<XtreamAccountInfoDto> =
        safeCall { apiFor(credentials.baseUrl).accountInfo(credentials.username, credentials.password) }

    suspend fun liveCategories(credentials: XtreamCredentials): XtreamResponse<List<XtreamCategoryDto>> =
        safeCall { apiFor(credentials.baseUrl).liveCategories(credentials.username, credentials.password) }

    suspend fun liveStreams(credentials: XtreamCredentials): XtreamResponse<List<XtreamLiveStreamDto>> =
        safeCall { apiFor(credentials.baseUrl).liveStreams(credentials.username, credentials.password) }

    suspend fun vodStreams(credentials: XtreamCredentials): XtreamResponse<List<XtreamVodStreamDto>> =
        safeCall { apiFor(credentials.baseUrl).vodStreams(credentials.username, credentials.password) }

    suspend fun series(credentials: XtreamCredentials): XtreamResponse<List<XtreamSeriesDto>> =
        safeCall { apiFor(credentials.baseUrl).series(credentials.username, credentials.password) }

    suspend fun vodInfo(credentials: XtreamCredentials, vodId: Long): XtreamResponse<XtreamVodInfoDto> =
        safeCall { apiFor(credentials.baseUrl).vodInfo(credentials.username, credentials.password, vodId) }

    /**
     * Series detail: tries the v2 action first, falls back to v1 on HTTP 404
     * (architecture.md §10 question 3 decision).
     */
    suspend fun seriesInfo(credentials: XtreamCredentials, seriesId: Long): XtreamResponse<XtreamSeriesInfoDto> =
        when (val v2 = safeCall {
            apiFor(credentials.baseUrl).seriesInfoV2(credentials.username, credentials.password, seriesId)
        }) {
            is XtreamResponse.Http ->
                if (v2.code == HTTP_NOT_FOUND) {
                    safeCall { apiFor(credentials.baseUrl).seriesInfoV1(credentials.username, credentials.password, seriesId) }
                } else {
                    v2
                }
            else -> v2
        }

    private fun apiFor(baseUrl: String): XtreamApi {
        val base = baseUrl.trim().removeSuffix("/") + "/"
        return apis.getOrPut(base) {
            Retrofit.Builder()
                .baseUrl(base)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(XtreamApi::class.java)
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): XtreamResponse<T> =
        try {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                XtreamResponse.Ok(body)
            } else {
                XtreamResponse.Http(response.code(), response.message().ifBlank { "HTTP ${response.code()}" })
            }
        } catch (e: JsonDataException) {
            // Non-JSON/error-object bodies (e.g. `{}` where a list was expected) land here.
            XtreamResponse.Malformed(e)
        } catch (e: IOException) {
            XtreamResponse.Network(e)
        }

    companion object {
        private const val HTTP_NOT_FOUND = 404
    }
}
