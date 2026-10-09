package com.yunemusic.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunemusic.R
import com.yunemusic.domain.model.DownloadState
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.service.MusicService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Enum lebt jetzt in domain.model, damit auch der Service sie nutzen kann;
// Alias hält die bestehenden Verwendungen in ui.player kompatibel
typealias RepeatMode = com.yunemusic.domain.model.RepeatMode

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val isLoading: Boolean = false,
    val isLoadingRadio: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val showQueue: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

/**
 * Dünner UI-Spiegel des [MusicService]: Die eigentliche Queue-/Radio-/
 * Auto-Advance-Logik lebt im Service (QueueManager), damit die Wiedergabe
 * auch ohne Activity weiterläuft. Hier bleiben nur UI-State, Downloads
 * und Like-Verwaltung.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // The Android binding owns the Service lifetime; the ViewModel must not retain its Context.
    private var connectedService = java.lang.ref.WeakReference<MusicService>(null)
    private val musicService: MusicService? get() = connectedService.get()
    private var serviceBound = false
    private var observeJob: Job? = null
    private var downloadJob: Job? = null
    private var downloadingTrackId: String? = null

    // Befehle, die eintreffen, bevor das Service-Binding fertig ist
    private var pendingCommand: ((MusicService) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            val svc = binder.getService()
            connectedService = java.lang.ref.WeakReference(svc)
            observeServiceState(svc)
            pendingCommand?.let { cmd ->
                pendingCommand = null
                cmd(svc)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Bindung besteht weiter (System versucht Auto-Rebind):
            // serviceBound NICHT zurücksetzen, sonst leakt die Connection in onCleared
            observeJob?.cancel()
            connectedService.clear()
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(context, MusicService::class.java)
        serviceBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeServiceState(service: MusicService) {
        // Alte Collector der vorherigen Service-Instanz beenden (kein Doppel-Update/Leak)
        observeJob?.cancel()
        val job = SupervisorJob()
        observeJob = job
        val qm = service.queueManager

        fun <T> collect(flow: Flow<T>, apply: (T) -> Unit) {
            viewModelScope.launch(job) { flow.collect { apply(it) } }
        }

        collect(service.currentTrack) { track ->
            _uiState.update { it.copy(currentTrack = track) }
            track?.let { checkIfLiked(it.id) }
        }
        collect(service.isPlaying) { playing -> _uiState.update { it.copy(isPlaying = playing) } }
        collect(service.isBuffering) { buffering -> _uiState.update { it.copy(isBuffering = buffering) } }
        collect(service.progress) { p -> _uiState.update { it.copy(progress = p) } }
        collect(qm.queue) { q -> _uiState.update { it.copy(queue = q) } }
        collect(qm.currentIndex) { i -> _uiState.update { it.copy(currentQueueIndex = i) } }
        collect(qm.isLoading) { l -> _uiState.update { it.copy(isLoading = l) } }
        collect(qm.isLoadingRadio) { l -> _uiState.update { it.copy(isLoadingRadio = l) } }
        collect(qm.error) { e -> if (e != null) _uiState.update { it.copy(error = e) } }
        collect(qm.isShuffleEnabled) { s -> _uiState.update { it.copy(isShuffleEnabled = s) } }
        collect(qm.repeatMode) { r -> _uiState.update { it.copy(repeatMode = r) } }
    }

    /** Führt den Befehl aus, sobald der Service verbunden ist (puffert genau einen). */
    private fun withService(command: (MusicService) -> Unit) {
        val service = musicService
        if (service != null) {
            command(service)
        } else {
            pendingCommand = command
            if (!serviceBound) bindService()
        }
    }

    // ─── Öffentliche Playback-Funktionen ──────────────────────────────────────

    fun playTrack(track: Track) {
        startServiceIfNeeded()
        withService { it.queueManager.playTrack(track) }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        startServiceIfNeeded()
        withService { it.queueManager.playQueue(tracks, startIndex) }
    }

    fun togglePlayPause() = withService { it.togglePlayPause() }

    fun playFromSearch(query: String?) {
        startServiceIfNeeded()
        withService { it.playFromSearch(query) }
    }

    fun skipNext() = withService { it.queueManager.skipNext() }

    fun skipPrevious() = withService { it.queueManager.skipPrevious() }

    fun seekTo(progress: Float) {
        val service = musicService ?: return
        val durationMs = service.getDurationMs()
        if (durationMs > 0) service.seekTo((progress * durationMs).toLong())
    }

    /** Relativ spulen, z. B. seekBy(-10) = 10 Sekunden zurück */
    fun seekBy(deltaSeconds: Int) {
        val service = musicService ?: return
        val durationMs = service.getDurationMs()
        if (durationMs <= 0) return
        val newPos = (service.getPositionMs() + deltaSeconds * 1000L).coerceIn(0L, durationMs)
        service.seekTo(newPos)
    }

    fun toggleLike() {
        val track = _uiState.value.currentTrack ?: return
        // Synchron togglen, damit Doppeltipp nicht doppelt liked (Race)
        val newLiked = !_uiState.value.isLiked
        _uiState.update { it.copy(isLiked = newLiked) }
        musicService?.queueManager?.setCurrentTrackLiked(newLiked)
        viewModelScope.launch {
            if (newLiked) repository.likeTrack(track) else repository.unlikeTrack(track.id)
        }
    }

    fun toggleShuffle() = withService { it.queueManager.toggleShuffle() }

    fun toggleRepeat() = withService { it.queueManager.toggleRepeat() }

    fun toggleQueueVisibility() {
        _uiState.update { it.copy(showQueue = !it.showQueue) }
    }

    fun clearError() {
        musicService?.queueManager?.clearError()
        _uiState.update { it.copy(error = null) }
    }

    fun jumpToQueueItem(index: Int) = withService { it.queueManager.jumpTo(index) }

    fun addToQueue(track: Track) {
        startServiceIfNeeded()
        withService { it.queueManager.addToQueue(track) }
    }

    fun playNext(track: Track) {
        startServiceIfNeeded()
        withService { it.queueManager.playNext(track) }
    }

    fun removeFromQueue(index: Int) = withService { it.queueManager.removeFromQueue(index) }

    fun setShuffle(enabled: Boolean) = withService { it.queueManager.setShuffle(enabled) }

    fun likeTrackDirect(track: Track) {
        viewModelScope.launch {
            repository.likeTrack(track)
            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(isLiked = true) }
                musicService?.queueManager?.setCurrentTrackLiked(true)
            }
        }
    }

    // ─── Downloads ────────────────────────────────────────────────────────────

    fun downloadCurrentTrack() {
        val track = _uiState.value.currentTrack ?: return
        downloadTrack(track)
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadingTrackId = null
        _uiState.update {
            it.copy(isDownloading = false, downloadProgress = 0f, downloadStatus = "")
        }
        Log.d(TAG, "Download abgebrochen durch Benutzer")
    }

    fun downloadTrack(track: Track) {
        if (downloadJob?.isActive == true) {
            _uiState.update { it.copy(error = context.getString(R.string.player_download_already_running)) }
            return
        }
        downloadingTrackId = track.id
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isDownloading = true, downloadProgress = 0f, downloadStatus = context.getString(R.string.player_download_resolving_url), error = null)
            }
            Log.d(TAG, "Starte Download: ${track.title} (id=${track.id})")

            repository.downloadTrackWithProgress(track).collect { state ->
                when (state) {
                    is DownloadState.GettingUrl -> {
                        _uiState.update { it.copy(downloadStatus = context.getString(R.string.player_download_resolving_stream)) }
                    }
                    is DownloadState.Downloading -> {
                        val status = if (state.progress >= 0) {
                            context.getString(R.string.player_download_progress_percent, (state.progress * 100).toInt())
                        } else {
                            context.getString(R.string.player_download_progress_mb, state.mbDownloaded)
                        }
                        _uiState.update {
                            it.copy(
                                downloadProgress = state.progress.coerceAtLeast(0f),
                                downloadStatus = status
                            )
                        }
                    }
                    is DownloadState.Completed -> {
                        Log.d(TAG, "Download erfolgreich: ${track.title}")
                        downloadingTrackId = null
                        _uiState.update {
                            it.copy(
                                isDownloading = false, downloadProgress = 1f, downloadStatus = "",
                                // "Downloaded"-Badge nur setzen, wenn der geladene Track
                                // auch der aktuell spielende ist
                                isDownloaded = if (it.currentTrack?.id == track.id) true else it.isDownloaded
                            )
                        }
                    }
                    is DownloadState.Failed -> {
                        Log.e(TAG, "Download fehlgeschlagen: ${track.title} – ${state.error}")
                        downloadingTrackId = null
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = 0f, downloadStatus = "",
                                error = context.getString(R.string.player_download_failed, state.error)
                            )
                        }
                    }
                }
            }
        }
    }

    fun deleteDownload(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDownload(trackId)
            if (_uiState.value.currentTrack?.id == trackId) {
                _uiState.update { it.copy(isDownloaded = false) }
            }
        }
    }

    // ─── Intern ───────────────────────────────────────────────────────────────

    private fun checkIfLiked(trackId: String) {
        viewModelScope.launch {
            val liked = repository.isTrackLiked(trackId)
            val downloaded = repository.isTrackDownloaded(trackId)
            _uiState.update { it.copy(isLiked = liked, isDownloaded = downloaded) }
        }
    }

    private fun startServiceIfNeeded() {
        val intent = Intent(context, MusicService::class.java)
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
        observeJob?.cancel()
        pendingCommand = null
        connectedService.clear()
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            serviceBound = false
        }
    }
}
