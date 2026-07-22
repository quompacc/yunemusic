package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class GetRecommendationsUseCase @Inject constructor(
    private val repository: MusicRepository
) {
    suspend operator fun invoke(): Result<List<Track>> = runCatching {
        val profile = repository.getTasteProfile()
        val playedIds = repository.getPlayedTrackIds()

        // Alle Strategien parallel laden (vorher bis zu 9 sequentielle Netzwerk-Calls,
        // 10–20 s Ladezeit für den Empfehlungs-Screen)
        val candidates = coroutineScope {
            val related = profile.topTrackIds.take(3).map { id ->
                async {
                    repository.getRelatedTracks(id).getOrElse { emptyList() }
                        .map { ScoredTrack(it, scoreTrack(it, profile), Source.RELATED) }
                }
            }
            val artists = profile.favoriteArtists.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { (artist, _) ->
                    async {
                        repository.searchTracks("$artist music").getOrElse { emptyList() }
                            .map { ScoredTrack(it, scoreTrack(it, profile), Source.ARTIST) }
                    }
                }
            val genres = if (profile.playCount >= 10) {
                profile.favoriteGenres.entries
                    .sortedByDescending { it.value }
                    .take(2)
                    .map { (genre, _) ->
                        async {
                            repository.searchTracks("$genre music").getOrElse { emptyList() }
                                .map { ScoredTrack(it, scoreTrack(it, profile), Source.GENRE) }
                        }
                    }
            } else emptyList()
            val trending = async {
                repository.getTrending().getOrElse { emptyList() }
                    .map { ScoredTrack(it, scoreTrack(it, profile), Source.TRENDING) }
            }

            (related + artists + genres + trending).awaitAll().flatten()
        }

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
