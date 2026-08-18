package com.tvproxy.app.data.xtream

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Moshi codegen DTOs for the Xtream Codes `player_api.php` endpoints. Fields marked
// Any? are the ones real-world panels serialize inconsistently (string vs number) —
// converted at the repository boundary via XtreamModels.kt helpers.

@JsonClass(generateAdapter = true)
data class XtreamAccountInfoDto(
    @Json(name = "user_info") val userInfo: XtreamUserInfoDto? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamUserInfoDto(
    val username: String? = null,
    val status: String? = null, // "Active" when the subscription is valid
    val auth: Any? = null, // 0 → credentials rejected
    @Json(name = "exp_date") val expDate: String? = null, // epoch as string
    @Json(name = "is_trial") val isTrial: Any? = null,
    @Json(name = "allowed_output_formats") val allowedOutputFormats: List<String>? = null,
    @Json(name = "max_connections") val maxConnections: Any? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamCategoryDto(
    @Json(name = "category_id") val categoryId: Any? = null,
    @Json(name = "category_name") val categoryName: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamLiveStreamDto(
    val name: String? = null,
    @Json(name = "stream_id") val streamId: Any? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "category_id") val categoryId: Any? = null,
    @Json(name = "tv_archive") val tvArchive: Any? = null,
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Any? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodStreamDto(
    val name: String? = null,
    @Json(name = "stream_id") val streamId: Any? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    @Json(name = "rating_5based") val rating5Based: Any? = null,
    @Json(name = "category_id") val categoryId: Any? = null,
    val added: String? = null, // epoch as string
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesDto(
    @Json(name = "series_id") val seriesId: Any? = null,
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val rating: Any? = null,
    @Json(name = "rating_5based") val rating5Based: Any? = null,
    @Json(name = "category_id") val categoryId: Any? = null,
    @Json(name = "last_modified") val lastModified: String? = null, // epoch as string
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesInfoDto(
    val seasons: List<XtreamSeasonDto>? = null,
    val episodes: Map<String, List<XtreamEpisodeDto>>? = null,
    val info: XtreamSeriesDto? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamSeasonDto(
    @Json(name = "season_number") val seasonNumber: Any? = null,
    val name: String? = null,
)

/** One episode inside `get_series_info`; `id` is the streamable episode id. */
@JsonClass(generateAdapter = true)
data class XtreamEpisodeDto(
    val id: Any? = null,
    @Json(name = "episode_num") val episodeNum: Any? = null,
    val title: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    val info: XtreamEpisodeInfoDto? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamEpisodeInfoDto(
    @Json(name = "movie_image") val movieImage: String? = null,
    val plot: String? = null,
    @Json(name = "duration_secs") val durationSecs: Any? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfoDto(
    val info: XtreamVodDetailDto? = null,
    @Json(name = "movie_data") val movieData: XtreamVodStreamDto? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodDetailDto(
    val name: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    @Json(name = "duration_secs") val durationSecs: Any? = null,
)
