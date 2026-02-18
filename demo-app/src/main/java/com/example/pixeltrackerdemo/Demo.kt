package com.example.pixeltrackerdemo

import android.app.Application
import android.util.Log
import com.example.pixeltracker.PixelTracker

class Demo : Application() {
    override fun onCreate() {
        super.onCreate()

        PixelTracker.initialize("https://your-server.com/api",  true)
    }

    override fun onTerminate() {
        PixelTracker.shutdown()
        super.onTerminate()
    }
}