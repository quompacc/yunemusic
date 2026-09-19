package com.yunemusic.service

import androidx.media3.common.Player

/** Silence while buffering or resolving the next track is not a user pause. */
internal data class PlaybackLifecycle(val foreground: Boolean, val transitionWakeLock: Boolean)

internal fun playbackLifecycle(
    hasTrack: Boolean, playWhenReady: Boolean, state: Int, failed: Boolean
): PlaybackLifecycle {
    val active = hasTrack && playWhenReady && !failed && state != Player.STATE_IDLE
    return PlaybackLifecycle(active, active && state == Player.STATE_ENDED)
}
