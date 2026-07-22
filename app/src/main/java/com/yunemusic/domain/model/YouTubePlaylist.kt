package com.yunemusic.domain.model

data class YouTubePlaylist(
    val id: String,            // YouTube Playlist-ID (z.B. "PLxxx")
    val title: String,
    val channelName: String,   // Uploader / Künstler
    val thumbnailUrl: String,
    val trackCount: Int,
    val url: String
)