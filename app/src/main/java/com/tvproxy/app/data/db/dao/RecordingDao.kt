package com.tvproxy.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tvproxy.app.core.model.RecordingState
import com.tvproxy.app.data.db.entity.RecordingEntity
import com.tvproxy.app.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Upsert
    suspend fun upsert(recording: RecordingEntity): Long

    @Query("SELECT * FROM recordings ORDER BY startEpochMs DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("UPDATE recordings SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: RecordingState)

    @Query("UPDATE recordings SET state = :state, filePath = :filePath, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateFileResult(id: Long, state: RecordingState, filePath: String, sizeBytes: Long)

    /** SCHEDULED rows whose start falls in [fromMs, toMs) — the scheduler drain window (M6). */
    @Query(
        """SELECT * FROM recordings
           WHERE state = 'SCHEDULED' AND startEpochMs >= :fromMs AND startEpochMs < :toMs
           ORDER BY startEpochMs""",
    )
    suspend fun dueBetween(fromMs: Long, toMs: Long): List<RecordingEntity>

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReminderDao {

    @Upsert
    suspend fun upsert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders ORDER BY startEpochMs")
    fun observeAll(): Flow<List<ReminderEntity>>

    /** Reminders to fire between [fromMs, toMs) that have not fired yet. */
    @Query(
        """SELECT * FROM reminders
           WHERE notified = 0 AND startEpochMs >= :fromMs AND startEpochMs < :toMs
           ORDER BY startEpochMs""",
    )
    suspend fun dueBetween(fromMs: Long, toMs: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
