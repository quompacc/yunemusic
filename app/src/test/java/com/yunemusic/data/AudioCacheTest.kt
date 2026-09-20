package com.yunemusic.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import com.yunemusic.data.cache.AudioCacheStore
import com.yunemusic.data.cache.NetworkGuardDataSource
import com.yunemusic.data.cache.RoutingDataSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioCacheTest {
    private lateinit var directory: File
    private lateinit var database: StandaloneDatabaseProvider
    private lateinit var cache: SimpleCache
    private lateinit var store: AudioCacheStore
    private val audio = ByteArray(128) { it.toByte() }
    private var networkAvailable = true
    private var opens = 0
    private var networkBytes = 0
    private val url = "https://audio.example.test/song?expire=1&itag=251"
    private val upstream = DataSource.Factory {
        val bytes = ByteArrayDataSource(audio)
        object : DataSource by bytes {
            override fun open(spec: DataSpec): Long {
                opens++
                if (!networkAvailable) throw IOException("No network")
                return bytes.open(spec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val read = bytes.read(buffer, offset, length)
                if (read > 0) networkBytes += read
                return read
            }
        }
    }

    @Before fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        directory = File(context.cacheDir, "audio-test-${System.nanoTime()}")
        database = StandaloneDatabaseProvider(context)
        reopen()
    }
    private fun reopen(limit: Long = 1024 * 1024) {
        cache = SimpleCache(directory, LeastRecentlyUsedCacheEvictor(limit), database)
        store = AudioCacheStore(cache, upstream)
    }
    @After fun cleanup() { cache.release(); database.close(); directory.deleteRecursively() }

    private fun readAll(source: DataSource, uri: String = url): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            source.open(DataSpec.Builder().setUri(uri).build())
            val buffer = ByteArray(32)
            while (true) {
                val n = source.read(buffer, 0, buffer.size)
                if (n == -1) break
                output.write(buffer, 0, n)
            }
        } finally { source.close() }
        return output.toByteArray()
    }

    @Test fun prefetchedAudioPlaysWithoutNetworkEvenWithExpiredUrl() = runBlocking {
        store.register("song", 1, url)
        store.prefetch(url) { true }
        assertEquals(url, store.completeUrl("song", 1))
        networkAvailable = false
        val previousOpens = opens
        assertArrayEquals(audio, readAll(store.factory.createDataSource()))
        assertEquals(previousOpens, opens)
    }

    @Test fun partialAudioIsNotAdvertisedAsOfflineAndPrefetchOnlyFetchesMissingBytes() = runBlocking {
        store.register("song", 1, url)
        val source = store.factory.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(url).build())
            assertEquals(16, source.read(ByteArray(16), 0, 16))
        } finally { source.close() }
        assertNull(store.completeUrl("song", 1))
        store.prefetch(url) { true }
        assertEquals(url, store.completeUrl("song", 1))
        assertEquals(audio.size, networkBytes)
    }

    @Test fun cacheSurvivesReopeningAndDoesNotMixQualityOrVideoIds() = runBlocking {
        store.register("song", 1, url)
        store.prefetch(url) { true }
        cache.release()
        reopen()
        assertEquals(url, store.completeUrl("song", 1))
        assertNull(store.completeUrl("song", 2))
        assertNull(store.completeUrl("other-song", 1))
    }

    @Test fun disabledPrefetchDoesNotOpenNetwork() = runBlocking {
        store.prefetch(url) { false }
        assertEquals(0, opens)
    }

    @Test fun evictedAudioIsNotReportedAsComplete() = runBlocking {
        cache.release()
        reopen(200)
        store.register("song", 1, url)
        store.prefetch(url) { true }
        val second = "$url&different=1"
        store.register("second", 1, second)
        store.prefetch(second) { true }
        assertTrue(cache.cacheSpace <= 200)
        assertNull(store.completeUrl("song", 1))
        assertEquals(second, store.completeUrl("second", 1))
    }

    @Test fun networkRestrictionIsCheckedAgainDuringTransfer() {
        var allowed = true
        val source = NetworkGuardDataSource(upstream.createDataSource()) { allowed }
        try {
            source.open(DataSpec.Builder().setUri(url).build())
            source.read(ByteArray(8), 0, 8)
            allowed = false
            assertTrue(runCatching { source.read(ByteArray(8), 0, 8) }.exceptionOrNull() is IOException)
            assertEquals(8, networkBytes)
        } finally { source.close() }
    }

    @Test fun localDownloadsBypassCache() {
        var cacheSelected = false
        val source = RoutingDataSource(upstream) { cacheSelected = true; store.factory }
        assertArrayEquals(audio, readAll(source, "file:///private/download.webm"))
        assertFalse(cacheSelected)
    }
}
