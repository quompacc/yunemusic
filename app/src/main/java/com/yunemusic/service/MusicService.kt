package com.yunemusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.yunemusic.MainActivity
import com.yunemusic.R
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    interface Callback {
        fun onTrackCompleted()
        fun onSkipNextRequested()
        fun onSkipPreviousRequested()
        fun onPlayTrackRequested(track: Track)
    }

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "yune_music_playback"
        private const val NOTIFICATION_ID = 1001
        private const val MEDIA_ROOT_ID = "yune_music_root"

        // Android Auto browse categories
        private const val BROWSE_RECENT = "browse_recent"
        private const val BROWSE_LIKED  = "browse_liked"
        private const val BROWSE_QUEUE  = "browse_queue"

        const val ACTION_PLAY = "com.yunemusic.ACTION_PLAY"
        const val ACTION_PAUSE = "com.yunemusic.ACTION_PAUSE"
        const val ACTION_NEXT = "com.yunemusic.ACTION_NEXT"
        const val ACTION_PREV = "com.yunemusic.ACTION_PREV"
        const val ACTION_STOP = "com.yunemusic.ACTION_STOP"
    }

    @Inject lateinit var tasteAnalyzer: TasteAnalyzer
    @Inject lateinit var repository: MusicRepository

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var callback: Callback? = null

    // Cache Track objects so onPlayFromMediaId can look them up by ID
    private val browseCache = mutableMapOf<String, Track>()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        initExoPlayer()
        startProgressTracking()
    }

    override fun onBind(intent: Intent): IBinder {
        return if (intent.action == SERVICE_INTERFACE) {
            super.onBind(intent)!!
        } else {
            binder
        }
    }

    private fun initMediaSession() {
        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSessionCompat(this, TAG).apply {
            setSessionActivity(sessionActivityPendingIntent)
            setCallback(MediaSessionCallback())
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    updatePlaybackState()
                    if (state == Player.STATE_ENDED) {
                        callback?.onTrackCompleted()
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    updatePlaybackState()
                    updateNotification()
                }
            })
        }
    }

    private fun startProgressTracking() {
        serviceScope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    val duration = exoPlayer.duration
                    val position = exoPlayer.currentPosition
                    if (duration > 0) {
                        _progress.value = position.toFloat() / duration.toFloat()
                    }
                }
                delay(500)
            }
        }
    }

    // ── Playback controls ──────────────────────────────────────────────────────

    fun playTrack(track: Track, streamUrl: String) {
        _currentTrack.value = track
        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        updateMediaSessionMetadata(track)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun play() { exoPlayer.play() }

    fun setQueue(tracks: List<Track>, @Suppress("UNUSED_PARAMETER") startIndex: Int = 0) {
        _queue.value = tracks
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs) }

    fun getDurationMs(): Long = exoPlayer.duration
    fun getPositionMs(): Long = exoPlayer.currentPosition

    // ── MediaSession metadata & state ─────────────────────────────────────────

    private fun updateMediaSessionMetadata(track: Track) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.channelName)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationSeconds * 1000L)
            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, track.thumbnailUrl)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, track.thumbnailUrl)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updatePlaybackState() {
        val state = if (exoPlayer.isPlaying) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            )
            .setState(state, exoPlayer.currentPosition, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YuneMusic Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback controls"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val track = _currentTrack.value
        val isPlaying = exoPlayer.isPlaying

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(track?.title ?: "YuneMusic")
            .setContentText(track?.channelName ?: "")
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    // ── onStartCommand (notification button intents) ──────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY  -> exoPlayer.play()
            ACTION_PAUSE -> exoPlayer.pause()
            ACTION_NEXT  -> callback?.onSkipNextRequested()
            ACTION_PREV  -> callback?.onSkipPreviousRequested()
            ACTION_STOP  -> {
                exoPlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ── Android Auto: MediaBrowser ────────────────────────────────────────────

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        // Allow all callers — the app has no sensitive private content
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        serviceScope.launch(Dispatchers.IO) {
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            try {
                when (parentId) {
                    MEDIA_ROOT_ID -> {
                        // Root: show category tiles in Auto
                        items += browsableItem(BROWSE_RECENT, "Zuletzt gehört", "Zuletzt gespielte Tracks")
                        items += browsableItem(BROWSE_LIKED,  "Geliked",        "Deine Lieblingstracks")
                        if (_queue.value.isNotEmpty()) {
                            items += browsableItem(BROWSE_QUEUE, "Warteschlange", "${_queue.value.size} Tracks")
                        }
                    }
                    BROWSE_RECENT -> {
                        repository.getRecentTracks().first().take(30).forEach { track ->
                            browseCache[track.id] = track
                            items += track.toPlayableItem()
                        }
                    }
                    BROWSE_LIKED -> {
                        repository.getLikedTracks().first().take(30).forEach { track ->
                            browseCache[track.id] = track
                            items += track.toPlayableItem()
                        }
                    }
                    BROWSE_QUEUE -> {
                        _queue.value.forEach { track ->
                            browseCache[track.id] = track
                            items += track.toPlayableItem()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onLoadChildren error for $parentId", e)
            }
            result.sendResult(items)
        }
    }

    private fun browsableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun Track.toPlayableItem(): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(channelName)
            .apply { if (thumbnailUrl.isNotEmpty()) setIconUri(Uri.parse(thumbnailUrl)) }
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        callback = null
        browseCache.clear()
        serviceScope.cancel()
        mediaSession.release()
        exoPlayer.release()
    }

    // ── MediaSession callbacks (Auto + headset buttons + lock screen) ─────────

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay()           { exoPlayer.play() }
        override fun onPause()          { exoPlayer.pause() }
        override fun onSkipToNext()     { callback?.onSkipNextRequested() }
        override fun onSkipToPrevious() { callback?.onSkipPreviousRequested() }
        override fun onSeekTo(pos: Long){ seekTo(pos) }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val track = mediaId?.let { browseCache[it] } ?: return
            callback?.onPlayTrackRequested(track)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            // Voice search from Auto: "Hey Google, spiel [Song] auf YuneMusic"
            // Falls kein Track gefunden → leere Queue → Radio lädt Tracks
            if (!query.isNullOrBlank()) {
                Log.d(TAG, "Voice search: $query")
                // The ViewModel will handle the search-to-play flow via a future extension
            }
        }

        override fun onStop() {
            exoPlayer.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
