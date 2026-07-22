package com.yunemusic.service

import android.util.Log
import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TasteAnalyzer @Inject constructor(
    private val repository: MusicRepository
) {
    companion object {
        private const val TAG = "TasteAnalyzer"

        // Positive signals
        private const val WEIGHT_LIKED = 3.0f         // explicit like button
        private const val WEIGHT_FULL = 1.5f           // 90%+ completion
        private const val WEIGHT_GOOD = 1.0f           // 70–89% completion
        private const val WEIGHT_PARTIAL = 0.3f        // 40–69% completion

        // Decay: 0.97 = score halves after ~23 plays (memory ~1 week of listening)
        private const val DECAY = 0.97f
        private const val MAX_MAP_SIZE = 50
        private const val MAX_TOP_TRACKS = 15          // track IDs for related lookups

        // Early skip threshold: < 20% AND skipped
        private const val EARLY_SKIP_THRESHOLD = 0.20f

        private const val MAX_DISLIKED = 50
        private const val EVENT_RETENTION_MS = 90L * 24 * 60 * 60 * 1000  // 90 Tage
        private const val CLEANUP_EVERY_N_PLAYS = 50
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Read-Modify-Write aufs Profil serialisieren, sonst gehen bei zwei schnellen
    // Play-Events (Skip + neuer Track) Updates verloren
    private val profileMutex = Mutex()

    fun onTrackPlayed(track: Track, event: PlayEvent) {
        scope.launch {
            try {
                repository.savePlayEvent(event)
                profileMutex.withLock {
                    updateProfile(track, event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile", e)
            }
        }
    }

    private suspend fun updateProfile(track: Track, event: PlayEvent) {
        val profile = repository.getTasteProfile()
        val completion = if (event.totalSeconds > 0)
            event.playedSeconds.toFloat() / event.totalSeconds else 0f

        val isEarlySkip = event.skipped && completion < EARLY_SKIP_THRESHOLD

        // Positive weight only when user actually engaged
        val positiveWeight = when {
            isEarlySkip -> 0f
            event.liked -> WEIGHT_LIKED
            completion >= 0.90f -> WEIGHT_FULL
            completion >= 0.70f -> WEIGHT_GOOD
            completion >= 0.40f -> WEIGHT_PARTIAL
            else -> 0f
        }

        // Artists: decay all, then boost current (or skip if early skip)
        val newArtists = if (isEarlySkip) {
            decayMap(profile.favoriteArtists)
        } else {
            updateWeightMap(profile.favoriteArtists, track.channelName, positiveWeight)
        }

        // Genres: same logic
        val newGenres = if (isEarlySkip || track.genre.isBlank()) {
            decayMap(profile.favoriteGenres)
        } else {
            updateWeightMap(profile.favoriteGenres, track.genre, positiveWeight)
        }

        // Disliked artists: add on early skip if artist is not already well-liked
        val currentArtistScore = profile.favoriteArtists[track.channelName] ?: 0f
        val newDisliked = when {
            isEarlySkip && currentArtistScore < 1.0f ->
                // Cap: die ältesten Einträge fliegen raus, sonst wächst das Set unbegrenzt
                (profile.dislikedArtists + track.channelName).let { set ->
                    if (set.size > MAX_DISLIKED) set.drop(set.size - MAX_DISLIKED).toSet() else set
                }
            positiveWeight >= WEIGHT_GOOD ->
                profile.dislikedArtists - track.channelName  // un-dislike if user engages
            else -> profile.dislikedArtists
        }

        // Top track IDs for related lookups: add when liked or fully played
        val newTopTracks = if (event.liked || completion >= 0.90f) {
            (listOf(track.id) + profile.topTrackIds).distinct().take(MAX_TOP_TRACKS)
        } else {
            profile.topTrackIds
        }

        repository.updateTasteProfile(
            profile.copy(
                favoriteArtists = newArtists,
                favoriteGenres = newGenres,
                dislikedArtists = newDisliked,
                topTrackIds = newTopTracks,
                playCount = profile.playCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
        )

        // play_events wächst sonst unbegrenzt (wurde bisher nirgends aufgeräumt)
        if ((profile.playCount + 1) % CLEANUP_EVERY_N_PLAYS == 0) {
            repository.deleteOldPlayEvents(System.currentTimeMillis() - EVENT_RETENTION_MS)
        }
    }

    private fun decayMap(map: Map<String, Float>): Map<String, Float> =
        map.mapValues { it.value * DECAY }

    private fun updateWeightMap(
        current: Map<String, Float>,
        key: String,
        weight: Float
    ): Map<String, Float> {
        if (key.isBlank()) return decayMap(current)
        val mutable = current.toMutableMap()
        mutable.keys.toList().forEach { k -> mutable[k] = (mutable[k] ?: 0f) * DECAY }
        mutable[key] = (mutable[key] ?: 0f) + weight
        return mutable.entries
            .sortedByDescending { it.value }
            .take(MAX_MAP_SIZE)
            .associate { it.key to it.value }
    }

    suspend fun resetProfile() {
        repository.updateTasteProfile(TasteProfile())
    }
}
