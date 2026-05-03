package com.yunemusic.data.repository

import android.util.Log
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.local.entities.toDomain
import com.yunemusic.data.local.entities.toEntity
import com.yunemusic.data.preferences.UserPreferences
import com.yunemusic.data.youtube.YouTubeRepository
import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val trackDao: TrackDao,
    private val playEventDao: PlayEventDao,
    private val userPreferences: UserPreferences
) : MusicRepository {

    companion object {
        private const val TAG = "MusicRepositoryImpl"
    }

    override suspend fun searchTracks(query: String, pageToken: String?): Result<List<Track>> =
        youTubeRepository.search(query)

    override suspend fun getTrackDetails(videoId: String): Result<Track> =
        youTubeRepository.getTrackInfo(videoId)

    override suspend fun getStreamUrl(videoId: String): Result<String> =
        youTubeRepository.getAudioStreamUrl(videoId)

    override fun getPlayHistory(): Flow<List<PlayEvent>> =
        playEventDao.getAllPlayEvents().map { it.map { e -> e.toDomain() } }

    override suspend fun savePlayEvent(event: PlayEvent) {
        playEventDao.insertEvent(event.toEntity())
    }

    override suspend fun getTasteProfile(): TasteProfile {
        val json = userPreferences.tasteProfileJson.firstOrNull() ?: ""
        return if (json.isBlank()) TasteProfile()
        else runCatching { Json.decodeFromString<TasteProfile>(json) }.getOrDefault(TasteProfile())
    }

    override suspend fun updateTasteProfile(profile: TasteProfile) {
        userPreferences.setTasteProfileJson(Json.encodeToString(profile))
    }

    override fun getLikedTracks(): Flow<List<Track>> =
        trackDao.getLikedTracks().map { it.map { e -> e.toDomain() } }

    override suspend fun likeTrack(track: Track) {
        if (trackDao.getTrackById(track.id) != null) {
            trackDao.setLiked(track.id, true)
        } else {
            trackDao.insertTrack(track.toEntity(isLiked = true))
        }
    }

    override suspend fun unlikeTrack(trackId: String) {
        trackDao.setLiked(trackId, false)
    }

    override suspend fun isTrackLiked(trackId: String): Boolean =
        trackDao.isLiked(trackId) ?: false

    override suspend fun saveTrack(track: Track) {
        if (trackDao.getTrackById(track.id) == null) {
            trackDao.insertTrack(track.toEntity())
        }
    }

    override fun getRecentTracks(): Flow<List<Track>> =
        trackDao.getRecentTracks().map { it.map { e -> e.toDomain() } }

    override suspend fun getRelatedTracks(videoId: String): Result<List<Track>> =
        youTubeRepository.getRelatedTracks(videoId)

    override suspend fun getTrending(): Result<List<Track>> =
        youTubeRepository.getTrending()

    override suspend fun getPlayedTrackIds(): Set<String> =
        playEventDao.getAllPlayedTrackIds().toSet()
}
