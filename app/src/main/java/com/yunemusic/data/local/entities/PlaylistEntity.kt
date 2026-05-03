package com.yunemusic.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunemusic.data.local.dao.PlaylistWithCount
import com.yunemusic.domain.model.Playlist
import com.yunemusic.domain.model.Track

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val trackTitle: String,
    val trackChannelName: String,
    val trackThumbnailUrl: String,
    val trackDurationSeconds: Int,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

fun PlaylistWithCount.toDomain() = Playlist(
    id = id,
    name = name,
    trackCount = trackCount,
    createdAt = createdAt
)

fun PlaylistEntity.toDomain(trackCount: Int = 0) = Playlist(
    id = id,
    name = name,
    trackCount = trackCount,
    createdAt = createdAt
)

fun PlaylistTrackEntity.toTrack() = Track(
    id = trackId,
    title = trackTitle,
    channelName = trackChannelName,
    thumbnailUrl = trackThumbnailUrl,
    durationSeconds = trackDurationSeconds
)

fun Track.toPlaylistTrack(playlistId: Long, position: Int = 0) = PlaylistTrackEntity(
    playlistId = playlistId,
    trackId = id,
    trackTitle = title,
    trackChannelName = channelName,
    trackThumbnailUrl = thumbnailUrl,
    trackDurationSeconds = durationSeconds,
    position = position
)
