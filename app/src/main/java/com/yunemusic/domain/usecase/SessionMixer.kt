package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.Track
import java.util.Locale
import kotlin.random.Random

internal data class SessionCandidate(val track: Track, val affinity: Double)

internal fun sessionArtistKey(name: String): String = name.lowercase(Locale.ROOT)
    .removeSuffix(" - topic").removeSuffix("vevo").trim()

internal fun isSessionArtistMatch(track: Track, artist: String): Boolean {
    fun identity(value: String) = sessionArtistKey(value).replace(Regex("[^\\p{L}\\p{N}]"), "")
    val expected = identity(artist)
    if (expected.isBlank()) return false
    if (identity(track.channelName) == expected) return true
    // A reupload can name the performer before the song title. A mere acronym
    // somewhere in the text (e.g. F.R.K. vs FRK Music Pro) is not enough.
    val performer = track.title.split(Regex("\\s[-–—]\\s"), limit = 2)
    return performer.size == 2 && identity(performer.first()) == expected
}

private val musicCue = Regex("(?i)\\b(music|song|official|audio|lyrics?|remix|mix|live|acoustic|instrumental|rave|techno|house|jazz|rock|rap|metal|soundtrack|feat|ft)\\b|\\s[-–—]\\s")

/** Related results may include tutorials/Shorts. Require music evidence and song length.
 * This is a metadata heuristic, not a guarantee that YouTube has classified the video as music.
 */
internal fun isSessionMusicCandidate(track: Track, seedArtists: Set<String>): Boolean =
    track.id.isNotBlank() && track.durationSeconds in 60..900 &&
        (sessionArtistKey(track.channelName) in seedArtists || musicCue.containsMatchIn(track.title))

private fun songKey(track: Track): String {
    val title = track.title.lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    return if (title.length >= 12) title else "${sessionArtistKey(track.channelName)}:$title"
}

/** Aim for 7 discoveries / 3 familiar songs per ten slots, starting with discovery.
 * Soft novelty penalties keep repeated sessions fresh; artist caps and spacing are hard.
 * When discovery fails, return a varied local fallback rather than inventing new tracks.
 */
internal fun mixSessionTracks(
    familiar: List<Track>, discoveries: List<SessionCandidate>, knownIds: Set<String>,
    recentIds: Set<String>, previousSessionIds: Set<String>, random: Random, limit: Int = 30,
    knownTracks: List<Track> = familiar
): List<Track> {
    fun weighted(candidate: SessionCandidate): SessionCandidate {
        var weight = candidate.affinity
        if (candidate.track.id in previousSessionIds) weight *= 0.12
        if (candidate.track.id in recentIds) weight *= 0.2
        return candidate.copy(affinity = weight.coerceAtLeast(0.001))
    }
    val knownSongs = knownTracks.map(::songKey).toSet()
    val fresh = discoveries.filter {
        it.track.id !in knownIds && it.track.id.isNotBlank() && songKey(it.track) !in knownSongs
    }
        .groupBy { it.track.id }.map { (_, entries) -> entries.maxBy { it.affinity } }
        .map(::weighted).toMutableList()
    val old = familiar.filter { it.id.isNotBlank() }.distinctBy { it.id }
        .mapIndexed { index, track -> weighted(SessionCandidate(track, 1.0 + 1.0 / (index + 1))) }
        .toMutableList()
    val artists = (fresh + old).map { sessionArtistKey(it.track.channelName) }.distinct().size
    val maxPerArtist = if (artists >= 5) 3 else 4
    val result = mutableListOf<Track>()
    val usedIds = mutableSetOf<String>()
    val usedSongs = mutableSetOf<String>()
    val counts = mutableMapOf<String, Int>()

    fun choose(pool: MutableList<SessionCandidate>): Track? {
        val eligible = pool.filter {
            val artist = sessionArtistKey(it.track.channelName)
            it.track.id !in usedIds && songKey(it.track) !in usedSongs &&
                counts.getOrDefault(artist, 0) < maxPerArtist
        }
        val spaced = eligible.filter { sessionArtistKey(it.track.channelName) !=
            result.lastOrNull()?.let { last -> sessionArtistKey(last.channelName) } }
        // A single-artist taste can still play, but don't stack artists when alternatives exist.
        val choices = spaced.ifEmpty { if (artists <= 1) eligible else emptyList() }
        if (choices.isEmpty()) return null
        var draw = random.nextDouble() * choices.sumOf { it.affinity }
        val selected = choices.firstOrNull { draw -= it.affinity; draw <= 0 } ?: choices.last()
        pool.remove(selected)
        return selected.track
    }
    repeat(limit.coerceAtLeast(0)) { slot ->
        val preferFresh = slot % 10 !in setOf(3, 6, 9)
        val track = if (preferFresh) choose(fresh) ?: choose(old) else choose(old) ?: choose(fresh)
        if (track != null) {
            result += track
            usedIds += track.id
            usedSongs += songKey(track)
            val artist = sessionArtistKey(track.channelName)
            counts[artist] = counts.getOrDefault(artist, 0) + 1
        }
    }
    return result
}
