package com.yunemusic.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yunemusic.data.local.dao.DownloadDao
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.PlaylistDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.local.entities.DownloadEntity
import com.yunemusic.data.local.entities.PlayEventEntity
import com.yunemusic.data.local.entities.PlaylistEntity
import com.yunemusic.data.local.entities.PlaylistTrackEntity
import com.yunemusic.data.local.entities.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlayEventEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class YuneMusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlistId INTEGER NOT NULL,
                        trackId TEXT NOT NULL,
                        trackTitle TEXT NOT NULL,
                        trackChannelName TEXT NOT NULL,
                        trackThumbnailUrl TEXT NOT NULL,
                        trackDurationSeconds INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, trackId)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Selbstheilung für Schema-Drift: Installationen aus der Entwicklungszeit
                // hatten teils Version 3 OHNE diese Tabellen — Room validiert nach der
                // Migration das Gesamtschema und crasht sonst beim App-Start
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS downloads (
                        videoId TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        channelName TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        filePath TEXT NOT NULL,
                        downloadedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlistId INTEGER NOT NULL,
                        trackId TEXT NOT NULL,
                        trackTitle TEXT NOT NULL,
                        trackChannelName TEXT NOT NULL,
                        trackThumbnailUrl TEXT NOT NULL,
                        trackDurationSeconds INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, trackId)
                    )
                """.trimIndent())

                // play_events wird pro Wiedergabe größer — ohne Indizes werden
                // Empfehlungen/History mit der Zeit spürbar langsam
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_trackId ON play_events(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_timestamp ON play_events(timestamp)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS downloads (
                        videoId TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        channelName TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        filePath TEXT NOT NULL,
                        downloadedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}