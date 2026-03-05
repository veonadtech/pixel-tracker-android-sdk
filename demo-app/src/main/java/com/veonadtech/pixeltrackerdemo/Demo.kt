package com.veonadtech.pixeltrackerdemo

import android.app.Application
import android.util.Log
import com.veonadtech.pixeltracker.PixelTracker

class Demo : Application() {

    companion object {
        private const val TAG = "PixelTracker"
        private const val BASEURL = "https://api-pixel-tracker.veonadtech.com/v1/pixel-event"
        private const val ISDEBUGMODE = true
    }

    override fun onCreate() {
        super.onCreate()

        try {

            PixelTracker.initialize(
                BASEURL,
                ISDEBUGMODE
            )

            Log.d(TAG, "Pixel tracker SDK initialized with URL: $BASEURL, debugMode: $ISDEBUGMODE")

        } catch (e: IllegalArgumentException) {

            Log.e(TAG, "Invalid config: ${e.message}")

        } catch (e: Exception) {

            Log.e(TAG, "Unexpected error: ${e.message}")

        }
    }
}
