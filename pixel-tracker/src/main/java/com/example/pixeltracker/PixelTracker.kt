package com.example.pixeltracker

import android.content.Context
import android.view.ViewGroup
import com.example.pixeltracker.api.PixelConfig
import com.example.pixeltracker.api.PixelHandle
import com.example.pixeltracker.internal.logger.DefaultPixelLogger
import com.example.pixeltracker.internal.logger.PixelNetworkLogger
import com.example.pixeltracker.internal.tracker.PixelTrackerView
import com.example.pixeltracker.network.PixelNetworkManager

object PixelTracker {

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
        }
    }
}
