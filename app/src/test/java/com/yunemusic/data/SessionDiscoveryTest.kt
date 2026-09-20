package com.yunemusic.data

import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.random.Random

class SessionDiscoveryTest {
    private fun track(id: String, artist: String = "artist-$id") =
        Track(id, "Artist - Song $id", artist, "", 180)
    private fun mix(old: List<Track>, fresh: List<Track>, previous: Set<String> = emptySet(), seed: Int = 42) =
        mixSessionTracks(old, fresh.map { SessionCandidate(it, 1.0) }, old.map { it.id }.toSet(),
            emptySet(), previous, Random(seed))

    @Test fun plentifulCandidatesProduceSeventyPercentDiscoveriesAndInterleaveFavorites() {
        val old = (1..30).map { track("old$it") }
        val result = mix(old, (1..60).map { track("new$it") })
        assertEquals(30, result.size)
        assertEquals(21, result.count { it.id.startsWith("new") })
        assertTrue(result.first().id.startsWith("new"))
        assertTrue(result[3].id.startsWith("old"))
    }

    @Test fun sessionsRotateRatherThanSortingLikesInDatabaseOrder() {
        val old = (1..30).map { track("old$it") }
        val fresh = (1..60).map { track("new$it") }
        assertNotEquals(mix(old, fresh, seed = 1), mix(old, fresh, seed = 2))
        assertNotEquals(old, mix(old, emptyList()))
    }

    @Test fun artistCapsAndSpacingSurviveAnOverrepresentedCandidateSource() {
        val dominant = (1..40).map { track("dominant$it", "one artist") }
        val varied = (1..40).map { track("varied$it", "artist${it % 12}") }
        val result = mix(emptyList(), dominant + varied)
        assertEquals(30, result.size)
        assertTrue(result.groupingBy { it.channelName }.eachCount().values.all { it <= 3 })
        assertTrue(result.zipWithNext().all { (a, b) -> a.channelName != b.channelName })
    }

    @Test fun knownResultsAreNotCountedAsDiscoveriesAndDuplicateVersionsAreRemoved() {
        val old = track("old")
        val fresh = track("new").copy(title = "Artist - A distinctive song (Official Video)")
        val alternate = fresh.copy(id = "alternate", title = "Artist - A distinctive song [Official Audio]")
        val result = mix(listOf(old), listOf(old, fresh, fresh, alternate))
        assertEquals(2, result.size)
        assertEquals(2, result.map { it.id }.distinct().size)
    }

    @Test fun previousSessionPenaltyFavorsUnheardAlternatives() {
        val tracks = (1..80).map { track("song$it") }
        val previous = tracks.take(40).map { it.id }.toSet()
        val result = mix(emptyList(), tracks, previous)
        assertTrue(result.count { it.id !in previous } >= 20)
    }

    @Test fun alternateUploadOfKnownSongIsNotPresentedAsDiscovery() {
        val known = track("known").copy(title = "Artist - Already Heard (Official Video)")
        val reupload = known.copy(id = "other-id", title = "Artist - Already Heard [HD]")
        val result = mix(listOf(known), listOf(reupload, track("new")))
        assertTrue(result.any { it.id == "new" })
        assertFalse(result.any { it.id == "other-id" })
    }

    @Test fun recentlyHeardFavoritesAreDownweighted() {
        val old = (1..80).map { track("old$it") }
        val recent = old.take(40).map { it.id }.toSet()
        val result = mixSessionTracks(old, emptyList(), old.map { it.id }.toSet(), recent,
            emptySet(), Random(42))
        assertTrue(result.count { it.id !in recent } >= 20)
    }

    @Test fun singleArtistStillWorksButCannotFillAnEntireSession() {
        val result = mix((1..20).map { track("old$it", "same") }, emptyList())
        assertEquals(4, result.size)
    }

