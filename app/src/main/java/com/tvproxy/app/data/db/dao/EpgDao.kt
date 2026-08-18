package com.tvproxy.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tvproxy.app.data.db.entity.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    /** Duplicate-safe upsert keyed on the unique (channelEpgId, startEpochMs) index. */
    @Upsert
    suspend fun upsertAll(programs: List<EpgProgramEntity>): List<Long>

    @Query(
        """SELECT * FROM epg_programs
           WHERE channelEpgId = :channelEpgId AND startEpochMs >= :fromMs AND startEpochMs < :toMs
           ORDER BY startEpochMs""",
    )
    fun observePrograms(channelEpgId: String, fromMs: Long, toMs: Long): Flow<List<EpgProgramEntity>>

    /** Offset paging window for a channel's programme range. */
    @Query(
        """SELECT * FROM epg_programs
           WHERE channelEpgId = :channelEpgId AND startEpochMs >= :fromMs AND startEpochMs < :toMs
           ORDER BY startEpochMs
           LIMIT :limit OFFSET :offset""",
    )
    suspend fun programSlice(
        channelEpgId: String,
        fromMs: Long,
        toMs: Long,
        limit: Int,
        offset: Int,
    ): List<EpgProgramEntity>

    /** Current + next programme (2 rows) at [nowEpochMs] for the now/next strip. */
    @Query(
        """SELECT * FROM epg_programs
           WHERE channelEpgId = :channelEpgId AND endEpochMs > :nowEpochMs
           ORDER BY startEpochMs LIMIT 2""",
    )
    suspend fun nowNext(channelEpgId: String, nowEpochMs: Long): List<EpgProgramEntity>

    /** Resolves programmes for an internal channel row via its tvg-id (XMLTV join). */
    @Query(
        """SELECT epg_programs.* FROM epg_programs
           INNER JOIN channels ON channels.tvgId = epg_programs.channelEpgId
           WHERE channels.id = :channelId
             AND epg_programs.startEpochMs >= :fromMs AND epg_programs.startEpochMs < :toMs
           ORDER BY epg_programs.startEpochMs""",
    )
    suspend fun programsForChannelId(channelId: Long, fromMs: Long, toMs: Long): List<EpgProgramEntity>

    @Query("SELECT COUNT(*) FROM epg_programs WHERE channelEpgId = :channelEpgId")
    suspend fun countForChannelEpgId(channelEpgId: String): Int

    /** Drops programmes that ended before [beforeEpochMs] (import hygiene). Returns row count. */
    @Query("DELETE FROM epg_programs WHERE endEpochMs < :beforeEpochMs")
    suspend fun pruneBefore(beforeEpochMs: Long): Int
}
