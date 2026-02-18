package com.example.pixeltracker

import android.content.Context
import android.util.Log
import com.example.pixeltracker.network.PixelNetworkManager

object PixelTracker {

    // Constants

    private const val TAG = "PixelTracker"

    // Private state

    private var networkManager: PixelNetworkManager? = null
    private var networkLogger: NetworkPixelLogger? = null
    private var isDebugMode: Boolean = false

    // Public API

    fun initialize(baseUrl: String, isDebugMode: Boolean = false) {
        if (networkManager != null) {
            Log.d(TAG, "Already initialized")
            return
        }

        networkLogger = NetworkPixelLogger()

        this.isDebugMode = isDebugMode
        networkManager = PixelNetworkManager(baseUrl, isDebugMode)

        Log.d(TAG, "Initialized with URL: $baseUrl, debugMode: $isDebugMode")
    }

    fun createView(
        context: Context,
        pixelId: String,
        refreshTimeSeconds: Long = 0L,
        pixelSize: Int = 1
    ): PixelTrackerView {

        if (networkManager == null) {
            Log.w(TAG, "Network manager not initialized. Events will not be sent.")
        }

        return PixelTrackerView(context, pixelId).apply {
            refreshTime = refreshTimeSeconds * 1000
            this.pixelSize = pixelSize
            isDebugMode = this@PixelTracker.isDebugMode
        }
    }

    fun shutdown() {
        networkManager?.shutdown()
        networkManager = null
        networkLogger?.shutdown()
        networkLogger = null
        Log.d(TAG, "Shutdown complete")
    }

    // Internal API

    internal fun getNetworkManager(): PixelNetworkManager? = networkManager

}