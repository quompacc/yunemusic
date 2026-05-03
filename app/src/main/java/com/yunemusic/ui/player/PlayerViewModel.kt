package com.yunemusic.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.PlayTrackUseCase
import com.yunemusic.service.MusicService
import com.yunemusic.service.TasteAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingRadio: Boolean = false,
    val error: String? = null,
    val showQueue: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playTrackUseCase: PlayTrackUseCase,
    private val repository: MusicRepository,
    private val tasteAnalyzer: TasteAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var musicService: MusicService? = null
    private var serviceBound = false
    private var trackPlayStartMs: Long = 0L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            musicService?.callback = serviceCallback
            serviceBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.callback = null
            musicService = null
            serviceBound = false
        }
    }

    private val serviceCallback = object : MusicService.Callback {
        override fun onTrackCompleted() { onTrackEndedNaturally() }
        override fun onSkipNextRequested() { skipNext() }
        override fun onSkipPreviousRequested() { skipPrevious() }
        override fun onPlayTrackRequested(track: Track) { playTrack(track) }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(context, MusicService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeServiceState() {
        val service = musicService ?: return

        viewModelScope.launch {
            service.currentTrack.collect { track ->
                _uiState.update { it.copy(currentTrack = track) }
                track?.let { checkIfLiked(it.id) }
            }
        }
        viewModelScope.launch {
            service.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            service.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
        viewModelScope.launch {
            service.queue.collect { queue ->
                _uiState.update { it.copy(queue = queue) }
            }
        }
    }

    // ─── Öffentliche Playback-Funktionen ──────────────────────────────────────

    fun playTrack(track: Track) {
        recordCurrentTrackEvent(skipped = true)
        val newQueue = listOf(track)
        _uiState.update { it.copy(queue = newQueue, currentQueueIndex = 0) }
        musicService?.setQueue(newQueue, 0)
        loadAndPlay(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        recordCurrentTrackEvent(skipped = true)
        _uiState.update { it.copy(queue = tracks, currentQueueIndex = startIndex) }
        musicService?.setQueue(tracks, startIndex)
        loadAndPlay(tracks[startIndex])
    }

    fun togglePlayPause() {
        musicService?.togglePlayPause()
    }

    fun skipNext() {
        recordCurrentTrackEvent(skipped = true)
        val state = _uiState.value
        when {
            state.isShuffleEnabled && state.queue.size > 1 -> {
                val other = state.queue.indices.filter { it != state.currentQueueIndex }
                playFromQueue(other.random())
            }
            state.currentQueueIndex < state.queue.size - 1 ->
                playFromQueue(state.currentQueueIndex + 1)
            else ->
                loadRadioTracks(state.currentTrack)
        }
    }

    fun skipPrevious() {
        val positionMs = musicService?.getPositionMs() ?: 0
        if (positionMs > 3000L) {
            musicService?.seekTo(0)
        } else {
            val idx = _uiState.value.currentQueueIndex
            if (idx > 0) {
                recordCurrentTrackEvent(skipped = true)
                playFromQueue(idx - 1)
            } else {
                musicService?.seekTo(0)
            }
        }
    }

    fun seekTo(progress: Float) {
        val durationMs = musicService?.getDurationMs() ?: return
        if (durationMs > 0) musicService?.seekTo((progress * durationMs).toLong())
    }

    fun toggleLike() {
        val track = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            val liked = _uiState.value.isLiked
            if (liked) repository.unlikeTrack(track.id) else repository.likeTrack(track)
            _uiState.update { it.copy(isLiked = !liked) }
        }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun toggleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = next) }
    }

    fun toggleQueueVisibility() {
        _uiState.update { it.copy(showQueue = !it.showQueue) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun jumpToQueueItem(index: Int) = playFromQueue(index)

    fun addToQueue(track: Track) {
        val currentQueue = _uiState.value.queue
        if (currentQueue.isEmpty()) { playTrack(track); return }
        val newQueue = currentQueue + track
        _uiState.update { it.copy(queue = newQueue) }
        musicService?.setQueue(newQueue, _uiState.value.currentQueueIndex)
    }

    fun playNext(track: Track) {
        val state = _uiState.value
        if (state.queue.isEmpty()) { playTrack(track); return }
        val insertIndex = state.currentQueueIndex + 1
        val newQueue = state.queue.toMutableList().also { it.add(insertIndex, track) }
        _uiState.update { it.copy(queue = newQueue) }
        musicService?.setQueue(newQueue, state.currentQueueIndex)
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queue.size) return
        val newQueue = state.queue.toMutableList().also { it.removeAt(index) }
        val newIndex = if (index < state.currentQueueIndex) state.currentQueueIndex - 1 else state.currentQueueIndex
        val safeIndex = newIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(queue = newQueue, currentQueueIndex = safeIndex) }
        musicService?.setQueue(newQueue, safeIndex)
    }

    fun setShuffle(enabled: Boolean) {
        _uiState.update { it.copy(isShuffleEnabled = enabled) }
    }

    fun likeTrackDirect(track: Track) {
        viewModelScope.launch {
            repository.likeTrack(track)
            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(isLiked = true) }
            }
        }
    }

    // ─── Interne Queue- und Radio-Logik ───────────────────────────────────────

    private fun onTrackEndedNaturally() {
        val state = _uiState.value
        if (state.repeatMode == RepeatMode.ONE) {
            musicService?.seekTo(0)
            musicService?.play()
            return
        }
        recordCurrentTrackEvent(skipped = false)
        when {
            state.isShuffleEnabled && state.queue.size > 1 -> {
                val other = state.queue.indices.filter { it != state.currentQueueIndex }
                playFromQueue(other.random())
            }
            state.currentQueueIndex < state.queue.size - 1 ->
                playFromQueue(state.currentQueueIndex + 1)
            state.repeatMode == RepeatMode.ALL ->
                playFromQueue(0)
            else ->
                loadRadioTracks(state.currentTrack)
        }
    }

    private fun playFromQueue(index: Int) {
        val queue = _uiState.value.queue
        if (index < 0 || index >= queue.size) return
        _uiState.update { it.copy(currentQueueIndex = index) }
        loadAndPlay(queue[index])
    }

    private fun loadAndPlay(track: Track) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            playTrackUseCase(track)
                .onSuccess { streamUrl ->
                    startServiceIfNeeded()
                    musicService?.playTrack(track, streamUrl)
                    trackPlayStartMs = System.currentTimeMillis()
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Wiedergabe fehlgeschlagen")
                    }
                }
        }
    }

    private fun recordCurrentTrackEvent(skipped: Boolean) {
        val track = _uiState.value.currentTrack ?: return
        if (trackPlayStartMs == 0L) return
        val playedSec = ((System.currentTimeMillis() - trackPlayStartMs) / 1000)
            .toInt()
            .coerceAtMost(track.durationSeconds)
        val event = PlayEvent(
            trackId = track.id,
            playedSeconds = playedSec,
            totalSeconds = track.durationSeconds,
            skipped = skipped,
            liked = _uiState.value.isLiked
        )
        tasteAnalyzer.onTrackPlayed(track, event)
        trackPlayStartMs = 0L
    }

    private fun loadRadioTracks(seedTrack: Track?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRadio = true) }

            val related = if (seedTrack != null) {
                repository.getRelatedTracks(seedTrack.id).getOrElse { emptyList() }
                    .filter { it.id != seedTrack.id }
                    .take(10)
            } else emptyList()

            val moreTracks = related.ifEmpty {
                repository.getTrending().getOrElse { emptyList() }.take(10)
            }

            if (moreTracks.isNotEmpty()) {
                val currentQueue = _uiState.value.queue
                val newQueue = (currentQueue + moreTracks).distinctBy { it.id }
                val nextIndex = _uiState.value.currentQueueIndex + 1
                _uiState.update { it.copy(queue = newQueue, isLoadingRadio = false) }
                musicService?.setQueue(newQueue, nextIndex)
                playFromQueue(nextIndex)
            } else {
                _uiState.update { it.copy(isLoadingRadio = false) }
            }
        }
    }

    private fun checkIfLiked(trackId: String) {
        viewModelScope.launch {
            val liked = repository.isTrackLiked(trackId)
            _uiState.update { it.copy(isLiked = liked) }
        }
    }

    private fun startServiceIfNeeded() {
        val intent = Intent(context, MusicService::class.java)
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        recordCurrentTrackEvent(skipped = true)
        musicService?.callback = null
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
