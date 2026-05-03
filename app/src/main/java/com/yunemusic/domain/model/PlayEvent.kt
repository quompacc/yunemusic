package com.yunemusic.domain.model

data class PlayEvent(
    val trackId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val playedSeconds: Int,
    val totalSeconds: Int,
    val skipped: Boolean = false,
    val liked: Boolean = false
)
