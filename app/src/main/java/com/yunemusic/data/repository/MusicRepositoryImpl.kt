package com.yunemusic.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.yunemusic.data.local.dao.DownloadDao
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.PlaylistDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.local.entities.DownloadEntity
import com.yunemusic.data.local.entities.PlaylistEntity
import com.yunemusic.data.local.entities.toDomain
import com.yunemusic.data.local.entities.toEntity
import com.yunemusic.data.local.entities.toPlaylistTrack
import com.yunemusic.data.local.entities.toTrack
import com.yunemusic.data.preferences.UserPreferences
import com.yunemusic.data.youtube.YouTubeRepository
import com.yunemusic.domain.model.DownloadState
import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.Playlist
import com.yunemusic.domain.model.TasteProfile
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.model.YouTubePlaylist
import com.yunemusic.domain.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeRepository: YouTubeRepository,
    private val trackDao: TrackDao,
    private val playEventDao: PlayEventDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val userPreferences: UserPreferences
) : MusicRepository {

    companion object {
        private const val TAG = "MusicRepositoryImpl"

        // Tolerant gegenüber Schema-Änderungen des TasteProfile zwischen App-Versionen —
        // sonst wird das gelernte Profil bei jedem Feld-Umbau still zurückgesetzt
        private val json = Json { ignoreUnknownKeys = true }
    }

    // Ein Client für alle Downloads (statt pro Download eigener Pool/Threads)
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun searchTracks(query: String, pageToken: String?): Result<List<Track>> =
        youTubeRepository.search(query)

    override suspend fun getTrackDetails(videoId: String): Result<Track> =
        youTubeRepository.getTrackInfo(videoId)

    override suspend fun getStreamUrl(videoId: String): Result<String> {
        val wifiOnly = userPreferences.wifiOnly.firstOrNull() ?: false
        if (wifiOnly && !isOnWifi()) {
            return Result.failure(Exception("Kein WLAN verfügbar. Streaming nur über WLAN."))
        }
        val quality = userPreferences.audioQuality.firstOrNull() ?: 1
        return youTubeRepository.getAudioStreamUrl(videoId, quality)
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun getPlayHistory(): Flow<List<PlayEvent>> =
        playEventDao.getAllPlayEvents().map { it.map { e -> e.toDomain() } }

    override suspend fun savePlayEvent(event: PlayEvent) {
        playEventDao.insertEvent(event.toEntity())
    }

    override suspend fun deleteOldPlayEvents(olderThanMs: Long) {
        playEventDao.deleteOldEvents(olderThanMs)
    }

    override suspend fun getTasteProfile(): TasteProfile {
        val stored = userPreferences.tasteProfileJson.firstOrNull() ?: ""
        return if (stored.isBlank()) TasteProfile()
        else runCatching { json.decodeFromString<TasteProfile>(stored) }
            .onFailure { Log.w(TAG, "TasteProfile-JSON nicht lesbar, setze zurück", it) }
            .getOrDefault(TasteProfile())
    }

    override suspend fun updateTasteProfile(profile: TasteProfile) {
        userPreferences.setTasteProfileJson(json.encodeToString(profile))
    }

    override fun getLikedTracks(): Flow<List<Track>> =
        trackDao.getLikedTracks().map { it.map { e -> e.toDomain() } }

    override suspend fun likeTrack(track: Track) {
        // Atomar statt Check-then-Act: IGNORE-Insert + Update erhält vorhandene
        // Metadaten (addedAt etc.), REPLACE würde sie überschreiben
        trackDao.insertTrackIfAbsent(track.toEntity(isLiked = true))
        trackDao.setLiked(track.id, true)
    }

    override suspend fun unlikeTrack(trackId: String) {
        trackDao.setLiked(trackId, false)
    }

    override suspend fun isTrackLiked(trackId: String): Boolean =
        trackDao.isLiked(trackId) ?: false

    override suspend fun saveTrack(track: Track) {
        if (track.id.isBlank()) return
        trackDao.insertTrackIfAbsent(track.toEntity())
    }

    override fun getRecentTracks(): Flow<List<Track>> =
        trackDao.getRecentTracks().map { it.map { e -> e.toDomain() } }

    override suspend fun getRelatedTracks(videoId: String): Result<List<Track>> =
        youTubeRepository.getRelatedTracks(videoId)

    override suspend fun getTrending(): Result<List<Track>> =
        youTubeRepository.getTrending()

    override suspend fun getPlayedTrackIds(): Set<String> =
        playEventDao.getAllPlayedTrackIds().toSet()

    override fun getPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylistsWithCount().map { list -> list.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun deletePlaylist(id: Long) =
        playlistDao.deletePlaylist(id)

    override suspend fun addTrackToPlaylist(playlistId: Long, track: Track) =
        playlistDao.addTrack(track.toPlaylistTrack(playlistId))

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) =
        playlistDao.removeTrack(playlistId, trackId)

    override fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> =
        playlistDao.getTracksForPlaylist(playlistId).map { it.map { e -> e.toTrack() } }

    override suspend fun searchYouTubePlaylists(query: String): Result<List<YouTubePlaylist>> =
        youTubeRepository.searchPlaylists(query)

    override suspend fun getYouTubePlaylistTracks(playlistUrl: String): Result<List<Track>> =
        youTubeRepository.getPlaylistTracks(playlistUrl)

    // ── Downloads ────────────────────────────────────────────────────────

    override fun getDownloadedTracks(): Flow<List<Track>> =
        downloadDao.getAllDownloads().map { list -> list.map { it.toTrack() } }

    override suspend fun isTrackDownloaded(videoId: String): Boolean =
        downloadDao.isDownloaded(videoId)

    override suspend fun getDownloadFilePath(videoId: String): String? =
        downloadDao.getFilePath(videoId)

    override suspend fun downloadTrack(track: Track): Result<String> {
        val states = downloadTrackWithProgress(track).toList()
        val last = states.lastOrNull()
        return when (last) {
            is DownloadState.Completed -> Result.success(last.filePath)
            is DownloadState.Failed -> Result.failure(Exception(last.error))
            else -> Result.failure(Exception("Download nicht abgeschlossen"))
        }
    }

    override fun downloadTrackWithProgress(track: Track): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.GettingUrl)

            // "Nur WLAN" gilt auch für Downloads, nicht nur fürs Streaming
            val wifiOnly = userPreferences.wifiOnly.firstOrNull() ?: false
            if (wifiOnly && !isOnWifi()) {
                emit(DownloadState.Failed("Kein WLAN verfügbar. Downloads nur über WLAN."))
                return@flow
            }

            val quality = userPreferences.audioQuality.firstOrNull() ?: 1
            val streamResult = youTubeRepository.getAudioStreamUrl(track.id, quality)
            val streamUrl = streamResult.getOrElse { error ->
                emit(DownloadState.Failed("Stream-URL Fehler: ${error.message}"))
                return@flow
            }

            if (streamUrl.isBlank()) {
                emit(DownloadState.Failed("Leere Stream-URL"))
                return@flow
            }

            Log.d(TAG, "Stream-URL erhalten, starte Download: ${track.title}")

            val dir = File(context.filesDir, "downloads")
            if (!dir.exists()) dir.mkdirs()
            val tempFile = File(dir, "${track.id}.webm.tmp")
            val finalFile = File(dir, "${track.id}.webm")
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                response.close()
                emit(DownloadState.Failed("HTTP ${response.code} – $errBody"))
                return@flow
            }

            val responseBody = response.body ?: run {
                response.close()
                emit(DownloadState.Failed("Leere Antwort vom Server"))
                return@flow
            }

            val contentLength = responseBody.contentLength()
            val mbTotal = if (contentLength > 0) contentLength / 1048576f else -1f

            var totalRead = 0L
            responseBody.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastEmitMs = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 200) {
                            lastEmitMs = now
                            val progress = if (contentLength > 0) totalRead.toFloat() / contentLength else -1f
                            emit(DownloadState.Downloading(progress, totalRead / 1048576f, mbTotal))
                        }
                    }
                }
            }
            response.close()

            // Vorzeitig beendete Streams erkennen — sonst landet eine halbe Datei
            // dauerhaft als "Completed" in der DB
            if (contentLength > 0 && totalRead < contentLength) {
                tempFile.delete()
                emit(DownloadState.Failed("Download unvollständig ($totalRead von $contentLength Bytes)"))
                return@flow
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                emit(DownloadState.Failed("Datei konnte nicht gespeichert werden"))
                return@flow
            }

            downloadDao.insert(
                DownloadEntity(
                    videoId = track.id,
                    title = track.title,
                    channelName = track.channelName,
                    thumbnailUrl = track.thumbnailUrl,
                    durationSeconds = track.durationSeconds,
                    filePath = finalFile.absolutePath
                )
            )
            Log.d(TAG, "✅ Download fertig: ${track.title} (${finalFile.length() / 1024} KB)")
            emit(DownloadState.Completed(finalFile.absolutePath))

        } catch (e: kotlinx.coroutines.CancellationException) {
            // Kein emit in gecanceltem Flow; Cancellation muss weitergereicht werden
            Log.w(TAG, "Download abgebrochen: ${track.title}")
            File(context.filesDir, "downloads/${track.id}.webm.tmp").let { if (it.exists()) it.delete() }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download fehlgeschlagen: ${track.title}", e)
            File(context.filesDir, "downloads/${track.id}.webm.tmp").let { if (it.exists()) it.delete() }
            emit(DownloadState.Failed(e.message ?: e.javaClass.simpleName))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteDownload(videoId: String) {
        val entity = downloadDao.getById(videoId) ?: return
        val file = File(entity.filePath)
        if (file.exists()) file.delete()
        downloadDao.delete(videoId)
    }
}