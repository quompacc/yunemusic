package com.yunemusic.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.robolectric.RuntimeEnvironment

import com.yunemusic.data.local.YuneMusicDatabase
import com.yunemusic.di.DatabaseModule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs against a sandboxed Android filesystem, never a device's real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseUpgradeTest {
    private lateinit var context: Context
    private val name = "yune_music.db"

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        withDatabase { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL("INSERT INTO tracks VALUES ('saved-id','My song','Artist','cover',180,'Jazz',90,1,123)")
            db.execSQL("INSERT INTO play_events VALUES (1,'saved-id',123,120,180,0,1)")
            db.execSQL("INSERT INTO playlists VALUES (7,'My playlist',123)")
            db.execSQL("INSERT INTO playlist_tracks VALUES (7,'saved-id','My song','Artist','cover',180,0,123)")
            db.execSQL("INSERT INTO downloads VALUES ('saved-id','My song','Artist','cover',180,'/private/saved.webm',123)")
        }
    }

    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun sameSchemaUpdatePreservesAllUserTables() {
        assertPreserved(expectPlaylist = true, expectDownload = true)
    }

    @Test fun versionOneMigratesWithoutLosingLikesOrHistory() {
        downgradeFixture(1)
        assertPreserved(expectPlaylist = false, expectDownload = false)
    }

    @Test fun versionTwoPreservesPlaylistsWhileAddingDownloads() {
        downgradeFixture(2)
        assertPreserved(expectPlaylist = true, expectDownload = false)
    }

    @Test fun versionThreePreservesAllRowsWhileAddingIndexes() {
        downgradeFixture(3)
        assertPreserved(expectPlaylist = true, expectDownload = true)
    }

    @Test fun historicalVersionThreeWithMissingTablesPreservesHistory() {
        downgradeFixture(3, missingTables = true)
        assertPreserved(expectPlaylist = false, expectDownload = false)
    }

    @Test fun unsupportedVersionFailsWithoutDeletingUserData() {
        raw().use { it.version = 99 }
        val room = DatabaseModule.provideDatabase(context)
        try {
            assertTrue("Missing migration must fail, never reset", runCatching {
                room.openHelper.writableDatabase
            }.isFailure)
        } finally { room.close() }
        raw().use { db ->
            db.rawQuery("SELECT title,isLiked FROM tracks WHERE id='saved-id'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("My song", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
            assertEquals(99, db.version)
        }
    }

    private fun withDatabase(block: (YuneMusicDatabase) -> Unit) {
        val room = DatabaseModule.provideDatabase(context)
        try { block(room) } finally { room.close() }
    }

    private fun raw(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)

    private fun downgradeFixture(version: Int, missingTables: Boolean = false) {
        raw().use { db ->
            // Reconstruct the historical structures described by the production migrations.
            if (version < 2 || missingTables) {
                db.execSQL("DROP TABLE playlist_tracks")
                db.execSQL("DROP TABLE playlists")
            }
            if (version < 3 || missingTables) db.execSQL("DROP TABLE downloads")
            db.execSQL("DROP INDEX index_play_events_trackId")
            db.execSQL("DROP INDEX index_play_events_timestamp")
            db.execSQL("DROP TABLE room_master_table")
            db.version = version
        }
    }

    private fun assertPreserved(expectPlaylist: Boolean, expectDownload: Boolean) {
        withDatabase { room ->
            val db = room.openHelper.writableDatabase // Room validates the entire migrated schema.
            assertEquals(4, db.version)
            db.query("SELECT title,isLiked FROM tracks WHERE id='saved-id'").use {
                assertTrue(it.moveToFirst())
                assertEquals("My song", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
            db.query("SELECT playedSeconds,liked FROM play_events WHERE id=1").use {
                assertTrue(it.moveToFirst())
                assertEquals(120, it.getInt(0))
                assertEquals(1, it.getInt(1))
            }
            mapOf("playlists" to expectPlaylist, "playlist_tracks" to expectPlaylist,
                "downloads" to expectDownload).forEach { (table, expected) ->
                db.query("SELECT COUNT(*) FROM $table").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(if (expected) 1 else 0, it.getInt(0))
                }
            }
        }
    }
}
