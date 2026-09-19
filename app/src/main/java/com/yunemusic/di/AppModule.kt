package com.yunemusic.di

import android.content.Context
import androidx.room.Room
import com.yunemusic.data.local.YuneMusicDatabase
import com.yunemusic.data.local.dao.DownloadDao
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.PlaylistDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.repository.MusicRepositoryImpl
import com.yunemusic.domain.repository.MusicRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YuneMusicDatabase =
        // Update invariant: keep this name and explicit migrations. Never add
        // fallbackToDestructiveMigration: missing migrations must fail, not erase user data.
        Room.databaseBuilder(context, YuneMusicDatabase::class.java, "yune_music.db")
            .addMigrations(
                YuneMusicDatabase.MIGRATION_1_2,
                YuneMusicDatabase.MIGRATION_2_3,
                YuneMusicDatabase.MIGRATION_3_4
            )
            .build()

    @Provides
    @Singleton
    fun provideTrackDao(database: YuneMusicDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    fun providePlayEventDao(database: YuneMusicDatabase): PlayEventDao = database.playEventDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: YuneMusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideDownloadDao(database: YuneMusicDatabase): DownloadDao = database.downloadDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository
}
