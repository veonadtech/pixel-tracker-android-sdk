package com.veonadtech.pixeltracker

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.veonadtech.pixeltracker.api.PixelConfig
import com.veonadtech.pixeltracker.api.PixelHandle
import com.veonadtech.pixeltracker.internal.logger.DefaultPixelLogger
import com.veonadtech.pixeltracker.internal.logger.PixelNetworkLogger
import com.veonadtech.pixeltracker.internal.tracker.PixelTrackerView
import com.veonadtech.pixeltracker.network.PixelNetworkManager

object PixelTracker {

    private const val TAG = "PixelTracker"


    private val lock = Any()

    @Volatile
    private var networkManager: PixelNetworkManager? = null

    @Volatile
    private var pixelNetworkLogger: PixelNetworkLogger? = null

    @Volatile
    private var isDebugMode: Boolean = false

    fun initialize(baseUrl: String, isDebugMode: Boolean = false) {
        synchronized(lock) {
            if (networkManager != null) return

            this.isDebugMode = isDebugMode

            val manager = PixelNetworkManager(baseUrl, isDebugMode)
            pixelNetworkLogger = PixelNetworkLogger(manager).apply {
                setDelegate(
                    DefaultPixelLogger().apply {
                        this.isDebugMode = isDebugMode
                    }
                )
            }

            networkManager = manager

            Log.d(TAG, "Pixel tracker SDK initialized with URL: $baseUrl, debugMode: $isDebugMode")

        }
    }

    fun attach(
        context: Context,
        container: ViewGroup,
        config: PixelConfig
    ): PixelHandle {

        val view = PixelTrackerView(
            context = context,
            config = config,
            logger = pixelNetworkLogger ?: DefaultPixelLogger()
        )

        container.addView(view)

        return view
    }

    fun shutdown() {
        synchronized(lock) {
            networkManager?.shutdown()
            networkManager = null
            pixelNetworkLogger?.shutdown()
            pixelNetworkLogger = null

            Log.d(TAG, "Pixel tracker SDK shutdown")
        }
    }
}
