package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import javax.inject.Inject

class SearchTracksUseCase @Inject constructor(
    private val repository: MusicRepository
) {
    suspend operator fun invoke(query: String): Result<List<Track>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchTracks(query.trim())
    }
}
