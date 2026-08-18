package com.tvproxy.app.data.epg

import androidx.room.withTransaction
import com.tvproxy.app.core.model.ImportProgress
import com.tvproxy.app.data.db.TvProxyDatabase
import com.tvproxy.app.data.db.dao.EpgDao
import com.tvproxy.app.data.db.entity.EpgProgramEntity
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
 * XMLTV EPG ingest (architecture.md §5.3): fetch → stream-parse → batch upsert → prune.
 *
 * Batches of [XmltvParser.DEFAULT_BATCH_SIZE] keep the 100k-programme import under the
 * 30 s budget without loading the feed into memory; the whole import is transactional
 * (a broken feed keeps the previous EPG). Expired programmes are pruned at the end
 * of every successful import.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val db: TvProxyDatabase,
    private val epgDao: EpgDao,
    private val xmltvParser: XmltvParser,
    private val pullParserProvider: PullParserProvider,
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Import the XMLTV feed declared by [playlist] (`epgUrl`). The flow never throws. */
    fun importEpg(playlist: PlaylistEntity): Flow<ImportProgress> = flow {
        val url = playlist.epgUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Playlist '${playlist.name}' has no EPG URL configured")

        emit(ImportProgress.Fetching)
        val bytes = fetchBytes(url, playlist.userAgent)

        emit(ImportProgress.Parsing)
        val imported = parseAndPersist(bytes) { savedSoFar -> emit(ImportProgress.Saving(savedSoFar)) }
        db.withTransaction { epgDao.pruneBefore(pruneThreshold()) }

        emit(
            ImportProgress.Done(
                playlistId = playlist.id,
                channelCount = 0,
                groupCount = 0,
                programCount = imported,
            ),
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(ImportProgress.Failed(reason = throwable.epgReason()))
    }.flowOn(io)

    /** Stream-parse and upsert per batch inside one transaction; returns programme count. */
    private suspend fun parseAndPersist(
        bytes: ByteArray,
        onBatch: suspend (savedSoFar: Int) -> Unit,
    ): Int {
        var savedSoFar = 0
        db.withTransaction {
            xmltvParser.parse(
                input = bytes.inputStream(),
                parser = pullParserProvider.newPullParser(),
            ) { batch ->
                val programmes = batch.filterIsInstance<XmltvItem.Programme>()
                if (programmes.isNotEmpty()) {
                    epgDao.upsertAll(programmes.map(XmltvItem.Programme::toEntity))
                    savedSoFar += programmes.size
                    onBatch(savedSoFar)
                }
            }
        }
        return savedSoFar
    }

    private fun fetchBytes(url: String, userAgent: String?): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header(HEADER_USER_AGENT, userAgent ?: DEFAULT_USER_AGENT)
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from EPG server")
            response.body.bytes()
        }
    }

    /** Keep EPG rows whose programme ended within the retention window (7 days back). */
    private fun pruneThreshold(): Long =
        System.currentTimeMillis() - RETENTION_WINDOW_MS

    private fun XmltvItem.Programme.toEntity(): EpgProgramEntity = EpgProgramEntity(
        channelEpgId = channelEpgId,
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        title = title,
        subTitle = subTitle,
        description = description,
        seasonNo = seasonNo,
        episodeNo = episodeNo,
        imageUrl = imageUrl,
    )

    private fun Throwable.epgReason(): String = when (this) {
        is EpgParseException -> message ?: "Malformed XMLTV feed"
        is IOException -> message ?: "Network error while fetching EPG"
        else -> message ?: "EPG import failed"
    }

    companion object {
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val DEFAULT_USER_AGENT = "TVProxy/0.1 (Android)"
        private const val RETENTION_DAYS = 7L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val RETENTION_WINDOW_MS = RETENTION_DAYS * DAY_MS
    }
}
