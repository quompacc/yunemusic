package com.yunemusic.ui.discover

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunemusic.R
import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import com.yunemusic.domain.usecase.GetRecommendationsUseCase
import com.yunemusic.domain.usecase.SearchTracksUseCase
import com.yunemusic.domain.usecase.BuildPersonalSessionUseCase
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

data class SmartMix(
    /** Genre key; the display label is formatted in the UI (R.string.discover_genre_mix). */
    val genre: String,
    val emoji: String,
    val tracks: List<Track>
)

data class DiscoverUiState(
    val isBuildingSession: Boolean = false,
    @StringRes val sessionError: Int? = null,
    val recommendations: List<Track> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val featuredTracks: List<Track> = emptyList(),
    val smartMixes: List<SmartMix> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val isLoadingRecommendations: Boolean = false,
    val isLoadingTrending: Boolean = false,
    val isLoadingSearch: Boolean = false,
    @StringRes val error: Int? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val isSearchActive: Boolean = false,
    val hasPersonalProfile: Boolean = false
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val searchTracksUseCase: SearchTracksUseCase,
    private val buildPersonalSession: BuildPersonalSessionUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun startSession(onReady: (List<Track>) -> Unit) {
        if (_uiState.value.isBuildingSession) return
        _uiState.update { it.copy(isBuildingSession = true, sessionError = null) }
        viewModelScope.launch {
            try {
                val tracks = buildPersonalSession()
                if (tracks.isEmpty()) {
                    _uiState.update { it.copy(sessionError = R.string.discover_session_no_data) }
                } else {
                    onReady(tracks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(sessionError = R.string.discover_session_failed) }
            } finally {
                _uiState.update { it.copy(isBuildingSession = false) }
            }
        }
    }

    init {
        loadContent()
        observeRecentTracks()
    }

    fun loadContent() {
        loadRecommendations()
        loadTrending()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecommendations = true, error = null) }
            val profile = repository.getTasteProfile()
            getRecommendationsUseCase()
                .onSuccess { tracks ->
                    _uiState.update {
                        it.copy(
                            recommendations = tracks,
                            isLoadingRecommendations = false,
                            hasPersonalProfile = profile.playCount >= 5
                        )
                    }
                    if (profile.favoriteGenres.isNotEmpty()) {
                        loadSmartMixes(profile.favoriteGenres)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingRecommendations = false,
                            error = mapErrorMessage(error)
                        )
                    }
                }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrending = true) }
            repository.getTrending()
                .onSuccess { tracks ->
                    val featured = tracks.filter { it.thumbnailUrl.isNotEmpty() }.take(6)
                    _uiState.update { it.copy(trendingTracks = tracks, featuredTracks = featured, isLoadingTrending = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingTrending = false) }
                }
        }
    }

    private fun loadSmartMixes(favoriteGenres: Map<String, Float>) {
        val topGenres = favoriteGenres.entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }

        viewModelScope.launch {
            val mixes = mutableListOf<SmartMix>()
            for (genre in topGenres) {
                repository.searchTracks("$genre music").onSuccess { tracks ->
                    if (tracks.isNotEmpty()) {
                        mixes.add(
                            SmartMix(
                                genre = genre,
                                emoji = genreEmoji(genre),
                                tracks = tracks.take(10)
                            )
                        )
                    }
                }
            }
            if (mixes.isNotEmpty()) {
                _uiState.update { it.copy(smartMixes = mixes) }
            }
        }
    }

    private fun genreEmoji(genre: String): String = when (genre.lowercase()) {
        "rock" -> "🎸"
        "electronic", "edm", "electronic music" -> "🎛"
        "jazz" -> "🎷"
        "hip-hop", "rap", "hip hop" -> "🎤"
        "classical" -> "🎻"
        "pop" -> "🎵"
        "r&b", "rnb" -> "🎶"
        "metal" -> "🤘"
        "country" -> "🤠"
        "reggae" -> "🌴"
        else -> "🎵"
    }

    fun search(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            clearSearch()
            return
        }

        _uiState.update { it.copy(isSearchActive = true) }

        searchJob = viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(isLoadingSearch = true, error = null) }
            searchTracksUseCase(query)
                .onSuccess { tracks ->
                    _uiState.update { it.copy(searchResults = tracks, isLoadingSearch = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingSearch = false,
                            error = mapErrorMessage(error)
                        )
                    }
                }
        }
    }

    fun searchByCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        search("$category music")
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isLoadingSearch = false,
                error = null,
                isSearchActive = false,
                selectedCategory = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Mappt technische Exceptions (inkl. Ursachenkette) auf verständliche, lokalisierte Meldungen.
     */
    @StringRes
    private fun mapErrorMessage(error: Throwable): Int {
        val chain = generateSequence(error) { it.cause }.take(10).toList()
        return when {
            chain.any { it is UnknownHostException || it is SocketTimeoutException } ->
                R.string.discover_error_no_internet
            chain.any { it is ReCaptchaException } ->
                R.string.discover_error_recaptcha
            chain.any { it is ContentNotAvailableException } ->
                R.string.discover_error_unavailable
            chain.any { it is IOException } ->
                R.string.discover_error_no_internet
            chain.any { it is ExtractionException } ->
                R.string.discover_error_extraction
            else -> R.string.discover_error_generic
        }
    }

    private fun observeRecentTracks() {
        viewModelScope.launch {
            repository.getRecentTracks().collect { tracks ->
                _uiState.update { it.copy(recentTracks = tracks) }
            }
        }
    }
}
