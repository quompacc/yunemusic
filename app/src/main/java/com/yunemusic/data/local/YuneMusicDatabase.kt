package com.yunemusic.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.PlaylistDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.local.entities.PlayEventEntity
import com.yunemusic.data.local.entities.PlaylistEntity
import com.yunemusic.data.local.entities.PlaylistTrackEntity
import com.yunemusic.data.local.entities.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlayEventEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class YuneMusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun playlistDao(): PlaylistDao

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
    }
}
