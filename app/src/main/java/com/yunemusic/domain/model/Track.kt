package com.yunemusic.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Track(
    val id: String,           // YouTube Video ID
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val genre: String = "",
    val bpm: Int = 0
) : Parcelable
