package com.yunemusic.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunemusic.domain.model.Track

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val filePath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

fun DownloadEntity.toTrack(): Track = Track(
    id = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds
)
