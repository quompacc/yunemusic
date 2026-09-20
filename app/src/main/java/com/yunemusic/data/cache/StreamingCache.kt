package com.yunemusic.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.yunemusic.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One process-wide cache, separate from Room, downloads and backup data. */
@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamingCache @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: UserPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wifiOnly = preferences.wifiOnly.stateIn(scope, SharingStarted.Eagerly, true)
    val prefetchEnabled = preferences.prefetchEnabled.stateIn(scope, SharingStarted.Eagerly, false)
    private val network = context.getSystemService(ConnectivityManager::class.java)
    private val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(8_000).setReadTimeoutMs(8_000)
        .setUserAgent("YuneMusic")
    private val upstream = DefaultDataSource.Factory(context, DataSource.Factory {
        NetworkGuardDataSource(http.createDataSource()) { networkAllowed() }
    })

    private val store: AudioCacheStore? by lazy {
        // Disk/cache failure must not prevent ordinary streaming.
        runCatching {
            AudioCacheStore(SimpleCache(File(context.cacheDir, "audio_streams"),
                LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
                StandaloneDatabaseProvider(context)), upstream)
        }.getOrNull()
    }

    val dataSourceFactory = DataSource.Factory {
        // Preserve normal file playback for explicit downloads; don't cache them twice.
        RoutingDataSource(upstream, { store?.factory ?: upstream })
    }

    fun networkAllowed(): Boolean {
        val caps = network.getNetworkCapabilities(network.activeNetwork ?: return false) ?: return false
        return !wifiOnly.value || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun completeUrl(id: String, quality: Int): String? = withContext(Dispatchers.IO) {
        runCatching { store?.completeUrl(id, quality) }.getOrNull()
    }

    suspend fun register(id: String, quality: Int, url: String) = withContext(Dispatchers.IO) {
        runCatching { store?.register(id, quality, url) }
        Unit
    }

    suspend fun prefetch(url: String, playbackReady: () -> Boolean) {
        store?.prefetch(url) { prefetchEnabled.value && networkAllowed() && playbackReady() }
    }
}

/** Rechecks the WLAN restriction on network reads, including after a network switch. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NetworkGuardDataSource(
    private val delegate: DataSource, private val allowed: () -> Boolean
) : DataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        if (!allowed()) throw IOException("Netzwerk durch WLAN-Einstellung gesperrt")
        return delegate.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!allowed()) throw IOException("Netzwerk durch WLAN-Einstellung gesperrt")
        return delegate.read(buffer, offset, length)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RoutingDataSource(
    private val local: DataSource.Factory, private val cached: () -> DataSource.Factory
) : DataSource {
    private var source: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()
    override fun addTransferListener(listener: TransferListener) {
        listeners += listener
        source?.addTransferListener(listener)
    }
    override fun open(dataSpec: DataSpec): Long {
        val factory = if (dataSpec.uri.scheme in listOf("http", "https")) cached() else local
        val selected = factory.createDataSource()
        source = selected
        listeners.forEach(selected::addTransferListener)
        return selected.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) =
        requireNotNull(source).read(buffer, offset, length)
    override fun getUri(): Uri? = source?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = source?.responseHeaders ?: emptyMap()
    override fun close() { try { source?.close() } finally { source = null } }
}
