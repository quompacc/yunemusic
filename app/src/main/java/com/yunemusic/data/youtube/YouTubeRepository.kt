package com.yunemusic.data.youtube

import android.content.Context
import android.util.Log
import com.yunemusic.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.model.YouTubePlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "YouTubeRepository"
        private const val YT_BASE = "https://www.youtube.com/watch?v="
    }

    suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                null
            )
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
                .filter { it.id.isNotBlank() }
        }.onFailure { Log.e(TAG, "search failed: $query", it) }
    }

    suspend fun getRelatedTracks(videoId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = StreamInfo.getInfo("$YT_BASE$videoId")
            logStreamWarnings(videoId, streamInfo)
            streamInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
                .filter { it.id.isNotBlank() }
        }.onFailure { Log.e(TAG, "getRelatedTracks failed: $videoId", it) }
    }

    /**
     * Wählt den besten Audio-Stream für die gewünschte Qualitätsstufe.
     *
     * Qualitätsstufen:
     *   0 = Niedrig  → AAC/OPUS ~128kbps
     *   1 = Mittel   → OPUS ~130-160kbps oder AAC ~256kbps
     *   2 = Hoch     → höchste verfügbare Qualität, OPUS bevorzugt
     *
     * OPUS wird bei gleicher/niedrigerer Bitrate gegenüber AAC bevorzugt,
     * da es bei ~160kbps bessere Qualität liefert als AAC bei 256kbps.
     */
    suspend fun getAudioStreamUrl(videoId: String, qualityIndex: Int = 2): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = StreamInfo.getInfo("$YT_BASE$videoId")
            logStreamWarnings(videoId, streamInfo)
            val streams = streamInfo.audioStreams

            val best = selectBestStream(streams, qualityIndex)
                ?: error(context.getString(R.string.error_no_audio_stream))

            Log.d(TAG, "Selected stream: format=${best.format?.name}, " +
                    "bitrate=${best.averageBitrate}bps, deliveryMethod=${best.deliveryMethod}")
            best.content
        }.onFailure { Log.e(TAG, "getAudioStreamUrl failed: $videoId", it) }
    }

    private fun selectBestStream(streams: List<AudioStream>, qualityIndex: Int): AudioStream? {
        if (streams.isEmpty()) return null

        // Sortiere: OPUS zuerst, dann nach Bitrate absteigend
        val sorted = streams.sortedWith(
            compareByDescending<AudioStream> { isOpus(it) }
                .thenByDescending { it.averageBitrate }
        )

        val targetBps = when (qualityIndex) {
            0 -> 128_000
            1 -> 256_000
            else -> Int.MAX_VALUE
        }

        // Versuche: Höchste Bitrate ≤ Ziel, OPUS bevorzugt
        val underTarget = sorted.filter { it.averageBitrate <= targetBps }
        return underTarget.firstOrNull()
            // Fallback: Höchste verfügbare Qualität (OPUS bevorzugt)
            ?: sorted.firstOrNull()
    }

    /** Prüft ob ein Stream das OPUS-Format verwendet */
    private fun isOpus(stream: AudioStream): Boolean {
        val name = stream.format?.name?.lowercase() ?: return false
        return name.contains("opus") || name.contains("webm") && stream.averageBitrate > 0
    }

    suspend fun getTrackInfo(videoId: String): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = StreamInfo.getInfo("$YT_BASE$videoId")
            Track(
                id = videoId,
                title = streamInfo.name,
                channelName = streamInfo.uploaderName ?: "",
                thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url ?: "",
                durationSeconds = streamInfo.duration.coerceAtLeast(0).toInt()
            )
        }.onFailure { Log.e(TAG, "getTrackInfo failed: $videoId", it) }
    }

    suspend fun getTrending(): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val queries = listOf("trending music $year", "viral songs $year", "best music $year")
            val extractor = YouTube.getSearchExtractor(
                queries.random(),
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                null
            )
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
                .filter { it.id.isNotBlank() }
        }.onFailure { Log.e(TAG, "getTrending failed", it) }
    }

    // ── YouTube Playlist-Suche ─────────────────────────────────────────────

    suspend fun searchPlaylists(query: String): Result<List<YouTubePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.PLAYLISTS),
                null
            )
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                .map { it.toYouTubePlaylist() }
        }.onFailure { Log.e(TAG, "searchPlaylists failed: $query", it) }
    }

    suspend fun getPlaylistTracks(playlistUrl: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = YouTube.getPlaylistExtractor(playlistUrl)
            extractor.fetchPage()
            // Nur die erste Seite laden (meist 100 Tracks)
            val page = extractor.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
                .filter { it.id.isNotBlank() }
        }.onFailure { Log.e(TAG, "getPlaylistTracks failed: $playlistUrl", it) }
    }

    private fun logStreamWarnings(videoId: String, streamInfo: StreamInfo) {
        streamInfo.errors.forEachIndexed { i, err ->
            Log.w(TAG, "Stream warning[$i] for $videoId: ${err.message}")
        }
    }
}

private fun org.schabi.newpipe.extractor.playlist.PlaylistInfoItem.toYouTubePlaylist(): YouTubePlaylist {
    val playlistId = url?.let { u ->
        u.substringAfter("list=", "")
            .substringBefore("&")
            .takeIf { it.isNotBlank() }
    } ?: ""

    return YouTubePlaylist(
        id = playlistId,
        title = name ?: "",
        channelName = uploaderName ?: "",
        thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
        trackCount = streamCount.coerceAtLeast(0).toInt(),
        url = url ?: ""
    )
}

fun StreamInfoItem.toTrack(): Track {
    // Robust URL-Parsing: Video-ID extrahien
    val videoId = url?.let { u ->
        u.substringAfter("v=", "")
            .substringBefore("&")
            .takeIf { it.isNotBlank() && it.length >= 11 }
    } ?: ""

    return Track(
        id = videoId,
        title = name ?: "",
        channelName = uploaderName ?: "",
        thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
        durationSeconds = duration.coerceAtLeast(0).toInt()
    )
}
