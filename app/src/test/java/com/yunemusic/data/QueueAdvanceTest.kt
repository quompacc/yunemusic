package com.yunemusic.data

import com.yunemusic.domain.model.*
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.PlayTrackUseCase
import com.yunemusic.service.QueueManager
import com.yunemusic.service.TasteAnalyzer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class QueueAdvanceTest {
    private class Repository : MusicRepository by (Proxy.newProxyInstance(
        MusicRepository::class.java.classLoader, arrayOf(MusicRepository::class.java)
    ) { _, method, _ -> error("Unexpected call: ${method.name}") } as MusicRepository) {
        override suspend fun isTrackLiked(trackId: String) = false
        override suspend fun getDownloadFilePath(videoId: String): String? = null
        override suspend fun saveTrack(track: Track) {}
        override suspend fun getStreamUrl(videoId: String) = Result.success("https://example.test/$videoId")
        override suspend fun getRelatedTracks(videoId: String) = Result.success(emptyList<Track>())
        override suspend fun getTrending() = Result.success(emptyList<Track>())
        override suspend fun savePlayEvent(event: PlayEvent) {}
        override suspend fun getTasteProfile() = TasteProfile()
        override suspend fun updateTasteProfile(profile: TasteProfile) {}
    }
    private class Player : QueueManager.PlayerBridge {
        val played = mutableListOf<String>()
        var repeats = 0
        override fun playTrack(track: Track, streamUrl: String) { played += track.id }
        override fun seekToStart() { repeats++ }
        override fun resume() {}
        override fun getPositionMs() = 100_000L
    }
    private fun fixture(test: (QueueManager, Player) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        val player = Player()
        val manager = QueueManager(scope, repo, PlayTrackUseCase(repo), TasteAnalyzer(repo), player)
        try { test(manager, player) } finally { scope.cancel() }
    }
    private val tracks = listOf("one", "two", "three").map { Track(it, it, "Artist", "", 100) }

    @Test fun queueAdvancesWithoutRepeatOrShuffleEnabled() = fixture { queue, player ->
        queue.playQueue(tracks)
        queue.onTrackEnded()
        assertEquals(listOf("one", "two"), player.played)
        assertEquals(1, queue.currentIndex.value)
        queue.onTrackEnded()
        assertEquals(listOf("one", "two", "three"), player.played)
    }

    @Test fun repeatAllWrapsToFirstTrack() = fixture { queue, player ->
        queue.toggleRepeat()
        queue.playQueue(tracks, 2)
        queue.onTrackEnded()
        assertEquals(listOf("three", "one"), player.played)
    }

    @Test fun repeatOneIntentionallyDoesNotAdvance() = fixture { queue, player ->
        queue.toggleRepeat()
        queue.toggleRepeat()
        queue.playQueue(tracks)
        queue.onTrackEnded()
        assertEquals(listOf("one"), player.played)
        assertEquals(1, player.repeats)
    }

    @Test fun manuallyQueuedTrackAdvancesAutomatically() = fixture { queue, player ->
        queue.playTrack(tracks.first())
        queue.addToQueue(tracks[1])
        queue.onTrackEnded()
        assertEquals(listOf("one", "two"), player.played)
    }
}
