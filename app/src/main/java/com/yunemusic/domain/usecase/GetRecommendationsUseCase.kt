package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import javax.inject.Inject

class GetRecommendationsUseCase @Inject constructor(
    private val repository: MusicRepository
) {
    suspend operator fun invoke(): Result<List<Track>> = runCatching {
        val profile = repository.getTasteProfile()
        val playedIds = repository.getPlayedTrackIds()
        val candidates = mutableListOf<ScoredTrack>()

        // Strategy 1 – Related to liked/completed tracks (strongest personal signal)
        if (profile.topTrackIds.isNotEmpty()) {
            profile.topTrackIds.take(3).forEach { id ->
                repository.getRelatedTracks(id).getOrElse { emptyList() }
                    .forEach { candidates += ScoredTrack(it, scoreTrack(it, profile), Source.RELATED) }
            }
        }

        // Strategy 2 – Search by top artists
        profile.favoriteArtists.entries
            .sortedByDescending { it.value }
            .take(3)
            .forEach { (artist, _) ->
                repository.searchTracks("$artist music").getOrElse { emptyList() }
                    .forEach { candidates += ScoredTrack(it, scoreTrack(it, profile), Source.ARTIST) }
            }

        // Strategy 3 – Genre exploration (kicks in after 10 plays)
        if (profile.playCount >= 10) {
            profile.favoriteGenres.entries
                .sortedByDescending { it.value }
                .take(2)
                .forEach { (genre, _) ->
                    repository.searchTracks("$genre music").getOrElse { emptyList() }
                        .forEach { candidates += ScoredTrack(it, scoreTrack(it, profile), Source.GENRE) }
                }
        }

        // Strategy 4 – Trending (fallback for new users + discovery layer)
        repository.getTrending().getOrElse { emptyList() }
            .forEach { candidates += ScoredTrack(it, scoreTrack(it, profile), Source.TRENDING) }

        // Filter, deduplicate, sort by score
        val filtered = candidates
            .filter { it.track.id !in playedIds }
            .filter { it.score > -3f }
            .distinctBy { it.track.id }
            .sortedByDescending { it.score }

        // Diversity: max 2 tracks per artist
        applyDiversity(filtered.map { it.track }, maxPerArtist = 2).take(25)
    }

    private fun scoreTrack(track: Track, profile: TasteProfile): Float {
        var score = 0f
        score += (profile.favoriteArtists[track.channelName] ?: 0f) * 3f
        score += (profile.favoriteGenres[track.genre] ?: 0f) * 2f
        if (track.channelName in profile.dislikedArtists) score -= 5f
        return score
    }

    private fun applyDiversity(tracks: List<Track>, maxPerArtist: Int): List<Track> {
        val result = mutableListOf<Track>()
        val countPerArtist = mutableMapOf<String, Int>()
        for (track in tracks) {
            val count = countPerArtist.getOrDefault(track.channelName, 0)
            if (count < maxPerArtist) {
                result.add(track)
                countPerArtist[track.channelName] = count + 1
            }
        }
        return result
    }

    private data class ScoredTrack(val track: Track, val score: Float, val source: Source)
    private enum class Source { RELATED, ARTIST, GENRE, TRENDING }
}
