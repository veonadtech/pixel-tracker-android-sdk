package com.example.pixeltracker

import android.content.Context
import android.util.Log
import com.example.pixeltracker.network.PixelNetworkManager

object PixelTracker {

    // Constants

    private const val TAG = "PixelTracker"

    private val lock = Any()

    // Private var

    @Volatile
    private var networkManager: PixelNetworkManager? = null

    @Volatile
    private var networkLogger: NetworkPixelLogger? = null

    @Volatile
    private var isDebugMode: Boolean = false

    // Public API

    fun initialize(baseUrl: String, isDebugMode: Boolean = false) {
        synchronized(lock) {

            if (networkManager != null) {
                Log.d(TAG, "Already initialized")
                return
            }

            this.isDebugMode = isDebugMode

            val manager = PixelNetworkManager(baseUrl, isDebugMode)

            val logger = NetworkPixelLogger(manager).apply {
                setDelegate(
                    DefaultPixelLogger().apply {
                        this.isDebugMode = isDebugMode
                    }
                )
            }

            networkManager = manager
            networkLogger = logger

            Log.d(TAG, "Initialized with URL: $baseUrl, debugMode: $isDebugMode")
        }
    }

    fun createView(
        context: Context,
        pixelId: String,
        refreshTimeSeconds: Long = 0L,
        pixelSize: Int = 1
    ): PixelTrackerView {

        val logger = networkLogger ?: createFallbackLogger()

        return PixelTrackerView(context, pixelId).apply {
            refreshTime = refreshTimeSeconds * 1000
            this.pixelSize = pixelSize
            isDebugMode = this@PixelTracker.isDebugMode
            setLogger(logger)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            networkManager?.shutdown()
            networkLogger?.shutdown()

            networkManager = null
            networkLogger = null

            Log.d(TAG, "Shutdown complete")
        }
    }

    // Private methods

    private fun createFallbackLogger(): PixelLogger {
        return DefaultPixelLogger().apply {
            isDebugMode = this@PixelTracker.isDebugMode
        }
    }
}