    @Test fun metadataFilterRejectsUnrelatedShortsAndLongCompilations() {
        val unrelated = track("other").copy(title = "Smart workers building a house", durationSeconds = 45)
        assertFalse(isSessionMusicCandidate(unrelated, setOf("favorite")))
        assertFalse(isSessionMusicCandidate(unrelated.copy(title = "Construction tutorial", durationSeconds = 300), emptySet()))
        assertFalse(isSessionMusicCandidate(track("long").copy(durationSeconds = 7200), emptySet()))
        assertTrue(isSessionMusicCandidate(track("new"), emptySet()))
        assertTrue(isSessionMusicCandidate(track("known", "Favorite - Topic").copy(title = "New track"), setOf("favorite")))
    }

    @Test fun artistSearchRejectsNamesakesButAllowsOfficialChannelsAndReuploads() {
        assertFalse(isSessionArtistMatch(track("wrong", "FRK Music Pro")
            .copy(title = "Sufna (MUSIC VIDEO) | Hamie Kamboh X FRK"), "F.R.K."))
        assertTrue(isSessionArtistMatch(track("right", "FRK - Topic"), "F.R.K."))
        assertTrue(isSessionArtistMatch(track("reupload", "Music collection")
            .copy(title = "F.R.K. - Clip Our Own Wings (Audio)"), "F.R.K."))
        assertTrue(isSessionArtistMatch(track("official", "WuTangClanVEVO"), "Wu-Tang Clan"))
    }

    private class FakeRepository : MusicRepository by (Proxy.newProxyInstance(
        MusicRepository::class.java.classLoader, arrayOf(MusicRepository::class.java)
    ) { _, method, _ -> error("Unexpected call: ${method.name}") } as MusicRepository) {
        var saved = emptyList<Track>()
        var liked = emptyList<Track>()
        var history = emptyList<PlayEvent>()
        var profile = TasteProfile()
        var candidates = emptyList<Track>()
        var cancel = false
        val seeds = mutableListOf<String>()
        override fun getSavedTracks() = flowOf(saved)
        override fun getLikedTracks() = flowOf(liked)
        override fun getPlayHistory() = flowOf(history)
        override suspend fun getTasteProfile() = profile
        override suspend fun getRelatedTracks(videoId: String): Result<List<Track>> {
            if (cancel) throw CancellationException("cancelled")
            synchronized(seeds) { seeds += videoId }
            return Result.success(candidates)
        }
        override suspend fun searchTracks(query: String, pageToken: String?) =
            Result.failure<List<Track>>(java.io.IOException("Search unavailable"))
    }

    @Test fun discoveryUsesFreshDatabaseSeedsAndSurvivesOneFailedSource() = runBlocking {
        val repo = FakeRepository().apply {
            saved = (1..10).map { track("liked$it") }
            liked = saved
            candidates = (1..50).map { track("new$it") }
        }
        val result = BuildPersonalSessionUseCase(repo)()
        assertEquals(30, result.size)
        assertTrue(result.count { it.id.startsWith("new") } >= 21)
        assertTrue(repo.seeds.isNotEmpty())
        assertTrue(repo.seeds.all { it in repo.liked.map { t -> t.id } })
    }

    @Test fun recentSkipsAndDislikedArtistsDoNotEnterTheMix() = runBlocking {
        val repo = FakeRepository().apply {
            saved = listOf(track("liked"))
            liked = saved
            history = listOf(PlayEvent("skipped", System.currentTimeMillis(), 1, 180, true))
            profile = TasteProfile(dislikedArtists = setOf("Bad"))
            candidates = listOf(track("skipped"), track("bad", "Bad - Topic"), track("good"))
        }
        val result = BuildPersonalSessionUseCase(repo)()
        assertTrue(result.any { it.id == "good" })
        assertTrue(result.none { it.id == "skipped" || it.id == "bad" })
    }

    @Test fun cancellationIsNotConvertedIntoLocalPlayback() = runBlocking {
        val repo = FakeRepository().apply { saved = listOf(track("liked")); liked = saved; cancel = true }
        try {
            BuildPersonalSessionUseCase(repo)()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { /* expected */ }
    }
}
