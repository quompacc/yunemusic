package com.yunemusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunemusic.domain.model.Playlist
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.model.YouTubePlaylist
import com.yunemusic.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val likedTracks: List<Track> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    // YouTube Playlists
    val ytSearchQuery: String = "",
    val ytSearchResults: List<YouTubePlaylist> = emptyList(),
    val ytSelectedPlaylist: YouTubePlaylist? = null,
    val ytPlaylistTracks: List<Track> = emptyList(),
    val ytIsSearching: Boolean = false,
    val ytIsLoadingTracks: Boolean = false,
    val ytError: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var playlistTracksJob: Job? = null

    init {
        observeLikedTracks()
        observeRecentTracks()
        observeDownloadedTracks()
        observePlaylists()
    }

    private fun observeLikedTracks() {
        viewModelScope.launch {
            repository.getLikedTracks().collect { tracks ->
                _uiState.update { it.copy(likedTracks = tracks) }
            }
        }
    }

    private fun observeRecentTracks() {
        viewModelScope.launch {
            repository.getRecentTracks().collect { tracks ->
                _uiState.update { it.copy(historyTracks = tracks) }
            }
        }
    }

    private fun observeDownloadedTracks() {
        viewModelScope.launch {
            repository.getDownloadedTracks().collect { tracks ->
                _uiState.update { it.copy(downloadedTracks = tracks) }
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            repository.getPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    // ── Lokale Playlists ─────────────────────────────────────────────────

    fun selectPlaylist(playlist: Playlist) {
        _uiState.update { it.copy(selectedPlaylist = playlist, selectedPlaylistTracks = emptyList()) }
        playlistTracksJob?.cancel()
        playlistTracksJob = viewModelScope.launch {
            repository.getPlaylistTracks(playlist.id).collect { tracks ->
                _uiState.update { it.copy(selectedPlaylistTracks = tracks) }
            }
        }
    }

    fun deselectPlaylist() {
        playlistTracksJob?.cancel()
        _uiState.update { it.copy(selectedPlaylist = null, selectedPlaylistTracks = emptyList()) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
            if (_uiState.value.selectedPlaylist?.id == id) deselectPlaylist()
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch { repository.addTrackToPlaylist(playlistId, track) }
    }

    fun removeTrackFromPlaylist(trackId: String) {
        val playlistId = _uiState.value.selectedPlaylist?.id ?: return
        viewModelScope.launch { repository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun unlikeTrack(trackId: String) {
        viewModelScope.launch { repository.unlikeTrack(trackId) }
    }

    fun deleteDownload(trackId: String) {
        viewModelScope.launch { repository.deleteDownload(trackId) }
    }

    // ── YouTube Playlists ────────────────────────────────────────────────

    fun updateYtSearchQuery(query: String) {
        _uiState.update { it.copy(ytSearchQuery = query) }
    }

    fun searchYouTubePlaylists() {
        val query = _uiState.value.ytSearchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(ytIsSearching = true, ytError = null, ytSearchResults = emptyList()) }
            repository.searchYouTubePlaylists(query)
                .onSuccess { playlists ->
                    _uiState.update { it.copy(ytSearchResults = playlists, ytIsSearching = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(ytIsSearching = false, ytError = error.message ?: "Suche fehlgeschlagen") }
                }
        }
    }

    fun selectYouTubePlaylist(playlist: YouTubePlaylist) {
        _uiState.update { it.copy(ytSelectedPlaylist = playlist, ytPlaylistTracks = emptyList(), ytIsLoadingTracks = true, ytError = null) }
        viewModelScope.launch {
            repository.getYouTubePlaylistTracks(playlist.url)
                .onSuccess { tracks ->
                    _uiState.update { it.copy(ytPlaylistTracks = tracks, ytIsLoadingTracks = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(ytIsLoadingTracks = false, ytError = error.message ?: "Tracks konnten nicht geladen werden") }
                }
        }
    }

    fun deselectYouTubePlaylist() {
        _uiState.update { it.copy(ytSelectedPlaylist = null, ytPlaylistTracks = emptyList()) }
    }

    fun clearYtError() {
        _uiState.update { it.copy(ytError = null) }
    }
}