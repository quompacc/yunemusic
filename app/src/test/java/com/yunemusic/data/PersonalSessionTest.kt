package com.yunemusic.data

import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.BuildPersonalSessionUseCase
import com.yunemusic.domain.usecase.rankSessionTracks
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PersonalSessionTest {
    private val now = 100L * 86_400_000
    private fun track(id: String) = Track(id = id, title = id, channelName = "Artist",
        thumbnailUrl = "", durationSeconds = 100)
    private fun event(id: String, seconds: Int = 100, skipped: Boolean = false, ageDays: Int = 0) =
        PlayEvent(id, now - ageDays * 86_400_000L, seconds, 100, skipped)

    @Test fun likesOutrankRepeatedPlaysAndEarlySkipsAreExcluded() {
        val result = rankSessionTracks(listOf(track("repeat"), track("liked"), track("skip")),
            setOf("liked"), List(20) { event("repeat") } + event("skip", 5, true), now)
        assertEquals(listOf("liked", "repeat"), result.map { it.id })
    }

    @Test fun recentEngagementOutranksOldEngagement() {
        val result = rankSessionTracks(listOf(track("old"), track("new")), emptySet(),
            listOf(event("old", ageDays = 60), event("new")), now)
        assertEquals(listOf("new", "old"), result.map { it.id })
    }

    @Test fun emptyEvidenceDoesNotPlayArbitrarySavedTracks() {
        assertTrue(rankSessionTracks(listOf(track("unheard")), emptySet(), emptyList(), now).isEmpty())
    }

    @Test fun duplicatesAndBlankIdsAreRemovedAndQueueIsBounded() {
        val tracks = (1..40).map { track("song$it") }
        val result = rankSessionTracks(tracks + tracks + track(""), tracks.map { it.id }.toSet(), emptyList(), now)
        assertEquals(30, result.size)
        assertEquals(30, result.map { it.id }.distinct().size)
    }

    @Test fun removedLikeIsNotRestoredByHistoricalLikeFlag() {
        val pastLike = event("unliked", 0).copy(liked = true)
        assertTrue(rankSessionTracks(listOf(track("unliked")), emptySet(), listOf(pastLike), now).isEmpty())
    }

    @Test fun sessionReadsDatabaseFreshAndFallsBackLocallyWithoutTrending() = runBlocking {
        var liked = listOf(track("first"))
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val repository = Proxy.newProxyInstance(MusicRepository::class.java.classLoader,
            arrayOf(MusicRepository::class.java)) { _, method, _ ->
            val name = method.name.substringBefore('-') // Kotlin Result-returning methods are mangled.
            calls += name
            when (name) {
                "getSavedTracks" -> flowOf(listOf(track("first"), track("second")))
                "getLikedTracks" -> flowOf(liked)
                "getPlayHistory" -> flowOf(emptyList<PlayEvent>())
                "getTasteProfile" -> TasteProfile()
                "getRelatedTracks", "searchTracks" -> emptyList<Track>() // Result's JVM representation
                else -> error("Session must not call ${method.name}")
            }
        } as MusicRepository
        val session = BuildPersonalSessionUseCase(repository)
        assertEquals(listOf("first"), session().map { it.id })
        liked = listOf(track("second"))
        assertEquals(listOf("second"), session().map { it.id })
        assertEquals(2, calls.count { it == "getSavedTracks" })
        assertTrue("getTrending" !in calls)
        assertTrue("getRelatedTracks" in calls)
    }
}
