package com.yunemusic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TasteProfile(
    val favoriteArtists: Map<String, Float> = emptyMap(),
    val favoriteGenres: Map<String, Float> = emptyMap(),
    val dislikedArtists: Set<String> = emptySet(),
    val topTrackIds: List<String> = emptyList(), // liked/completed tracks for related lookups
    val playCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
