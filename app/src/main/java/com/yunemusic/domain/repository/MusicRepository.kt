package com.yunemusic.domain.repository

import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.Playlist
import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun searchTracks(query: String, pageToken: String? = null): Result<List<Track>>
    suspend fun getTrackDetails(videoId: String): Result<Track>
    suspend fun getStreamUrl(videoId: String): Result<String>
    fun getPlayHistory(): Flow<List<PlayEvent>>
    suspend fun savePlayEvent(event: PlayEvent)
    suspend fun getTasteProfile(): TasteProfile
    suspend fun updateTasteProfile(profile: TasteProfile)
    fun getLikedTracks(): Flow<List<Track>>
    suspend fun likeTrack(track: Track)
    suspend fun unlikeTrack(trackId: String)
    suspend fun isTrackLiked(trackId: String): Boolean
    suspend fun saveTrack(track: Track)
    fun getRecentTracks(): Flow<List<Track>>
    suspend fun getRelatedTracks(videoId: String): Result<List<Track>>
    suspend fun getTrending(): Result<List<Track>>
    suspend fun getPlayedTrackIds(): Set<String>

    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(id: Long)
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)
    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>>
}
