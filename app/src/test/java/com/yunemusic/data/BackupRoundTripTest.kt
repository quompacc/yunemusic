package com.yunemusic.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.yunemusic.data.backup.BackupManager
import com.yunemusic.data.local.YuneMusicDatabase
import com.yunemusic.data.local.entities.DownloadEntity
import com.yunemusic.data.local.entities.PlayEventEntity
import com.yunemusic.data.local.entities.PlaylistEntity
import com.yunemusic.data.local.entities.PlaylistTrackEntity
import com.yunemusic.data.local.entities.TrackEntity
import com.yunemusic.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Simulates a move: export on the old device, import on a fresh one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupRoundTripTest {
    private lateinit var context: Context
    private lateinit var prefs: UserPreferences
    private lateinit var oldDevice: YuneMusicDatabase
    private lateinit var newDevice: YuneMusicDatabase
    private lateinit var file: File

    private val profileJson =
        """{"favoriteArtists":{"Artist":4.5},"dislikedArtists":["Skipped"],"playCount":42,"futureField":true}"""

    @Before fun prepare() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        prefs = UserPreferences(context)
        oldDevice = inMemoryDatabase()
        newDevice = inMemoryDatabase()
        file = File(context.cacheDir, "backup.json").apply { delete() }

        oldDevice.trackDao().insertTracks(listOf(
            TrackEntity("liked", "Liked song", "Artist", "cover", 180, "Jazz", 90, isLiked = true, addedAt = 5),
            TrackEntity("saved", "Saved song", "Other", "cover2", 200, addedAt = 6)
        ))
        oldDevice.playEventDao().insertEvents(listOf(
            PlayEventEntity(trackId = "liked", timestamp = 100, playedSeconds = 180, totalSeconds = 180, skipped = false, liked = true),
            PlayEventEntity(trackId = "saved", timestamp = 200, playedSeconds = 10, totalSeconds = 200, skipped = true, liked = false)
        ))
        oldDevice.playlistDao().insertPlaylists(listOf(PlaylistEntity(7, "Road trip", 50)))
        oldDevice.playlistDao().insertPlaylistTracks(listOf(
            PlaylistTrackEntity(7, "liked", "Liked song", "Artist", "cover", 180, position = 0, addedAt = 51)
        ))
        oldDevice.downloadDao().insert(
            DownloadEntity("offline", "Offline song", "Third", "cover3", 240, "/old/device/offline.webm", 60)
        )
        prefs.setTasteProfileJson(profileJson)
        prefs.setAudioQuality(2)
        prefs.setLoudnessLimiter(false)
    }

    @After fun cleanup() {
        oldDevice.close()
        newDevice.close()
        file.delete()
    }

    @Test fun moveRestoresTasteHistoryLibraryAndSettings() = runBlocking {
        BackupManager(context, oldDevice, prefs).export(Uri.fromFile(file))

        // The new device starts with its own state, which the import replaces.
        newDevice.trackDao().insertTrack(TrackEntity("stale", "Stale", "X", "", 1))
        prefs.setTasteProfileJson("")
        prefs.setAudioQuality(0)
        prefs.setLoudnessLimiter(true)

        BackupManager(context, newDevice, prefs).import(Uri.fromFile(file))

        val tracks = newDevice.trackDao().getAllTracksOnce().associateBy { it.id }
        assertEquals(setOf("liked", "saved", "offline"), tracks.keys)
        assertEquals(TrackEntity("liked", "Liked song", "Artist", "cover", 180, "Jazz", 90, true, 5), tracks["liked"])
        assertFalse("Former download returns as a library entry", tracks.getValue("offline").isLiked)
        assertTrue("Audio files never move", newDevice.downloadDao().getAllDownloadsOnce().isEmpty())

        val events = newDevice.playEventDao().getRecentEvents(10).sortedBy { it.timestamp }
        assertEquals(listOf("liked", "saved"), events.map { it.trackId })
        assertEquals(listOf(true, false), events.map { it.liked })
        assertEquals(listOf(false, true), events.map { it.skipped })

        assertEquals(listOf(PlaylistEntity(7, "Road trip", 50)), newDevice.playlistDao().getAllPlaylists())
        assertEquals(
            listOf(PlaylistTrackEntity(7, "liked", "Liked song", "Artist", "cover", 180, 0, 51)),
            newDevice.playlistDao().getAllPlaylistTracks()
        )

        val restoredProfile = prefs.tasteProfileJson.first()
        assertTrue(restoredProfile.contains("\"playCount\":42"))
        assertTrue("Unknown fields survive", restoredProfile.contains("futureField"))
        assertEquals(2, prefs.audioQuality.first())
        assertFalse(prefs.loudnessLimiter.first())
    }

    @Test fun foreignFileIsRejectedWithoutTouchingData() = runBlocking {
        file.writeText("""{"format":"something-else","version":1,"exportedAt":0}""")
        newDevice.trackDao().insertTrack(TrackEntity("kept", "Kept", "X", "", 1))

        val result = runCatching { BackupManager(context, newDevice, prefs).import(Uri.fromFile(file)) }

        assertTrue(result.isFailure)
        assertEquals(listOf("kept"), newDevice.trackDao().getAllTracksOnce().map { it.id })
    }

    private fun inMemoryDatabase() =
        Room.inMemoryDatabaseBuilder(context, YuneMusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}
