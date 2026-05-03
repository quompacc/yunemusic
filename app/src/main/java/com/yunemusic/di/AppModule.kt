package com.yunemusic.di

import android.content.Context
import androidx.room.Room
import com.yunemusic.data.local.YuneMusicDatabase
import com.yunemusic.data.local.dao.PlayEventDao
import com.yunemusic.data.local.dao.TrackDao
import com.yunemusic.data.preferences.UserPreferences
import com.yunemusic.data.repository.MusicRepositoryImpl
import com.yunemusic.data.youtube.YouTubeRepository
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
        Room.databaseBuilder(context, YuneMusicDatabase::class.java, "yune_music.db").build()

    @Provides
    @Singleton
    fun provideTrackDao(database: YuneMusicDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    fun providePlayEventDao(database: YuneMusicDatabase): PlayEventDao = database.playEventDao()
}

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences =
        UserPreferences(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository
}
