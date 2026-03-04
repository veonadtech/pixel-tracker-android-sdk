package com.veonadtech.pixeltracker

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.veonadtech.pixeltracker.api.PixelConfig
import com.veonadtech.pixeltracker.api.PixelHandle
import com.veonadtech.pixeltracker.internal.logger.DefaultPixelLogger
import com.veonadtech.pixeltracker.internal.logger.PixelNetworkLogger
import com.veonadtech.pixeltracker.internal.network.PixelNetworkManager
import com.veonadtech.pixeltracker.internal.tracker.PixelTrackerView
import java.util.Collections

object PixelTracker {

    private const val TAG = "PixelTracker"

    private val lock = Any()

    @Volatile
    private var networkManager: PixelNetworkManager? = null

    @Volatile
    private var pixelNetworkLogger: PixelNetworkLogger? = null

    private val activeViews =
        Collections.synchronizedSet(mutableSetOf<PixelTrackerView>())

    fun initialize(baseUrl: String, isDebugMode: Boolean = false) {
        synchronized(lock) {
            if (networkManager != null) return

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
        synchronized(lock) {
            val logger = pixelNetworkLogger
                ?: throw IllegalStateException("PixelTracker must be initialized before attach(). Call initialize() first.")

            val view = PixelTrackerView(
                context = context,
                config = config,
                logger = logger,
                onDestroyed = { destroyedView ->
                    activeViews.remove(destroyedView)
                }
            )

            container.addView(view)
            activeViews.add(view)

            return view
        }
    }

    fun shutdown() {
        synchronized(lock) {
            activeViews.toList().forEach { it.shutdown() }
            activeViews.clear()

            networkManager?.shutdown()
            networkManager = null

            pixelNetworkLogger?.shutdown()
            pixelNetworkLogger = null

            Log.d(TAG, "Pixel tracker SDK shutdown")
        }
    }
}
