package com.yunemusic.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunemusic.domain.model.PlayEvent

@Entity(tableName = "play_events")
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val timestamp: Long,
    val playedSeconds: Int,
    val totalSeconds: Int,
    val skipped: Boolean,
    val liked: Boolean
)

fun PlayEventEntity.toDomain(): PlayEvent = PlayEvent(
    trackId = trackId,
    timestamp = timestamp,
    playedSeconds = playedSeconds,
    totalSeconds = totalSeconds,
    skipped = skipped,
    liked = liked
)

fun PlayEvent.toEntity(): PlayEventEntity = PlayEventEntity(
    trackId = trackId,
    timestamp = timestamp,
    playedSeconds = playedSeconds,
    totalSeconds = totalSeconds,
    skipped = skipped,
    liked = liked
)
