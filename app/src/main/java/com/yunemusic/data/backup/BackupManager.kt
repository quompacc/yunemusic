package com.yunemusic.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.yunemusic.data.local.YuneMusicDatabase
import com.yunemusic.data.local.entities.PlayEventEntity
import com.yunemusic.data.local.entities.PlaylistEntity
import com.yunemusic.data.local.entities.PlaylistTrackEntity
import com.yunemusic.data.local.entities.TrackEntity
import com.yunemusic.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves everything the recommendations learn from to another device: taste profile,
 * play history, library and playlists, plus settings.
 *
 * Downloaded audio files and the car radio stay behind; the files are large and can be
 * fetched again, and the radio pairing only exists on this device. Downloaded tracks are
 * kept as library entries so they are not lost from view.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: YuneMusicDatabase,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val FORMAT = "yunemusic-backup"
        private const val VERSION = 1

        // Tolerant towards fields added by newer app versions.
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val downloads = database.downloadDao().getAllDownloadsOnce()
        val backup = Backup(
            format = FORMAT,
            version = VERSION,
            exportedAt = System.currentTimeMillis(),
            // Copied verbatim so fields of other app versions survive the round trip.
            tasteProfile = userPreferences.tasteProfileJson.first()
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
                ?: JsonNull,
            tracks = database.trackDao().getAllTracksOnce().map { it.toBackup() },
            playEvents = database.playEventDao().getRecentEvents(Int.MAX_VALUE).map { it.toBackup() },
            playlists = database.playlistDao().getAllPlaylists().map { it.toBackup() },
            playlistTracks = database.playlistDao().getAllPlaylistTracks().map { it.toBackup() },
            downloadedTracks = downloads.map {
                BackupTrack(it.videoId, it.title, it.channelName, it.thumbnailUrl, it.durationSeconds)
            },
            settings = BackupSettings(
                audioQuality = userPreferences.audioQuality.first(),
                wifiOnly = userPreferences.wifiOnly.first(),
                prefetchEnabled = userPreferences.prefetchEnabled.first(),
                loudnessLimiter = userPreferences.loudnessLimiter.first(),
                duckOnNotification = userPreferences.duckOnNotification.first()
            )
        )
        val output = context.contentResolver.openOutputStream(target, "wt")
            ?: throw IOException("Ziel nicht beschreibbar")
        output.use { it.write(json.encodeToString(Backup.serializer(), backup).toByteArray()) }
    }

    /** Replaces library, history, playlists, taste profile and settings with the backup. */
    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)
            ?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Datei nicht lesbar")
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.format == FORMAT) { "Keine YuneMusic-Sicherung" }
        require(backup.version <= VERSION) { "Sicherung stammt aus einer neueren App-Version" }

        val tracks = backup.tracks.map { it.toEntity() }
        val knownIds = tracks.mapTo(HashSet()) { it.id }
        // Downloads have no files here, so they reappear as plain library entries.
        val formerDownloads = backup.downloadedTracks
            .filter { it.id !in knownIds }
            .map { TrackEntity(it.id, it.title, it.channelName, it.thumbnailUrl, it.durationSeconds) }

        database.withTransaction {
            database.playlistDao().deleteAllPlaylistTracks()
            database.playlistDao().deleteAllPlaylists()
            database.playEventDao().deleteAllEvents()
            database.trackDao().deleteAllTracks()

            database.trackDao().insertTracks(tracks + formerDownloads)
            database.playEventDao().insertEvents(backup.playEvents.map { it.toEntity() })
            database.playlistDao().insertPlaylists(backup.playlists.map { it.toEntity() })
            database.playlistDao().insertPlaylistTracks(backup.playlistTracks.map { it.toEntity() })
        }
        userPreferences.setTasteProfileJson(
            if (backup.tasteProfile is JsonNull) "" else backup.tasteProfile.toString()
        )
        with(backup.settings) {
            userPreferences.setAudioQuality(audioQuality)
            userPreferences.setWifiOnly(wifiOnly)
            userPreferences.setPrefetchEnabled(prefetchEnabled)
            userPreferences.setLoudnessLimiter(loudnessLimiter)
            userPreferences.setDuckOnNotification(duckOnNotification)
        }
    }
}

// The backup has its own types so that Room entities can change without breaking old files.

@Serializable
private data class Backup(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val tasteProfile: JsonElement = JsonNull,
    val tracks: List<BackupLibraryTrack> = emptyList(),
    val playEvents: List<BackupPlayEvent> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val playlistTracks: List<BackupPlaylistTrack> = emptyList(),
    val downloadedTracks: List<BackupTrack> = emptyList(),
    val settings: BackupSettings = BackupSettings()
)

@Serializable
private data class BackupTrack(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Int
)

@Serializable
private data class BackupLibraryTrack(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val genre: String = "",
    val bpm: Int = 0,
    val isLiked: Boolean = false,
    val addedAt: Long = 0
)

@Serializable
private data class BackupPlayEvent(
    val trackId: String,
    val timestamp: Long,
    val playedSeconds: Int,
    val totalSeconds: Int,
    val skipped: Boolean,
    val liked: Boolean
)

@Serializable
private data class BackupPlaylist(val id: Long, val name: String, val createdAt: Long)

@Serializable
private data class BackupPlaylistTrack(
    val playlistId: Long,
    val track: BackupTrack,
    val position: Int,
    val addedAt: Long
)

@Serializable
private data class BackupSettings(
    val audioQuality: Int = 1,
    val wifiOnly: Boolean = false,
    val prefetchEnabled: Boolean = true,
    val loudnessLimiter: Boolean = true,
    val duckOnNotification: Boolean = true
)

private fun TrackEntity.toBackup() =
    BackupLibraryTrack(id, title, channelName, thumbnailUrl, durationSeconds, genre, bpm, isLiked, addedAt)

private fun BackupLibraryTrack.toEntity() =
    TrackEntity(id, title, channelName, thumbnailUrl, durationSeconds, genre, bpm, isLiked, addedAt)

private fun PlayEventEntity.toBackup() =
    BackupPlayEvent(trackId, timestamp, playedSeconds, totalSeconds, skipped, liked)

private fun BackupPlayEvent.toEntity() = PlayEventEntity(
    trackId = trackId, timestamp = timestamp, playedSeconds = playedSeconds,
    totalSeconds = totalSeconds, skipped = skipped, liked = liked
)

private fun PlaylistEntity.toBackup() = BackupPlaylist(id, name, createdAt)

private fun BackupPlaylist.toEntity() = PlaylistEntity(id, name, createdAt)

private fun PlaylistTrackEntity.toBackup() = BackupPlaylistTrack(
    playlistId,
    BackupTrack(trackId, trackTitle, trackChannelName, trackThumbnailUrl, trackDurationSeconds),
    position,
    addedAt
)

private fun BackupPlaylistTrack.toEntity() = PlaylistTrackEntity(
    playlistId, track.id, track.title, track.channelName, track.thumbnailUrl,
    track.durationSeconds, position, addedAt
)
