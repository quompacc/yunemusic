package com.yunemusic.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
