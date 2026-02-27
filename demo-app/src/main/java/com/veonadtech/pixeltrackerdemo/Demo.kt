package com.veonadtech.pixeltrackerdemo

import android.app.Application
import com.veonadtech.pixeltracker.PixelTracker

class Demo : Application() {

    override fun onCreate() {
        super.onCreate()

        PixelTracker.initialize("http://185.203.239.197:9111/v1/pixel-event", true)
    }
}
