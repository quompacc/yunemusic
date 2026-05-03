package com.yunemusic.domain.usecase

import com.yunemusic.domain.model.Track
import com.yunemusic.domain.repository.MusicRepository
import javax.inject.Inject

class PlayTrackUseCase @Inject constructor(
    private val repository: MusicRepository
) {
    suspend operator fun invoke(track: Track): Result<String> {
        repository.saveTrack(track)
        return repository.getStreamUrl(track.id)
    }
}
