package com.veonadtech.pixeltrackerdemo

import android.app.Application
import com.veonadtech.pixeltracker.BuildConfig
import com.veonadtech.pixeltracker.PixelTracker

class Demo : Application() {

    override fun onCreate() {
        super.onCreate()

        PixelTracker.initialize("https://your-server.com/api", BuildConfig.DEBUG)
    }
}
