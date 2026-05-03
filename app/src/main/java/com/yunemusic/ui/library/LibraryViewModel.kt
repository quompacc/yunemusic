package com.yunemusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val likedTracks: List<Track> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeLikedTracks()
        observeRecentTracks()
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

    fun unlikeTrack(trackId: String) {
        viewModelScope.launch {
            repository.unlikeTrack(trackId)
        }
    }
}
