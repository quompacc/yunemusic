package com.yunemusic.data

import androidx.media3.common.Player
import com.yunemusic.service.playbackLifecycle
import org.junit.Assert.*
import org.junit.Test

class PlaybackLifecycleTest {
    @Test fun endToNextTrackNeverDropsForegroundProtection() {
        val states = listOf(Player.STATE_READY, Player.STATE_ENDED, Player.STATE_BUFFERING, Player.STATE_READY)
        val transitions = states.map { playbackLifecycle(true, true, it, false) }
        assertTrue(transitions.all { it.foreground })
        assertEquals(listOf(false, true, false, false), transitions.map { it.transitionWakeLock })
    }

    @Test fun pauseDuringUrlResolutionReleasesBothProtections() {
        val paused = playbackLifecycle(true, false, Player.STATE_ENDED, false)
        assertFalse(paused.foreground)
        assertFalse(paused.transitionWakeLock)
    }

    @Test fun pauseWhileBufferingIsRecognizedEvenWithoutIsPlayingChange() {
        assertTrue(playbackLifecycle(true, true, Player.STATE_BUFFERING, false).foreground)
        assertFalse(playbackLifecycle(true, false, Player.STATE_BUFFERING, false).foreground)
    }

    @Test fun failedUrlResolutionReleasesBothProtections() {
        val failed = playbackLifecycle(true, true, Player.STATE_ENDED, true)
        assertFalse(failed.foreground)
        assertFalse(failed.transitionWakeLock)
    }

    @Test fun stoppedOrUninitializedPlaybackDoesNotHoldProtection() {
        assertFalse(playbackLifecycle(true, true, Player.STATE_IDLE, false).foreground)
        assertFalse(playbackLifecycle(false, true, Player.STATE_ENDED, false).transitionWakeLock)
    }
}
