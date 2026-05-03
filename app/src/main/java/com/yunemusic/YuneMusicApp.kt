package com.yunemusic

import android.app.Application
import com.yunemusic.data.youtube.DownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe

@HiltAndroidApp
class YuneMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl.getInstance())
    }
}
