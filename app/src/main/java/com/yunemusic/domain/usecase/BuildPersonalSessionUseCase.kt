package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

/** Reads local evidence at click time. Never falls back to trending or screen recommendations. */
class BuildPersonalSessionUseCase @Inject constructor(private val repository: MusicRepository) {
    suspend operator fun invoke(): List<Track> = withContext(Dispatchers.Default) {
        rankSessionTracks(repository.getSavedTracks().first(),
            repository.getLikedTracks().first().map { it.id }.toSet(),
            repository.getPlayHistory().first(), System.currentTimeMillis())
    }
}

/** Current likes dominate; repeated engaged listens help, early skips reduce the score.
 * Listening evidence has a 30-day half-life and is capped so old repeat counts cannot dominate.
 */
internal fun rankSessionTracks(
    tracks: List<Track>, likedIds: Set<String>, history: List<PlayEvent>, now: Long, limit: Int = 30
): List<Track> {
    val evidence = history.groupBy { it.trackId }
    return tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { track ->
        val listening = evidence[track.id].orEmpty().sumOf { event ->
            val completion = if (event.totalSeconds > 0)
                (event.playedSeconds.toDouble() / event.totalSeconds).coerceIn(0.0, 1.0) else 0.0
            val signal = when {
                event.skipped && completion < 0.2 -> -3.0
                completion >= 0.9 -> 2.0
                completion >= 0.7 -> 1.0
                completion >= 0.4 -> 0.25
                else -> 0.0
            }
            val ageDays = (now - event.timestamp).coerceAtLeast(0).toDouble() / 86_400_000
            signal * 0.5.pow(ageDays / 30.0)
        }.coerceIn(-8.0, 8.0)
        track to (listening + if (track.id in likedIds) 10.0 else 0.0)
    }.filter { it.second > 0.0 }
        .sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })
        .take(limit.coerceAtLeast(0)).map { it.first }
}
