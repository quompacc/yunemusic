package com.yunemusic.data

import android.app.SearchManager
import android.content.Intent
import android.provider.MediaStore
import com.yunemusic.service.EXTRA_CONTROL_TOKEN
import com.yunemusic.service.isAuthorizedPlaybackCommand
import com.yunemusic.service.voiceSearchQuery
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaybackIntentsTest {
    @Test fun externalExplicitCommandWithoutCapabilityIsRejected() {
        assertFalse(isAuthorizedPlaybackCommand(Intent("com.yunemusic.ACTION_NEXT"), "secret"))
        assertFalse(isAuthorizedPlaybackCommand(null, "secret"))
    }
    @Test fun wrongOrPreviousServiceCapabilityIsRejected() {
        val intent = Intent().putExtra(EXTRA_CONTROL_TOKEN, "old-token")
        assertFalse(isAuthorizedPlaybackCommand(intent, "new-token"))
        assertFalse(isAuthorizedPlaybackCommand(Intent().putExtra(EXTRA_CONTROL_TOKEN, ""), ""))
    }
    @Test fun ownNotificationCapabilityIsAccepted() {
        assertTrue(isAuthorizedPlaybackCommand(Intent().putExtra(EXTRA_CONTROL_TOKEN, "secret"), "secret"))
    }
    @Test fun voiceQueryTakesPrecedenceOverMetadata() {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, "  Wu-Tang Clan  ")
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, "Different title")
        assertEquals("Wu-Tang Clan", voiceSearchQuery(intent))
    }
    @Test fun structuredVoiceMetadataFormsSearchQuery() {
        val intent = Intent().putExtra(MediaStore.EXTRA_MEDIA_ARTIST, "Artist")
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, "Song")
        assertEquals("Artist Song", voiceSearchQuery(intent))
    }
    @Test fun emptyVoiceRequestMeansResumeNotAnEmptyNetworkSearch() {
        assertNull(voiceSearchQuery(Intent().putExtra(SearchManager.QUERY, "   ")))
    }
}
