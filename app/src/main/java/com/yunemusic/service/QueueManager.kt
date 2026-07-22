package com.yunemusic.service

import android.util.Log
import com.yunemusic.domain.model.PlayEvent
import com.yunemusic.domain.model.RepeatMode
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.PlayTrackUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Verwaltet Queue, Auto-Advance, Shuffle/Repeat und Smart Radio.
 *
 * Lebt im MusicService (nicht im ViewModel), damit die Wiedergabe auch dann
 * weiterläuft, wenn die Activity zerstört wurde (App weggewischt, Android Auto,
 * Notification-Buttons).
 */
class QueueManager(
    private val scope: CoroutineScope,
    private val repository: MusicRepository,
    private val playTrackUseCase: PlayTrackUseCase,
    private val tasteAnalyzer: TasteAnalyzer,
    private val player: PlayerBridge
) {
    /** Schmale Brücke zum ExoPlayer im MusicService */
    interface PlayerBridge {
        fun playTrack(track: Track, streamUrl: String)
        fun seekToStart()
        fun resume()
        fun getPositionMs(): Long
    }

    companion object {
        private const val TAG = "QueueManager"
        private const val RADIO_BATCH_SIZE = 10
    }

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingRadio = MutableStateFlow(false)
    val isLoadingRadio: StateFlow<Boolean> = _isLoadingRadio.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var playJob: Job? = null
    private var trackPlayStartMs = 0L
    private var currentTrackLiked = false

    // Shuffle ohne Sofort-Wiederholung: bereits gespielte Indizes des Zyklus
    private val shufflePlayed = mutableSetOf<Int>()

    // ── Öffentliche Steuerung ─────────────────────────────────────────────────

    fun playTrack(track: Track) {
        recordCurrentTrackEvent(skipped = true)
        _queue.value = listOf(track)
        _currentIndex.value = 0
        shufflePlayed.clear()
        loadAndPlay(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.size - 1)
        recordCurrentTrackEvent(skipped = true)
        _queue.value = tracks
        _currentIndex.value = index
        shufflePlayed.clear()
        shufflePlayed += index
        loadAndPlay(tracks[index])
    }

    fun skipNext() {
        recordCurrentTrackEvent(skipped = true)
        advance(fromUserSkip = true)
    }

    fun skipPrevious() {
        if (player.getPositionMs() > 3000L) {
            player.seekToStart()
            return
        }
        val idx = _currentIndex.value
        if (idx > 0) {
            recordCurrentTrackEvent(skipped = true)
            playFromQueue(idx - 1)
        } else {
            player.seekToStart()
        }
    }

    /** Wird vom Service bei Player.STATE_ENDED aufgerufen */
    fun onTrackEnded() {
        if (_repeatMode.value == RepeatMode.ONE) {
            player.seekToStart()
            player.resume()
            return
        }
        recordCurrentTrackEvent(skipped = false)
        advance(fromUserSkip = false)
    }

    fun jumpTo(index: Int) {
        recordCurrentTrackEvent(skipped = true)
        playFromQueue(index)
    }

    fun addToQueue(track: Track) {
        if (_queue.value.isEmpty()) { playTrack(track); return }
        _queue.value = _queue.value + track
    }

    fun playNext(track: Track) {
        val current = _queue.value
        if (current.isEmpty()) { playTrack(track); return }
        val insertIndex = (_currentIndex.value + 1).coerceAtMost(current.size)
        _queue.value = current.toMutableList().also { it.add(insertIndex, track) }
    }

    fun removeFromQueue(index: Int) {
        val current = _queue.value
        if (index < 0 || index >= current.size) return
        val currentIdx = _currentIndex.value
        val newQueue = current.toMutableList().also { it.removeAt(index) }

        when {
            // Aktuell spielenden Track entfernt: nächsten starten (oder Wiedergabe behalten, wenn leer)
            index == currentIdx -> {
                _queue.value = newQueue
                if (newQueue.isNotEmpty()) {
                    val nextIndex = index.coerceAtMost(newQueue.size - 1)
                    _currentIndex.value = nextIndex
                    loadAndPlay(newQueue[nextIndex])
                } else {
                    _currentIndex.value = 0
                }
            }
            index < currentIdx -> {
                _queue.value = newQueue
                _currentIndex.value = currentIdx - 1
            }
            else -> _queue.value = newQueue
        }
        shufflePlayed.clear()
    }

    fun setShuffle(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
        shufflePlayed.clear()
        shufflePlayed += _currentIndex.value
    }

    fun toggleShuffle() = setShuffle(!_isShuffleEnabled.value)

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun clearError() { _error.value = null }

    /** Beim Zerstören des Service: letztes Play-Event noch festhalten */
    fun onDestroy() {
        recordCurrentTrackEvent(skipped = true)
    }

    fun currentTrackOrNull(): Track? = _queue.value.getOrNull(_currentIndex.value)

    fun setCurrentTrackLiked(liked: Boolean) { currentTrackLiked = liked }

    // ── Interne Logik ─────────────────────────────────────────────────────────

    private fun advance(fromUserSkip: Boolean) {
        val queue = _queue.value
        val currentIdx = _currentIndex.value
        when {
            _isShuffleEnabled.value && queue.size > 1 -> {
                val remaining = queue.indices.filter { it != currentIdx && it !in shufflePlayed }
                if (remaining.isNotEmpty()) {
                    playFromQueue(remaining.random())
                } else if (_repeatMode.value == RepeatMode.ALL || fromUserSkip) {
                    // Zyklus fertig: neu beginnen
                    shufflePlayed.clear()
                    playFromQueue(queue.indices.filter { it != currentIdx }.random())
                } else {
                    loadRadioTracks(currentTrackOrNull())
                }
            }
            currentIdx < queue.size - 1 -> playFromQueue(currentIdx + 1)
            _repeatMode.value == RepeatMode.ALL && queue.isNotEmpty() -> playFromQueue(0)
            else -> loadRadioTracks(currentTrackOrNull())
        }
    }

    private fun playFromQueue(index: Int) {
        val queue = _queue.value
        if (index < 0 || index >= queue.size) return
        _currentIndex.value = index
        shufflePlayed += index
        loadAndPlay(queue[index])
    }

    private fun loadAndPlay(track: Track) {
        // Fix für Race Condition: alten Ladevorgang abbrechen, sonst gewinnt
        // bei schnellen Track-Wechseln der langsamere (veraltete) Request
        playJob?.cancel()
        playJob = scope.launch {
            _isLoading.value = true
            _error.value = null
            currentTrackLiked = repository.isTrackLiked(track.id)

            val localPath = repository.getDownloadFilePath(track.id)
            if (localPath != null) {
                player.playTrack(track, localPath)
                trackPlayStartMs = System.currentTimeMillis()
                _isLoading.value = false
                prefillQueueIfNeeded(track)
                return@launch
            }

            playTrackUseCase(track)
                .onSuccess { streamUrl ->
                    player.playTrack(track, streamUrl)
                    trackPlayStartMs = System.currentTimeMillis()
                    _isLoading.value = false
                    prefillQueueIfNeeded(track)
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _error.value = toUserMessage(error)
                }
        }
    }

    private fun prefillQueueIfNeeded(seedTrack: Track) {
        if (_queue.value.size > _currentIndex.value + 1) return
        scope.launch {
            val upcoming = fetchRadioTracks(seedTrack)
            if (upcoming.isNotEmpty()) {
                val existingIds = _queue.value.map { it.id }.toSet()
                _queue.value = _queue.value + upcoming.filter { it.id !in existingIds }
            }
        }
    }

    private fun loadRadioTracks(seedTrack: Track?) {
        // Guard gegen doppeltes Nachladen (Doppeltipp auf Next am Queue-Ende)
        if (_isLoadingRadio.value) return
        _isLoadingRadio.value = true
        scope.launch {
            try {
                val existingIds = _queue.value.map { it.id }.toSet()
                val fresh = fetchRadioTracks(seedTrack).filter { it.id !in existingIds }
                if (fresh.isNotEmpty()) {
                    val newQueue = _queue.value + fresh
                    _queue.value = newQueue
                    val nextIndex = (_currentIndex.value + 1).coerceAtMost(newQueue.size - 1)
                    playFromQueue(nextIndex)
                } else {
                    _error.value = "Keine weiteren Titel gefunden"
                }
            } finally {
                _isLoadingRadio.value = false
            }
        }
    }

    private suspend fun fetchRadioTracks(seedTrack: Track?): List<Track> {
        val related = if (seedTrack != null) {
            repository.getRelatedTracks(seedTrack.id).getOrElse { emptyList() }
                .filter { it.id.isNotBlank() && it.id != seedTrack.id }
                .take(RADIO_BATCH_SIZE)
        } else emptyList()

        return related.ifEmpty {
            repository.getTrending().getOrElse { emptyList() }
                .filter { it.id.isNotBlank() }
                .take(RADIO_BATCH_SIZE)
        }
    }

    private fun recordCurrentTrackEvent(skipped: Boolean) {
        val track = currentTrackOrNull() ?: return
        if (trackPlayStartMs == 0L) return
        val playedSec = ((System.currentTimeMillis() - trackPlayStartMs) / 1000)
            .toInt()
            .coerceAtMost(track.durationSeconds)
        val event = PlayEvent(
            trackId = track.id,
            playedSeconds = playedSec,
            totalSeconds = track.durationSeconds,
            skipped = skipped,
            liked = currentTrackLiked
        )
        tasteAnalyzer.onTrackPlayed(track, event)
        trackPlayStartMs = 0L
    }

    private fun toUserMessage(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.toList()
        return when {
            chain.any { it is UnknownHostException || it is SocketTimeoutException } ->
                "Keine Internetverbindung"
            chain.any { it is ReCaptchaException } ->
                "YouTube blockiert vorübergehend Anfragen — bitte später erneut versuchen"
            chain.any { it is ContentNotAvailableException } ->
                "Video nicht verfügbar"
            chain.any { it is ExtractionException } ->
                "YouTube-Abruf fehlgeschlagen — vermutlich hat YouTube etwas geändert (App-Update nötig)"
            chain.any { it is IOException } ->
                "Netzwerkfehler — bitte Verbindung prüfen"
            else -> error.message ?: "Wiedergabe fehlgeschlagen"
        }.also { Log.w(TAG, "Playback error: ${error.message}", error) }
    }
}
