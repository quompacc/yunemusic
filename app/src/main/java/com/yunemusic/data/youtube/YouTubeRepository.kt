package com.yunemusic.data.youtube

import android.util.Log
import com.yunemusic.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor() {

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
        }.onFailure { Log.e(TAG, "search failed: $query", it) }
    }

    suspend fun getRelatedTracks(videoId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = StreamInfo.getInfo("$YT_BASE$videoId")
            logStreamWarnings(videoId, streamInfo)
            streamInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
        }.onFailure { Log.e(TAG, "getRelatedTracks failed: $videoId", it) }
    }

    suspend fun getAudioStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = StreamInfo.getInfo("$YT_BASE$videoId")
            logStreamWarnings(videoId, streamInfo)
            streamInfo.audioStreams
                .maxByOrNull { it.averageBitrate }
                ?.content
                ?: error("No audio stream found for $videoId")
        }.onFailure { Log.e(TAG, "getAudioStreamUrl failed: $videoId", it) }
    }

    suspend fun getTrackInfo(videoId: String): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val streamInfo = StreamInfo.getInfo("$YT_BASE$videoId")
            Track(
                id = videoId,
                title = streamInfo.name,
                channelName = streamInfo.uploaderName ?: "",
                thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url ?: "",
                durationSeconds = streamInfo.duration.toInt()
            )
        }.onFailure { Log.e(TAG, "getTrackInfo failed: $videoId", it) }
    }

    suspend fun getTrending(): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val kioskList = YouTube.kioskList
            val trending = kioskList.getExtractorById("Trending", null)
            trending.fetchPage()
            trending.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
        }.onFailure { Log.e(TAG, "getTrending failed", it) }
    }

    private fun logStreamWarnings(videoId: String, streamInfo: StreamInfo) {
        streamInfo.errors.forEachIndexed { i, err ->
            Log.w(TAG, "Stream warning[$i] for $videoId: ${err.message}")
        }
    }
}

fun StreamInfoItem.toTrack() = Track(
    id = url.substringAfter("v=").substringBefore("&"),
    title = name,
    channelName = uploaderName ?: "",
    thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
    durationSeconds = duration.toInt()
)
