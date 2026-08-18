package com.tvproxy.app.data.xtream

import androidx.room.withTransaction
import com.tvproxy.app.core.model.CatchupType
import com.tvproxy.app.core.model.ImportProgress
import com.tvproxy.app.data.db.ChannelStateMerger
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.dao.ChannelDao
import com.tvproxy.app.data.db.dao.ChannelStateDao
import com.tvproxy.app.data.db.dao.GroupDao
import com.tvproxy.app.data.db.dao.PlaylistDao
import com.tvproxy.app.data.db.dao.VodDao
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.ChannelGroupEntity
import com.tvproxy.app.data.db.entity.EpisodeEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import com.tvproxy.app.data.db.entity.SeriesItemEntity
import com.tvproxy.app.data.db.entity.VodItemEntity
import com.tvproxy.app.core.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Xtream Codes sync (architecture.md §4/§5.1): account → live → VOD → series.
 *
 * Same guarantees as [com.tvproxy.app.data.playlist.PlaylistRepository]: transactional
 * upserts (previous data survives failures), duplicate-safe keys, and a user-state
 * merge so re-syncs keep favorites/history. Episode catalogs are imported lazily via
 * [syncSeriesInfo] — series-info payloads are large, so M5 fetches them on demand.
 */
@Singleton
class XtreamRepository @Inject constructor(
    private val db: TvProxyDatabase,
    private val client: XtreamClient,
    private val playlistDao: PlaylistDao,
    private val groupDao: GroupDao,
    private val channelDao: ChannelDao,
    private val channelStateDao: ChannelStateDao,
    private val vodDao: VodDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Full catalog sync for one Xtream playlist; the flow never throws. */
    fun syncPlaylist(playlist: PlaylistEntity): Flow<ImportProgress> = flow {
        val credentials = credentialsOf(playlist)

        emit(ImportProgress.Fetching)
        val account = client.accountInfo(credentials).requireOk("account authentication")
        validateAccount(account)
        val liveFormat = account.outputFormat()

        val liveCategories = client.liveCategories(credentials).requireOk("live categories")
        val liveStreams = client.liveStreams(credentials).requireOk("live streams")
        val vodStreams = client.vodStreams(credentials).requireOk("VOD streams")
        val seriesList = client.series(credentials).requireOk("series list")

        emit(ImportProgress.Parsing)
        val context = SyncContext(credentials, playlist.id, liveFormat)
        val channelCount = context.persist(liveCategories, liveStreams, vodStreams, seriesList) { savedSoFar ->
            emit(ImportProgress.Saving(savedSoFar))
        }
        playlistDao.updateLastSync(playlist.id, System.currentTimeMillis())

        emit(
            ImportProgress.Done(
                playlistId = playlist.id,
                channelCount = channelCount,
                groupCount = liveCategories.size,
                vodCount = vodStreams.size,
                seriesCount = seriesList.size,
            ),
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(ImportProgress.Failed(reason = throwable.message ?: "Xtream sync failed"))
    }.flowOn(io)

    /** Lazily imports episodes for one series (v2 action with v1 fallback, ADR §10.3). */
    suspend fun syncSeriesInfo(playlist: PlaylistEntity, seriesXtreamId: Long): Int {
        val credentials = credentialsOf(playlist)
        val info = client.seriesInfo(credentials, seriesXtreamId).requireOk("series info")
        val episodes = info.toEpisodeEntities(playlist.id, seriesXtreamId)
        db.withTransaction {
            vodDao.deleteEpisodesForSeries(playlist.id, seriesXtreamId)
            vodDao.upsertEpisodes(episodes)
        }
        return episodes.size
    }

    /** Transactional all-or-nothing catalog write; returns the imported channel count. */
    private inner class SyncContext(
        val credentials: XtreamCredentials,
        val playlistId: Long,
        val liveFormat: String,
    ) {
        suspend fun persist(
            categories: List<XtreamCategoryDto>,
            liveStreams: List<XtreamLiveStreamDto>,
            vodStreams: List<XtreamVodStreamDto>,
            seriesList: List<XtreamSeriesDto>,
            onChannelsSaved: suspend (Int) -> Unit,
        ): Int = db.withTransaction {
            val categoryNames = categories.toIdNameMap()
            val groupIds = persistGroups(categories, liveStreams, categoryNames)
            val channelCount = persistLiveChannels(liveStreams, categoryNames, groupIds, onChannelsSaved)
            vodDao.upsertVod(vodStreams.mapNotNull { it.toEntity(playlistId) })
            vodDao.upsertSeries(seriesList.mapNotNull { it.toEntity(playlistId) })
            channelCount
        }

        /** Upserts only live categories that are actually referenced by a stream. */
        private suspend fun persistGroups(
            categories: List<XtreamCategoryDto>,
            liveStreams: List<XtreamLiveStreamDto>,
            categoryNames: Map<Long, String>,
        ): Map<String, Long> {
            val usedIds = liveStreams.mapNotNull { it.categoryId.asLongOrNull() }.toSet()
            val rows = categories.mapIndexedNotNull { index, dto ->
                val id = dto.categoryId.asLongOrNull()
                val name = categoryNames[id]
                if (id == null || name == null || id !in usedIds) {
                    null
                } else {
                    ChannelGroupEntity(playlistId = playlistId, name = name, position = index)
                }
            }
            groupDao.upsertAll(rows)
            return rows.associate { row -> row.name to (groupDao.findByName(playlistId, row.name)?.id ?: 0L) }
        }

        private suspend fun persistLiveChannels(
            liveStreams: List<XtreamLiveStreamDto>,
            categoryNames: Map<Long, String>,
            groupIds: Map<String, Long>,
            onChannelsSaved: suspend (Int) -> Unit,
        ): Int {
            val fresh = liveStreams.mapIndexedNotNull { index, dto ->
                dto.toChannelEntity(credentials, playlistId, liveFormat, categoryNames, groupIds, index)
            }
            val merged = ChannelStateMerger.merge(fresh, channelStateDao.stateRowsForPlaylist(playlistId))
            var savedSoFar = 0
            merged.chunked(DB_CHUNK).forEach { chunk ->
                channelDao.upsertAll(chunk)
                savedSoFar += chunk.size
                onChannelsSaved(savedSoFar)
            }
            // An empty provider response never wipes existing channels (no chunks → no delete).
            fresh.map { it.streamUrl }.chunked(DB_CHUNK).forEach { urlChunk ->
                channelDao.deleteMissingByUrl(playlistId, urlChunk)
            }
            return fresh.size
        }
    }

    // ---- mapping helpers ----------------------------------------------------------

    private fun credentialsOf(playlist: PlaylistEntity): XtreamCredentials {
        val user = playlist.username.orEmpty()
        require(user.isNotEmpty()) { "Xtream playlist '${playlist.name}' is missing credentials" }
        return XtreamCredentials(
            baseUrl = playlist.url.trim().removeSuffix("/"),
            username = user,
            password = playlist.password.orEmpty(),
        )
    }

    private fun validateAccount(account: XtreamAccountInfoDto) {
        val info = account.userInfo ?: throw XtreamAuthException("Portal returned no user info")
        if (info.auth.asLongOrNull() == 0L) throw XtreamAuthException("Username or password rejected")
        val status = info.status
        require(status == null || status.equals(XTREAM_STATUS_ACTIVE, ignoreCase = true)) {
            "Account not active (status: $status)"
        }
    }

    private fun XtreamAccountInfoDto.outputFormat(): String {
        val formats = userInfo?.allowedOutputFormats.orEmpty().map { it.lowercase() }
        return if (FORMAT_HLS in formats) FORMAT_HLS else formats.firstOrNull() ?: FORMAT_TS
    }

    private fun List<XtreamCategoryDto>.toIdNameMap(): Map<Long, String> =
        mapNotNull { dto ->
            val id = dto.categoryId.asLongOrNull()
            val name = dto.categoryName?.trim().orEmpty().ifEmpty { null }
            if (id == null || name == null) null else id to name
        }.toMap()

    private fun XtreamLiveStreamDto.toChannelEntity(
        credentials: XtreamCredentials,
        playlistId: Long,
        liveFormat: String,
        categoryNames: Map<Long, String>,
        groupIds: Map<String, Long>,
        index: Int,
    ): ChannelEntity? {
        val streamIdValue = streamId.asLongOrNull()
        val displayName = name?.trim().orEmpty()
        if (streamIdValue == null || displayName.isEmpty()) return null
        val groupName = categoryId.asLongOrNull()?.let { categoryNames[it] }
        return ChannelEntity(
            playlistId = playlistId,
            groupId = groupName?.let { groupIds[it] },
            name = displayName,
            logoUrl = streamIcon?.ifBlank { null },
            streamUrl = liveStreamUrl(credentials, streamIdValue, liveFormat),
            tvgId = epgChannelId?.ifBlank { null },
            catchupType = if (tvArchive.asBooleanFlag()) CatchupType.XTREAM else CatchupType.NONE,
            catchupDays = tvArchiveDuration.asIntOrNull(),
            xtreamStreamId = streamIdValue,
            sortOrder = index,
        )
    }

    private fun XtreamVodStreamDto.toEntity(playlistId: Long): VodItemEntity? {
        val id = streamId.asLongOrNull()
        val displayName = name?.trim().orEmpty()
        return if (id == null || displayName.isEmpty()) {
            null
        } else {
            VodItemEntity(
                playlistId = playlistId,
                xtreamId = id,
                name = displayName,
                posterUrl = streamIcon?.ifBlank { null },
                rating = rating5Based.asDoubleOrNull(),
                categoryId = categoryId.asLongOrNull(),
                containerExtension = containerExtension?.ifBlank { null } ?: VodItemEntity.DEFAULT_CONTAINER,
                addedEpochMs = added?.trim()?.toLongOrNull()?.let { it * SECONDS_TO_MS },
            )
        }
    }

    private fun XtreamSeriesDto.toEntity(playlistId: Long): SeriesItemEntity? {
        val id = seriesId.asLongOrNull()
        val displayName = name?.trim().orEmpty()
        return if (id == null || displayName.isEmpty()) {
            null
        } else {
            SeriesItemEntity(
                playlistId = playlistId,
                xtreamId = id,
                name = displayName,
                posterUrl = cover?.ifBlank { null },
                plot = plot?.ifBlank { null },
                rating = (rating5Based ?: rating).asDoubleOrNull(),
                categoryId = categoryId.asLongOrNull(),
                lastModifiedEpochMs = lastModified?.trim()?.toLongOrNull()?.let { it * SECONDS_TO_MS },
            )
        }
    }

    private fun XtreamSeriesInfoDto.toEpisodeEntities(playlistId: Long, seriesXtreamId: Long): List<EpisodeEntity> =
        episodes.orEmpty().flatMap { (seasonKey, seasonEpisodes) ->
            val seasonNo = seasonKey.trim().toIntOrNull() ?: 0
            seasonEpisodes.mapNotNull { episode -> episode.toEntity(playlistId, seriesXtreamId, seasonNo) }
        }

    private fun XtreamEpisodeDto.toEntity(
        playlistId: Long,
        seriesXtreamId: Long,
        seasonNo: Int,
    ): EpisodeEntity? {
        val id = id.asLongOrNull()
        val number = episodeNum.asIntOrNull() ?: 0
        return if (id == null) {
            null
        } else {
            EpisodeEntity(
                playlistId = playlistId,
                seriesXtreamId = seriesXtreamId,
                episodeXtreamId = id,
                seasonNo = seasonNo,
                episodeNo = number,
                title = title?.trim().orEmpty().ifEmpty { "S%sE%s".format(seasonNo, number) },
                plot = info?.plot?.ifBlank { null },
                stillUrl = info?.movieImage?.ifBlank { null },
                durationSec = info?.durationSecs.asIntOrNull(),
                containerExtension = containerExtension?.ifBlank { null } ?: VodItemEntity.DEFAULT_CONTAINER,
            )
        }
    }

    companion object {
        const val FORMAT_HLS = "m3u8"
        const val FORMAT_TS = "ts"
        private const val XTREAM_STATUS_ACTIVE = "Active"
        private const val DB_CHUNK = 500
        private const val SECONDS_TO_MS = 1000L

        /** Xtream live URL layout: {portal}/live/{user}/{pass}/{streamId}.{ext} */
        fun liveStreamUrl(credentials: XtreamCredentials, streamId: Long, format: String): String =
            "${credentials.baseUrl}/live/${credentials.username}/${credentials.password}/$streamId.$format"

        /** Xtream VOD URL layout: {portal}/movie/{user}/{pass}/{streamId}.{ext} (used by M5 playback). */
        fun vodStreamUrl(credentials: XtreamCredentials, streamId: Long, extension: String): String =
            "${credentials.baseUrl}/movie/${credentials.username}/${credentials.password}/$streamId.$extension"
    }
}

/** Authentication/authorization failure surfaced by the portal handshake. */
class XtreamAuthException(message: String) : Exception(message)

/** Non-2xx response from the portal. */
class XtreamHttpException(val code: Int, message: String) : Exception(message)

/** Unwrap a client response or throw a typed error naming the failed stage. */
private fun <T> XtreamResponse<T>.requireOk(stage: String): T = when (this) {
    is XtreamResponse.Ok -> value
    is XtreamResponse.Http ->
        throw XtreamHttpException(code, "Xtream $stage failed (HTTP $code)")

    is XtreamResponse.Network, is XtreamResponse.Malformed ->
        throw XtreamHttpException(code = 0, message = "Xtream $stage failed: transport/parse error")
}
