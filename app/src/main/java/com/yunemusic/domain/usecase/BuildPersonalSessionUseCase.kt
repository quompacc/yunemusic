package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.ln
import kotlin.random.Random

/** Local evidence chooses the discovery seeds; the screen's recommendations are never reused. */
@Singleton
class BuildPersonalSessionUseCase @Inject constructor(private val repository: MusicRepository) {
    private var previousSessionIds = emptySet<String>()

    suspend operator fun invoke(): List<Track> = withContext(Dispatchers.IO) {
        val saved = repository.getSavedTracks().first()
        val liked = repository.getLikedTracks().first()
        val likedIds = liked.map { it.id }.toSet()
        val history = repository.getPlayHistory().first()
        val now = System.currentTimeMillis()
        val profile = repository.getTasteProfile()
        val blockedArtists = profile.dislikedArtists.map(::sessionArtistKey).toSet() -
            liked.map { sessionArtistKey(it.channelName) }.toSet()
        val skipped = history.groupBy { it.trackId }.filterValues { events ->
            val latest = events.maxBy { it.timestamp }
            latest.skipped && latest.totalSeconds > 0 &&
                latest.playedSeconds.toDouble() / latest.totalSeconds < 0.2 &&
                now - latest.timestamp < 14L * 86_400_000
        }.keys
        val familiar = rankSessionTracks(saved + liked, likedIds, history, now, 100)
            .filter { it.id !in skipped && sessionArtistKey(it.channelName) !in blockedArtists }
        if (familiar.isEmpty()) return@withContext emptyList()

        // Rotate seed songs and artists rather than querying the same top like every time.
        val seeds = familiar.take(40).mapIndexed { index, track ->
            // Weighted sampling: strong evidence wins more often without a fixed top-five order.
            track to -ln(Random.nextDouble().coerceAtLeast(1e-12)) * (index + 3)
        }.sortedBy { it.second }.map { it.first }
            .distinctBy { sessionArtistKey(it.channelName) }.take(5)
        val artists = seeds.map { sessionArtistKey(it.channelName) }.filter { it.isNotBlank() }.toSet()
        val knownIds = (saved.map { it.id } + likedIds + history.map { it.trackId }).toSet()
        val gate = Semaphore(3)
        suspend fun fetch(block: suspend () -> Result<List<Track>>): List<Track> = gate.withPermit {
            // Repository extraction may return cancellation inside Result; recheck our Job.
            withTimeoutOrNull(8_000) {
                val result = try { block().getOrDefault(emptyList()) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                ensureActive()
                result
            }.orEmpty()
        }
        val candidates = coroutineScope {
            val related = seeds.map { seed -> async {
                fetch { repository.getRelatedTracks(seed.id) }
                    .filter { isSessionMusicCandidate(it, artists) }
                    .map { SessionCandidate(it, 1.0) }
            } }
            val byArtist = seeds.take(3).filter { it.channelName.isNotBlank() }.map { seed -> async {
                fetch { repository.searchTracks("\"${seed.channelName}\" music") }
                    .filter { isSessionArtistMatch(it, seed.channelName) && isSessionMusicCandidate(it, artists) }
                    .map { SessionCandidate(it, 1.4) }
            } }
            (related + byArtist).awaitAll().flatten()
        }.filter { it.track.id !in skipped && sessionArtistKey(it.track.channelName) !in blockedArtists }

        val recentIds = history.filter { now - it.timestamp < 24L * 60 * 60 * 1000 }
            .map { it.trackId }.toSet()
        mixSessionTracks(familiar, candidates, knownIds, recentIds, previousSessionIds, Random.Default,
            knownTracks = saved + liked)
            .also { previousSessionIds = it.map { track -> track.id }.toSet() }
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
