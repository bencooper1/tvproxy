package com.tvproxy.app.data.xtream

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit surface for Xtream Codes `player_api.php` (ADR-006).
 *
 * All actions return `Response<T>` rather than throwing `HttpException`, so the client
 * can inspect 404s for the series v2→v1 fallback (architecture.md §10, question 3).
 */
internal interface XtreamApi {

    @GET("player_api.php")
    suspend fun accountInfo(
        @Query("username") username: String,
        @Query("password") password: String,
    ): Response<XtreamAccountInfoDto>

    @GET("player_api.php?action=get_live_categories")
    suspend fun liveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
    ): Response<List<XtreamCategoryDto>>

    @GET("player_api.php?action=get_live_streams")
    suspend fun liveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
    ): Response<List<XtreamLiveStreamDto>>

    @GET("player_api.php?action=get_vod_streams")
    suspend fun vodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
    ): Response<List<XtreamVodStreamDto>>

    @GET("player_api.php?action=get_vod_info")
    suspend fun vodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Long,
    ): Response<XtreamVodInfoDto>

    @GET("player_api.php?action=get_series")
    suspend fun series(
        @Query("username") username: String,
        @Query("password") password: String,
    ): Response<List<XtreamSeriesDto>>

    /** Newer panels (`_v2` adds season metadata). Falls back to [seriesInfoV1] on 404. */
    @GET("player_api.php?action=get_series_info_v2")
    suspend fun seriesInfoV2(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Long,
    ): Response<XtreamSeriesInfoDto>

    @GET("player_api.php?action=get_series_info")
    suspend fun seriesInfoV1(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Long,
    ): Response<XtreamSeriesInfoDto>
}
