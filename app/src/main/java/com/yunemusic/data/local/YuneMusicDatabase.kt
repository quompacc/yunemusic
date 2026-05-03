package com.yunemusic.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.local.entities.PlayEventEntity
import com.yunemusic.data.local.entities.TrackEntity

@Database(
    entities = [TrackEntity::class, PlayEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class YuneMusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playEventDao(): PlayEventDao
}
