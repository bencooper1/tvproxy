package com.tvproxy.app.data.playlist

import androidx.room.withTransaction
import com.tvproxy.app.core.model.ImportProgress
import com.tvproxy.app.data.db.ChannelStateMerger
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.dao.ChannelDao
import com.tvproxy.app.data.db.dao.ChannelStateDao
import com.tvproxy.app.data.db.dao.GroupDao
import com.tvproxy.app.data.db.dao.PlaylistDao
import com.tvproxy.app.data.db.entity.ChannelEntity
import com.tvproxy.app.data.db.entity.ChannelGroupEntity
import com.tvproxy.app.data.db.entity.PlaylistEntity
import com.tvproxy.app.core.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * M3U playlist ingest (architecture.md §5.1): fetch → parse → transactional upsert.
 *
 * Guarantees (plan.md M1 acceptance):
 * - transactional: any failure keeps previously imported data;
 * - duplicate-safe upserts via the (playlistId, streamUrl) unique index;
 * - user state (favorites/hidden/history) survives re-import ([ChannelStateMerger]);
 * - an empty parse is treated as failure (broken provider response) and imports nothing.
 *
 * Progress is exposed as a [Flow] of [ImportProgress]; the flow never throws.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val db: TvProxyDatabase,
    private val playlistDao: PlaylistDao,
    private val groupDao: GroupDao,
    private val channelDao: ChannelDao,
    private val channelStateDao: ChannelStateDao,
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    val playlists: Flow<List<PlaylistEntity>> = playlistDao.observeAll()

    fun importM3uPlaylist(playlist: PlaylistEntity): Flow<ImportProgress> = flow {
        emit(ImportProgress.Fetching)
        val text = fetchText(playlist)

        emit(ImportProgress.Parsing)
        val entries = m3uParser.parse(text)
        require(entries.isNotEmpty()) { "Playlist contained no playable entries" }

        val saved = persistImport(playlist.id, entries) { savedSoFar ->
            emit(ImportProgress.Saving(savedSoFar))
        }
        playlistDao.updateLastSync(playlist.id, System.currentTimeMillis())
        emit(
            ImportProgress.Done(
                playlistId = playlist.id,
                channelCount = entries.size,
                groupCount = saved.groupCount,
            ),
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(ImportProgress.Failed(reason = throwable.importReason()))
    }.flowOn(io)

    /** All-or-nothing upsert of groups + channels; reports per-chunk progress. */
    private suspend fun persistImport(
        playlistId: Long,
        entries: List<M3uEntry>,
        onChunk: suspend (savedSoFar: Int) -> Unit,
    ): ImportSummary = db.withTransaction {
        val groupNames = entries.mapNotNull { it.groupTitle }.distinct()
        groupDao.upsertAll(
            groupNames.mapIndexed { index, name -> ChannelGroupEntity(playlistId = playlistId, name = name, position = index) },
        )
        val groupIds = groupNames.associateWith { name -> groupDao.findByName(playlistId, name)?.id }

        val existing = channelStateDao.stateRowsForPlaylist(playlistId)
        val fresh = entries.mapIndexed { index, entry -> entry.toEntity(playlistId, groupIds, index) }
        val merged = ChannelStateMerger.merge(fresh, existing)

        var savedSoFar = 0
        merged.chunked(DB_CHUNK).forEach { chunk ->
            channelDao.upsertAll(chunk)
            savedSoFar += chunk.size
            onChunk(savedSoFar)
        }
        dropStaleRows(playlistId, fresh.map { it.streamUrl })
        ImportSummary(groupCount = groupNames.size)
    }

    /** Remove provider-deleted channels. Never called with an empty keep-list (guarded by the empty-parse check). */
    private suspend fun dropStaleRows(playlistId: Long, keptUrls: List<String>) {
        keptUrls.chunked(DB_CHUNK).forEach { urls -> channelDao.deleteMissingByUrl(playlistId, urls) }
    }

    private fun fetchText(playlist: PlaylistEntity): String {
        val request = Request.Builder()
            .url(playlist.url)
            .header(HEADER_USER_AGENT, playlist.userAgent ?: DEFAULT_USER_AGENT)
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from playlist server")
            response.body.string().ifBlank { throw IOException("Playlist server returned an empty body") }
        }
    }

    private fun M3uEntry.toEntity(
        playlistId: Long,
        groupIds: Map<String, Long?>,
        index: Int,
    ): ChannelEntity = ChannelEntity(
        playlistId = playlistId,
        groupId = groupTitle?.let { groupIds[it] },
        number = tvgChno,
        name = name,
        logoUrl = tvgLogo,
        streamUrl = url,
        tvgId = tvgId,
        tvgName = tvgName,
        catchupType = catchupType,
        catchupSource = catchupSource,
        catchupDays = catchupDays,
        sortOrder = index,
    )

    private fun Throwable.importReason(): String = when (this) {
        is IOException -> message ?: "Network error while fetching playlist"
        else -> message ?: "Playlist import failed"
    }

    private data class ImportSummary(val groupCount: Int)

    companion object {
        private const val DB_CHUNK = 500
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val DEFAULT_USER_AGENT = "TVProxy/0.1 (Android)"
    }
}
