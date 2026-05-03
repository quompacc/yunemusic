package com.yunemusic.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunemusic.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val genre: String = "",
    val bpm: Int = 0,
    val isLiked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    genre = genre,
    bpm = bpm
)

fun Track.toEntity(isLiked: Boolean = false): TrackEntity = TrackEntity(
    id = id,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    genre = genre,
    bpm = bpm,
    isLiked = isLiked
)
