package com.veonadtech.pixeltrackerdemo

import android.app.Application
import android.util.Log
import com.veonadtech.pixeltracker.PixelTracker

class Demo : Application() {

    override fun onCreate() {
        super.onCreate()

        try {

            PixelTracker.initialize(
                "https://api-pixel-tracker.veonadtech.com/v1/pixel-event",
                true
            )

        } catch (e: IllegalArgumentException) {

            Log.e("PixelTracker", "Invalid config: ${e.message}")

        } catch (e: Exception) {

            Log.e("PixelTracker", "Unexpected error: ${e.message}")

        }
    }
}
