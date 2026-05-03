package com.yunemusic.data.local.dao

import androidx.room.*
import com.yunemusic.data.local.entities.PlayEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayEventDao {
    @Query("SELECT * FROM play_events ORDER BY timestamp DESC")
    fun getAllPlayEvents(): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events WHERE trackId = :trackId ORDER BY timestamp DESC")
    fun getEventsForTrack(trackId: String): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 100): List<PlayEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PlayEventEntity)

    @Query("DELETE FROM play_events WHERE timestamp < :olderThan")
    suspend fun deleteOldEvents(olderThan: Long)

    @Query("DELETE FROM play_events")
    suspend fun deleteAllEvents()

    @Query("SELECT COUNT(*) FROM play_events WHERE trackId = :trackId")
    suspend fun getPlayCountForTrack(trackId: String): Int

    @Query("SELECT DISTINCT trackId FROM play_events")
    suspend fun getAllPlayedTrackIds(): List<String>
}
