package com.veonadtech.pixeltrackerdemo

import android.app.Application
import android.util.Log
import com.veonadtech.pixeltracker.InitStatus
import com.veonadtech.pixeltracker.PixelTracker

class Demo : Application() {

    companion object {
        private const val TAG = "PixelTracker"
        private const val BASEURL = "https://api-pixel-tracker.veonadtech.com/v1/pixel-event"
        private const val ISDEBUGMODE = true
    }

    override fun onCreate() {
        super.onCreate()

        PixelTracker.initialize(BASEURL, ISDEBUGMODE) { status ->
            when (status) {
                is InitStatus.Success ->
                    Log.d(TAG, "Pixel tracker SDK initialized with URL: $BASEURL, debugMode: $ISDEBUGMODE")
                is InitStatus.Failure -> {
                    Log.e(TAG, "${status.reason}: ${status.exception.message}", status.exception)
                }
            }
        }
    }
}
