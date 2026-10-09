package com.yunemusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.net.Uri
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaSessionManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.yunemusic.data.cache.StreamingCache
import com.yunemusic.MainActivity
import com.yunemusic.R
import com.yunemusic.domain.model.Track
import com.yunemusic.data.preferences.UserPreferences
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.PlayTrackUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicService : MediaBrowserServiceCompat() {

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

    @Inject lateinit var repository: MusicRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var playTrackUseCase: PlayTrackUseCase
    @Inject lateinit var tasteAnalyzer: TasteAnalyzer
    @Inject lateinit var duckingController: DuckingController
    @Inject lateinit var streamingCache: StreamingCache

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dynamicsProcessing: DynamicsProcessing? = null
    private lateinit var transitionWakeLock: PowerManager.WakeLock
    private var prefetchJob: Job? = null
    private var voiceSearchJob: Job? = null
    // Never persisted or logged; old notification capabilities fail closed after service restart.
    private val controlToken = java.util.UUID.randomUUID().toString()
    private var nextPrefetchAt = 0L
    @Volatile private var prefetchReady = false

    /** Queue/Radio/Auto-Advance leben im Service, damit die Wiedergabe auch ohne UI weiterläuft */
    lateinit var queueManager: QueueManager
        private set

    // Lautstärke = Limiter-Fallback (API < 28) × Notification-Ducking
    private var limiterFallbackVolume = 1f
    private var duckFactor = 1f

    // Cache Track objects so onPlayFromMediaId can look them up by ID
    private val browseCache = mutableMapOf<String, Track>()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    val queue: StateFlow<List<Track>> get() = queueManager.queue

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        queueManager = QueueManager(
            context = applicationContext,
            scope = serviceScope,
            repository = repository,
            playTrackUseCase = playTrackUseCase,
            tasteAnalyzer = tasteAnalyzer,
            player = object : QueueManager.PlayerBridge {
                override fun playTrack(track: Track, streamUrl: String) =
                    this@MusicService.playTrack(track, streamUrl)
                override fun seekToStart() = seekTo(0)
                override fun resume() = play()
                override fun getPositionMs(): Long = exoPlayer.currentPosition
            }
        )
        createNotificationChannel()
        transitionWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YuneMusic:TrackTransition")
            .apply { setReferenceCounted(false) }
        initMediaSession()
        initExoPlayer()
        startProgressTracking()
        observeLimiterSetting()
        observeDucking()
        serviceScope.launch {
            queueManager.error.collect { error ->
                if (error != null) syncPlaybackLifecycle()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return if (intent.action == SERVICE_INTERFACE) {
            super.onBind(intent) ?: binder
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
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60000,  // Maintain a larger reserve through short reception gaps.
                300000, // Up to five minutes ahead; disk cache also retains these bytes.
                1500,   // bufferForPlaybackMs
                3000    // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        exoPlayer = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(DefaultMediaSourceFactory(streamingCache.dataSourceFactory))
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()

        exoPlayer.setAudioAttributes(audioAttributes, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)

        exoPlayer.apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    _isBuffering.value = state == Player.STATE_BUFFERING
                    updatePlaybackState()
                    if (state == Player.STATE_ENDED) {
                        // ExoPlayer releases its wake lock at EOF. Keep the CPU awake
                        // while NewPipe resolves the next URL, before launching the job.
                        syncPlaybackLifecycle()
                        queueManager.onTrackEnded()
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    updatePlaybackState()
                    updateNotification()
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    updatePrefetch()
                    // Includes pauses during buffering, which do not change isPlaying.
                    syncPlaybackLifecycle()
                    updatePlaybackState()
                    updateNotification()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        serviceScope.launch {
                            applyLimiter(userPreferences.loudnessLimiter.first(), audioSessionId)
                        }
                    }
                }
            })
        }
    }

    private fun startProgressTracking() {
        serviceScope.launch {
            while (true) {
                updatePrefetch()
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

    private fun updatePrefetch() {
        prefetchReady = exoPlayer.isPlaying && exoPlayer.totalBufferedDuration >= 30_000L &&
            !queueManager.isLoading.value && !queueManager.isShuffleEnabled.value &&
            streamingCache.prefetchEnabled.value && streamingCache.networkAllowed()
        if (!prefetchReady) {
            prefetchJob?.cancel()
            return
        }
        if (prefetchJob?.isCompleted == false || android.os.SystemClock.elapsedRealtime() < nextPrefetchAt) return
        val currentId = _currentTrack.value?.id ?: return
        val upcoming = queueManager.queue.value.drop(queueManager.currentIndex.value + 1)
            .distinctBy { it.id }.take(2)
        if (upcoming.isEmpty()) return
        nextPrefetchAt = android.os.SystemClock.elapsedRealtime() + 30_000L
        prefetchJob = serviceScope.launch {
            for (track in upcoming) {
                ensureActive()
                fun stillWanted() = prefetchReady && _currentTrack.value?.id == currentId &&
                    track.id in queueManager.queue.value.drop(queueManager.currentIndex.value + 1)
                        .take(2).map { it.id }
                if (!stillWanted()) break
                try {
                    // Explicit downloads already work offline and must not be fetched twice.
                    if (repository.getDownloadFilePath(track.id) != null) continue
                    val quality = userPreferences.audioQuality.first()
                    if (streamingCache.completeUrl(track.id, quality) != null) continue
                    val url = repository.getStreamUrl(track.id).getOrThrow()
                    if (stillWanted()) streamingCache.prefetch(url, ::stillWanted)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Speculative work must never stop the currently playing track.
                }
            }
        }
    }

    // ── Notification-Ducking (leiser bei eingehenden Benachrichtigungen) ──────

    private fun observeDucking() {
        serviceScope.launch {
            duckingController.duckFactor.collect { factor ->
                duckFactor = factor
                applyVolume()
            }
        }
    }

    private fun applyVolume() {
        exoPlayer.volume = limiterFallbackVolume * duckFactor
    }

    // ── Loudness limiter ──────────────────────────────────────────────────────

    private fun observeLimiterSetting() {
        serviceScope.launch {
            userPreferences.loudnessLimiter.collect { enabled ->
                applyLimiter(enabled)
            }
        }
    }

    private fun applyLimiter(enabled: Boolean, sessionId: Int = exoPlayer.audioSessionId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.release()
            dynamicsProcessing = null
            if (enabled && sessionId != C.AUDIO_SESSION_ID_UNSET) {
                dynamicsProcessing = createDynamicsProcessing(sessionId)
            }
        } else {
            // API < 28 fallback: reduce internal volume to provide ~3 dB headroom
            limiterFallbackVolume = if (enabled) 0.85f else 1.0f
            applyVolume()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamicsProcessing(audioSessionId: Int): DynamicsProcessing? = try {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,               // stereo
            false, 0,        // no pre-EQ
            false, 0,        // no MBC
            false, 0,        // no post-EQ
            true             // limiter only
        ).build()
        DynamicsProcessing(0, audioSessionId, config).also { dp ->
            val limiter = DynamicsProcessing.Limiter(
                true, true,
                0,            // linkGroup: beide Kanäle gekoppelt
                1f,           // attackTime ms — schnell einrasten
                100f,         // releaseTime ms
                100f,         // ratio — Brick-Wall
                -1f,          // threshold dBFS — greift kurz vor 0 dBFS
                0f            // postGain dB
            )
            repeat(2) { ch -> dp.setLimiterByChannelIndex(ch, limiter) }
            dp.enabled = true
        }
    } catch (e: Exception) {
        Log.w(TAG, "DynamicsProcessing nicht verfügbar: ${e.message}")
        null
    }

    // ── Playback controls ──────────────────────────────────────────────────────

    fun playTrack(track: Track, streamUrl: String) {
        _currentTrack.value = track
        _progress.value = 0f
        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        updateMediaSessionMetadata(track)
        startForegroundWithNotification()
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun syncPlaybackLifecycle() {
        val lifecycle = playbackLifecycle(
            _currentTrack.value != null, exoPlayer.playWhenReady, exoPlayer.playbackState,
            exoPlayer.playerError != null || queueManager.error.value != null
        )
        if (lifecycle.foreground) startForegroundWithNotification()
        else stopForeground(STOP_FOREGROUND_DETACH)

        if (lifecycle.transitionWakeLock) {
            // Timeout also bounds battery use if URL extraction never completes.
            if (!transitionWakeLock.isHeld) transitionWakeLock.acquire(60_000L)
        } else if (transitionWakeLock.isHeld) {
            transitionWakeLock.release()
        }
    }

    fun play() { exoPlayer.play() }

    fun togglePlayPause() {
        if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        // Anzeige sofort aktualisieren — der 500-ms-Tick läuft nur bei aktiver Wiedergabe
        val duration = exoPlayer.duration
        if (duration > 0) {
            _progress.value = positionMs.coerceIn(0, duration).toFloat() / duration
        }
        updatePlaybackState()
    }

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
        val state = when {
            exoPlayer.playerError != null || queueManager.error.value != null -> PlaybackStateCompat.STATE_ERROR
            !exoPlayer.playWhenReady -> PlaybackStateCompat.STATE_PAUSED
            exoPlayer.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            exoPlayer.playbackState == Player.STATE_BUFFERING ||
                exoPlayer.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_BUFFERING
            else -> PlaybackStateCompat.STATE_STOPPED
        }

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
            .setState(state, exoPlayer.currentPosition, if (exoPlayer.isPlaying) 1.0f else 0.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val track = _currentTrack.value
        val isPlaying = exoPlayer.playWhenReady

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
                putExtra(EXTRA_CONTROL_TOKEN, controlToken)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).apply {
                action = ACTION_NEXT
                putExtra(EXTRA_CONTROL_TOKEN, controlToken)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).apply {
                action = ACTION_PREV
                putExtra(EXTRA_CONTROL_TOKEN, controlToken)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(track?.title ?: "YuneMusic")
            .setContentText(track?.channelName ?: "")
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_skip_previous, getString(R.string.service_action_previous), prevIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (isPlaying) R.string.service_action_pause else R.string.service_action_play),
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, getString(R.string.service_action_next), nextIntent)
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
        if (intent?.action != null && !isAuthorizedPlaybackCommand(intent, controlToken)) {
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PLAY  -> exoPlayer.play()
            ACTION_PAUSE -> exoPlayer.pause()
            ACTION_NEXT  -> queueManager.skipNext()
            ACTION_PREV  -> queueManager.skipPrevious()
            ACTION_STOP  -> {
                exoPlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** Shared by the Activity voice intent and trusted MediaSession controllers. */
    fun playFromSearch(query: String?) {
        voiceSearchJob?.cancel()
        val search = query?.trim().orEmpty()
        if (search.isEmpty() && _currentTrack.value != null) {
            play()
            return
        }
        voiceSearchJob = serviceScope.launch {
            val results = if (search.isEmpty()) repository.getLikedTracks().first()
                else repository.searchTracks(search, null).getOrElse { emptyList() }
            ensureActive()
            val playable = results.filter { it.id.isNotBlank() }.take(20)
            if (playable.isNotEmpty()) queueManager.playQueue(playable, 0)
        }
    }

    // ── Android Auto: MediaBrowser ────────────────────────────────────────────

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        // History and likes are private. Trust the OS media-controller policy,
        // not an unverified package-name allowlist.
        val client = MediaSessionManager.RemoteUserInfo(clientPackageName, -1, clientUid)
        if (clientUid != applicationInfo.uid &&
            !MediaSessionManager.getSessionManager(this).isTrustedForMediaControl(client)) {
            return null
        }
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
                        items += browsableItem(BROWSE_RECENT, getString(R.string.auto_recent_title), getString(R.string.auto_recent_subtitle))
                        items += browsableItem(BROWSE_LIKED, getString(R.string.auto_liked_title), getString(R.string.auto_liked_subtitle))
                        if (queueManager.queue.value.isNotEmpty()) {
                            items += browsableItem(BROWSE_QUEUE, getString(R.string.auto_queue_title), queueManager.queue.value.size.let { n ->
                                resources.getQuantityString(R.plurals.auto_queue_track_count, n, n)
                            })
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
                        queueManager.queue.value.forEach { track ->
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
        if (::transitionWakeLock.isInitialized && transitionWakeLock.isHeld) transitionWakeLock.release()
        super.onDestroy()
        queueManager.onDestroy()
        browseCache.clear()
        serviceScope.cancel()
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        mediaSession.release()
        exoPlayer.release()
    }

    // ── MediaSession callbacks (Auto + headset buttons + lock screen) ─────────

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay()           { exoPlayer.play() }
        override fun onPause()          { exoPlayer.pause() }
        override fun onSkipToNext()     { queueManager.skipNext() }
        override fun onSkipToPrevious() { queueManager.skipPrevious() }
        override fun onSeekTo(pos: Long){ seekTo(pos) }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val track = mediaId?.let { browseCache[it] } ?: return
            queueManager.playTrack(track)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            this@MusicService.playFromSearch(query)
        }

        override fun onStop() {
            exoPlayer.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
