package com.yunemusic.data.cache

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** Keys are exact resource URLs: distinct encodings must never share byte ranges. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioCacheStore(private val cache: Cache, upstream: DataSource.Factory) {
    val factory = CacheDataSource.Factory().setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun register(videoId: String, quality: Int, url: String) {
        cache.applyContentMetadataMutations(url, ContentMetadataMutations()
            .set("custom_video", videoId).set("custom_quality", quality.toLong()))
    }

    fun completeUrl(videoId: String, quality: Int): String? = cache.keys.firstOrNull { key ->
        val metadata = cache.getContentMetadata(key)
        val length = ContentMetadata.getContentLength(metadata)
        metadata.get("custom_video", "") == videoId &&
            metadata.get("custom_quality", -1L) == quality.toLong() &&
            length > 0 && cache.isCached(key, 0, length)
    }

    suspend fun prefetch(url: String, allowed: () -> Boolean) = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()
        job.ensureActive()
        if (!allowed()) return@withContext
        val writer = CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(url).build(),
            null) { length, cached, _ ->
            job.ensureActive()
            if (!allowed()) throw IOException("Vorladen pausiert")
            // Don't spend the entire cache/data allowance on a concert or long mix.
            if (length > MAX_PREFETCH_BYTES || cached > MAX_PREFETCH_BYTES)
                throw IOException("Titel zu groß zum automatischen Vorladen")
        }
        try { writer.cache() } finally { writer.cancel() }
    }

    companion object { const val MAX_PREFETCH_BYTES = 20L * 1024 * 1024 }
}
